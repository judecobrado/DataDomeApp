package com.example.datadomeapp.teacher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.datadomeapp.models.ClassAssignment // Make sure this import is correct

class TeacherScheduleMatrixActivity : AppCompatActivity() {

    private val TAG = "ScheduleMatrix"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tableLayout: TableLayout

    // Define days (Must match the format saved in Firestore)
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Define standard time slots (These are strictly the hour blocks used for ROWS)
    private val timeSlots = listOf(
        "07:00", "08:00", "09:00", "10:00", "11:00", "12:00",
        "13:00", "14:00", "15:00", "16:00", "17:00", "18:00"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_matrix)

        tableLayout = findViewById(R.id.tableLayoutSchedule)

        // 1. Build the empty grid structure (Headers and rows)
        initializeTableLayout()

        // 2. Load the actual class data from Firestore
        loadAssignedClasses()
    }

    private fun initializeTableLayout() {
        // --- 1. Add Column Headers (Day Names) ---
        val headerRow = TableRow(this)
        headerRow.addView(createHeaderCell("Time", isTimeHeader = true))

        days.forEach { day ->
            headerRow.addView(createHeaderCell(day))
        }
        tableLayout.addView(headerRow)

        // --- 2. Add Rows for each Time Slot ---
        timeSlots.forEach { time ->
            val dataRow = TableRow(this)
            dataRow.addView(createHeaderCell(time, isTimeHeader = true))

            // Add an empty cell for each day (to be filled later)
            days.forEach { day ->
                val cell = createDataCell(time, day)
                dataRow.addView(cell)
            }
            tableLayout.addView(dataRow)
        }
        Log.d(TAG, "Table structure initialized with ${days.size} days and ${timeSlots.size} time slots.")
    }

    // Helper function to create the header cells
    private fun createHeaderCell(text: String, isTimeHeader: Boolean = false): TextView {
        val cell = TextView(this).apply {
            this.text = text
            textSize = 12f // Smaller text for better fit
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
            setBackgroundColor(if (isTimeHeader) Color.parseColor("#CCCCCC") else Color.parseColor("#E0E0E0"))
            setTextColor(Color.BLACK)
        }
        // Use a weight of 1 for the Time column, and 2 for the day columns
        val layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, if (isTimeHeader) 1f else 2f)
        cell.layoutParams = layoutParams
        return cell
    }

    // Helper function to create the empty data cells (CardView container)
    private fun createDataCell(startTime: String, day: String): View {
        val cellTag = "$day-$startTime"
        val card = CardView(this).apply {
            tag = cellTag // The key for matching data to the cell
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f).apply {
                setMargins(2, 2, 2, 2)
            }
            radius = 4f
            elevation = 2f
            minimumHeight = 70
            setBackgroundColor(Color.WHITE)
        }
        return card
    }

    private fun loadAssignedClasses() {
        val currentTeacherUid = auth.currentUser?.uid
        if (currentTeacherUid == null) {
            Toast.makeText(this, "Error: Teacher not logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.d(TAG, "Starting class fetch for teacher: $currentTeacherUid")

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = mutableListOf<ClassAssignment>()

                for (document in snapshot.documents) {
                    val assignment = document.toObject(ClassAssignment::class.java)
                    if (assignment != null) {
                        classList.add(assignment.copy(assignmentId = document.id))
                    }
                }

                Log.d(TAG, "✅ Fetch Success. Loaded ${classList.size} classes.")

                if (classList.isEmpty()) {
                    Toast.makeText(this, "You have no classes assigned this week.", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "No classes found.")
                }

                // Populate the schedule matrix
                classList.forEach { assignment ->
                    populateScheduleCell(assignment)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Fetch Failed: Error loading assigned classes: ${e.message}", e)
                Toast.makeText(this, "Failed to load schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Fills a single cell in the grid with class details
    private fun populateScheduleCell(assignment: ClassAssignment) {
        val dayKey = assignment.day // e.g., "Mon"

        // FIX: Instead of just substring, we convert to minutes and find the nearest hour slot
        val timeKey: String = try {
            val fullTime = assignment.startTime.split(":")
            if (fullTime.size >= 2) {
                // Get the hour part, e.g., from "09:29", get "09"
                val hour = fullTime[0]
                // Construct the key for the row slot, e.g., "09:00"
                "$hour:00"
            } else {
                Log.e(TAG, "Invalid startTime format (no colon) for ${assignment.subjectCode}: ${assignment.startTime}")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing startTime for ${assignment.subjectCode}: ${assignment.startTime}", e)
            return
        }

        val cellTag = "$dayKey-$timeKey"
        val cardView = tableLayout.findViewWithTag<CardView>(cellTag)

        if (cardView != null) {
            // Log successful placement
            Log.d(TAG, "Placement Success: ${assignment.subjectCode} placed at $cellTag (Original time: ${assignment.startTime})")

            val tvClassInfo = TextView(this).apply {
                text = "${assignment.subjectCode}\n(${assignment.sectionName})"
                textSize = 10f
                setPadding(4, 4, 4, 4)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#00796B")) // Teal color for a clear block

                // Set the layout params for the TextView inside the CardView
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            cardView.removeAllViews()
            cardView.addView(tvClassInfo)

            // Set Click Listener to navigate to ClassDetails
            cardView.setOnClickListener {
                navigateToClassDetails(assignment)
            }
        } else {
            // Log placement failure (this helps debug data vs timeSlots mismatch)
            Log.w(TAG, "Placement Mismatch: No cell found for $cellTag. Check timeSlots array or data.")
        }
    }

    private fun navigateToClassDetails(assignment: ClassAssignment) {
        val intent = Intent(this, ClassDetailsActivity::class.java)
        // Pass essential data
        intent.putExtra("ASSIGNMENT_ID", assignment.assignmentId)
        intent.putExtra("CLASS_NAME", "${assignment.subjectTitle} - ${assignment.sectionName}")
        intent.putExtra("SUBJECT_CODE", assignment.subjectCode)
        startActivity(intent)
    }
}

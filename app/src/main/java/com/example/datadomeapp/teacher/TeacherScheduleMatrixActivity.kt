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
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.TimeSlot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class TeacherScheduleMatrixActivity : AppCompatActivity() {

    private val TAG = "ScheduleMatrix"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tableLayout: TableLayout

    // Define days (Must match the format saved in Firestore)
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Generate 30-minute interval time slots from 7:00 to 19:00 (7:00 PM)
    private val timeSlots24Hr = generateTimeSlots24Hr("07:00", "19:00", 30)

    // The base height of a single 30-minute card cell (used for flexible height calculation)
    private val BASE_CELL_HEIGHT_DP = 40

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_matrix)

        tableLayout = findViewById(R.id.tableLayoutSchedule)

        initializeTableLayout()
        loadAssignedClasses()
    }

    /**
     * Helper function to generate time slots in 24-hour format with a given interval.
     */
    private fun generateTimeSlots24Hr(startHour: String, endHour: String, intervalMinutes: Int): List<String> {
        val format = SimpleDateFormat("HH:mm", Locale.US)
        val slots = mutableListOf<String>()
        var currentTime = format.parse(startHour)
        val endTime = format.parse(endHour)

        // Loop until the current time is the end time
        while (currentTime != null && endTime != null && currentTime.before(endTime)) {
            slots.add(format.format(currentTime))

            // Add the interval (30 minutes)
            val calendar = java.util.Calendar.getInstance()
            calendar.time = currentTime
            calendar.add(java.util.Calendar.MINUTE, intervalMinutes)
            currentTime = calendar.time
        }
        return slots
    }

    /** Helper function to convert 24hr time (HH:mm) to 12hr time (h:mm a) */
    private fun formatTime12Hr(time24Hr: String): String {
        return try {
            val format24 = SimpleDateFormat("HH:mm", Locale.US)
            val format12 = SimpleDateFormat("h:mm a", Locale.US)
            val date = format24.parse(time24Hr)
            if (date != null) format12.format(date) else time24Hr
        } catch (e: Exception) {
            time24Hr
        }
    }


    private fun initializeTableLayout() {
        // --- 1. Add Column Headers (Day Names) ---
        val headerRow = TableRow(this)
        headerRow.addView(createHeaderCell("Time", isTimeHeader = true))

        days.forEach { day ->
            headerRow.addView(createHeaderCell(day))
        }
        tableLayout.addView(headerRow)

        // --- 2. Add Rows for each 30-Minute Time Slot ---
        timeSlots24Hr.forEach { time24Hr ->
            val dataRow = TableRow(this)

            // Use 12-hour format for display
            val time12Hr = formatTime12Hr(time24Hr)
            dataRow.addView(createHeaderCell(time12Hr, isTimeHeader = true))

            // Add an empty cell for each day (to be filled later)
            days.forEach { day ->
                val cell = createDataCell(time24Hr, day) // Use 24Hr for the tag/key
                dataRow.addView(cell)
            }
            tableLayout.addView(dataRow)
        }
        Log.d(TAG, "Table structure initialized with ${days.size} days and ${timeSlots24Hr.size} time slots.")
    }

    private fun createHeaderCell(text: String, isTimeHeader: Boolean = false): TextView {
        val cell = TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
            setBackgroundColor(if (isTimeHeader) Color.parseColor("#CCCCCC") else Color.parseColor("#E0E0E0"))
            setTextColor(Color.BLACK)
        }
        val layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, if (isTimeHeader) 1f else 2f)
        cell.layoutParams = layoutParams
        return cell
    }

    // Helper function to create the empty data cells (CardView container)
    private fun createDataCell(startTime24Hr: String, day: String): View {
        val cellTag = "$day-$startTime24Hr"
        val card = CardView(this).apply {
            tag = cellTag
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f).apply {
                setMargins(2, 2, 2, 2)
            }
            radius = 4f
            elevation = 2f
            // Set minimum height based on the defined constant
            minimumHeight = (BASE_CELL_HEIGHT_DP * resources.displayMetrics.density).toInt()
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

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = mutableListOf<ClassAssignment>()

                for (document in snapshot.documents) {
                    val assignment = document.toObject(ClassAssignment::class.java)
                    if (assignment != null) {
                        // Using assignmentNo to store the document ID
                        classList.add(assignment.copy(assignmentNo = document.id))
                    }
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "You have no classes assigned this week.", Toast.LENGTH_LONG).show()
                }

                // Populate the schedule matrix
                classList.forEach { assignment ->
                    // Loop through all time slots for this assignment
                    assignment.scheduleSlots.forEach { mapEntry -> // 👈 Pinalitan ang 'slot' ng 'mapEntry' para sa kalinawan
                        val slot = mapEntry.value // 👈 Kinuha ang TimeSlot object (ang Value ng Map Entry)
                        populateScheduleCells(assignment, slot)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Fetch Failed: Error loading assigned classes: ${e.message}", e)
                Toast.makeText(this, "Failed to load schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Fills the starting cell and hides all subsequent cells covered by the class
    private fun populateScheduleCells(assignment: ClassAssignment, slot: TimeSlot) {

        // 1. Calculations
        val startMinutes = timeToMinutes(slot.startTime)
        val endMinutes = timeToMinutes(slot.endTime)
        val durationMinutes = endMinutes - startMinutes

        val placementKey24Hr = findNearestSlotKey(slot.startTime)
        val numBlocks = (durationMinutes / 30).coerceAtLeast(1)

        // Convert DP to Pixels based on screen density
        val density = resources.displayMetrics.density
        val baseHeightPx = (BASE_CELL_HEIGHT_DP * density).toInt()
        val requiredHeightPx = baseHeightPx * numBlocks

        // Get the section name from the TimeSlot
        val sectionBlock = slot.sectionBlock.ifEmpty { "N/A" }

        // 2. PLACE CLASS INFO AND SET CUSTOM HEIGHT ON THE STARTING SLOT
        val firstCellTag = "${slot.day}-$placementKey24Hr"
        val cardView = tableLayout.findViewWithTag<CardView>(firstCellTag)

        if (cardView != null) {

            // CRITICAL: Set Custom Height
            val params = cardView.layoutParams as TableRow.LayoutParams
            params.height = requiredHeightPx
            cardView.layoutParams = params
            cardView.visibility = View.VISIBLE // Ensure it's visible


            // 3. Populate the CardView
            val roomDisplay = if (slot.roomLocation.isNotEmpty()) " (${slot.roomLocation})" else ""
            val startTime12Hr = formatTime12Hr(slot.startTime)
            val endTime12Hr = formatTime12Hr(slot.endTime)

            val tvClassInfo = TextView(this).apply {
                // Use the sectionBlock
                text = "${assignment.subjectCode}\n$sectionBlock\n$startTime12Hr - $endTime12Hr $roomDisplay"
                textSize = 9f
                setPadding(4, 4, 4, 4)
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#00796B"))

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            cardView.removeAllViews()
            cardView.addView(tvClassInfo)
            cardView.elevation = 4f
            cardView.radius = 8f

            // Set Click Listener
            cardView.setOnClickListener {
                navigateToClassDetails(assignment, sectionBlock)
            }
        }

        // 4. HIDE SUBSEQUENT CELLS (CRITICAL STEP)
        var currentSlotMinutes = timeToMinutes(placementKey24Hr)

        // Loop through subsequent 30-minute blocks covered by the class
        for (i in 1 until numBlocks) {
            currentSlotMinutes += 30
            val nextSlot24Hr = minutesToTime(currentSlotMinutes)
            val nextCellTag = "${slot.day}-$nextSlot24Hr"

            val nextCard = tableLayout.findViewWithTag<CardView>(nextCellTag)
            if (nextCard != null) {
                // CRITICAL: Set Height to 0 and V.GONE to make it disappear completely
                nextCard.removeAllViews()
                nextCard.visibility = View.GONE
                val nextParams = nextCard.layoutParams as TableRow.LayoutParams
                nextParams.height = 0
                nextCard.layoutParams = nextParams
            }
        }
    }

    /** Converts HH:mm (24hr) to total minutes from midnight. */
    private fun timeToMinutes(time24Hr: String): Int {
        return try {
            val parts = time24Hr.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    /** Converts total minutes to HH:mm (24hr). */
    private fun minutesToTime(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }

    /** Finds the 30-minute slot key (HH:00 or HH:30) that the class starts in. */
    private fun findNearestSlotKey(startTime24Hr: String): String {
        return timeSlots24Hr.firstOrNull { slot ->
            timeToMinutes(slot) <= timeToMinutes(startTime24Hr) && timeToMinutes(startTime24Hr) < timeToMinutes(slot) + 30
        } ?: timeSlots24Hr.first() // Fallback to 7:00 AM
    }

    private fun navigateToClassDetails(assignment: ClassAssignment, sectionBlock: String) {
        val intent = Intent(this, ClassDetailsActivity::class.java)
        // Use assignment.assignmentNo for consistency
        intent.putExtra("ASSIGNMENT_ID", assignment.assignmentNo)
        intent.putExtra("CLASS_NAME", "${assignment.subjectTitle} - $sectionBlock")
        intent.putExtra("SUBJECT_CODE", assignment.subjectCode)
        startActivity(intent)
    }
}
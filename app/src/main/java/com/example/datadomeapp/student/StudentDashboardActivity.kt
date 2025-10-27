package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout // 🟢 Idinagdag para sa Vertical Layout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
// Model Imports (Tiyakin na ito ang tamang package ng iyong models)
import com.example.datadomeapp.models.StudentSubject
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.TimeSlot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// Activity Imports
import com.example.datadomeapp.LibraryActivity

class StudentDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlDailySchedule: TableLayout
    private lateinit var tvScheduleStatus: TextView
    private lateinit var tvUserInfo: TextView

    // Variable para sa Section ID at Student ID
    private var studentSectionId: String? = null
    private var studentId: String? = null // Gagamitin ito para sa schedule path

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_dashboard)

        tlDailySchedule = findViewById(R.id.tlDailySchedule)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        tvUserInfo = findViewById(R.id.tvUserInfo)

        if (auth.currentUser == null) {
            // Redirect sa Login Activity
            // startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Simulan ang pagkuha ng data at i-set up ang mga button
        loadStudentInfo()
        setupFeatureButtons()
    }

    /**
     * Kukunin ang Section ID at Student ID ng student mula sa Master Record (students/{uid}).
     */
    private fun loadStudentInfo() {
        val uid = auth.currentUser?.uid ?: return
        tvUserInfo.text = "Loading student info..."

        // I-query ang master 'users' collection para makuha ang studentId.
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val fetchedStudentId = userDoc.getString("studentId")
                val fetchedCourseCode = userDoc.getString("courseCode")
                val fetchedYearLevel = userDoc.getString("yearLevel")

                if (fetchedStudentId.isNullOrEmpty()) {
                    // Walang Student ID. Hindi pa fully enrolled.
                    studentId = null
                    tvUserInfo.text = "Section: N/A | Status: Account created, not enrolled."
                    tvScheduleStatus.text = "🚫 Enrollment not finalized. Contact admin."
                    tvScheduleStatus.visibility = TextView.VISIBLE
                    return@addOnSuccessListener
                }

                // Kapag nakuha na ang Student ID, i-load ang master student record para sa Section ID
                firestore.collection("students").document(fetchedStudentId).get()
                    .addOnSuccessListener { studentDoc ->
                        val fetchedSectionId = studentDoc.getString("sectionId")

                        studentId = fetchedStudentId
                        studentSectionId = fetchedSectionId

                        val displayInfo = "ID: $studentId | Course: $fetchedCourseCode $fetchedYearLevel | Section: ${fetchedSectionId ?: "Irregular"}"
                        tvUserInfo.text = displayInfo

                        // Kapag nakuha na ang Student ID, I-LOAD NA AGAD ang schedule gamit ang Student ID.
                        loadDailySchedule(fetchedStudentId)
                        Log.i("SCHEDULE_DEBUG", "Fetched Student ID: $studentId | Section ID: $studentSectionId")
                    }
                    .addOnFailureListener { e ->
                        // Error sa pag-load ng student master record
                        tvUserInfo.text = "Section: Error loading details."
                        tvScheduleStatus.text = "Error loading student details."
                        tvScheduleStatus.visibility = TextView.VISIBLE
                        Log.e("SCHEDULE_DEBUG", "Failed to load student master record.", e)
                    }
            }
            .addOnFailureListener { e ->
                // Error sa pag-load ng user record
                tvUserInfo.text = "Section: Error loading info."
                tvScheduleStatus.text = "Error loading user data: ${e.localizedMessage}"
                tvScheduleStatus.visibility = TextView.VISIBLE
                Log.e("SCHEDULE_DEBUG", "Failed to load user info.", e)
            }
    }

    /**
     * Gumagamit ng Two-Step Fetching (StudentSubject -> ClassAssignment) para kunin ang schedule.
     */
    private fun loadDailySchedule(studentId: String) {
        // Ang currentDay ay laging "Mon", "Tue", etc.
        val currentDay = SimpleDateFormat("EEE", Locale.US).format(Date())
        val timeFormatDisplay = SimpleDateFormat("h:mm a", Locale.US)
        val timeFormatInternal = SimpleDateFormat("HH:mm", Locale.US)
        val currentTimeInternal = timeFormatInternal.format(Date())

        // Gagamitin natin ang Full Day name sa status para mas malinaw sa user
        val currentDayFull = SimpleDateFormat("EEEE", Locale.US).format(Date())

        tvScheduleStatus.text = "Loading classes for $currentDayFull..."
        tvScheduleStatus.visibility = View.VISIBLE

        if (tlDailySchedule.childCount > 1) {
            tlDailySchedule.removeViews(1, tlDailySchedule.childCount - 1)
        }

        Log.i("SCHEDULE_DEBUG", "Checking schedule for abbreviated day: $currentDay (Full: $currentDayFull)")

        // --- STEP 1: Get all StudentSubject records (to extract assignmentNo) ---
        firestore.collection("students")
            .document(studentId)
            .collection("subjects")
            .get()
            .addOnSuccessListener { studentSnapshot ->

                val studentSubjects: List<StudentSubject> = studentSnapshot.documents.mapNotNull {
                    it.toObject(StudentSubject::class.java)
                }

                val assignmentNos: List<String> = studentSubjects
                    .map { subject -> subject.assignmentNo }
                    .filter { no -> no.isNotEmpty() }
                    .distinct()
                    .take(10)

                if (assignmentNos.isEmpty()) {
                    tvScheduleStatus.text = "🎉 **Walang naka-enroll na subject para sa semester na ito.**"
                    tvScheduleStatus.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // --- STEP 2: Fetch the ClassAssignment records (The source of schedule data) ---
                val query = firestore.collection("classAssignments").whereIn("assignmentNo", assignmentNos)

                query.get()
                    .addOnSuccessListener { assignmentSnapshot ->
                        val classAssignments = assignmentSnapshot.documents.mapNotNull { it.toObject(ClassAssignment::class.java) }

                        val todaySchedule = mutableListOf<Map<String, String>>()

                        // --- STEP 3: Merge and Filter ---
                        for (subject in studentSubjects) {
                            val assignment = classAssignments.find { it.assignmentNo == subject.assignmentNo } ?: continue

                            // I-iterate ang lahat ng scheduleSlots ng Assignment
                            for (slot in assignment.scheduleSlots.values) {

                                // A. PAG-AAYOS: Ginawang UPPERCASE ang comparison para maging case-insensitive
                                // Tiyakin na ang day sa Firestore ("Fri") ay mag-ma-match sa currentDay ("Fri")
                                if (slot.day.uppercase(Locale.ROOT) != currentDay.uppercase(Locale.ROOT)) {
                                    Log.d("SCHEDULE_DEBUG", "Day mismatch. Skipping slot day ${slot.day} != $currentDay")
                                    continue
                                }

                                // B. CRITICAL TIME FILTER: Check kung tapos na ang oras.
                                try {
                                    // I-parse ang oras mula sa Firestore (e.g., "8:30 AM")
                                    val endTimeDate = timeFormatDisplay.parse(slot.endTime)
                                    // I-convert sa Internal Format (e.g., "08:30")
                                    val endTimeInternal = timeFormatInternal.format(endTimeDate)

                                    if (endTimeInternal.compareTo(currentTimeInternal) < 0) {
                                        Log.d("SCHEDULE_DEBUG", "Time passed. Skipping class ending at $endTimeInternal. Current time: $currentTimeInternal")
                                        continue // Class ended. Skip.
                                    }
                                } catch (e: Exception) {
                                    Log.e("SCHEDULE_DEBUG", "Time parsing error for ${subject.subjectCode} (${slot.endTime}): ${e.message}")
                                    continue // Skip this slot if time parsing fails
                                }

                                // 4. I-add sa listahan
                                todaySchedule.add(mapOf(
                                    "subjectCode" to subject.subjectCode,
                                    "sectionName" to slot.sectionBlock,
                                    "startTime" to slot.startTime,
                                    "endTime" to slot.endTime,
                                    "venue" to slot.roomLocation
                                ))
                            }
                        }

                        // --- STEP 4: Final Display ---
                        todaySchedule.sortBy { it["startTime"] } // I-sort

                        if (todaySchedule.isEmpty()) {
                            // 🟢 Final "No Schedule" Message
                            tvScheduleStatus.text = "🎉 **Walang natitirang klase ngayong $currentDayFull!** Masiyahan sa iyong araw."
                            tvScheduleStatus.visibility = View.VISIBLE
                            return@addOnSuccessListener
                        }

                        todaySchedule.forEach { item ->
                            tlDailySchedule.addView(createScheduleRow(item))
                        }

                        tvScheduleStatus.visibility = View.GONE
                        Log.i("SCHEDULE_DEBUG", "Schedule render successful. Classes found: ${todaySchedule.size}")

                    }
                    .addOnFailureListener { e ->
                        tvScheduleStatus.text = "❌ Error fetching class assignments: ${e.localizedMessage}"
                        tvScheduleStatus.visibility = View.VISIBLE
                        Log.e("SCHEDULE_DEBUG", "Failed to fetch class assignments.", e)
                    }

            }
            .addOnFailureListener { e ->
                tvScheduleStatus.text = "❌ Error loading student subjects: ${e.localizedMessage}"
                tvScheduleStatus.visibility = View.VISIBLE
                Log.e("SCHEDULE_DEBUG", "Failed to load daily schedule.", e)
            }
    }

    /**
     * Helper function para gumawa ng TableRow para sa bawat class, na may mas maayos na view.
     */
    private fun createScheduleRow(item: Map<String, String>): TableRow {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            setPadding(0, 10, 0, 10)
        }

        // 🟢 Column 1: Time (Oras)
        val tvTime = TextView(this).apply {
            text = "${item["startTime"]}\n- ${item["endTime"]}" // Ginawang 2 lines
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.parseColor("#808080")) // Gray for less emphasis
        }

        // 🟢 Column 2: Subject and Section
        val tvSubject = TextView(this).apply {
            text = "${item["subjectCode"]} (${item["sectionName"]})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.parseColor("#1F3A93")) // Dark Blue for Subject
        }

        // 🟢 Column 3: Venue Room
        val tvVenue = TextView(this).apply {
            text = item["venue"]
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.parseColor("#555555")) // Gray for Venue
        }

        // I-add ang 3 Columns sa Row
        row.addView(tvTime)
        row.addView(tvSubject)
        row.addView(tvVenue)
        return row
    }

    private fun setupFeatureButtons() {
        // Logout Button
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            // Dito mo ilalagay ang redirect sa Login Activity
            // startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // I-set up ang mga button na may "Coming Soon" Toast
        val comingSoonIds = listOf(
            R.id.btnAssignments,
            R.id.btnCanteen
        )

        for (id in comingSoonIds) {
            findViewById<Button>(id).setOnClickListener {
                val buttonText = findViewById<Button>(id).text
                Toast.makeText(this, "$buttonText: Coming Soon!", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAttendance).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load attendance.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentAttendanceActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            val intent = Intent(this, LibraryActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnOnlineClasses).setOnClickListener {
            // 1. Check for the Student ID
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load online classes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentOnlineClassesActivity::class.java)
            // 2. PASS THE STUDENT ID
            intent.putExtra("STUDENT_ID", studentId) // <-- PASS THE CORRECT ID
            startActivity(intent)
        }


        // Full Schedule Button
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load full schedule.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Tiyakin na tama ang target activity (StudentFullScheduleActivity)
            val intent = Intent(this, StudentFullScheduleActivity::class.java)
            // Gamitin ang Student ID bilang "USER_ID" para sa schedule activity
            intent.putExtra("USER_ID", studentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnToDoList).setOnClickListener {
            val intent = Intent(this, StudentToDoListActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnNotes).setOnClickListener {
            val intent = Intent(this, StudentNotesActivity::class.java)
            startActivity(intent)
        }
    }
}

package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.library.UserLibraryActivity
import com.example.datadomeapp.models.StudentSubject
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.TimeSlot
import com.example.datadomeapp.models.Assignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class StudentDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlDailySchedule: TableLayout
    private lateinit var tvScheduleStatus: TextView
    private lateinit var tvUserInfo: TextView
    private lateinit var tvAssignmentAlert: TextView
    private lateinit var tvQuizAlert: TextView
    private lateinit var tvNextClassDetails: TextView

    private var studentSectionId: String? = null
    private var studentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_dashboard)

        initializeViews()

        if (auth.currentUser == null) {
            finish()
            return
        }

        loadStudentInfo()
        setupFeatureButtons()
        // Don't load assignment alerts here - wait until studentId is loaded
    }

    private fun initializeViews() {
        tlDailySchedule = findViewById(R.id.tlDailySchedule)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        tvUserInfo = findViewById(R.id.tvUserInfo)
        tvAssignmentAlert = findViewById(R.id.tvAssignmentAlert)
        tvQuizAlert = findViewById(R.id.tvQuizAlert)
        tvNextClassDetails = findViewById(R.id.tvNextClassDetails)
    }

    private fun loadStudentInfo() {
        val uid = auth.currentUser?.uid ?: return
        tvUserInfo.text = "Loading student info..."

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val fetchedStudentId = userDoc.getString("studentId")
                val fetchedCourseCode = userDoc.getString("courseCode")
                val fetchedYearLevel = userDoc.getString("yearLevel")

                if (fetchedStudentId.isNullOrEmpty()) {
                    studentId = null
                    tvUserInfo.text = "Section: N/A | Status: Account created, not enrolled."
                    tvScheduleStatus.text = "🚫 Enrollment not finalized. Contact admin."
                    tvScheduleStatus.visibility = TextView.VISIBLE
                    tvAssignmentAlert.text = "Complete enrollment to view assignments"
                    return@addOnSuccessListener
                }

                firestore.collection("students").document(fetchedStudentId).get()
                    .addOnSuccessListener { studentDoc ->
                        val fetchedSectionId = studentDoc.getString("sectionId")

                        studentId = fetchedStudentId
                        studentSectionId = fetchedSectionId

                        val displayInfo = "ID: $studentId | Course: $fetchedCourseCode $fetchedYearLevel | Section: ${fetchedSectionId ?: "Irregular"}"
                        tvUserInfo.text = displayInfo

                        loadDailySchedule(fetchedStudentId)
                        loadAssignmentAlerts() // Load assignments AFTER studentId is set
                        Log.i("SCHEDULE_DEBUG", "Fetched Student ID: $studentId | Section ID: $studentSectionId")
                    }
                    .addOnFailureListener { e ->
                        tvUserInfo.text = "Section: Error loading details."
                        tvScheduleStatus.text = "Error loading student details."
                        tvScheduleStatus.visibility = TextView.VISIBLE
                        tvAssignmentAlert.text = "Error loading student data"
                        Log.e("SCHEDULE_DEBUG", "Failed to load student master record.", e)
                    }
            }
            .addOnFailureListener { e ->
                tvUserInfo.text = "Section: Error loading info."
                tvScheduleStatus.text = "Error loading user data: ${e.localizedMessage}"
                tvScheduleStatus.visibility = TextView.VISIBLE
                tvAssignmentAlert.text = "Error loading user data"
                Log.e("SCHEDULE_DEBUG", "Failed to load user info.", e)
            }
    }

    private fun loadDailySchedule(studentId: String) {
        val currentDay = SimpleDateFormat("EEE", Locale.US).format(Date())
        val timeFormatDisplay = SimpleDateFormat("h:mm a", Locale.US)
        val timeFormatInternal = SimpleDateFormat("HH:mm", Locale.US)
        val currentTimeInternal = timeFormatInternal.format(Date())

        val currentDayFull = SimpleDateFormat("EEEE", Locale.US).format(Date())

        tvScheduleStatus.text = "Loading classes for $currentDayFull..."
        tvScheduleStatus.visibility = View.VISIBLE

        if (tlDailySchedule.childCount > 1) {
            tlDailySchedule.removeViews(1, tlDailySchedule.childCount - 1)
        }

        Log.i("SCHEDULE_DEBUG", "Checking schedule for abbreviated day: $currentDay (Full: $currentDayFull)")

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

                val query = firestore.collection("classAssignments").whereIn("assignmentNo", assignmentNos)

                query.get()
                    .addOnSuccessListener { assignmentSnapshot ->
                        val classAssignments = assignmentSnapshot.documents.mapNotNull { it.toObject(ClassAssignment::class.java) }

                        val todaySchedule = mutableListOf<Map<String, String>>()

                        for (subject in studentSubjects) {
                            val assignment = classAssignments.find { it.assignmentNo == subject.assignmentNo } ?: continue

                            for (slot in assignment.scheduleSlots.values) {
                                if (slot.day.uppercase(Locale.ROOT) != currentDay.uppercase(Locale.ROOT)) {
                                    Log.d("SCHEDULE_DEBUG", "Day mismatch. Skipping slot day ${slot.day} != $currentDay")
                                    continue
                                }

                                try {
                                    val endTimeDate = timeFormatDisplay.parse(slot.endTime)
                                    val endTimeInternal = timeFormatInternal.format(endTimeDate)

                                    if (endTimeInternal.compareTo(currentTimeInternal) < 0) {
                                        Log.d("SCHEDULE_DEBUG", "Time passed. Skipping class ending at $endTimeInternal. Current time: $currentTimeInternal")
                                        continue
                                    }
                                } catch (e: Exception) {
                                    Log.e("SCHEDULE_DEBUG", "Time parsing error for ${subject.subjectCode} (${slot.endTime}): ${e.message}")
                                    continue
                                }

                                todaySchedule.add(mapOf(
                                    "subjectCode" to subject.subjectCode,
                                    "sectionName" to slot.sectionBlock,
                                    "startTime" to slot.startTime,
                                    "endTime" to slot.endTime,
                                    "venue" to slot.roomLocation
                                ))
                            }
                        }

                        todaySchedule.sortBy { it["startTime"] }

                        if (todaySchedule.isEmpty()) {
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

    private fun createScheduleRow(item: Map<String, String>): TableRow {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            setPadding(0, 10, 0, 10)
        }

        val tvTime = TextView(this).apply {
            text = "${item["startTime"]}\n- ${item["endTime"]}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.parseColor("#808080"))
        }

        val tvSubject = TextView(this).apply {
            text = "${item["subjectCode"]} (${item["sectionName"]})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.parseColor("#1F3A93"))
        }

        val tvVenue = TextView(this).apply {
            text = item["venue"]
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.parseColor("#555555"))
        }

        row.addView(tvTime)
        row.addView(tvSubject)
        row.addView(tvVenue)
        return row
    }

    private fun loadAssignmentAlerts() {
        // FIXED: Check if studentId is available before proceeding
        if (studentId.isNullOrEmpty()) {
            tvAssignmentAlert.text = "Student ID not available"
            return
        }

        tvAssignmentAlert.text = "Loading assignments..."
        tvQuizAlert.text = "No upcoming quizzes"

        firestore.collection("students")
            .document(studentId!!) // FIXED: Now studentId is guaranteed to be non-null
            .collection("subjects")
            .get()
            .addOnSuccessListener { subjectsSnapshot ->
                val assignmentNos = subjectsSnapshot.documents.mapNotNull {
                    it.getString("assignmentNo")
                }

                if (assignmentNos.isEmpty()) {
                    tvAssignmentAlert.text = "No subjects enrolled"
                    return@addOnSuccessListener
                }

                firestore.collection("assignments")
                    .whereIn("classId", assignmentNos)
                    .whereGreaterThan("dueDateMillis", System.currentTimeMillis())
                    .orderBy("dueDateMillis", Query.Direction.ASCENDING)
                    .limit(3)
                    .get()
                    .addOnSuccessListener { assignmentsSnapshot ->
                        val upcomingCount = assignmentsSnapshot.documents.size

                        runOnUiThread {
                            if (upcomingCount > 0) {
                                tvAssignmentAlert.text = "$upcomingCount assignment(s) due soon"
                                tvAssignmentAlert.setTextColor(Color.RED)
                            } else {
                                tvAssignmentAlert.text = "No upcoming assignments"
                                tvAssignmentAlert.setTextColor(Color.parseColor("#555555"))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        tvAssignmentAlert.text = "Error loading assignments"
                    }
            }
            .addOnFailureListener { e ->
                tvAssignmentAlert.text = "Error loading subjects"
            }
    }

    private fun setupFeatureButtons() {
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            finish()
        }

        // Subjects Button
        findViewById<Button>(R.id.btnSubjects).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load subjects.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, StudentSubjectsActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        // Grades Button - Coming Soon
        findViewById<Button>(R.id.btnGrades).setOnClickListener {
            Toast.makeText(this, "Grades: Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // Quizzes Button - Coming Soon
        findViewById<Button>(R.id.btnQuizzes).setOnClickListener {
            Toast.makeText(this, "Quizzes: Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // Exam Button - Coming Soon
        findViewById<Button>(R.id.btnExam).setOnClickListener {
            Toast.makeText(this, "Exams: Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // Schedule Button
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load full schedule.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, StudentFullScheduleActivity::class.java)
            intent.putExtra("USER_ID", studentId)
            startActivity(intent)
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

        // Library Button
        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            val intent = Intent(this, UserLibraryActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnOnlineClasses).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load online classes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, StudentOnlineClassesActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
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

        // Canteen Button - Coming Soon
        findViewById<Button>(R.id.btnCanteen).setOnClickListener {
            Toast.makeText(this, "Canteen: Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // Profile Button - Coming Soon
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            Toast.makeText(this, "Profile: Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }
}
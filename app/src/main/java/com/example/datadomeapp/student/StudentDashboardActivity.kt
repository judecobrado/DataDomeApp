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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.StudentSubject
import com.example.datadomeapp.models.ClassAssignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlDailySchedule: TableLayout
    private lateinit var tvScheduleStatus: TextView
    private lateinit var tvAssignmentAlert: TextView
    private lateinit var tvQuizAlert: TextView
    private lateinit var tvUserInfo: TextView
    private var studentSectionId: String? = null
    private var studentId: String? = null
    private var studentUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_dashboard)

        tlDailySchedule = findViewById(R.id.tlDailySchedule)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        tvUserInfo = findViewById(R.id.tvUserInfo)
        tvAssignmentAlert = findViewById(R.id.tvAssignmentAlert)
        tvQuizAlert = findViewById(R.id.tvQuizAlert)

        studentUid = intent.getStringExtra("USER_UID") ?: auth.currentUser?.uid

        val finalUid = intent.getStringExtra("USER_UID") ?: auth.currentUser?.uid

        Log.i("DASHBOARD_DEBUG", "Starting dashboard with UID: $finalUid")

        if (finalUid.isNullOrEmpty()) {
            Toast.makeText(this, "Session expired or User ID missing. Please log in again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, com.example.datadomeapp.LoginActivity::class.java))
            finish()
            return
        }

        // Simulan ang pagkuha ng data at i-set up ang mga button
        loadStudentInfo(finalUid)
        setupFeatureButtons()
        setupProfileButton() // 🟢 IDINAGDAG - Profile button functionality
    }

    /**
     * 🟢 IDINAGDAG - Profile Button Click Listener
     */
    private fun setupProfileButton() {
        val btnProfile = findViewById<CardView>(R.id.btnProfile)

        btnProfile.setOnClickListener {
            // I-check kung may student ID na nakuha
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student information not yet loaded. Please wait.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Gumawa ng intent para pumunta sa StudentProfileActivity
            val intent = Intent(this, StudentProfileActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            intent.putExtra("USER_UID", studentUid)
            startActivity(intent)
        }

        Log.i("PROFILE_DEBUG", "Profile button setup complete. Student ID: $studentId")
    }

    /**
     * Kukunin ang Section ID at Student ID ng student mula sa Master Record (students/{uid}).
     */
    private fun loadStudentInfo(uid: String) {
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

            showLogoutConfirmation()

        }

        // Subjects Button
        findViewById<Button>(R.id.btnSubjects).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Check your internet connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, StudentSubjectsActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnGrades).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Check your internet connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentGradesActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        // I-set up ang mga button na may "Coming Soon" Toast
        findViewById<Button>(R.id.btnQuizzes).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Check your internet connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentQuizListActivity::class.java)
            intent.putExtra("QUIZ_TYPE", "Quiz") // Only Quizzes
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnCanteen).setOnClickListener {
            // ✅ I-check kung may valid Student ID (DDS-0008) na nakuha.
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID missing. Cannot open canteen. Please contact admin.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val intent = Intent(this, UserCanteenMenuActivity::class.java)
            intent.putExtra("USER_TYPE", "student")
            // ⭐️ CRITICAL: Ginagamit na ang studentId (e.g., DDS-0008) bilang USER_ID!
            intent.putExtra("USER_ID", studentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnExam).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(
                    this,
                    "Check your internet connection.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentQuizListActivity::class.java)
            intent.putExtra("QUIZ_TYPE", "Exam") // Only Exams
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnAttendance).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Check your internet connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentAttendanceActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnOnlineClasses).setOnClickListener {
            // 1. Check for the Student ID
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Check your internet connection.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Check your internet connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, StudentFullScheduleActivity::class.java)
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

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { dialog, which ->
                performLogout()
            }
            .setNegativeButton("No") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Performs the actual logout process
     */
    private fun performLogout() {
        auth.signOut()

        val intent = Intent(this, com.example.datadomeapp.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

}
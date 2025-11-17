package com.example.datadomeapp.teacher

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.student.UserCanteenMenuActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var teacherUid: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tacher_dashboard) // Make sure this matches your XML filename

        teacherUid = auth.currentUser?.uid

        // Initialize header views
        val tvTeacherName = findViewById<TextView>(R.id.tvTeacherName)
        val tvTeacherId = findViewById<TextView>(R.id.tvTeacherId)
        val tvClassCount = findViewById<TextView>(R.id.tvClassCount)
        val tvStudentCount = findViewById<TextView>(R.id.tvStudentCount)
        val tvPendingCount = findViewById<TextView>(R.id.tvPendingCount)

        // Set initial values
        tvTeacherName.text = "Welcome, Teacher!"
        tvTeacherId.text = "ID: ${teacherUid?.takeLast(6) ?: "TCH-001"}"

        // Load teacher stats
        loadTeacherStats(tvClassCount, tvStudentCount, tvPendingCount)

        // Initialize card views
        val cardManageClasses = findViewById<MaterialCardView>(R.id.cardManageClasses)
        val cardMySchedule = findViewById<MaterialCardView>(R.id.cardMySchedule)
        val cardQuiz = findViewById<MaterialCardView>(R.id.cardQuiz)
        val cardAssessment = findViewById<MaterialCardView>(R.id.cardAssessment)
        val cardAttendance = findViewById<MaterialCardView>(R.id.cardAttendance)
        val cardGrades = findViewById<MaterialCardView>(R.id.cardGrades)
        val cardCanteen = findViewById<MaterialCardView>(R.id.cardCanteen)
        val cardTodo = findViewById<MaterialCardView>(R.id.cardTodo)
        val cardNotes = findViewById<MaterialCardView>(R.id.cardNotes)
        val cardVoice = findViewById<MaterialCardView>(R.id.cardVoice)
        val cardRoulette = findViewById<MaterialCardView>(R.id.cardRoulette)
        val cardLogout = findViewById<MaterialCardView>(R.id.cardLogout)

        // Set click listeners for cards
        cardQuiz.setOnClickListener {
            showClassPickerDialogForActivity("Quiz")
        }

        cardAssessment.setOnClickListener {
            showClassPickerDialogForActivity("Assessment")
        }

        cardAttendance.setOnClickListener {
            showClassPickerDialogForActivity("Attendance")
        }

        cardGrades.setOnClickListener {
            showClassPickerDialogForActivity("Grades")
        }

        // Open Canteen Menu
        cardCanteen.setOnClickListener {
            val intent = Intent(this, UserCanteenMenuActivity::class.java)
            intent.putExtra("USER_TYPE", "teacher")
            intent.putExtra("USER_ID", teacherUid)
            startActivity(intent)
        }

        // Manage Classes
        cardManageClasses.setOnClickListener {
            startActivity(Intent(this, ManageClassesActivity::class.java))
        }

        // Schedule
        cardMySchedule.setOnClickListener {
            startActivity(Intent(this, TeacherScheduleMatrixActivity::class.java))
        }

        // Notes
        cardNotes.setOnClickListener {
            val intent = Intent(this, TeacherNotesActivity::class.java)
            startActivity(intent)
        }

        // To-Do List
        cardTodo.setOnClickListener {
            val intent = Intent(this, TeacherToDoListActivity::class.java)
            startActivity(intent)
        }

        // Voice Detection
        cardVoice.setOnClickListener {
            startActivity(Intent(this, VoiceDetectionActivity::class.java))
        }

        // Roleta
        cardRoulette.setOnClickListener {
            showClassPickerDialogForRouleta()
        }

        // Logout
        cardLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun loadTeacherStats(tvClassCount: TextView, tvStudentCount: TextView, tvPendingCount: TextView) {
        val currentTeacherUid = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // Load class assignments
                val classSnapshot = firestore.collection("classAssignments")
                    .whereEqualTo("teacherUid", currentTeacherUid)
                    .get()
                    .await()

                val classCount = classSnapshot.documents.size
                tvClassCount.text = classCount.toString()

                // Calculate total students across all classes
                var totalStudents = 0
                var pendingCount = 0

                // For demo purposes, set some values - replace with actual logic
                totalStudents = classCount * 25 // Assuming 25 students per class average
                pendingCount = classCount * 3   // Assuming 3 pending items per class

                tvStudentCount.text = totalStudents.toString()
                tvPendingCount.text = pendingCount.toString()

            } catch (e: Exception) {
                Log.e("TeacherDashboard", "Error loading stats: ${e.message}")
                // Set default values
                tvClassCount.text = "0"
                tvStudentCount.text = "0"
                tvPendingCount.text = "0"
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun showClassPickerDialogForActivity(activityType: String) {
        val currentTeacherUid = auth.currentUser?.uid ?: return

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = snapshot.documents.mapNotNull { doc ->
                    val subjectCode = doc.getString("subjectCode") ?: return@mapNotNull null
                    val subjectTitle = doc.getString("subjectTitle") ?: "Unnamed Subject"
                    val semester = doc.getString("semester") ?: return@mapNotNull null
                    val yearLevel = doc.getString("yearLevel") ?: return@mapNotNull null
                    val section = doc.getString("section") ?: "No Section"
                    val course = doc.getString("courseCode") ?: "No Course"
                    val assignmentId = doc.id

                    val yearNumber = yearLevel.replace(Regex("[^0-9]"), "").take(1)
                    val displayText = "$course - $yearNumber$section - $subjectCode"

                    ClassRouletteData(
                        assignmentId = assignmentId,
                        displayText = displayText,
                        subjectCode = subjectCode,
                        subjectTitle = subjectTitle,
                        semester = semester,
                        yearLevel = yearLevel,
                        section = section,
                        course = course
                    )
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "No classes assigned.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val builder = AlertDialog.Builder(this)
                builder.setTitle("Select Class for $activityType")

                val displayItems = classList.map { it.displayText }.toTypedArray()

                builder.setItems(displayItems) { _, which ->
                    val selectedClass = classList[which]
                    navigateToActivity(activityType, selectedClass)
                }

                builder.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

                builder.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load classes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToActivity(activityType: String, classData: ClassRouletteData) {
        val intent = when (activityType) {
            "Quiz" -> Intent(this, ManageQuizzesActivity::class.java)
            "Assessment" -> Intent(this, AssignmentListActivity::class.java)
            "Attendance" -> Intent(this, RecordAttendanceActivity::class.java)
            "Grades" -> Intent(this, ManageGradesActivity::class.java)
            else -> return
        }

        if (activityType == "Assessment") {
            intent.putExtra("assignmentId", classData.assignmentId)
        } else {
            intent.putExtra("ASSIGNMENT_ID", classData.assignmentId)
        }

        intent.putExtra("CLASS_NAME", classData.displayText)
        intent.putExtra("SUBJECT_CODE", classData.subjectCode)

        if (activityType == "Grades") {
            intent.putExtra("SECTION_NAME", classData.section)
            intent.putExtra("YEAR_LEVEL", classData.yearLevel)
        }

        startActivity(intent)
    }

    private fun showClassPickerDialogForRouleta() {
        val currentTeacherUid = auth.currentUser?.uid ?: return

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = snapshot.documents.mapNotNull { doc ->
                    val subjectCode = doc.getString("subjectCode") ?: return@mapNotNull null
                    val subjectTitle = doc.getString("subjectTitle") ?: "Unnamed Subject"
                    val semester = doc.getString("semester") ?: return@mapNotNull null
                    val yearLevel = doc.getString("yearLevel") ?: return@mapNotNull null
                    val section = doc.getString("section") ?: "No Section"
                    val course = doc.getString("courseCode") ?: "No Course"
                    val assignmentId = doc.id

                    val yearNumber = yearLevel.replace(Regex("[^0-9]"), "").take(1)
                    val displayText = "$course - $yearNumber$section - $subjectCode"

                    ClassRouletteData(
                        assignmentId = assignmentId,
                        displayText = displayText,
                        subjectCode = subjectCode,
                        subjectTitle = subjectTitle,
                        semester = semester,
                        yearLevel = yearLevel,
                        section = section,
                        course = course
                    )
                }

                if (classList.isEmpty()) {
                    Toast.makeText(this, "No classes assigned.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val builder = AlertDialog.Builder(this)
                builder.setTitle("Select Class for Roleta")

                val displayItems = classList.map { it.displayText }.toTypedArray()

                builder.setItems(displayItems) { _, which ->
                    val selectedClass = classList[which]
                    loadStudentsAndOpenRoulette(selectedClass)
                }

                builder.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

                builder.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load classes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadStudentsAndOpenRoulette(classData: ClassRouletteData) {
        firestore.collection("students")
            .whereEqualTo("courseCode", classData.course)
            .whereEqualTo("sectionId", classData.section)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No students found for ${classData.displayText}", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val studentDocs = snapshot.documents
                val studentIds = studentDocs.map { it.id }

                lifecycleScope.launch {
                    val enrolledStudentNames = mutableListOf<String>()
                    val enrollmentDocId = "${classData.yearLevel.replace(" ", "")}_${
                        classData.semester.replace(" ", "").replace("-", "")
                    }_${classData.subjectCode}"

                    val enrollmentChecks = studentIds.map { studentId ->
                        async {
                            val doc = firestore.collection("students")
                                .document(studentId)
                                .collection("subjects")
                                .document(enrollmentDocId)
                                .get()
                                .await()

                            if (doc.exists()) {
                                val student = studentDocs.first { it.id == studentId }
                                val firstName = student.getString("firstName") ?: ""
                                val lastName = student.getString("lastName") ?: ""
                                "$lastName, $firstName"
                            } else null
                        }
                    }

                    enrolledStudentNames.addAll(enrollmentChecks.awaitAll().filterNotNull())

                    if (enrolledStudentNames.isEmpty()) {
                        Toast.makeText(this@TeacherDashboardActivity,
                            "No enrolled students found for ${classData.subjectCode}.",
                            Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    // Open RouletteActivity
                    val intent = Intent(this@TeacherDashboardActivity, RouletteActivity::class.java)
                    intent.putStringArrayListExtra("STUDENT_NAMES_LIST", ArrayList(enrolledStudentNames))
                    intent.putExtra("CLASS_NAME", classData.displayText)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load students.", Toast.LENGTH_SHORT).show()
            }
    }

    // Data class to hold class information for roulette
    data class ClassRouletteData(
        val assignmentId: String,
        val displayText: String,
        val subjectCode: String,
        val subjectTitle: String,
        val semester: String,
        val yearLevel: String,
        val section: String,
        val course: String
    )
}
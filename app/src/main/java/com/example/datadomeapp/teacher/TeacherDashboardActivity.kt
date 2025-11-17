package com.example.datadomeapp.teacher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.util.Log
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.student.UserCanteenMenuActivity
import com.example.datadomeapp.models.ClassAssignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TeacherDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var teacherUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tacher_dashboard)

        teacherUid = auth.currentUser?.uid
        val tvDashboard = findViewById<TextView>(R.id.tvDashboard)
        tvDashboard.text = "Welcome, Teacher!"

        val btnCanteen = findViewById<Button>(R.id.btnCanteenMenu)
        val btnManageClasses = findViewById<Button>(R.id.btnManageClasses)
        val btnRecordAttendance = findViewById<Button>(R.id.btnMySchedule)
        val btnVoiceDetection = findViewById<Button>(R.id.btnVoiceDetection)
        val btnQuiz = findViewById<Button>(R.id.btnQuiz)
        val btnAssessment = findViewById<Button>(R.id.btnAssessment)
        val btnAttendance = findViewById<Button>(R.id.btnAttendance)
        val btnGrades = findViewById<Button>(R.id.btnGrades)
        val btnRoulette = findViewById<Button>(R.id.btnRoulette)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        btnQuiz.setOnClickListener {
            showClassPickerDialogForActivity("Quiz")
        }

        btnAssessment.setOnClickListener {
            showClassPickerDialogForActivity("Assessment")
        }

        btnAttendance.setOnClickListener {
            showClassPickerDialogForActivity("Attendance")
        }

        btnGrades.setOnClickListener {
            showClassPickerDialogForActivity("Grades")
        }

        // Open Canteen Menu
        btnCanteen.setOnClickListener {
            val intent = Intent(this, UserCanteenMenuActivity::class.java)
            intent.putExtra("USER_TYPE", "teacher")
            intent.putExtra("USER_ID", teacherUid)
            startActivity(intent)
        }

        // Manage Classes
        btnManageClasses.setOnClickListener {
            startActivity(Intent(this, ManageClassesActivity::class.java))
        }

        // Attendance
        btnRecordAttendance.setOnClickListener {
            startActivity(Intent(this, TeacherScheduleMatrixActivity::class.java))
        }

        val btnNotes = findViewById<Button>(R.id.btnNotes)
        btnNotes.setOnClickListener {
            val intent = Intent(this, TeacherNotesActivity::class.java)
            startActivity(intent)
        }

        // 6. To-Do List Button
        val btnToDoList = findViewById<Button>(R.id.btnToDoList)
        btnToDoList.setOnClickListener {
            val intent = Intent(this, TeacherToDoListActivity::class.java)
            startActivity(intent)
        }

        // Voice Detection
        btnVoiceDetection.setOnClickListener {
            startActivity(Intent(this, VoiceDetectionActivity::class.java))
        }

        // 🎡 Roleta Button with section & subject choices
        btnRoulette.setOnClickListener {
            showClassPickerDialogForRouleta()
        }

        // Logout
        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showClassPickerDialogForActivity(activityType: String) {
        val firestore = FirebaseFirestore.getInstance()
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

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

    // 🆕 BAGONG METHOD: Navigation based on activity type
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
        val firestore = FirebaseFirestore.getInstance()
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

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
                    // FORMAT: Include year level like in Manage Classes
                    // Example: "BSIT 1-A - CS101" or "BSIT - 1st Year - A - CS101"
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

                // Create formatted display items with year level
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
        val firestore = FirebaseFirestore.getInstance()

        // GAMITIN ANG SIMPLER QUERY (tulad ng lumang code)
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
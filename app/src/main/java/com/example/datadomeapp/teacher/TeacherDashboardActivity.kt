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
        val btnManageAllQuizzes = findViewById<Button>(R.id.btnManageAllQuizzes)
        val btnVoiceDetection = findViewById<Button>(R.id.btnVoiceDetection)
        val btnRoulette = findViewById<Button>(R.id.btnRoulette)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

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

        // Quizzes
        btnManageAllQuizzes.setOnClickListener {
            startActivity(Intent(this, ManageQuizzesActivity::class.java))
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

    private fun showClassPickerDialogForRouleta() {
        val firestore = FirebaseFirestore.getInstance()
        val currentTeacherUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classList = snapshot.documents.mapNotNull { doc ->
                    val subjectCode = doc.getString("subjectCode") ?: return@mapNotNull null
                    val rawClassName = doc.getString("subjectTitle")
                    val docClassName = if (rawClassName.isNullOrBlank()) "Unnamed Class" else rawClassName
                    val semester = doc.getString("semester") ?: return@mapNotNull null
                    val yearLevel = doc.getString("yearLevel") ?: return@mapNotNull null
                    val assignmentId = doc.id
                    Triple(
                        assignmentId,
                        "$docClassName - $subjectCode", // for dialog display
                        Triple(subjectCode, semester, yearLevel)
                    )
                }


                if (classList.isEmpty()) {
                    Toast.makeText(this, "No classes assigned.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val builder = android.app.AlertDialog.Builder(this)
                builder.setTitle("Select Class for Roleta")
                builder.setItems(classList.map { it.second }.toTypedArray()) { _, which ->
                    val selectedAssignment = classList[which]
                    val (subjectCode, semester, yearLevel) = selectedAssignment.third

                    // Extract the real class name from "ClassName - SubjectCode"
                    val realClassName = selectedAssignment.second.split(" - ")[0]

                    // Only call once
                    loadStudentsAndOpenRoulette(subjectCode, semester, yearLevel, realClassName)
                }

                builder.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load classes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadStudentsAndOpenRoulette(
        subjectCode: String,
        semester: String,
        yearLevel: String,
        className: String
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val studentNames = arrayListOf<String>()

        // 1. Load students by yearLevel & status
        firestore.collection("students")
            .whereEqualTo("yearLevel", yearLevel)
            .whereEqualTo("status", "Admitted")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No students found for $className", Toast.LENGTH_LONG)
                        .show()
                    return@addOnSuccessListener
                }

                val studentDocs = snapshot.documents
                val studentIds = studentDocs.map { it.id }

                // 2. Check enrollment in the subject (batch)
                lifecycleScope.launch {
                    val enrolledStudentNames = mutableListOf<String>()
                    val enrollmentDocId = "${yearLevel.replace(" ", "")}_${
                        semester.replace(" ", "").replace("-", "")
                    }_$subjectCode"

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
                        Toast.makeText(
                            this@TeacherDashboardActivity,
                            "No enrolled students found.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    // Open RouletteActivity immediately
                    val intent = Intent(this@TeacherDashboardActivity, RouletteActivity::class.java)
                    intent.putStringArrayListExtra(
                        "STUDENT_NAMES_LIST",
                        ArrayList(enrolledStudentNames)
                    )
                    intent.putExtra("CLASS_NAME", className)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load students.", Toast.LENGTH_SHORT).show()
            }
    }
}
package com.example.datadomeapp.teacher

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.datadomeapp.teacher.GradeInputAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GradeInputActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var tvGradeTitle: TextView
    private lateinit var tvLoadingStatus: TextView
    private lateinit var recyclerViewGrades: RecyclerView

    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null
    private var gradingPeriod: String? = null // Prelim, Midterm, Finals

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_grade_input)

        // --- Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")
        className = intent.getStringExtra("CLASS_NAME")
        gradingPeriod = intent.getStringExtra("GRADING_PERIOD")

        tvGradeTitle = findViewById(R.id.tvGradeTitle)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        recyclerViewGrades = findViewById(R.id.recyclerViewGrades)

        recyclerViewGrades.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing grade context.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvGradeTitle.text = "$gradingPeriod Grades\n$className ($subjectCode)"

        // --- Load students and activities ---
        loadGradingData()
    }

    private fun loadGradingData() {
        tvLoadingStatus.text = "Fetching enrolled students..."

        lifecycleScope.launch {
            try {
                // 1️⃣ Get class info (to know section, yearLevel, semester)
                val classDoc = firestore.collection("classAssignments")
                    .document(assignmentId!!)
                    .get().await()

                val yearLevel = classDoc.getString("yearLevel") ?: ""
                val semester = classDoc.getString("semester") ?: ""
                val sectionId = className?.split(" - ")?.lastOrNull() ?: ""

                if (yearLevel.isEmpty() || semester.isEmpty() || sectionId.isEmpty()) {
                    tvLoadingStatus.text = "Error: Missing class details."
                    return@launch
                }

                // 2️⃣ Fetch students in this section and year
                val studentsSnapshot = firestore.collection("students")
                    .whereEqualTo("sectionId", sectionId)
                    .whereEqualTo("yearLevel", yearLevel)
                    .whereEqualTo("status", "Admitted")
                    .get().await()

                val studentIds = studentsSnapshot.documents.map { it.id }

                if (studentIds.isEmpty()) {
                    tvLoadingStatus.text = "No admitted students found in this section."
                    return@launch
                }

                // 3️⃣ Check enrollment for the subject per student
                val enrollmentDocId = "${yearLevel.replace(" ", "")}_${semester.replace(" ", "")}_${subjectCode}"
                val enrolledStudents = mutableListOf<Student>()

                val studentMap = studentsSnapshot.documents.mapNotNull {
                    it.toObject(Student::class.java)?.copy(id = it.id)
                }.associateBy { it.id }

                val enrollmentChecks = studentIds.map { studentId ->
                    async {
                        val doc = firestore.collection("students")
                            .document(studentId)
                            .collection("subjects")
                            .document(enrollmentDocId)
                            .get().await()
                        if (doc.exists()) studentId else null
                    }
                }

                val enrolledIds = enrollmentChecks.awaitAll().filterNotNull()
                enrolledIds.forEach { id ->
                    studentMap[id]?.let { enrolledStudents.add(it) }
                }

                if (enrolledStudents.isEmpty()) {
                    tvLoadingStatus.text = "No students officially enrolled in $subjectCode."
                    return@launch
                }

                // 4️⃣ Display in RecyclerView
                val adapter = GradeInputAdapter(enrolledStudents, gradingPeriod!!)
                recyclerViewGrades.adapter = adapter
                tvLoadingStatus.text = "✅ ${enrolledStudents.size} students loaded."

            } catch (e: Exception) {
                Log.e("GradeInput", "Error fetching students: ${e.message}", e)
                tvLoadingStatus.text = "Error loading students."
                Toast.makeText(this@GradeInputActivity, "Loading error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

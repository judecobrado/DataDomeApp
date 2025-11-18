package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.StudentSubject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentGradesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tvLoadingStatus: TextView
    private lateinit var recyclerViewGrades: RecyclerView
    private lateinit var gradeSummaryAdapter: GradeSummaryAdapter

    private var studentId: String? = null
    private val subjects = mutableListOf<StudentSubject>()
    private val publishedGrades = mutableMapOf<String, Map<String, Double>>() // subjectCode -> (gradingPeriod -> grade)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_grades)

        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        recyclerViewGrades = findViewById(R.id.recyclerViewGrades)

        // Get student ID from intent or auth
        studentId = intent.getStringExtra("STUDENT_ID") ?: auth.currentUser?.uid

        if (studentId.isNullOrEmpty()) {
            tvLoadingStatus.text = "Error: Student ID not found"
            Toast.makeText(this, "Student ID missing. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()
        loadStudentSubjects()
    }

    private fun setupRecyclerView() {
        recyclerViewGrades.layoutManager = LinearLayoutManager(this)
        gradeSummaryAdapter = GradeSummaryAdapter(emptyList())
        recyclerViewGrades.adapter = gradeSummaryAdapter
    }

    private fun loadStudentSubjects() {
        tvLoadingStatus.text = "Loading enrolled subjects..."

        firestore.collection("students")
            .document(studentId!!)
            .collection("subjects")
            .get()
            .addOnSuccessListener { snapshot ->
                subjects.clear()

                for (doc in snapshot.documents) {
                    val subject = doc.toObject(StudentSubject::class.java)
                    if (subject != null) {
                        subjects.add(subject)
                    }
                }

                if (subjects.isEmpty()) {
                    tvLoadingStatus.text = "No subjects enrolled for this semester"
                    gradeSummaryAdapter.updateGrades(emptyList())
                } else {
                    tvLoadingStatus.text = "Loading grades for ${subjects.size} subjects..."
                    loadPublishedGrades()
                }
            }
            .addOnFailureListener { e ->
                tvLoadingStatus.text = "Error loading subjects"
                Toast.makeText(this, "Failed to load subjects: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("GradeSummary", "Error loading subjects: ${e.message}")
            }
    }

    private fun loadPublishedGrades() {
        firestore.collection("finalStudentGrades")
            .whereEqualTo("studentId", studentId!!)
            .whereEqualTo("isPublished", true)
            .get()
            .addOnSuccessListener { querySnapshot ->
                publishedGrades.clear()

                for (document in querySnapshot.documents) {
                    try {
                        val subjectCode = document.getString("subjectCode") ?: continue
                        val gradingPeriod = document.getString("gradingPeriod") ?: continue
                        val finalGrade = document.getDouble("finalGrade") ?: continue

                        if (!publishedGrades.containsKey(subjectCode)) {
                            publishedGrades[subjectCode] = mutableMapOf()
                        }

                        val subjectGrades = publishedGrades[subjectCode] as MutableMap
                        subjectGrades[gradingPeriod] = finalGrade
                    } catch (e: Exception) {
                        Log.e("GradeSummary", "Error parsing grade document: ${e.message}")
                    }
                }

                generateGradeSummary()
            }
            .addOnFailureListener { e ->
                tvLoadingStatus.text = "Error loading published grades"
                Log.e("GradeSummary", "Error loading published grades: ${e.message}")
                // Still generate summary with asterisks
                generateGradeSummary()
            }
    }

    private fun generateGradeSummary() {
        val gradeSummaryList = mutableListOf<GradeSummary>()

        for (subject in subjects) {
            val subjectGrades = publishedGrades[subject.subjectCode] ?: emptyMap()

            gradeSummaryList.add(GradeSummary(
                subjectCode = subject.subjectCode,
                subjectTitle = subject.subjectTitle ?: "",
                prelimGrade = subjectGrades["Prelim"] ?: -1.0, // -1.0 means no grade available
                midtermGrade = subjectGrades["Midterm"] ?: -1.0,
                finalGrade = subjectGrades["Finals"] ?: -1.0,
                // Calculate average if all grades are available
                averageGrade = calculateAverageGrade(subjectGrades)
            ))
        }

        if (gradeSummaryList.isEmpty()) {
            tvLoadingStatus.text = "No grade data available"
        } else {
            tvLoadingStatus.text = "Loaded subjects"
            gradeSummaryAdapter.updateGrades(gradeSummaryList)
        }
    }

    private fun calculateAverageGrade(subjectGrades: Map<String, Double>): Double {
        val prelim = subjectGrades["Prelim"]
        val midterm = subjectGrades["Midterm"]
        val finals = subjectGrades["Finals"]

        return when {
            prelim != null && midterm != null && finals != null -> {
                // Calculate weighted average: Prelim (30%), Midterm (30%), Finals (40%)
                (prelim * 0.3) + (midterm * 0.3) + (finals * 0.4)
            }
            else -> -1.0 // Not all grades available
        }
    }
}

data class GradeSummary(
    val subjectCode: String,
    val subjectTitle: String,
    val prelimGrade: Double,
    val midtermGrade: Double,
    val finalGrade: Double,
    val averageGrade: Double
)
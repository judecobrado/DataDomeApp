package com.example.datadomeapp.student

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView // Import para sa RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.StudentSubject
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StudentSubjectsActivity : AppCompatActivity() {

    // Pinalitan ang ListView ng RecyclerView
    private lateinit var rvSubjects: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar

    // Gagamitin ang bagong custom adapter
    private lateinit var subjectAdapter: SubjectRecyclerAdapter

    private val firestore = FirebaseFirestore.getInstance()
    private var studentId: String? = null
    private val subjects = mutableListOf<StudentSubject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // I-set ang layout na may RecyclerView
        setContentView(R.layout.activity_student_subjects)

        // Pinalitan ang ID reference
        rvSubjects = findViewById(R.id.rvSubjects)
        tvEmpty = findViewById(R.id.tvEmptySubjects)
        progressBar = findViewById(R.id.progressBar)

        studentId = intent.getStringExtra("STUDENT_ID")

        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "Student ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView() // Pinalitan ang setupListView
        loadStudentSubjects()
    }

    private fun setupRecyclerView() {
        // I-define ang click action para sa RecyclerView items
        val onItemClick: (StudentSubject) -> Unit = { subject ->
            val intent = Intent(this, StudentAssignmentsActivity::class.java)
            intent.putExtra("classId", subject.assignmentNo)
            intent.putExtra("subjectName", "${subject.subjectCode} - ${subject.subjectTitle}")
            startActivity(intent)
        }

        // Initialize ang Recycler Adapter
        subjectAdapter = SubjectRecyclerAdapter(subjects, onItemClick)
        rvSubjects.adapter = subjectAdapter

        // I-set ang initial loading state
        tvEmpty.text = "Loading subjects..."
    }

    private fun loadStudentSubjects() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.VISIBLE
        rvSubjects.visibility = View.GONE

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

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (subjects.isEmpty()) {
                        tvEmpty.text = "No subjects enrolled for this semester"
                        tvEmpty.visibility = View.VISIBLE
                        rvSubjects.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        rvSubjects.visibility = View.VISIBLE

                        subjectAdapter.notifyDataSetChanged() // I-notify ang adapter
                        loadAssignmentsCount()
                    }
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvEmpty.text = "Error loading subjects: ${e.message}"
                    tvEmpty.visibility = View.VISIBLE
                    rvSubjects.visibility = View.GONE
                    Toast.makeText(this, "Failed to load subjects", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Tinanggal ang buildSubjectDisplayText dahil ang logic ay nasa adapter na.

    private fun loadAssignmentsCount() {
        val assignmentNos = subjects.map { it.assignmentNo }.distinct()

        if (assignmentNos.isEmpty()) return

        for (assignmentNo in assignmentNos) {
            firestore.collection("assignments")
                .whereEqualTo("classId", assignmentNo)
                .get()
                .addOnSuccessListener { assignmentsSnapshot ->
                    val assignmentCount = assignmentsSnapshot.documents.size

                    runOnUiThread {
                        // Ginagamit ang dedikadong function ng Recycler Adapter
                        subjectAdapter.updateAssignmentCount(assignmentNo, assignmentCount)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("StudentSubjects", "Failed to load assignments for $assignmentNo: ${e.message}")
                }
        }
    }

    // Tinanggal ang updateSubjectDisplayWithCount dahil ang logic ay nasa adapter na.
}
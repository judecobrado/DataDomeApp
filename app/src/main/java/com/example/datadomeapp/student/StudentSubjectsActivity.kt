package com.example.datadomeapp.student

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.StudentSubject
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StudentSubjectsActivity : AppCompatActivity() {

    private lateinit var lvSubjects: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar

    private val firestore = FirebaseFirestore.getInstance()
    private var studentId: String? = null
    private val subjects = mutableListOf<StudentSubject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_subjects)

        lvSubjects = findViewById(R.id.lvSubjects)
        tvEmpty = findViewById(R.id.tvEmptySubjects)
        progressBar = findViewById(R.id.progressBar)

        studentId = intent.getStringExtra("STUDENT_ID")

        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "Student ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListView()
        loadStudentSubjects()
    }

    private fun setupListView() {
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf("Loading subjects...")
        )
        lvSubjects.adapter = adapter

        lvSubjects.setOnItemClickListener { _, _, position, _ ->
            if (position < subjects.size) {
                val subject = subjects[position]
                val intent = Intent(this, StudentAssignmentsActivity::class.java)
                intent.putExtra("classId", subject.assignmentNo) // This becomes the classId
                intent.putExtra("subjectName", "${subject.subjectCode} - ${subject.subjectTitle}")
                startActivity(intent)
            }
        }
    }

    private fun loadStudentSubjects() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.text = "Loading subjects..."

        firestore.collection("students")
            .document(studentId!!)
            .collection("subjects")
            .get()
            .addOnSuccessListener { snapshot ->
                subjects.clear()
                val displayList = mutableListOf<String>()

                for (doc in snapshot.documents) {
                    val subject = doc.toObject(StudentSubject::class.java)
                    if (subject != null) {
                        subjects.add(subject)
                        val displayText = buildSubjectDisplayText(subject)
                        displayList.add(displayText)
                    }
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (subjects.isEmpty()) {
                        tvEmpty.text = "No subjects enrolled for this semester"
                        val adapter = ArrayAdapter<String>(
                            this,
                            android.R.layout.simple_list_item_1,
                            mutableListOf("No subjects found")
                        )
                        lvSubjects.adapter = adapter
                    } else {
                        tvEmpty.text = ""
                        val adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            displayList
                        )
                        lvSubjects.adapter = adapter

                        loadAssignmentsCount()
                    }
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvEmpty.text = "Error loading subjects: ${e.message}"
                    Toast.makeText(this, "Failed to load subjects", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun buildSubjectDisplayText(subject: StudentSubject): String {
        return buildString {
            append(subject.subjectCode)
            append("\n")
            append(subject.subjectTitle)

            if (subject.sectionBlock.isNotEmpty() || subject.sectionName.isNotEmpty()) {
                append("\nSection: ")
                if (subject.sectionBlock.isNotEmpty()) {
                    append(subject.sectionBlock)
                }
                if (subject.sectionName.isNotEmpty()) {
                    if (subject.sectionBlock.isNotEmpty()) append(" - ")
                    append(subject.sectionName)
                }
            }

            append("\nAssignments: Loading...")
        }
    }

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
                        updateSubjectDisplayWithCount(assignmentNo, assignmentCount)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("StudentSubjects", "Failed to load assignments for $assignmentNo: ${e.message}")
                }
        }
    }

    private fun updateSubjectDisplayWithCount(assignmentNo: String, count: Int) {
        val adapter = lvSubjects.adapter as? ArrayAdapter<*> ?: return

        val updatedDisplayList = mutableListOf<String>()
        for (subject in subjects) {
            if (subject.assignmentNo == assignmentNo) {
                val baseText = buildSubjectDisplayText(subject)
                val updatedText = baseText.replace("Assignments: Loading...", "Assignments: $count")
                updatedDisplayList.add(updatedText)
            } else {
                val currentPosition = updatedDisplayList.size
                if (currentPosition < adapter.count) {
                    val currentText = adapter.getItem(currentPosition) as? String ?: ""
                    updatedDisplayList.add(currentText)
                } else {
                    val displayText = buildSubjectDisplayText(subject)
                    updatedDisplayList.add(displayText)
                }
            }
        }

        val newAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            updatedDisplayList
        )
        lvSubjects.adapter = newAdapter
    }
}
package com.example.datadomeapp.teacher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath

// ✅ IMPORTS: Tiyakin na ang mga ito ay tama sa iyong project structure
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.Student         // Gagamitin ang iyong Student model


// 🛑 Tandaan: Ang placeholder na 'data class Student' ay TINANGGAL na dito.

class ClassDetailsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tvClassNameHeader: TextView
    private lateinit var tvLoading: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnTakeAttendance: Button
    private lateinit var btnManageGrades: Button
    private lateinit var btnStartRoulette: Button
    private var studentNamesForRoulette: ArrayList<String> = ArrayList()

    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_class_details)

        // --- Get Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")

        // --- View Binding ---
        tvClassNameHeader = findViewById(R.id.tvClassNameHeader)
        tvLoading = findViewById(R.id.tvLoading)
        recyclerView = findViewById(R.id.recyclerViewStudents)
        btnTakeAttendance = findViewById(R.id.btnTakeAttendance)
        btnManageGrades = findViewById(R.id.btnManageGrades)
        btnStartRoulette = findViewById(R.id.btnStartRoulette)

        tvClassNameHeader.text = className ?: "Class Details"
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing class information.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadClassDetails(assignmentId!!)

        // --- Button Click Listeners ---
        btnTakeAttendance.setOnClickListener {
            navigateToAttendance(assignmentId!!, subjectCode!!)
        }

        btnManageGrades.setOnClickListener {
            navigateToGrades(assignmentId!!, subjectCode!!)
        }

        // 🎡 BAGONG LISTENER: Para sa Roleta
        btnStartRoulette.setOnClickListener {
            navigateToRoulette()
        }
    }

    private fun loadClassDetails(assignmentId: String) {
        // 1. Kuhanin ang assignment details para sa sectionName
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val assignment = doc.toObject(ClassAssignment::class.java)
                if (assignment != null) {
                    // 2. Kuhanin ang mga estudyante batay sa subject code at section name
                    loadStudentsBySection(assignment.subjectCode, assignment.sectionName)
                } else {
                    tvLoading.text = "Error: Class assignment not found."
                }
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error loading assignment: $e")
                tvLoading.text = "Error loading class details."
            }
    }

    private fun loadStudentsBySection(selectedSubjectCode: String, selectedSectionName: String) {
        // Ginamit ang Collection Group Query para mahanap ang lahat ng student subject documents
        firestore.collectionGroup("subjects")
            .whereEqualTo("subjectCode", selectedSubjectCode)
            .whereEqualTo("sectionName", selectedSectionName)
            .get()
            .addOnSuccessListener { subjectsSnapshot ->
                // Kunin ang studentId mula sa parent document reference (students/{id}/subjects/...)
                val studentIds = subjectsSnapshot.documents.map { it.reference.parent.parent!!.id }

                if (studentIds.isEmpty()) {
                    tvLoading.text = "No students enrolled in this section."
                    return@addOnSuccessListener
                }

                // 3. Kuhanin ang student profile details gamit ang studentIds
                // TANDAAN: whereIn() ay limitado sa 10 items.
                if (studentIds.size <= 10) {
                    fetchStudentProfiles(studentIds)
                } else {
                    // Mag-implement ng batching dito kung lalagpas sa 10 ang studentIds
                    Toast.makeText(this, "Section has too many students for simple query (Max 10).", Toast.LENGTH_LONG).show()
                    tvLoading.text = "Error: Too many students (Limit reached). Showing up to 10."
                    fetchStudentProfiles(studentIds.take(10)) // Limitahan sa 10 para hindi mag-crash
                }
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error querying subjects: $e")
                tvLoading.text = "Error fetching student subject links."
            }
    }

    private fun fetchStudentProfiles(studentIds: List<String>) {
        firestore.collection("students")
            .whereIn(FieldPath.documentId(), studentIds) // use document ID
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                val studentList = studentsSnapshot.documents.mapNotNull { it.toObject(Student::class.java) }

                tvLoading.text = "${studentList.size} students loaded."

                // ✅ ROLETTA LOGIC: (This part must be present in the kept function)
                studentNamesForRoulette.clear()
                studentList.forEach { student ->
                    studentNamesForRoulette.add("${student.lastName}, ${student.firstName}")
                }
                btnStartRoulette.text = "Roleta (${studentNamesForRoulette.size})"

                val studentAdapter = ClassStudentAdapter(studentList)
                recyclerView.adapter = studentAdapter
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error fetching student profiles: $e")
                tvLoading.text = "Error fetching student profiles."
                btnStartRoulette.isEnabled = false // Also a new addition
            }
    }

    // --- Navigation Functions ---
    private fun navigateToAttendance(assignmentId: String, subjectCode: String) {
        val intent = Intent(this, RecordAttendanceActivity::class.java)
        intent.putExtra("ASSIGNMENT_ID", assignmentId)
        intent.putExtra("SUBJECT_CODE", subjectCode) // I-pasa ang subjectCode para sa attendance record
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }

    private fun navigateToGrades(assignmentId: String, subjectCode: String) {
        val intent = Intent(this, ManageGradesActivity::class.java)
        intent.putExtra("ASSIGNMENT_ID", assignmentId)
        intent.putExtra("SUBJECT_CODE", subjectCode)
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }

    private fun navigateToRoulette() {
        if (studentNamesForRoulette.isEmpty()) {
            Toast.makeText(this, "No students loaded for the Roleta. Please wait for the class list to finish loading.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, RouletteActivity::class.java) // ⚠️ Tiyakin na mayroon kayong RouletteActivity.kt
        // Ipinapasa ang buong listahan ng pangalan sa bagong activity
        intent.putStringArrayListExtra("STUDENT_NAMES_LIST", studentNamesForRoulette)
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }
}
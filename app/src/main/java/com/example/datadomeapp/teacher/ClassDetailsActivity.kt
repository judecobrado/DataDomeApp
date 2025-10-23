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
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// ✅ IMPORTS: Tiyakin na ang mga ito ay tama sa iyong project structure
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.Student         // Gagamitin ang iyong Student model


// 🛑 Tandaan: Ang placeholder na 'data class Student' ay TINANGGAL na dito.

class ClassDetailsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tvClassNameHeader: TextView
    private lateinit var tvLoading: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnCreateQuiz: Button
    private lateinit var btnCreateAssessment: Button
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
        btnCreateQuiz = findViewById(R.id.btnCreateQuiz)
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
        //btnCreateQuiz.setOnClickListener {
          //  if (!assignmentId.isNullOrEmpty() && !subjectCode.isNullOrEmpty()) {
            //    val intent = Intent(this, TeacherQuizListActivity::class.java)
              //  intent.putExtra("assignmentId", assignmentId)
                //intent.putExtra("subjectCode", subjectCode)
                //startActivity(intent)
            //} else {
              //  Toast.makeText(this, "Assignment ID or Subject Code is missing", Toast.LENGTH_SHORT).show()
            //}
        //}

        //btnCreateAssessment.setOnClickListener {
          //  navigateToAttendance(assignmentId!!, subjectCode!!)
        //}

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
        // Ang Subject Code at ang Class Name ay nakuha na natin sa Intent/onCreate.
        // Ang Class Name (hal: "CS101 - 1A") ay naglalaman ng section name.

        val selectedSubjectCode = subjectCode
        val classNameHeader = className

        if (selectedSubjectCode.isNullOrEmpty() || classNameHeader.isNullOrEmpty()) {
            tvLoading.text = "Error: Missing required class details."
            return
        }

        // 1. Kukunin ang Section Name mula sa className header (e.g., "CS101 - 1A" -> "1A")
        // NOTE: Ina-assume na ang section name ay palaging huling item pagkatapos ng ' - '
        val sectionNameFromHeader = classNameHeader.split(" - ").lastOrNull()

        if (sectionNameFromHeader.isNullOrEmpty()) {
            tvLoading.text = "Error: Cannot determine section name."
            return
        }

        loadStudentsBySection(selectedSubjectCode, sectionNameFromHeader)
    }

    // SA CLASS DETAILSACTIVITY.KT

    private fun loadStudentsBySection(selectedSubjectCode: String, selectedSectionName: String) {
        // 1. Titingnan kung aling students ang may tamang section
        firestore.collection("students")
            .whereEqualTo("sectionId", selectedSectionName) // ⬅️ GAMITIN ANG sectionId sa Student Profile
            .whereEqualTo("yearLevel", "1st Year")          // ⬅️ OPTIONAL: Mag-add ng yearLevel filter kung kailangan
            .whereEqualTo("status", "Admitted")             // ⬅️ OPTIONAL: Filter for admitted students only
            .get()
            .addOnSuccessListener { studentsSnapshot ->

                val studentIds = studentsSnapshot.documents.map { it.id } // Kinuha ang lahat ng Student IDs

                if (studentIds.isEmpty()) {
                    tvLoading.text = "No Admitted students found in section $selectedSectionName."
                    return@addOnSuccessListener
                }

                // 2. I-check ang bawat student kung naka-enroll ba talaga sa Subject Code na ito.
                checkStudentEnrollmentBatch(studentIds, selectedSubjectCode)

            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error querying students by section: $e")
                tvLoading.text = "Error fetching student profiles."
            }
    }

    private fun checkStudentEnrollmentBatch(studentIds: List<String>, subjectCode: String) {

        val finalEnrolledStudents = mutableListOf<Student>()
        studentNamesForRoulette.clear()

        tvLoading.text = "Validating enrollment for ${studentIds.size} students..."

        // Gumamit ng Coroutine para i-handle ang multiple asynchronous checks
        lifecycleScope.launch {
            try {
                // Iterahin ang bawat student ID
                for (studentId in studentIds) {

                    // CRITICAL: Hanapin ang subject document sa ilalim ng student
                    val subjectRef = firestore.collection("students").document(studentId)
                        .collection("subjects").document(subjectCode)
                    // Tandaan: Ang Document ID ng subject ay ang subjectCode (e.g., MATH101)

                    val subjectSnapshot = subjectRef.get().await()

                    // Kung may subject document at ito ay may tamang subject code
                    if (subjectSnapshot.exists() && subjectSnapshot.getString("subjectCode") == subjectCode) {

                        // Kuhanin ang Student Profile Document (Parent)
                        val studentProfileSnapshot = firestore.collection("students").document(studentId).get().await()

                        val student = studentProfileSnapshot.toObject(Student::class.java)
                        if (student != null) {
                            finalEnrolledStudents.add(student)
                        }
                    }
                }

                // --- UI Update Logic ---
                if (finalEnrolledStudents.isEmpty()) {
                    tvLoading.text = "No students enrolled in $subjectCode."
                } else {
                    finalEnrolledStudents.forEach { student ->
                        studentNamesForRoulette.add("${student.lastName}, ${student.firstName}")
                    }
                    btnStartRoulette.text = "Roleta (${studentNamesForRoulette.size})"

                    val studentAdapter = ClassStudentAdapter(finalEnrolledStudents)
                    recyclerView.adapter = studentAdapter
                    tvLoading.text = "✅ ${finalEnrolledStudents.size} students successfully loaded."
                }

            } catch (e: Exception) {
                Log.e("ClassDetails", "Error validating student enrollment: ${e.message}", e)
                tvLoading.text = "Error fetching profiles."
            }
        }
    }

    private fun fetchStudentProfilesBatch(studentIds: List<String>) {
        // Hatiin ang studentIds sa chunks na may maximum na 10 ID bawat isa
        val idChunks = studentIds.chunked(10)

        val finalStudentList = mutableListOf<Student>()
        studentNamesForRoulette.clear()

        tvLoading.text = "Loading ${studentIds.size} student profiles..."

        // Gumamit ng Coroutine para i-handle ang multiple asynchronous calls
        lifecycleScope.launch {
            try {
                // Iterahin ang bawat chunk (batch) ng 10 IDs
                for (chunk in idChunks) {
                    // Gumawa ng hiwalay na query para sa bawat batch
                    val snapshot = firestore.collection("students")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await() // Ito ang maghihintay hanggang matapos ang query (requires kotlinx-coroutines-play-services)

                    // Idagdag ang students sa final list
                    val studentsInChunk = snapshot.documents.mapNotNull { it.toObject(Student::class.java) }
                    finalStudentList.addAll(studentsInChunk)
                }

                // --- UPDATED UI LOGIC ---

                // 1. I-update ang Listahan ng Pangalan para sa Roleta
                finalStudentList.forEach { student ->
                    studentNamesForRoulette.add("${student.lastName}, ${student.firstName}")
                }
                btnStartRoulette.text = "Roleta (${studentNamesForRoulette.size})"

                // 2. I-update ang RecyclerView
                val studentAdapter = ClassStudentAdapter(finalStudentList)
                recyclerView.adapter = studentAdapter

                tvLoading.text = "✅ ${finalStudentList.size} students successfully loaded."

            } catch (e: Exception) {
                Log.e("ClassDetails", "Error fetching student profiles in batch: ${e.message}", e)
                tvLoading.text = "Error fetching profiles. Max students: ${finalStudentList.size}"
                Toast.makeText(this@ClassDetailsActivity, "Failed to load all student profiles.", Toast.LENGTH_LONG).show()
            }
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
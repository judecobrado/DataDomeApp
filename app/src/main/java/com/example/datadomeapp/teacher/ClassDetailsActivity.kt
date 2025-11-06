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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.toObject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.Student

class ClassDetailsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tvClassNameHeader: TextView
    private lateinit var tvLoading: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnCreateQuiz: Button
    private lateinit var btnCreateAssignment: Button
    private lateinit var btnTakeAttendance: Button
    private lateinit var btnManageGrades: Button
    private lateinit var btnStartRoulette: Button
    private lateinit var btnViewSubmissions: Button
    private lateinit var btnViewAssignments: Button

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
        btnCreateAssignment = findViewById(R.id.btnCreateAssignment)
        btnTakeAttendance = findViewById(R.id.btnTakeAttendance)
        btnManageGrades = findViewById(R.id.btnManageGrades)
        btnStartRoulette = findViewById(R.id.btnStartRoulette)
        btnViewSubmissions = findViewById(R.id.btnViewSubmissions)
        btnViewAssignments = findViewById(R.id.btnViewAssignments)

        val formattedClassName = className?.split(" - ")?.mapIndexed { index, part ->
            if (index == 1) toTitleCaseWithExceptions(part) else part
        }?.joinToString(" - ") ?: "Class Details"

        tvClassNameHeader.text = formattedClassName
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing class information.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadClassDetails(assignmentId!!)

        // --- Button Click Listeners ---
        btnCreateQuiz.setOnClickListener {
            val intent = Intent(this, ManageQuizzesActivity::class.java)
            intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
            intent.putExtra("CLASS_NAME", className)
            startActivity(intent)
        }

        btnCreateAssignment.setOnClickListener {
            val intent = Intent(this, CreateAssignmentActivity::class.java)
            intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
            intent.putExtra("CLASS_NAME", className)
            Log.d("ClassDetails", "Creating assignment for assignmentId: $assignmentId")
            Toast.makeText(this, "Creating assignment for this class", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        btnViewAssignments.setOnClickListener {
            if (assignmentId.isNullOrEmpty()) {
                Toast.makeText(this, "No assignment ID found.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, AssignmentListActivity::class.java)
            intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
            intent.putExtra("CLASS_NAME", className)
            Log.d("ClassDetails", "Opening AssignmentListActivity with assignmentId: $assignmentId")
            Toast.makeText(this, "Opening assignments for this class", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        btnViewSubmissions.setOnClickListener {
            val intent = Intent(this, ViewSubmissionsActivity::class.java)
            intent.putExtra("assignmentId", assignmentId)
            intent.putExtra("assignmentTitle", className)
            startActivity(intent)
        }

        btnTakeAttendance.setOnClickListener {
            navigateToAttendance(assignmentId!!, subjectCode!!)
        }

        btnManageGrades.setOnClickListener {
            navigateToGrades(assignmentId!!, subjectCode!!)
        }

        btnStartRoulette.setOnClickListener {
            navigateToRoulette()
        }
    }

    // --- Load class details ---
    private fun loadClassDetails(assignmentId: String) {
        tvLoading.text = "Loading class data..."

        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val fetchedSemester = doc.getString("semester")
                val fetchedYearLevel = doc.getString("yearLevel")

                val classNameHeader = className
                val selectedSectionName = classNameHeader?.split(" - ")?.lastOrNull()

                if (fetchedSubjectCode.isNullOrEmpty() || selectedSectionName.isNullOrEmpty()
                    || fetchedSemester.isNullOrEmpty() || fetchedYearLevel.isNullOrEmpty()
                ) {
                    tvLoading.text = "Error: Missing required class details (Subject/Section/Semester/Year)."
                    return@addOnSuccessListener
                }

                loadStudentsBySection(
                    fetchedSubjectCode,
                    fetchedSemester,
                    fetchedYearLevel,
                    selectedSectionName
                )
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error loading assignment details: $e")
                tvLoading.text = "Error loading assignment details."
            }
    }

    // ✅ FIXED: Safe version that won't crash - uses original approach with subject filtering
    private fun loadStudentsBySection(
        selectedSubjectCode: String,
        selectedSemester: String,
        selectedYearLevel: String,
        selectedSectionName: String
    ) {
        tvLoading.text = "Loading students for $selectedSubjectCode..."

        Log.d("ClassDetails", "Loading students for subject: $selectedSubjectCode, section: $selectedSectionName")

        // Use the original safe approach but we'll filter by subject in the next step
        firestore.collection("students")
            .whereEqualTo("sectionId", selectedSectionName)
            .whereEqualTo("yearLevel", selectedYearLevel)
            .whereEqualTo("status", "Admitted")
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                val studentIds = studentsSnapshot.documents.map { it.id }

                if (studentIds.isEmpty()) {
                    tvLoading.text = "No Admitted students found in section $selectedSectionName."
                    return@addOnSuccessListener
                }

                Log.d("ClassDetails", "Found ${studentIds.size} students in section, checking subject enrollment...")

                // Now check which of these students are enrolled in THIS specific subject
                checkStudentEnrollmentBatch(
                    studentIds,
                    selectedSubjectCode,
                    selectedSemester,
                    selectedYearLevel
                )
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error querying students by section: $e")
                tvLoading.text = "Error fetching student profiles."
            }
    }

    private fun checkStudentEnrollmentBatch(
        studentIds: List<String>,
        subjectCode: String,
        semester: String,
        yearLevel: String
    ) {
        val finalEnrolledStudents = mutableListOf<Student>()
        studentNamesForRoulette.clear()

        tvLoading.text = "Validating enrollment for ${studentIds.size} students..."

        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"

        lifecycleScope.launch {
            try {
                // First get all student profiles
                val studentProfilesQuery = firestore.collection("students")
                    .whereIn(FieldPath.documentId(), studentIds)
                    .get().await()

                val studentMap = studentProfilesQuery.documents
                    .mapNotNull { it.toObject(Student::class.java)?.copy(id = it.id) }
                    .associateBy { it.id }

                Log.d("ClassDetails", "Checking enrollment for ${studentIds.size} students in subject: $subjectCode")

                // Check each student's enrollment in this specific subject
                val enrollmentChecks = studentIds.map { studentId ->
                    async {
                        try {
                            val subjectRef = firestore.collection("students").document(studentId)
                                .collection("subjects").document(enrollmentDocId)
                            val subjectSnapshot = subjectRef.get().await()
                            if (subjectSnapshot.exists()) {
                                Log.d("ClassDetails", "Student $studentId is enrolled in $subjectCode")
                                studentId
                            } else {
                                Log.d("ClassDetails", "Student $studentId is NOT enrolled in $subjectCode")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("ClassDetails", "Error checking enrollment for $studentId: ${e.message}")
                            null
                        }
                    }
                }

                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()

                // Get the actual student objects for enrolled students
                enrolledStudentIds.forEach { id ->
                    studentMap[id]?.let { finalEnrolledStudents.add(it) }
                }

                Log.d("ClassDetails", "Final result: ${finalEnrolledStudents.size} students enrolled in $subjectCode")

                // Update UI
                runOnUiThread {
                    if (finalEnrolledStudents.isEmpty()) {
                        tvLoading.text = "No students officially enrolled in $subjectCode."
                    } else {
                        finalEnrolledStudents.forEach { student ->
                            studentNamesForRoulette.add("${student.lastName}, ${student.firstName}")
                        }
                        btnStartRoulette.text = "Roleta (${studentNamesForRoulette.size})"
                        val studentAdapter = ClassStudentAdapter(finalEnrolledStudents)
                        recyclerView.adapter = studentAdapter
                        tvLoading.text = "✅ ${finalEnrolledStudents.size} students successfully loaded."
                    }
                }

            } catch (e: Exception) {
                Log.e("ClassDetails", "Error validating student enrollment: ${e.message}", e)
                runOnUiThread {
                    tvLoading.text = "Error validating student enrollment."
                }
            }
        }
    }

    private fun toTitleCaseWithExceptions(input: String): String {
        val exceptions = setOf("the", "on", "in", "of", "and", "a", "an", "for", "to", "at", "by", "from")
        return input
            .split(" ")
            .mapIndexed { index, word ->
                if (index == 0 || word.lowercase() !in exceptions) {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    word.lowercase()
                }
            }
            .joinToString(" ")
    }

    private fun navigateToAttendance(assignmentId: String, subjectCode: String) {
        val intent = Intent(this, RecordAttendanceActivity::class.java)
        intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
        intent.putExtra("SUBJECT_CODE", subjectCode)
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }

    private fun navigateToGrades(assignmentId: String, subjectCode: String) {
        val intent = Intent(this, ManageGradesActivity::class.java)
        intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
        intent.putExtra("SUBJECT_CODE", subjectCode)
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }

    private fun navigateToRoulette() {
        if (studentNamesForRoulette.isEmpty()) {
            Toast.makeText(
                this,
                "No students loaded for the Roleta. Please wait for the class list to finish loading.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(this, RouletteActivity::class.java)
        intent.putStringArrayListExtra("STUDENT_NAMES_LIST", studentNamesForRoulette)
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }
}
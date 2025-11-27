package com.example.datadomeapp.teacher

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ClassDetailsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tvClassNameHeader: TextView
    private lateinit var tvLoading: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnCreateQuiz: MaterialCardView
    private lateinit var btnCreateAssignment: MaterialCardView
    private lateinit var btnTakeAttendance: MaterialCardView
    private lateinit var btnManageGrades: MaterialCardView
    private lateinit var btnStartRoulette: MaterialCardView
    private lateinit var chipStudentCount: Chip
    private lateinit var tvStudentsHeader: TextView
    private var studentNamesForRoulette: ArrayList<String> = ArrayList()
    private var autoStartRoulette: Boolean = false
    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null
    private var sectionId: String? = null
    private var yearLevel: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_class_details)

        // --- Get Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")
        autoStartRoulette = intent.getBooleanExtra("AUTO_START_ROULETTE", false)

        // --- View Binding ---
        tvClassNameHeader = findViewById(R.id.tvClassNameHeader)
        tvLoading = findViewById(R.id.tvLoading)
        recyclerView = findViewById(R.id.recyclerViewStudents)
        chipStudentCount = findViewById(R.id.chipStudentCount)
        tvStudentsHeader = findViewById(R.id.tvStudentsHeader)

        // ✅ CHANGED: MaterialCardView instead of Button
        btnCreateQuiz = findViewById(R.id.btnCreateQuiz)
        btnCreateAssignment = findViewById(R.id.btnCreateAssignment)
        btnTakeAttendance = findViewById(R.id.btnTakeAttendance)
        btnManageGrades = findViewById(R.id.btnManageGrades)
        btnStartRoulette = findViewById(R.id.btnStartRoulette)

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

        // Auto-start roulette after loading
        if (autoStartRoulette) {
            lifecycleScope.launch {
                delay(2500)
                if (studentNamesForRoulette.isNotEmpty()) {
                    navigateToRoulette()
                } else {
                    Toast.makeText(this@ClassDetailsActivity, "Loading students... please wait.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ✅ UPDATED: Set click listeners for MaterialCardView buttons
        btnCreateQuiz.setOnClickListener {
            val intent = Intent(this, ManageQuizzesActivity::class.java)
            intent.putExtra("ASSIGNMENT_ID", assignmentId)
            intent.putExtra("CLASS_NAME", className)
            startActivity(intent)
        }

        btnCreateAssignment.setOnClickListener {
            if (assignmentId.isNullOrEmpty() || sectionId.isNullOrEmpty() || yearLevel.isNullOrEmpty()) {
                Toast.makeText(this, "Class details not fully loaded.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, AssignmentListActivity::class.java)
            intent.putExtra("assignmentId", assignmentId)
            intent.putExtra("CLASS_NAME", className)
            intent.putExtra("SECTION_ID", sectionId)
            intent.putExtra("YEAR_LEVEL", yearLevel)

            Log.d("ClassDetails", "Opening AssignmentListActivity with assignmentId: $assignmentId, Section: $sectionId, Year: $yearLevel")
            Toast.makeText(this, "Opening assignments for this class", Toast.LENGTH_SHORT).show()
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

    private fun loadClassDetails(assignmentId: String) {
        tvLoading.text = "Loading class data..."

        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                yearLevel = doc.getString("yearLevel")
                val fetchedSemester = doc.getString("semester")
                val fetchedYearLevel = doc.getString("yearLevel")

                val classNameHeader = className
                sectionId = classNameHeader?.split(" - ")?.lastOrNull()
                val selectedSectionName = classNameHeader?.split(" - ")?.lastOrNull()

                if (fetchedSubjectCode.isNullOrEmpty() || selectedSectionName.isNullOrEmpty()
                    || fetchedSemester.isNullOrEmpty() || fetchedYearLevel.isNullOrEmpty()) {
                    tvLoading.text = "Error: Missing required class details (Subject/Section/Semester/Year)."
                    return@addOnSuccessListener
                }

                loadStudentsBySection(fetchedSubjectCode, fetchedSemester, fetchedYearLevel, selectedSectionName)
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error loading assignment details: $e")
                tvLoading.text = "Error loading assignment details."
            }
    }

    private fun loadStudentsBySection(
        selectedSubjectCode: String,
        selectedSemester: String,
        selectedYearLevel: String,
        selectedSectionName: String
    ) {
        firestore.collection("students")
            .whereEqualTo("sectionId", selectedSectionName)
            .whereEqualTo("yearLevel", selectedYearLevel)
            .whereEqualTo("status", "Admitted")
            .get()
            .addOnSuccessListener { studentsSnapshot ->

                val studentIds = studentsSnapshot.documents.map { it.id }

                if (studentIds.isEmpty()) {
                    tvLoading.text = "No Admitted students found in section $selectedSectionName."
                    updateStudentCount(0)
                    return@addOnSuccessListener
                }

                checkStudentEnrollmentBatch(studentIds, selectedSubjectCode, selectedSemester, selectedYearLevel)
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error querying students by section: $e")
                tvLoading.text = "Error fetching student profiles."
                updateStudentCount(0)
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

        tvLoading.text = "Validating enrollment for ${studentIds.size} students... (Optimized Check)"

        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"

        lifecycleScope.launch {
            try {
                val studentProfilesQuery = firestore.collection("students")
                    .whereIn(FieldPath.documentId(), studentIds)
                    .get().await()

                val studentMap = studentProfilesQuery.documents
                    .mapNotNull { it.toObject(Student::class.java)?.copy(id = it.id) }
                    .associateBy { it.id }

                val enrollmentChecks = studentIds.map { studentId ->
                    async {
                        val subjectRef = firestore.collection("students").document(studentId)
                            .collection("subjects").document(enrollmentDocId)

                        val subjectSnapshot = subjectRef.get().await()

                        if (subjectSnapshot.exists()) {
                            studentId
                        } else {
                            null
                        }
                    }
                }

                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()

                enrolledStudentIds.forEach { id ->
                    studentMap[id]?.let { finalEnrolledStudents.add(it) }
                }

                finalEnrolledStudents.sortBy { it.lastName }

                if (finalEnrolledStudents.isEmpty()) {
                    tvLoading.text = "No students officially enrolled in $subjectCode."
                    updateStudentCount(0)
                } else {
                    finalEnrolledStudents.forEach { student ->
                        studentNamesForRoulette.add("${student.lastName}, ${student.firstName}")
                    }

                    val studentAdapter = ClassStudentAdapter(finalEnrolledStudents)
                    recyclerView.adapter = studentAdapter
                    tvLoading.text = "✅ ${finalEnrolledStudents.size} students successfully loaded."

                    updateStudentCount(finalEnrolledStudents.size)

                    if (autoStartRoulette) {
                        navigateToRoulette()
                        autoStartRoulette = false
                    }
                }

            } catch (e: Exception) {
                Log.e("ClassDetails", "Error validating student enrollment: ${e.message}", e)
                tvLoading.text = "Error fetching profiles."
                updateStudentCount(0)
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
        intent.putExtra("ASSIGNMENT_ID", assignmentId)
        intent.putExtra("SUBJECT_CODE", subjectCode)
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

        val intent = Intent(this, RouletteActivity::class.java)
        intent.putStringArrayListExtra("STUDENT_NAMES_LIST", studentNamesForRoulette)
        intent.putExtra("CLASS_NAME", className)
        startActivity(intent)
    }

    // ✅ ADD: New method to update student count
    private fun updateStudentCount(count: Int) {
        chipStudentCount.text = "$count students"
        tvStudentsHeader.text = "Students in this class"
    }
}
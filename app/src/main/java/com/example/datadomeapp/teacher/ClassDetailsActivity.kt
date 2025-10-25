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
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// ✅ IMPORTS: Tiyakin na ang mga ito ay tama sa iyong project structure
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.Student         // Gagamitin ang iyong Student model


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

        btnCreateQuiz.setOnClickListener {
            val intent = Intent(this, ManageQuizzesActivity::class.java)
            intent.putExtra("ASSIGNMENT_ID", assignmentId)
            intent.putExtra("CLASS_NAME", className)
            startActivity(intent)
        }

        // --- Button Click Listeners ---
        // (Wala akong inalis o binago dito. Hayaan na lang ang mga commented-out codes.)

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

    // 🛑 INAYOS: 1. loadClassDetails - Para makuha ang Semester at Year Level mula sa ClassAssignment
    private fun loadClassDetails(assignmentId: String) {
        tvLoading.text = "Loading class data..."

        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                // Kuhanin ang data na kailangan para buuin ang Enrollment ID
                val fetchedSubjectCode = doc.getString("subjectCode")
                val fetchedSemester = doc.getString("semester")    // <--- CRITICAL
                val fetchedYearLevel = doc.getString("yearLevel")  // <--- CRITICAL

                val classNameHeader = className
                // NOTE: Ina-assume na ang section name ay palaging huling item pagkatapos ng ' - '
                val selectedSectionName = classNameHeader?.split(" - ")?.lastOrNull()

                if (fetchedSubjectCode.isNullOrEmpty() || selectedSectionName.isNullOrEmpty()
                    || fetchedSemester.isNullOrEmpty() || fetchedYearLevel.isNullOrEmpty()) {
                    tvLoading.text = "Error: Missing required class details (Subject/Section/Semester/Year)."
                    return@addOnSuccessListener
                }

                // 1. I-load muna ang students base sa section (Initial Filter)
                // ✅ IPASA ang lahat ng bagong parameters
                loadStudentsBySection(fetchedSubjectCode, fetchedSemester, fetchedYearLevel, selectedSectionName)
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error loading assignment details: $e")
                tvLoading.text = "Error loading assignment details."
            }
    }

    // 🛑 INAYOS: 2. loadStudentsBySection - Idinagdag ang Semester at Year Level sa parameters
    private fun loadStudentsBySection(
        selectedSubjectCode: String,
        selectedSemester: String,    // <--- NEW PARAMETER
        selectedYearLevel: String,   // <--- NEW PARAMETER
        selectedSectionName: String
    ) {
        // 1. Titingnan kung aling students ang may tamang section
        firestore.collection("students")
            .whereEqualTo("sectionId", selectedSectionName) // ⬅️ GAMITIN ANG sectionId sa Student Profile
            .whereEqualTo("yearLevel", selectedYearLevel)  // ⬅️ Gamitin ang Year Level mula sa ClassAssignment
            .whereEqualTo("status", "Admitted")             // ⬅️ Filter for admitted students only
            .get()
            .addOnSuccessListener { studentsSnapshot ->

                val studentIds = studentsSnapshot.documents.map { it.id } // Kinuha ang lahat ng Student IDs

                if (studentIds.isEmpty()) {
                    tvLoading.text = "No Admitted students found in section $selectedSectionName."
                    return@addOnSuccessListener
                }

                // 2. I-check ang bawat student kung naka-enroll ba talaga sa Subject Code na ito.
                // ✅ IPASA ang Semester at YearLevel
                checkStudentEnrollmentBatch(studentIds, selectedSubjectCode, selectedSemester, selectedYearLevel)

            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error querying students by section: $e")
                tvLoading.text = "Error fetching student profiles."
            }
    }

    // 🛑 INAYOS: 3. checkStudentEnrollmentBatch - I-construct ang tamang Document ID
    // In ClassDetailsActivity.kt
// 🛑 PALITAN ang buong checkStudentEnrollmentBatch function ng bago at mas mabilis na bersyon na ito.

    private fun checkStudentEnrollmentBatch(
        studentIds: List<String>,
        subjectCode: String,
        semester: String,
        yearLevel: String
    ) {

        val finalEnrolledStudents = mutableListOf<Student>()
        studentNamesForRoulette.clear()

        tvLoading.text = "Validating enrollment for ${studentIds.size} students... (Optimized Check)"

        // 🚨 CRITICAL FIX: I-clean ang strings para magtugma sa Firestore key format
        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}" // <-- Ito ang tamang ID

        // Simulan ang Coroutine
        lifecycleScope.launch {
            try {
                // 1. Titingnan natin kung aling student ID ang may enrollment record
                // Dahil hindi puwedeng i-query ang Sub-collection documents base sa Document ID,
                // kailangan nating ipasok ang Enrollment Status sa Parent Student Document mismo.
                //
                // ⚠️ Assuming na may field na 'enrolledSubjects' (List<String>) sa Student Profile Document:

                // --- OPTIMIZED APPROACH (Requires new index on Student collection) ---

                // Ang pinakamabilis na paraan ay ibalik ang paggamit ng whereIn sa Parent Student Collection.
                // Ngunit kailangan nito ng field sa Parent Document para i-filter:
                // Halimbawa: Student Document ay may field na: `isEnrolled_1stYear_1stSemester_CS101: true`

                // Dahil ginamit mo ang Sub-collection (subjects), ang pinakamabilis na way ay:

                // 1. Gawin ang BATCH QUERY sa Parent Document gamit ang `whereIn`
                val studentProfilesQuery = firestore.collection("students")
                    .whereIn(FieldPath.documentId(), studentIds)
                    .get().await()

                // Map: Student ID -> Student Object
                val studentMap = studentProfilesQuery.documents
                    .mapNotNull { it.toObject(Student::class.java)?.copy(id = it.id) }
                    .associateBy { it.id }

                // 2. Muli, gamitin ang ASYNC loop, ngunit TANGGALIN ang "Student Profile Read" sa loob ng loop
                // Gagawa lang ito ng N reads (sa halip na 2N reads) na naka-async.

                val enrollmentChecks = studentIds.map { studentId ->
                    async {
                        val subjectRef = firestore.collection("students").document(studentId)
                            .collection("subjects").document(enrollmentDocId)

                        val subjectSnapshot = subjectRef.get().await()

                        if (subjectSnapshot.exists()) {
                            // Kung may enrollment, ibalik ang Student ID
                            studentId
                        } else {
                            null
                        }
                    }
                }

                // Hintayin ang lahat ng checks na matapos
                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()

                // Gamitin ang pre-fetched StudentMap para buuin ang listahan
                enrolledStudentIds.forEach { id ->
                    studentMap[id]?.let { finalEnrolledStudents.add(it) }
                }
                // --- END Optimized Approach (N+1 reads, but faster N reads in parallel) ---

                // ************ (I-update ang UI Logic ayon sa orihinal mong code) ************

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

            } catch (e: Exception) {
                Log.e("ClassDetails", "Error validating student enrollment: ${e.message}", e)
                tvLoading.text = "Error fetching profiles."
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
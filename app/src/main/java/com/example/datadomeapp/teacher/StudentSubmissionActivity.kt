package com.example.datadomeapp.teacher

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Assignment
import com.example.datadomeapp.models.Student
import com.example.datadomeapp.models.StudentExtension
import com.example.datadomeapp.models.Submission
import com.example.datadomeapp.repository.AssignmentRepository
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class StudentSubmissionsActivity : AppCompatActivity() {

    private lateinit var tvAssignmentTitle: TextView
    private lateinit var lvStudents: ListView
    private lateinit var btnBack: Button
    private lateinit var tvLoading: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabLayout: TabLayout

    private lateinit var assignment: Assignment
    private var classId: String = ""
    private var className: String? = null
    private val studentList = mutableListOf<Student>()
    private val studentExtensionsMap = mutableMapOf<String, StudentExtension>()
    private val studentSubmissionsMap = mutableMapOf<String, Submission>()
    private var adapter: StudentSubmissionsAdapter? = null

    private val firestore = FirebaseFirestore.getInstance()
    private var currentTabPosition = 0 // 0: Submissions, 1: Extensions

    // 🆕 NEW: Map to store Firebase UID to Student ID mappings
    private val userMapping = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_submissions)

        try {
            tvAssignmentTitle = findViewById(R.id.tvAssignmentTitle)
            lvStudents = findViewById(R.id.lvStudents)
            btnBack = findViewById(R.id.btnBack)
            progressBar = findViewById(R.id.progressBar)
            tabLayout = findViewById(R.id.tabLayout)

            tvLoading = try {
                findViewById(R.id.tvLoading)
            } catch (e: Exception) {
                TextView(this).apply { text = "Loading..." }
            }
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error initializing views: ${e.message}")
            Toast.makeText(this, "Error initializing screen", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            if (!intent.hasExtra("assignment")) {
                Toast.makeText(this, "Error: No assignment data received", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            assignment = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("assignment", Assignment::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Assignment>("assignment")
            } ?: run {
                Toast.makeText(this, "Error: Invalid assignment data", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            classId = intent.getStringExtra("assignmentId") ?: assignment.classId
            className = intent.getStringExtra("className")

            Log.d("StudentSubmissions", "🎯 Loaded assignment: ${assignment.title}")
            Log.d("StudentSubmissions", "🎯 Assignment ID: ${assignment.id}")
            Log.d("StudentSubmissions", "🎯 Class ID: $classId")
            Log.d("StudentSubmissions", "🎯 Class Name: $className")

            tvAssignmentTitle.text = "${assignment.title} - Student Submissions & Extensions"
            updateLoadingText("Loading data...")

        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error getting intent data: ${e.message}", e)
            Toast.makeText(this, "Error loading assignment data: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupTabLayout()

        // 🆕 FIX: Load everything in sequence
        loadAllData()

        btnBack.setOnClickListener { finish() }
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabPosition = tab?.position ?: 0
                adapter?.notifyDataSetChanged()
                updateEmptyState()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateLoadingText(text: String) {
        try {
            tvLoading.text = text
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error updating loading text: ${e.message}")
        }
    }

    // 🆕 FIX: Load all data in sequence
    private fun loadAllData() {
        progressBar.visibility = View.VISIBLE
        updateLoadingText("Loading students and submissions...")

        Log.d("StudentSubmissions", "🔄 Loading ALL data for assignment: ${assignment.id}")

        // Load students first, then user mappings, then submissions
        loadClassStudentsSimple()
    }

    // 🆕 FIXED: Now includes subject enrollment verification like ClassDetailsActivity
    private fun loadClassStudentsSimple() {
        Log.d("StudentSubmissions", "🔄 Loading students for class: $classId")

        // If we don't have a valid class ID, try to get it from the assignment
        val targetClassId = if (classId.isEmpty() || classId == assignment.id) {
            // Use assignment.classId if available, otherwise try to extract from className
            if (assignment.classId.isNotEmpty() && assignment.classId != assignment.id) {
                assignment.classId
            } else {
                // Try to extract class ID from className (e.g., "0013 - Section Name" -> "0013")
                className?.split(" - ")?.firstOrNull() ?: classId
            }
        } else {
            classId
        }

        if (targetClassId.isEmpty()) {
            Log.e("StudentSubmissions", "❌ No valid class ID found")
            Log.d("StudentSubmissions", "   assignment.classId: ${assignment.classId}")
            Log.d("StudentSubmissions", "   classId from intent: $classId")
            Log.d("StudentSubmissions", "   className: $className")
            return
        }

        Log.d("StudentSubmissions", "🎯 Loading students for class ID: $targetClassId")

        // Get class details first - SAME AS ClassDetailsActivity
        firestore.collection("classAssignments").document(targetClassId).get()
            .addOnSuccessListener { classDoc ->
                if (!classDoc.exists()) {
                    Log.e("StudentSubmissions", "❌ Class not found: $targetClassId")
                    return@addOnSuccessListener
                }

                val subjectCode = classDoc.getString("subjectCode") ?: ""
                val sectionName = classDoc.getString("sectionName") ?:
                className?.split(" - ")?.lastOrNull() ?: ""
                val yearLevel = classDoc.getString("yearLevel") ?: ""
                val semester = classDoc.getString("semester") ?: ""

                if (sectionName.isEmpty() || subjectCode.isEmpty() || semester.isEmpty()) {
                    Log.e("StudentSubmissions", "❌ Missing required class details")
                    return@addOnSuccessListener
                }

                Log.d("StudentSubmissions", "📋 Loading students for subject: $subjectCode, section: $sectionName, year: $yearLevel, semester: $semester")

                // STEP 1: Load students by section (same initial filter as ClassDetailsActivity)
                firestore.collection("students")
                    .whereEqualTo("sectionId", sectionName)
                    .whereEqualTo("yearLevel", yearLevel)
                    .whereEqualTo("status", "Admitted")
                    .get()
                    .addOnSuccessListener { studentsSnapshot ->
                        studentList.clear()
                        val initialStudents = mutableListOf<Student>()

                        for (doc in studentsSnapshot.documents) {
                            try {
                                val student = doc.toObject(Student::class.java)
                                if (student != null) {
                                    student.id = doc.id
                                    initialStudents.add(student)
                                    Log.d("StudentSubmissions", "✅ Found student in section: ${student.firstName} ${student.lastName} (${student.id})")
                                }
                            } catch (e: Exception) {
                                Log.e("StudentSubmissions", "❌ Error parsing student: ${e.message}")
                            }
                        }

                        Log.d("StudentSubmissions", "🎉 Found ${initialStudents.size} students in section")

                        // STEP 2: Verify enrollment in specific subject (CRITICAL FIX)
                        verifySubjectEnrollment(initialStudents, subjectCode, semester, yearLevel)
                    }
                    .addOnFailureListener { e ->
                        Log.e("StudentSubmissions", "❌ Error loading students: ${e.message}")
                        progressBar.visibility = View.GONE
                    }
            }
            .addOnFailureListener { e ->
                Log.e("StudentSubmissions", "❌ Error loading class details: ${e.message}")
                progressBar.visibility = View.GONE
            }
    }

    // 🆕 NEW METHOD: Verify students are actually enrolled in the specific subject
    private fun verifySubjectEnrollment(
        students: List<Student>,
        subjectCode: String,
        semester: String,
        yearLevel: String
    ) {
        if (students.isEmpty()) {
            Log.d("StudentSubmissions", "ℹ️ No students to verify enrollment for")
            loadStudentUserMappings()
            return
        }

        updateLoadingText("Verifying subject enrollment...")

        // Clean strings for Firestore document ID (same as ClassDetailsActivity)
        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"

        Log.d("StudentSubmissions", "🔍 Verifying enrollment for ${students.size} students in subject: $enrollmentDocId")

        lifecycleScope.launch {
            try {
                val enrollmentChecks = students.map { student ->
                    async {
                        try {
                            val subjectRef = firestore.collection("students").document(student.id)
                                .collection("subjects").document(enrollmentDocId)

                            val subjectSnapshot = subjectRef.get().await()

                            if (subjectSnapshot.exists()) {
                                Log.d("StudentSubmissions", "✅ Student ${student.id} is enrolled in $subjectCode")
                                student // Return student if enrolled
                            } else {
                                Log.d("StudentSubmissions", "❌ Student ${student.id} is NOT enrolled in $subjectCode")
                                null // Return null if not enrolled
                            }
                        } catch (e: Exception) {
                            Log.e("StudentSubmissions", "❌ Error checking enrollment for ${student.id}: ${e.message}")
                            null
                        }
                    }
                }

                // Wait for all checks to complete
                val enrolledStudents = enrollmentChecks.awaitAll().filterNotNull()

                Log.d("StudentSubmissions", "🎯 FINAL: ${enrolledStudents.size} students actually enrolled in $subjectCode")

                // Update the student list with ONLY enrolled students
                studentList.clear()
                studentList.addAll(enrolledStudents)

                // Continue with loading user mappings
                loadStudentUserMappings()

            } catch (e: Exception) {
                Log.e("StudentSubmissions", "❌ Error verifying enrollment: ${e.message}")
                // Fallback: use all students if verification fails
                studentList.clear()
                studentList.addAll(students)
                loadStudentUserMappings()
            }
        }
    }

    // 🆕 NEW METHOD: Load student-user mappings to connect Firebase UIDs with student IDs
    private fun loadStudentUserMappings() {
        if (studentList.isEmpty()) {
            Log.e("StudentSubmissions", "❌ No students loaded, cannot load user mappings")
            setupAdapter()
            loadAllSubmissions()
            return
        }

        updateLoadingText("Loading user mappings...")
        Log.d("StudentSubmissions", "🔄 Loading user mappings for ${studentList.size} students")

        val studentIds = studentList.map { it.id }

        firestore.collection("users")
            .whereIn("studentId", studentIds)
            .get()
            .addOnSuccessListener { userDocs ->
                userMapping.clear()

                for (doc in userDocs) {
                    val studentId = doc.getString("studentId")
                    val firebaseUid = doc.id
                    if (studentId != null) {
                        userMapping[firebaseUid] = studentId
                        Log.d("StudentSubmissions", "🔗 Mapped: Firebase UID $firebaseUid -> Student ID $studentId")
                    }
                }

                Log.d("StudentSubmissions", "✅ Loaded ${userMapping.size} user mappings")

                // 🆕 NOW load submissions with the user mappings
                loadAllSubmissions()
            }
            .addOnFailureListener { e ->
                Log.e("StudentSubmissions", "❌ Error loading user mappings: ${e.message}")
                // Continue without mappings as fallback
                loadAllSubmissions()
            }
    }

    // 🆕 UPDATED: Make submission loading with user mapping
    private fun loadAllSubmissions() {
        updateLoadingText("Loading submissions...")
        Log.d("StudentSubmissions", "🔄 Loading submissions with user mapping")
        Log.d("StudentSubmissions", "🎯 Assignment ID: ${assignment.id}")

        // 🆕 EXACT SAME QUERY AS ViewSubmissionsActivity
        firestore.collection("submissions")
            .whereEqualTo("assignmentId", assignment.id)
            .get()
            .addOnSuccessListener { submissionsSnapshot ->
                Log.d("StudentSubmissions", "✅ Found ${submissionsSnapshot.documents.size} submission documents")

                studentSubmissionsMap.clear()

                var mappedSubmissions = 0
                var unmappedSubmissions = 0

                for (doc in submissionsSnapshot.documents) {
                    try {
                        Log.d("StudentSubmissions", "📄 Processing submission: ${doc.id}")

                        val submission = doc.toObject(Submission::class.java)
                        if (submission != null) {
                            submission.id = doc.id

                            // 🆕 USE USER MAPPING to find correct student ID
                            val firebaseUid = submission.studentId
                            val studentId = userMapping[firebaseUid]

                            if (studentId != null) {
                                // 🆕 Map to the correct student ID
                                studentSubmissionsMap[studentId] = submission
                                mappedSubmissions++
                                Log.d("StudentSubmissions", "✅ MAPPED: Firebase UID $firebaseUid -> Student ID $studentId")
                            } else {
                                // 🆕 Fallback: use Firebase UID directly
                                studentSubmissionsMap[firebaseUid] = submission
                                unmappedSubmissions++
                                Log.w("StudentSubmissions", "⚠️ UNMAPPED: No student found for Firebase UID $firebaseUid")
                            }

                            Log.d("StudentSubmissions", "   📝 Assignment ID: ${submission.assignmentId}")
                            Log.d("StudentSubmissions", "   👤 Student ID: ${submission.studentId}")
                            Log.d("StudentSubmissions", "   📊 Grade: ${submission.grade}")
                            Log.d("StudentSubmissions", "   📎 File: ${submission.fileUrl != null}")
                        } else {
                            Log.e("StudentSubmissions", "❌ Failed to convert document to Submission object")
                        }
                    } catch (e: Exception) {
                        Log.e("StudentSubmissions", "❌ Error parsing submission ${doc.id}: ${e.message}")
                    }
                }

                Log.d("StudentSubmissions", "🗂️ Final submissions map: ${studentSubmissionsMap.size} submissions")
                Log.d("StudentSubmissions", "📊 Mapped: $mappedSubmissions, Unmapped: $unmappedSubmissions")

                // 🆕 Call debug method to see what's happening
                debugStudentIdMatching()

                // 🆕 Update UI after loading submissions
                setupAdapter()
                loadExistingExtensions()
                updateEmptyState()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e("StudentSubmissions", "❌ Error loading submissions: ${e.message}")
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading submissions: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // 🆕 NEW METHOD: Debug student ID matching
    private fun debugStudentIdMatching() {
        Log.d("StudentSubmissions", "🔍 DEBUG: Student ID Matching Analysis")

        // Log all student IDs from the class
        Log.d("StudentSubmissions", "👥 Class Student IDs (${studentList.size}): ${studentList.map { it.id }}")

        // Log all student IDs from submissions (after mapping)
        Log.d("StudentSubmissions", "📝 Submission Student IDs (${studentSubmissionsMap.size}): ${studentSubmissionsMap.keys}")

        // Check which students have submissions
        studentList.forEach { student ->
            val hasSubmission = studentSubmissionsMap.containsKey(student.id)
            val submission = studentSubmissionsMap[student.id]
            val status = when {
                submission?.grade != null -> "Graded (${submission.grade})"
                submission?.submittedAt != null && submission.submittedAt > 0 -> "Submitted"
                hasSubmission -> "Has submission but no submit time"
                else -> "No submission"
            }
            Log.d("StudentSubmissions", "   👤 ${student.firstName} ${student.lastName} (${student.id}) -> $status")
        }

        // Check unmapped submissions
        studentSubmissionsMap.forEach { (studentId, submission) ->
            if (!studentList.any { it.id == studentId }) {
                Log.w("StudentSubmissions", "   ⚠️ Orphaned submission: Student ID $studentId not in class list")
            }
        }
    }

    private fun loadExistingExtensions() {
        Log.d("StudentSubmissions", "🔄 Loading extensions for assignment: ${assignment.id}")
        AssignmentRepository.getStudentExtensions(assignment.id) { extensions ->
            try {
                studentExtensionsMap.clear()
                if (extensions != null) {
                    studentExtensionsMap.putAll(extensions)
                    Log.d("StudentSubmissions", "✅ Loaded ${extensions.size} extensions")
                } else {
                    Log.d("StudentSubmissions", "ℹ️ No extensions found for this assignment")
                }
                adapter?.notifyDataSetChanged()
                updateEmptyState()
            } catch (e: Exception) {
                Log.e("StudentSubmissions", "❌ Error loading extensions: ${e.message}")
            }
        }
    }

    private fun setupAdapter() {
        try {
            adapter = StudentSubmissionsAdapter(studentList, studentSubmissionsMap, studentExtensionsMap, assignment)
            lvStudents.adapter = adapter
            Log.d("StudentSubmissions", "✅ Adapter setup with ${studentList.size} students")
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "❌ Error setting up adapter: ${e.message}")
            Toast.makeText(this, "Error setting up student list", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateEmptyState() {
        if (studentList.isEmpty()) {
            tvLoading.visibility = View.VISIBLE
            lvStudents.visibility = View.GONE
            tvLoading.text = "No students found for this class."
        } else {
            tvLoading.visibility = View.GONE
            lvStudents.visibility = View.VISIBLE

            val hasData = when (currentTabPosition) {
                0 -> true // Submissions tab - always show students even if no submissions
                1 -> studentExtensionsMap.isNotEmpty() // Extensions tab
                else -> true
            }

            if (!hasData && currentTabPosition == 1) {
                Toast.makeText(this, "No extensions granted yet. Click 'Extend Due Date' to add extensions.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extendDueDateForStudent(student: Student) {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extend_due_date, null)
            val etReason = dialogView.findViewById<EditText>(R.id.etReason)
            val tvNewDueDate = dialogView.findViewById<TextView>(R.id.tvNewDueDate)
            val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectDate)

            // ✅ ADDED: Get the TextViews for student info
            val tvStudentName = dialogView.findViewById<TextView>(R.id.tvStudentName)
            val tvCurrentDueDate = dialogView.findViewById<TextView>(R.id.tvCurrentDueDate)

            var selectedDueDate: Long = assignment.dueDateMillis

            // ✅ ADDED: Set the actual student data
            tvStudentName.text = "Student: ${student.firstName} ${student.lastName}"

            val currentDueDateFormatted = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                .format(Date(assignment.dueDateMillis))
            tvCurrentDueDate.text = "Current Due Date: $currentDueDateFormatted"

            btnSelectDate.setOnClickListener {
                showDateTimePicker { dueDateMillis ->
                    selectedDueDate = dueDateMillis
                    val formatted = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                        .format(Date(dueDateMillis))
                    tvNewDueDate.text = formatted
                }
            }

            AlertDialog.Builder(this)
                .setTitle("Extend Due Date for ${student.firstName} ${student.lastName}")
                .setView(dialogView)
                .setPositiveButton("Grant Extension") { dialog, which ->
                    try {
                        val reason = etReason.text.toString().trim()
                        if (selectedDueDate <= assignment.dueDateMillis) {
                            Toast.makeText(this, "Extended date must be after original due date", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        if (reason.isEmpty()) {
                            Toast.makeText(this, "Please provide a reason for the extension", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        val teacherId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"

                        AssignmentRepository.extendDueDateForStudent(
                            assignmentId = assignment.id,
                            studentId = student.id,
                            studentName = "${student.firstName} ${student.lastName}",
                            extendedDueDate = selectedDueDate,
                            reason = reason,
                            grantedBy = teacherId
                        ) { success, error ->
                            if (success) {
                                Toast.makeText(this, "Extension granted successfully", Toast.LENGTH_SHORT).show()
                                loadExistingExtensions()
                            } else {
                                Toast.makeText(this, "Error: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StudentSubmissions", "Error granting extension: ${e.message}")
                        Toast.makeText(this, "Error granting extension", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error showing extension dialog: ${e.message}")
            Toast.makeText(this, "Error showing extension dialog", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeExtension(studentId: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle("Remove Extension")
                .setMessage("Are you sure you want to remove this student's extension?")
                .setPositiveButton("Remove") { dialog, which ->
                    AssignmentRepository.removeStudentExtension(assignment.id, studentId) { success, error ->
                        if (success) {
                            Toast.makeText(this, "Extension removed", Toast.LENGTH_SHORT).show()
                            studentExtensionsMap.remove(studentId)
                            adapter?.notifyDataSetChanged()
                            updateEmptyState()
                        } else {
                            Toast.makeText(this, "Error: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error removing extension: ${e.message}")
            Toast.makeText(this, "Error removing extension", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDateTimePicker(onDateSelected: (Long) -> Unit) {
        try {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = assignment.dueDateMillis

            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    try {
                        calendar.set(year, month, day)
                        val timePicker = TimePickerDialog(
                            this,
                            { _, hour, minute ->
                                try {
                                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                                    calendar.set(Calendar.MINUTE, minute)
                                    onDateSelected(calendar.timeInMillis)
                                } catch (e: Exception) {
                                    Log.e("StudentSubmissions", "Error in time picker: ${e.message}")
                                }
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false
                        )
                        timePicker.show()
                    } catch (e: Exception) {
                        Log.e("StudentSubmissions", "Error in date picker: ${e.message}")
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = assignment.dueDateMillis
            datePicker.show()
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error showing date picker: ${e.message}")
            Toast.makeText(this, "Error showing date picker", Toast.LENGTH_LONG).show()
        }
    }

    private fun gradeSubmission(submission: Submission, student: Student) {
        try {
            val view = LayoutInflater.from(this).inflate(R.layout.item_submission, null)
            val tvStudent = view.findViewById<TextView>(R.id.tvStudentId)
            val tvFile = view.findViewById<TextView>(R.id.tvSubmissionFile)
            val tvSubmittedDate = view.findViewById<TextView>(R.id.tvSubmittedDate)
            val etGrade = view.findViewById<EditText>(R.id.etGrade)
            val etFeedback = view.findViewById<EditText>(R.id.etFeedback)
            val btnOpenFile = view.findViewById<Button>(R.id.btnOpenFile)
            val btnReopenSubmission = view.findViewById<Button>(R.id.btnReopenSubmission)

            tvStudent.text = "${student.firstName} ${student.lastName}"

            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val submittedDate = if (submission.submittedAt > 0) {
                sdf.format(Date(submission.submittedAt))
            } else {
                "Not submitted"
            }
            tvSubmittedDate.text = "Submitted: $submittedDate"

            tvFile.text = if (!submission.fileUrl.isNullOrEmpty()) {
                "File: ${getFileNameFromUrl(submission.fileUrl)}"
            } else {
                "No file submitted"
            }

            etGrade.setText(submission.grade?.toString() ?: "")
            etFeedback.setText(submission.feedback ?: "")

            btnOpenFile.setOnClickListener {
                val url = submission.fileUrl
                if (!url.isNullOrEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot open file. No app available.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "No file to open.", Toast.LENGTH_SHORT).show()
                }
            }

            btnReopenSubmission.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Reopen Submission")
                    .setMessage("This will allow the student to resubmit. Are you sure?")
                    .setPositiveButton("Yes, Reopen") { dialog, which ->
                        AssignmentRepository.reopenSubmissionForStudent(submission.id) { success, error ->
                            runOnUiThread {
                                if (success) {
                                    Toast.makeText(this, "Submission reopened successfully!", Toast.LENGTH_SHORT).show()
                                    loadAllData() // Reload everything
                                } else {
                                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            AlertDialog.Builder(this)
                .setTitle("Grade Submission for ${student.firstName} ${student.lastName}")
                .setView(view)
                .setPositiveButton("Save Grade") { _, _ ->
                    val gradeStr = etGrade.text.toString().trim()
                    val grade = gradeStr.toDoubleOrNull()
                    val feedback = etFeedback.text.toString().trim()

                    if (grade == null && gradeStr.isNotEmpty()) {
                        Toast.makeText(this, "Please enter a valid grade or leave empty.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    AssignmentRepository.gradeSubmission(submission.id, grade ?: 0.0, feedback) { ok, err ->
                        runOnUiThread {
                            if (ok) {
                                Toast.makeText(this, "Grade saved successfully!", Toast.LENGTH_SHORT).show()
                                loadAllData() // Reload everything
                            } else {
                                Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error showing grade dialog: ${e.message}")
            Toast.makeText(this, "Error showing grade dialog", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileNameFromUrl(fileUrl: String?): String {
        return try {
            fileUrl?.substringAfterLast('/')?.substringBefore('?') ?: "Submitted File"
        } catch (e: Exception) {
            "Submitted File"
        }
    }

    private inner class StudentSubmissionsAdapter(
        private val students: List<Student>,
        private val submissions: Map<String, Submission>,
        private val extensions: Map<String, StudentExtension>,
        private val assignment: Assignment
    ) : BaseAdapter() {

        override fun getCount(): Int = students.size
        override fun getItem(position: Int): Student = students[position]
        override fun getItemId(position: Int): Long = position.toLong()

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return try {
                val view = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_student_submission, parent, false)
                val student = getItem(position)

                val tvStudentName = view.findViewById<TextView>(R.id.tvStudentName)
                val tvSubmissionStatus = view.findViewById<TextView>(R.id.tvSubmissionStatus)
                val tvDueDate = view.findViewById<TextView>(R.id.tvDueDate)
                val tvExtensionInfo = view.findViewById<TextView>(R.id.tvExtensionInfo)
                val tvFileInfo = view.findViewById<TextView>(R.id.tvFileInfo)
                val btnGradeSubmission = view.findViewById<Button>(R.id.btnGradeSubmission)
                val btnExtendDueDate = view.findViewById<Button>(R.id.btnExtendDueDate)
                val btnRemoveExtension = view.findViewById<Button>(R.id.btnRemoveExtension)

                // Set student name
                tvStudentName.text = "${student.firstName} ${student.lastName}"

                val submission = submissions[student.id]
                val extension = extensions[student.id]

                // Set due date info
                val dueDate = extension?.extendedDueDate ?: assignment.dueDateMillis
                val formattedDate = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    .format(Date(dueDate))

                if (extension != null) {
                    tvDueDate.text = "Extended to: $formattedDate"
                    tvDueDate.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                } else {
                    tvDueDate.text = "Due: $formattedDate"
                    tvDueDate.setTextColor(resources.getColor(android.R.color.black, null))
                }

                // Show/hide elements based on current tab
                when (currentTabPosition) {
                    0 -> { // Submissions tab - Show ALL students
                        tvSubmissionStatus.visibility = View.VISIBLE
                        tvFileInfo.visibility = View.VISIBLE
                        btnGradeSubmission.visibility = View.VISIBLE
                        btnExtendDueDate.visibility = View.GONE
                        btnRemoveExtension.visibility = View.GONE
                        tvExtensionInfo.visibility = if (extension != null) View.VISIBLE else View.GONE

                        if (submission != null) {
                            when {
                                submission.grade != null -> {
                                    tvSubmissionStatus.text = "Status: Graded (${submission.grade}/100)"
                                    tvSubmissionStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                                }
                                submission.submittedAt > 0 -> {
                                    tvSubmissionStatus.text = "Status: Submitted - Awaiting Grade"
                                    tvSubmissionStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
                                }
                                else -> {
                                    tvSubmissionStatus.text = "Status: Not submitted"
                                    tvSubmissionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                                }
                            }

                            if (!submission.fileUrl.isNullOrEmpty()) {
                                tvFileInfo.text = "File: ${getFileNameFromUrl(submission.fileUrl)}"
                                tvFileInfo.visibility = View.VISIBLE
                            } else {
                                tvFileInfo.visibility = View.GONE
                            }

                            btnGradeSubmission.text = if (submission.grade != null) "View/Regrade" else "Grade Submission"
                            btnGradeSubmission.isEnabled = true
                            btnGradeSubmission.setOnClickListener {
                                gradeSubmission(submission, student)
                            }
                        } else {
                            // Student has NOT submitted
                            tvSubmissionStatus.text = "Status: Not submitted"
                            tvSubmissionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                            tvFileInfo.visibility = View.GONE
                            btnGradeSubmission.text = "No Submission"
                            btnGradeSubmission.isEnabled = false
                        }

                        if (extension != null) {
                            tvExtensionInfo.text = "Extension granted (Reason: ${extension.reason})"
                        }
                    }

                    1 -> { // Extensions tab - Show ALL students
                        tvSubmissionStatus.visibility = View.GONE
                        tvFileInfo.visibility = View.GONE
                        btnGradeSubmission.visibility = View.GONE

                        if (extension != null) {
                            // Student HAS extension
                            btnExtendDueDate.visibility = View.GONE
                            btnRemoveExtension.visibility = View.VISIBLE
                            tvExtensionInfo.visibility = View.VISIBLE
                            tvExtensionInfo.text = "Extension granted by ${extension.grantedBy}\nReason: ${extension.reason}"

                            btnRemoveExtension.setOnClickListener {
                                removeExtension(student.id)
                            }
                        } else {
                            // Student has NO extension
                            btnExtendDueDate.visibility = View.VISIBLE
                            btnRemoveExtension.visibility = View.GONE
                            tvExtensionInfo.visibility = View.GONE

                            btnExtendDueDate.setOnClickListener {
                                extendDueDateForStudent(student)
                            }
                        }
                    }
                }

                view
            } catch (e: Exception) {
                Log.e("StudentSubmissions", "Error in getView: ${e.message}")
                TextView(parent.context).apply {
                    text = "Error loading student"
                    setPadding(16, 16, 16, 16)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (adapter != null) {
                Log.d("StudentSubmissions", "🔄 onResume: Refreshing data")
                loadAllData()
            }
        } catch (e: Exception) {
            Log.e("StudentSubmissions", "Error in onResume: ${e.message}")
        }
    }
}
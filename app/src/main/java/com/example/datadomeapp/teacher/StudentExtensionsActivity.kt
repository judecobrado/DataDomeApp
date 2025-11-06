package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Assignment
import com.example.datadomeapp.models.Student
import com.example.datadomeapp.models.StudentExtension
import com.example.datadomeapp.repository.AssignmentRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StudentExtensionsActivity : AppCompatActivity() {

    private lateinit var tvAssignmentTitle: TextView
    private lateinit var lvStudents: ListView
    private lateinit var btnBack: Button
    private lateinit var tvLoading: TextView

    private lateinit var assignment: Assignment
    private var assignmentId: String = ""
    private var className: String? = null
    private val studentList = mutableListOf<Student>()
    private val studentExtensionsMap = mutableMapOf<String, StudentExtension>()
    private lateinit var adapter: StudentExtensionsAdapter

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_extensions)

        // ✅ SAFE: Initialize views with null checks
        try {
            tvAssignmentTitle = findViewById(R.id.tvAssignmentTitle)
            lvStudents = findViewById(R.id.lvStudents)
            btnBack = findViewById(R.id.btnBack)

            // ✅ SAFE: Try to find tvLoading, but don't crash if it doesn't exist
            tvLoading = try {
                findViewById(R.id.tvLoading)
            } catch (e: Exception) {
                // Create a temporary TextView if tvLoading doesn't exist in layout
                TextView(this).apply {
                    text = "Loading..."
                }
            }
        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error initializing views: ${e.message}")
            Toast.makeText(this, "Error initializing screen", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ✅ SAFE: Get assignment data from intent
        try {
            assignment = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("assignment", Assignment::class.java) ?: run {
                    Toast.makeText(this, "Error: No assignment data received", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Assignment>("assignment") ?: run {
                    Toast.makeText(this, "Error: No assignment data received", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            }

            assignmentId = intent.getStringExtra("assignmentId") ?: assignment.classId
            className = intent.getStringExtra("className")

            tvAssignmentTitle.text = "Manage Extensions: ${assignment.title}"
            updateLoadingText("Loading students...")

        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error getting intent data: ${e.message}")
            Toast.makeText(this, "Error loading assignment data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadClassStudents()
        loadExistingExtensions()

        btnBack.setOnClickListener { finish() }
    }

    // ✅ ADDED: Safe method to update loading text
    private fun updateLoadingText(text: String) {
        try {
            if (::tvLoading.isInitialized) {
                tvLoading.text = text
            } else {
                // If tvLoading doesn't exist, show as Toast
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error updating loading text: ${e.message}")
        }
    }

    private fun loadClassStudents() {
        // ✅ Get class details first to know which students to load
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val fetchedSemester = doc.getString("semester")
                val fetchedYearLevel = doc.getString("yearLevel")
                val selectedSectionName = className?.split(" - ")?.lastOrNull()

                if (fetchedSubjectCode.isNullOrEmpty() || selectedSectionName.isNullOrEmpty()
                    || fetchedSemester.isNullOrEmpty() || fetchedYearLevel.isNullOrEmpty()
                ) {
                    updateLoadingText("Error: Missing required class details.")
                    return@addOnSuccessListener
                }

                // ✅ Load only students enrolled in this specific class
                loadStudentsBySection(fetchedSubjectCode, fetchedSemester, fetchedYearLevel, selectedSectionName)
            }
            .addOnFailureListener { e ->
                Log.e("StudentExtensions", "Error loading class details: $e")
                updateLoadingText("Error loading class details")
            }
    }

    private fun loadStudentsBySection(
        selectedSubjectCode: String,
        selectedSemester: String,
        selectedYearLevel: String,
        selectedSectionName: String
    ) {
        updateLoadingText("Loading students for $selectedSubjectCode...")

        firestore.collection("students")
            .whereEqualTo("sectionId", selectedSectionName)
            .whereEqualTo("yearLevel", selectedYearLevel)
            .whereEqualTo("status", "Admitted")
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                val studentIds = studentsSnapshot.documents.map { it.id }

                if (studentIds.isEmpty()) {
                    updateLoadingText("No students found in section $selectedSectionName.")
                    return@addOnSuccessListener
                }

                // Now check which of these students are enrolled in THIS specific subject
                checkStudentEnrollment(studentIds, selectedSubjectCode, selectedSemester, selectedYearLevel)
            }
            .addOnFailureListener { e ->
                Log.e("StudentExtensions", "Error loading students: $e")
                updateLoadingText("Error loading students")
            }
    }

    private fun checkStudentEnrollment(
        studentIds: List<String>,
        subjectCode: String,
        semester: String,
        yearLevel: String
    ) {
        lifecycleScope.launch {
            try {
                val yearClean = yearLevel.replace(" ", "")
                val semesterCleaned = semester.replace(" ", "").replace("-", "")
                val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"

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
                        if (subjectSnapshot.exists()) studentId else null
                    }
                }

                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()
                studentList.clear()
                enrolledStudentIds.forEach { id -> studentMap[id]?.let { studentList.add(it) } }

                runOnUiThread {
                    if (studentList.isEmpty()) {
                        updateLoadingText("No students enrolled in $subjectCode.")
                    } else {
                        setupAdapter()
                        updateLoadingText("✅ ${studentList.size} students loaded")
                    }
                }

            } catch (e: Exception) {
                Log.e("StudentExtensions", "Error checking enrollment: ${e.message}", e)
                runOnUiThread {
                    updateLoadingText("Error validating enrollment")
                }
            }
        }
    }

    private fun loadExistingExtensions() {
        AssignmentRepository.getStudentExtensions(assignment.id) { extensions ->
            try {
                studentExtensionsMap.clear()
                if (extensions != null) {
                    studentExtensionsMap.putAll(extensions)
                }
                adapter?.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("StudentExtensions", "Error loading extensions: ${e.message}")
            }
        }
    }

    private fun setupAdapter() {
        try {
            adapter = StudentExtensionsAdapter(studentList, studentExtensionsMap, assignment.dueDateMillis)
            lvStudents.adapter = adapter
        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error setting up adapter: ${e.message}")
            Toast.makeText(this, "Error setting up student list", Toast.LENGTH_LONG).show()
        }
    }

    private fun extendDueDateForStudent(student: Student) {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extend_due_date, null)
            val etReason = dialogView.findViewById<EditText>(R.id.etReason)
            val tvNewDueDate = dialogView.findViewById<TextView>(R.id.tvNewDueDate)
            val btnSelectDate = dialogView.findViewById<Button>(R.id.btnSelectDate)

            var selectedDueDate: Long = assignment.dueDateMillis

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
                        Log.e("StudentExtensions", "Error granting extension: ${e.message}")
                        Toast.makeText(this, "Error granting extension", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error showing extension dialog: ${e.message}")
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
                            adapter.notifyDataSetChanged()
                        } else {
                            Toast.makeText(this, "Error: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error removing extension: ${e.message}")
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
                                    Log.e("StudentExtensions", "Error in time picker: ${e.message}")
                                }
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false
                        )
                        timePicker.show()
                    } catch (e: Exception) {
                        Log.e("StudentExtensions", "Error in date picker: ${e.message}")
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = assignment.dueDateMillis
            datePicker.show()
        } catch (e: Exception) {
            Log.e("StudentExtensions", "Error showing date picker: ${e.message}")
            Toast.makeText(this, "Error showing date picker", Toast.LENGTH_LONG).show()
        }
    }

    private inner class StudentExtensionsAdapter(
        private val students: List<Student>,
        private val extensions: Map<String, StudentExtension>,
        private val originalDueDate: Long
    ) : BaseAdapter() {

        override fun getCount(): Int = students.size
        override fun getItem(position: Int): Student = students[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return try {
                val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.list_item_student_extension, parent, false)
                val student = getItem(position)

                val tvStudentName = view.findViewById<TextView>(R.id.tvStudentName)
                val tvDueDate = view.findViewById<TextView>(R.id.tvDueDate)
                val tvExtensionInfo = view.findViewById<TextView>(R.id.tvExtensionInfo)
                val btnExtend = view.findViewById<Button>(R.id.btnExtend)
                val btnRemoveExtension = view.findViewById<Button>(R.id.btnRemoveExtension)

                // ✅ SAFE: Set student name
                tvStudentName.text = "${student.firstName} ${student.lastName}"

                val extension = extensions[student.id]
                if (extension != null) {
                    val formattedDate = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                        .format(Date(extension.extendedDueDate))
                    tvDueDate.text = "Extended to: $formattedDate"
                    tvExtensionInfo.text = "Reason: ${extension.reason}"
                    tvExtensionInfo.visibility = View.VISIBLE
                    btnExtend.visibility = View.GONE
                    btnRemoveExtension.visibility = View.VISIBLE
                } else {
                    val formattedDate = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                        .format(Date(originalDueDate))
                    tvDueDate.text = "Due: $formattedDate"
                    tvExtensionInfo.visibility = View.GONE
                    btnExtend.visibility = View.VISIBLE
                    btnRemoveExtension.visibility = View.GONE
                }

                btnExtend.setOnClickListener {
                    extendDueDateForStudent(student)
                }

                btnRemoveExtension.setOnClickListener {
                    removeExtension(student.id)
                }

                view
            } catch (e: Exception) {
                Log.e("StudentExtensions", "Error in getView: ${e.message}")
                // Return a simple view if there's an error
                TextView(parent.context).apply {
                    text = "Error loading student"
                }
            }
        }
    }
}
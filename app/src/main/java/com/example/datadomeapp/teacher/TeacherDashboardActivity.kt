package com.example.datadomeapp.teacher

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.student.UserCanteenMenuActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var teacherUid: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tacher_dashboard) // Make sure this matches your XML filename

        teacherUid = auth.currentUser?.uid

        // Initialize header views
        val tvTeacherName = findViewById<TextView>(R.id.tvTeacherName)
        val tvTeacherId = findViewById<TextView>(R.id.tvTeacherId)
        val tvClassCount = findViewById<TextView>(R.id.tvClassCount)
        val tvStudentCount = findViewById<TextView>(R.id.tvStudentCount)
        val tvPendingCount = findViewById<TextView>(R.id.tvPendingCount)

        // Set initial values
        tvTeacherName.text = "Welcome, Teacher!"
        loadTeacherId(tvTeacherId)

        // Load teacher stats
        loadTeacherStats(tvClassCount, tvStudentCount, tvPendingCount)

        // Initialize card views
        val cardManageClasses = findViewById<MaterialCardView>(R.id.cardManageClasses)
        val cardMySchedule = findViewById<MaterialCardView>(R.id.cardMySchedule)
        val cardQuiz = findViewById<MaterialCardView>(R.id.cardQuiz)
        val cardAssessment = findViewById<MaterialCardView>(R.id.cardAssessment)
        val cardAttendance = findViewById<MaterialCardView>(R.id.cardAttendance)
        val cardGrades = findViewById<MaterialCardView>(R.id.cardGrades)
        val cardCanteen = findViewById<MaterialCardView>(R.id.cardCanteen)
        val cardTodo = findViewById<MaterialCardView>(R.id.cardTodo)
        val cardNotes = findViewById<MaterialCardView>(R.id.cardNotes)
        val cardVoice = findViewById<MaterialCardView>(R.id.cardVoice)
        val cardRoulette = findViewById<MaterialCardView>(R.id.cardRoulette)
        val cardLogout = findViewById<MaterialCardView>(R.id.cardLogout)

        // Set click listeners for cards - UPDATED WITH NEW DESIGN
        cardQuiz.setOnClickListener {
            showEnhancedClassPickerDialog("Quiz/Exam")
        }

        cardAssessment.setOnClickListener {
            showEnhancedClassPickerDialog("Assessment")
        }

        cardAttendance.setOnClickListener {
            showEnhancedClassPickerDialog("Attendance")
        }

        cardGrades.setOnClickListener {
            showEnhancedClassPickerDialog("Grades")
        }

        // Open Canteen Menu
        cardCanteen.setOnClickListener {
            val intent = Intent(this, UserCanteenMenuActivity::class.java)
            intent.putExtra("USER_TYPE", "teacher")
            intent.putExtra("USER_ID", teacherUid)
            startActivity(intent)
        }

        // Manage Classes
        cardManageClasses.setOnClickListener {
            startActivity(Intent(this, ManageClassesActivity::class.java))
        }

        // Schedule
        cardMySchedule.setOnClickListener {
            startActivity(Intent(this, TeacherScheduleMatrixActivity::class.java))
        }

        // Notes
        cardNotes.setOnClickListener {
            val intent = Intent(this, TeacherNotesActivity::class.java)
            startActivity(intent)
        }

        // To-Do List
        cardTodo.setOnClickListener {
            val intent = Intent(this, TeacherToDoListActivity::class.java)
            startActivity(intent)
        }

        // Voice Detection
        cardVoice.setOnClickListener {
            startActivity(Intent(this, VoiceDetectionActivity::class.java))
        }

        // Roleta
        cardRoulette.setOnClickListener {
            showClassPickerDialogForRouleta()
        }

        // Logout
        cardLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun loadTeacherId(tvTeacherId: TextView) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("teachers")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val teacherId = doc.getString("teacherId") ?: "TCH-0000"
                    tvTeacherId.text = "ID: $teacherId"
                } else {
                    tvTeacherId.text = "ID: TCH-0000"
                }
            }
            .addOnFailureListener {
                tvTeacherId.text = "ID: TCH-0000"
            }
    }


    private fun loadTeacherStats(tvClassCount: TextView, tvStudentCount: TextView, tvPendingCount: TextView) {
        val currentTeacherUid = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // Load class assignments
                val classSnapshot = firestore.collection("classAssignments")
                    .whereEqualTo("teacherUid", currentTeacherUid)
                    .get()
                    .await()

                val classCount = classSnapshot.documents.size
                tvClassCount.text = classCount.toString()

                // Calculate total students across all classes
                var totalStudents = 0
                var pendingCount = 0

                // For demo purposes, set some values - replace with actual logic
                totalStudents = classCount * 25 // Assuming 25 students per class average
                pendingCount = classCount * 3   // Assuming 3 pending items per class

                tvStudentCount.text = totalStudents.toString()
                tvPendingCount.text = pendingCount.toString()

            } catch (e: Exception) {
                Log.e("TeacherDashboard", "Error loading stats: ${e.message}")
                // Set default values
                tvClassCount.text = "0"
                tvStudentCount.text = "0"
                tvPendingCount.text = "0"
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    // NEW ENHANCED CLASS PICKER DIALOG WITH BETTER DESIGN
    private fun showEnhancedClassPickerDialog(activityType: String) {
        val currentTeacherUid = auth.currentUser?.uid ?: return

        // Show loading dialog
        val loadingDialog = createLoadingDialog()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                // Load class assignments
                val classSnapshot = firestore.collection("classAssignments")
                    .whereEqualTo("teacherUid", currentTeacherUid)
                    .get()
                    .await()

                if (classSnapshot.isEmpty) {
                    loadingDialog.dismiss()
                    showNoClassesDialog()
                    return@launch
                }

                // Create ClassSelectionDTO objects with enhanced data
                val classDTOs = classSnapshot.documents.mapNotNull { doc ->
                    try {
                        val subjectCode = doc.getString("subjectCode") ?: return@mapNotNull null
                        val subjectTitle = doc.getString("subjectTitle") ?: "Unnamed Subject"
                        val semester = doc.getString("semester") ?: return@mapNotNull null
                        val yearLevel = doc.getString("yearLevel") ?: return@mapNotNull null
                        val section = doc.getString("section") ?: "No Section"
                        val course = doc.getString("courseCode") ?: "No Course"
                        val assignmentId = doc.id

                        val yearNumber = yearLevel.replace(Regex("[^0-9]"), "").take(1)
                        val displayName = "$course - $yearNumber$section - $subjectCode"

                        // Get student count for this class
                        val totalStudents = getStudentCountForClass(course, section)

                        ClassSelectionDTO(
                            assignmentId = assignmentId,
                            className = displayName,
                            subjectCode = subjectCode,
                            subjectTitle = subjectTitle,
                            semester = semester,
                            yearLevel = yearLevel,
                            section = section,
                            course = course,
                            totalStudents = totalStudents,
                            activityType = activityType
                        )
                    } catch (e: Exception) {
                        Log.e("ClassSelectionDTO", "Error creating DTO: ${e.message}")
                        null
                    }
                }

                loadingDialog.dismiss()

                if (classDTOs.isEmpty()) {
                    showNoClassesDialog()
                    return@launch
                }

                showEnhancedClassSelectionDialog(classDTOs, activityType)

            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("EnhancedClassPicker", "Error loading classes: ${e.message}")
                showErrorDialog("Failed to load classes. Please try again.")
            }
        }
    }

    private suspend fun getStudentCountForClass(course: String, section: String): Int {
        return try {
            val snapshot = firestore.collection("students")
                .whereEqualTo("courseCode", course)
                .whereEqualTo("sectionId", section)
                .get()
                .await()
            snapshot.documents.size
        } catch (e: Exception) {
            Log.e("StudentCount", "Error getting student count: ${e.message}")
            0
        }
    }

    private fun showEnhancedClassSelectionDialog(classDTOs: List<ClassSelectionDTO>, activityType: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_enhanced_class_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        val tvTitle: TextView = dialog.findViewById(R.id.tvTitle)
        val rvClasses: RecyclerView = dialog.findViewById(R.id.rvClasses)
        val btnCancel: Button = dialog.findViewById(R.id.btnCancel)

        tvTitle.text = "Select Class for $activityType"

        // Setup RecyclerView
        val adapter = EnhancedClassSelectionAdapter(classDTOs) { selectedClassDTO ->
            dialog.dismiss()
            navigateToActivityWithDTO(selectedClassDTO)
        }

        rvClasses.adapter = adapter
        rvClasses.layoutManager = LinearLayoutManager(this)

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun navigateToActivityWithDTO(classDTO: ClassSelectionDTO) {
        val intent = when (classDTO.activityType) {
            "Quiz/Exam" -> Intent(this, ManageQuizzesActivity::class.java)
            "Assessment" -> Intent(this, AssignmentListActivity::class.java)
            "Attendance" -> Intent(this, RecordAttendanceActivity::class.java)
            "Grades" -> Intent(this, ManageGradesActivity::class.java)
            else -> return
        }

        if (classDTO.activityType == "Assessment") {
            intent.putExtra("assignmentId", classDTO.assignmentId)
        } else {
            intent.putExtra("ASSIGNMENT_ID", classDTO.assignmentId)
        }

        val classNameToPass = if (classDTO.activityType == "Grades") {
            // Use "Subject Title - Section" format for Grades
            "${classDTO.subjectTitle} - ${classDTO.section}"
        } else {
            // Keep original format for other activities
            classDTO.className
        }

        intent.putExtra("CLASS_NAME", classNameToPass)
        intent.putExtra("SUBJECT_CODE", classDTO.subjectCode)

        if (classDTO.activityType == "Grades") {
            intent.putExtra("SECTION_NAME", classDTO.section)
            intent.putExtra("YEAR_LEVEL", classDTO.yearLevel)
        }

        // Additional data for enhanced functionality
        intent.putExtra("SUBJECT_TITLE", classDTO.subjectTitle)
        intent.putExtra("SEMESTER", classDTO.semester)
        intent.putExtra("COURSE", classDTO.course)
        intent.putExtra("TOTAL_STUDENTS", classDTO.totalStudents)

        startActivity(intent)
    }

    private fun extractSectionIdFromClassName(className: String): String {
        return try {
            // Try different patterns:
            // "BSIT - 1A - GE 2" -> "1A"
            // "BSIT - 2B - Mathematics" -> "2B"
            // "BSCS - 3C - Programming" -> "3C"
            val pattern = """[A-Z]+ - (\d+[A-Z]) - .+""".toRegex()
            val match = pattern.find(className)

            if (match != null) {
                match.groupValues[1] // Returns "1A", "2B", etc.
            } else {
                // Fallback: get the part after first " - "
                className.split(" - ").getOrNull(1) ?: "A"
            }
        } catch (e: Exception) {
            Log.e("Dashboard", "Error extracting section from: $className")
            "A"
        }
    }

    // OLD METHOD - KEEP FOR REFERENCE BUT NOT USED
    private fun showClassPickerDialogForActivity(activityType: String) {
        // This method is replaced by showEnhancedClassPickerDialog
    }

    private fun showClassPickerDialogForRouleta() {
        val currentTeacherUid = auth.currentUser?.uid ?: return

        // Show loading dialog
        val loadingDialog = createLoadingDialog()
        loadingDialog.show()

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                loadingDialog.dismiss()

                val classList = snapshot.documents.mapNotNull { doc ->
                    val subjectCode = doc.getString("subjectCode") ?: return@mapNotNull null
                    val subjectTitle = doc.getString("subjectTitle") ?: "Unnamed Subject"
                    val semester = doc.getString("semester") ?: return@mapNotNull null
                    val yearLevel = doc.getString("yearLevel") ?: return@mapNotNull null
                    val section = doc.getString("section") ?: "No Section"
                    val course = doc.getString("courseCode") ?: "No Course"
                    val assignmentId = doc.id

                    val yearNumber = yearLevel.replace(Regex("[^0-9]"), "").take(1)
                    val displayText = "$course - $yearNumber$section - $subjectCode"

                    ClassRouletteData(
                        assignmentId = assignmentId,
                        displayText = displayText,
                        subjectCode = subjectCode,
                        subjectTitle = subjectTitle,
                        semester = semester,
                        yearLevel = yearLevel,
                        section = section,
                        course = course
                    )
                }

                if (classList.isEmpty()) {
                    showNoClassesDialog()
                    return@addOnSuccessListener
                }

                showClassSelectionDialog(classList)
            }
            .addOnFailureListener {
                loadingDialog.dismiss()
                showErrorDialog("Failed to load classes. Please check your connection.")
            }
    }

    private fun createLoadingDialog(): Dialog {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    private fun showNoClassesDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_no_classes)

        val btnClose: Button = dialog.findViewById(R.id.btnClose)
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun showClassSelectionDialog(classList: List<ClassRouletteData>) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_class_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Setup views
        val tvTitle: TextView = dialog.findViewById(R.id.tvTitle)
        val rvClasses: RecyclerView = dialog.findViewById(R.id.rvClasses)
        val btnCancel: Button = dialog.findViewById(R.id.btnCancel)

        tvTitle.text = "Select Class for Roleta"

        // Setup RecyclerView
        val adapter = ClassSelectionAdapter(classList) { selectedClass ->
            dialog.dismiss()
            loadStudentsAndOpenRoulette(selectedClass)
        }

        rvClasses.adapter = adapter
        rvClasses.layoutManager = LinearLayoutManager(this)

        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showErrorDialog(message: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_error)

        val tvErrorMessage: TextView = dialog.findViewById(R.id.tvErrorMessage)
        val btnClose: Button = dialog.findViewById(R.id.btnClose)

        tvErrorMessage.text = message
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun loadStudentsAndOpenRoulette(classData: ClassRouletteData) {
        firestore.collection("students")
            .whereEqualTo("courseCode", classData.course)
            .whereEqualTo("sectionId", classData.section)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No students found for ${classData.displayText}", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val studentDocs = snapshot.documents
                val studentIds = studentDocs.map { it.id }

                lifecycleScope.launch {
                    val enrolledStudentNames = mutableListOf<String>()
                    val enrollmentDocId = "${classData.yearLevel.replace(" ", "")}_${
                        classData.semester.replace(" ", "").replace("-", "")
                    }_${classData.subjectCode}"

                    val enrollmentChecks = studentIds.map { studentId ->
                        async {
                            val doc = firestore.collection("students")
                                .document(studentId)
                                .collection("subjects")
                                .document(enrollmentDocId)
                                .get()
                                .await()

                            if (doc.exists()) {
                                val student = studentDocs.first { it.id == studentId }
                                val firstName = student.getString("firstName") ?: ""
                                val lastName = student.getString("lastName") ?: ""
                                "$lastName, $firstName"
                            } else null
                        }
                    }

                    enrolledStudentNames.addAll(enrollmentChecks.awaitAll().filterNotNull())

                    if (enrolledStudentNames.isEmpty()) {
                        Toast.makeText(this@TeacherDashboardActivity,
                            "No enrolled students found for ${classData.subjectCode}.",
                            Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    // Open RouletteActivity
                    val intent = Intent(this@TeacherDashboardActivity, RouletteActivity::class.java)
                    intent.putStringArrayListExtra("STUDENT_NAMES_LIST", ArrayList(enrolledStudentNames))
                    intent.putExtra("CLASS_NAME", classData.displayText)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load students.", Toast.LENGTH_SHORT).show()
            }
    }

    // Data Classes
    data class ClassRouletteData(
        val assignmentId: String,
        val displayText: String,
        val subjectCode: String,
        val subjectTitle: String,
        val semester: String,
        val yearLevel: String,
        val section: String,
        val course: String
    )

    data class ClassSelectionDTO(
        val assignmentId: String,
        val className: String,
        val subjectCode: String,
        val subjectTitle: String,
        val semester: String,
        val yearLevel: String,
        val section: String,
        val course: String,
        val totalStudents: Int = 0,
        val activityType: String
    )

    // Adapters
    private inner class ClassSelectionAdapter(
        private val classList: List<ClassRouletteData>,
        private val onClassSelected: (ClassRouletteData) -> Unit
    ) : RecyclerView.Adapter<ClassSelectionAdapter.ClassViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_class_selection, parent, false)
            return ClassViewHolder(view)
        }

        override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
            holder.bind(classList[position])
        }

        override fun getItemCount(): Int = classList.size

        inner class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvClassName: TextView = itemView.findViewById(R.id.tvClassName)

            fun bind(classData: ClassRouletteData) {
                tvClassName.text = classData.displayText

                itemView.setOnClickListener {
                    onClassSelected(classData)
                }
            }
        }
    }

    private inner class EnhancedClassSelectionAdapter(
        private val classDTOs: List<ClassSelectionDTO>,
        private val onClassSelected: (ClassSelectionDTO) -> Unit
    ) : RecyclerView.Adapter<EnhancedClassSelectionAdapter.ClassViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_enhanced_class_selection, parent, false)
            return ClassViewHolder(view)
        }

        override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
            holder.bind(classDTOs[position])
        }

        override fun getItemCount(): Int = classDTOs.size

        inner class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardClass: MaterialCardView = itemView.findViewById(R.id.cardClass)
            private val tvClassName: TextView = itemView.findViewById(R.id.tvClassName)
            private val tvSubjectTitle: TextView = itemView.findViewById(R.id.tvSubjectTitle)
            private val tvClassDetails: TextView = itemView.findViewById(R.id.tvClassDetails)
            private val tvStudentCount: TextView = itemView.findViewById(R.id.tvStudentCount)
            private val tvActivityType: TextView = itemView.findViewById(R.id.tvActivityType)

            fun bind(classDTO: ClassSelectionDTO) {
                tvClassName.text = classDTO.className
                tvSubjectTitle.text = classDTO.subjectTitle
                tvClassDetails.text = "${classDTO.semester} • ${classDTO.yearLevel}"
                tvStudentCount.text = "${classDTO.totalStudents} students"
                tvActivityType.text = classDTO.activityType

                // Set different background color based on activity type
                when (classDTO.activityType) {
                    "Quiz/Exam" -> tvActivityType.setBackgroundColor(ContextCompat.getColor(this@TeacherDashboardActivity, R.color.quiz_color))
                    "Assessment" -> tvActivityType.setBackgroundColor(ContextCompat.getColor(this@TeacherDashboardActivity, R.color.assessment_color))
                    "Attendance" -> tvActivityType.setBackgroundColor(ContextCompat.getColor(this@TeacherDashboardActivity, R.color.attendance_color))
                    "Grades" -> tvActivityType.setBackgroundColor(ContextCompat.getColor(this@TeacherDashboardActivity, R.color.grades_color))
                }

                cardClass.setOnClickListener {
                    onClassSelected(classDTO)
                }
            }
        }
    }
}
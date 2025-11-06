package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Submission
import com.example.datadomeapp.repository.AssignmentRepository
import com.example.datadomeapp.models.Assignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentAssignmentsActivity : AppCompatActivity() {

    private lateinit var lvAssignments: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSubjectTitle: TextView
    private lateinit var progressBar: ProgressBar

    private val assignments = mutableListOf<Assignment>()
    private val submissionStatusMap = mutableMapOf<String, Submission?>()
    private lateinit var adapter: ArrayAdapter<String>

    private var classId: String? = null
    private var subjectName: String? = null
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val studentId = auth.currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_assignments)

        initializeViews()
        getIntentData()
        setupListView()
        loadAssignments()
    }

    private fun initializeViews() {
        lvAssignments = findViewById(R.id.lvStudentAssignments)
        tvEmpty = findViewById(R.id.tvEmptyAssignments)
        tvSubjectTitle = findViewById(R.id.tvSubjectTitle)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun getIntentData() {
        classId = intent.getStringExtra("classId")
        subjectName = intent.getStringExtra("subjectName")

        // Set subject title
        tvSubjectTitle.text = subjectName ?: "Assignments"

        if (classId.isNullOrEmpty()) {
            tvEmpty.text = "No class ID found!"
            return
        }
    }

    private fun setupListView() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf("Loading assignments..."))
        lvAssignments.adapter = adapter

        lvAssignments.setOnItemClickListener { _, _, position, _ ->
            if (position < assignments.size) {
                val assignment = assignments[position]
                val submission = submissionStatusMap[assignment.id]

                // ALWAYS open assignment details, regardless of submission status or overdue
                // The assignment details screen will handle the overdue logic
                openAssignmentDetails(assignment, submission)
            }
        }
    }

    private fun openAssignmentDetails(assignment: Assignment, submission: Submission?) {
        val intent = Intent(this, AssignmentDetailsActivity::class.java)
        intent.putExtra("assignmentId", assignment.id)
        intent.putExtra("assignmentTitle", assignment.title)
        intent.putExtra("assignmentInstructions", assignment.instructions)
        intent.putExtra("assignmentFileUrl", assignment.fileUrl)
        intent.putExtra("dueDateMillis", assignment.dueDateMillis)
        intent.putExtra("classId", classId)

        // Pass submission data if exists
        submission?.let { sub ->
            intent.putExtra("submissionId", sub.id)
            intent.putExtra("submissionFileUrl", sub.fileUrl)
            intent.putExtra("submissionDate", sub.submittedAt)
            intent.putExtra("grade", sub.grade)
            intent.putExtra("feedback", sub.feedback)
        }

        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Refresh assignments and submission status when returning from submission
        loadAssignments()
    }

    /** 🔹 Fetch all assignments for the student's class */
    private fun loadAssignments() {
        val id = classId
        if (id.isNullOrEmpty()) {
            tvEmpty.text = "No class ID found!"
            return
        }

        progressBar.visibility = View.VISIBLE
        tvEmpty.text = "Loading assignments..."
        adapter.clear()
        adapter.add("Loading assignments...")
        adapter.notifyDataSetChanged()

        AssignmentRepository.getAssignmentsForClass(id) { success, snapshot, error ->
            runOnUiThread {
                progressBar.visibility = View.GONE

                if (!success || snapshot == null) {
                    tvEmpty.text = "Error loading assignments."
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                assignments.clear()
                submissionStatusMap.clear()

                for (doc in snapshot.documents) {
                    val assignment = doc.toObject(Assignment::class.java)
                    if (assignment != null) {
                        assignment.id = doc.id
                        assignments.add(assignment)
                    }
                }

                if (assignments.isEmpty()) {
                    tvEmpty.text = "No assignments found for this subject."
                    adapter.clear()
                    adapter.add("No assignments found")
                    adapter.notifyDataSetChanged()
                } else {
                    tvEmpty.text = ""
                    // Sort assignments by due date (soonest first)
                    assignments.sortBy { it.dueDateMillis }

                    // Show loading status first
                    val loadingList = assignments.map { assignment ->
                        "${assignment.title}\nDue: ${formatDueDate(assignment.dueDateMillis)}\nStatus: Checking..."
                    }

                    adapter.clear()
                    adapter.addAll(loadingList)
                    adapter.notifyDataSetChanged()

                    // Load submission status for each assignment
                    loadSubmissionStatusForAllAssignments()
                }
            }
        }
    }

    /** 🔹 Load submission status for all assignments */
    private fun loadSubmissionStatusForAllAssignments() {
        if (studentId.isNullOrEmpty()) {
            updateAssignmentDisplay()
            return
        }

        var completedRequests = 0
        val totalAssignments = assignments.size

        if (totalAssignments == 0) {
            updateAssignmentDisplay()
            return
        }

        assignments.forEach { assignment ->
            AssignmentRepository.getSubmissionByStudentAndAssignment(studentId, assignment.id) { submission ->
                submissionStatusMap[assignment.id] = submission
                completedRequests++

                // Update UI when all requests are complete
                if (completedRequests == totalAssignments) {
                    runOnUiThread {
                        updateAssignmentDisplay()
                    }
                }
            }
        }
    }

    /** 🔹 Update the assignment list with submission status */
    private fun updateAssignmentDisplay() {
        val displayList = mutableListOf<String>()

        for (assignment in assignments) {
            val submission = submissionStatusMap[assignment.id]

            // Check if assignment is overdue
            val isOverdue = isAssignmentOverdue(assignment)
            val dueDateText = formatDueDate(assignment.dueDateMillis)

            // Determine status text and color coding
            val statusInfo = getAssignmentStatusInfo(assignment, submission, isOverdue)

            val displayText = buildString {
                append("${assignment.title}\n")
                append("📅 $dueDateText\n")
                append("${statusInfo.statusText}")

                // Add grade if available
                submission?.grade?.let { grade ->
                    append("\n📊 Grade: $grade")
                }

                // Add attachment indicator if teacher uploaded file
                if (!assignment.fileUrl.isNullOrEmpty()) {
                    append("\n📎 Has attached file")
                }

                // Add submission date if submitted
                if (submission != null && submission.submittedAt > 0) {
                    val submittedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(submission.submittedAt))
                    append("\n🕒 Submitted: $submittedDate")
                }
            }

            displayList.add(displayText)
        }

        adapter.clear()
        adapter.addAll(displayList)
        adapter.notifyDataSetChanged()
    }

    /** 🔹 Format due date with color coding for overdue assignments */
    private fun formatDueDate(dueDateMillis: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

        return if (dueDateMillis > 0) {
            val dueDate = sdf.format(Date(dueDateMillis))

            // Check if assignment is overdue
            if (System.currentTimeMillis() > dueDateMillis) {
                "⏰ $dueDate (OVERDUE)"
            } else {
                "⏰ $dueDate"
            }
        } else {
            "No due date"
        }
    }

    /** 🔹 Check if assignment is overdue */
    private fun isAssignmentOverdue(assignment: Assignment): Boolean {
        return assignment.dueDateMillis > 0 && System.currentTimeMillis() > assignment.dueDateMillis
    }

    /** 🔹 Get assignment status information */
    private fun getAssignmentStatusInfo(assignment: Assignment, submission: Submission?, isOverdue: Boolean): StatusInfo {
        return when {
            // Submitted and graded
            submission != null && submission.submittedAt > 0 && submission.grade != null -> {
                StatusInfo("✅ Submitted & Graded", Color.parseColor("#2E7D32")) // Green
            }

            // Submitted but not graded
            submission != null && submission.submittedAt > 0 -> {
                StatusInfo("✅ Submitted - Awaiting Grade", Color.parseColor("#FF9800")) // Orange
            }

            // Not submitted and overdue
            isOverdue -> {
                StatusInfo("❌ OVERDUE - Tap to View", Color.parseColor("#D32F2F")) // Red
            }

            // Not submitted but still time
            else -> {
                StatusInfo("📝 Not Submitted - Tap to View", Color.parseColor("#2196F3")) // Blue
            }
        }
    }

    /** 🔹 Data class for status information */
    data class StatusInfo(val statusText: String, val statusColor: Int)
}
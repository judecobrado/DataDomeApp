package com.example.datadomeapp.student

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.firebase.FirebaseUtils
import com.example.datadomeapp.models.Submission
import com.example.datadomeapp.repository.AssignmentRepository
import java.text.SimpleDateFormat
import java.util.*

class AssignmentDetailsActivity : AppCompatActivity() {

    private var assignmentId: String? = null
    private var classId: String? = null
    private var assignmentFileUrl: String? = null
    private var studentId: String? = null
    private var submission: Submission? = null
    private var dueDateMillis: Long = 0L
    private var academicTerm: String? = null
    private var academicYear: String? = null
    private var semester: String? = null
    private lateinit var tvAssignmentTitle: TextView
    private lateinit var tvDueDate: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var tvSubmissionStatus: TextView
    private lateinit var tvOverdueMessage: TextView
    private lateinit var btnViewAssignmentFile: Button
    private lateinit var btnSubmitAssignment: Button
    private lateinit var btnViewSubmission: Button
    private lateinit var layoutTeacherFile: LinearLayout
    private lateinit var layoutSubmission: LinearLayout
    private lateinit var tvGrade: TextView
    private lateinit var tvFeedback: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assignment_details)

        initializeViews()
        getIntentData()
        setupClickListeners()
        displayAssignmentDetails()
    }

    private fun initializeViews() {
        tvAssignmentTitle = findViewById(R.id.tvAssignmentTitle)
        tvDueDate = findViewById(R.id.tvDueDate)
        tvInstructions = findViewById(R.id.tvInstructions)
        tvSubmissionStatus = findViewById(R.id.tvSubmissionStatus)
        tvOverdueMessage = findViewById(R.id.tvOverdueMessage)
        btnViewAssignmentFile = findViewById(R.id.btnViewAssignmentFile)
        btnSubmitAssignment = findViewById(R.id.btnSubmitAssignment)
        btnViewSubmission = findViewById(R.id.btnViewSubmission)
        layoutTeacherFile = findViewById(R.id.layoutTeacherFile)
        layoutSubmission = findViewById(R.id.layoutSubmission)
        tvGrade = findViewById(R.id.tvGrade)
        tvFeedback = findViewById(R.id.tvFeedback)

        studentId = FirebaseUtils.currentUid()
    }

    private fun getIntentData() {
        assignmentId = intent.getStringExtra("assignmentId")
        classId = intent.getStringExtra("classId")
        assignmentFileUrl = intent.getStringExtra("assignmentFileUrl")
        dueDateMillis = intent.getLongExtra("dueDateMillis", 0L)
        academicTerm = intent.getStringExtra("academicTerm")
        academicYear = intent.getStringExtra("academicYear")
        semester = intent.getStringExtra("semester")
        // Get submission data if exists
        val submissionId = intent.getStringExtra("submissionId")
        val submissionFileUrl = intent.getStringExtra("submissionFileUrl")
        val submissionDate = intent.getLongExtra("submissionDate", 0L)
        val grade = intent.getDoubleExtra("grade", -1.0)
        val feedback = intent.getStringExtra("feedback")

        if (submissionId != null) {
            submission = Submission(
                id = submissionId,
                fileUrl = submissionFileUrl,
                submittedAt = submissionDate,
                grade = if (grade != -1.0) grade else null,
                feedback = feedback
            )
        }
    }

    private fun setupClickListeners() {
        btnViewAssignmentFile.setOnClickListener {
            assignmentFileUrl?.let { url ->
                openFileInBrowser(url)
            } ?: Toast.makeText(this, "No file available", Toast.LENGTH_SHORT).show()
        }

        btnSubmitAssignment.setOnClickListener {
            if (assignmentId != null && classId != null) {
                val intent = Intent(this, SubmitAssignmentActivity::class.java)
                intent.putExtra("assignmentId", assignmentId)
                intent.putExtra("assignmentTitle", tvAssignmentTitle.text.toString())
                intent.putExtra("classId", classId)
                intent.putExtra("academicTerm", academicTerm)
                intent.putExtra("academicYear", academicYear)
                intent.putExtra("semester", semester)
                startActivity(intent)
            }
        }

        btnViewSubmission.setOnClickListener {
            submission?.fileUrl?.let { url ->
                openFileInBrowser(url)
            } ?: Toast.makeText(this, "No submission file available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayAssignmentDetails() {
        // Set assignment title
        tvAssignmentTitle.text = intent.getStringExtra("assignmentTitle") ?: "Assignment"

        // Set due date
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        val dueDateText = if (dueDateMillis > 0) {
            "Due: ${sdf.format(Date(dueDateMillis))}"
        } else {
            "No due date"
        }
        tvDueDate.text = dueDateText

        // Set instructions
        val instructions = intent.getStringExtra("assignmentInstructions") ?: "No instructions provided."
        tvInstructions.text = instructions

        // Show/hide teacher file section
        if (!assignmentFileUrl.isNullOrEmpty()) {
            layoutTeacherFile.visibility = View.VISIBLE
        } else {
            layoutTeacherFile.visibility = View.GONE
        }

        // Display submission status and handle overdue assignments
        displaySubmissionStatus()
    }

    private fun displaySubmissionStatus() {
        val isOverdue = isAssignmentOverdue()

        if (submission != null && submission!!.submittedAt > 0) {
            // Already submitted
            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val submittedDate = sdf.format(Date(submission!!.submittedAt))

            tvSubmissionStatus.text = "✅ Submitted on: $submittedDate"
            layoutSubmission.visibility = View.VISIBLE
            btnSubmitAssignment.visibility = View.GONE
            tvOverdueMessage.visibility = View.GONE

            // Show grade and feedback if available
            submission!!.grade?.let { grade ->
                tvGrade.text = "Grade: $grade"
                tvGrade.visibility = View.VISIBLE
            } ?: run {
                tvGrade.visibility = View.GONE
            }

            submission!!.feedback?.let { feedback ->
                tvFeedback.text = "Feedback: $feedback"
                tvFeedback.visibility = View.VISIBLE
            } ?: run {
                tvFeedback.visibility = View.GONE
            }

        } else {
            // Not submitted
            if (isOverdue) {
                // Overdue and not submitted - show message instead of button
                tvSubmissionStatus.text = "❌ Assignment Overdue"
                tvOverdueMessage.visibility = View.VISIBLE
                btnSubmitAssignment.visibility = View.GONE
                layoutSubmission.visibility = View.GONE
                tvGrade.visibility = View.GONE
                tvFeedback.visibility = View.GONE
            } else {
                // Not submitted but still time - show submit button
                tvSubmissionStatus.text = "📝 Not submitted yet"
                tvOverdueMessage.visibility = View.GONE
                btnSubmitAssignment.visibility = View.VISIBLE
                layoutSubmission.visibility = View.GONE
                tvGrade.visibility = View.GONE
                tvFeedback.visibility = View.GONE
            }
        }
    }

    private fun isAssignmentOverdue(): Boolean {
        return dueDateMillis > 0 && System.currentTimeMillis() > dueDateMillis
    }

    private fun openFileInBrowser(fileUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file. No app available.", Toast.LENGTH_LONG).show()
        }
    }
}
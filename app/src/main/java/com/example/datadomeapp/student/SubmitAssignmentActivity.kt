package com.example.datadomeapp.student

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import com.example.datadomeapp.models.Assignment
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.firebase.FirebaseUtils
import com.example.datadomeapp.models.Submission
import com.example.datadomeapp.repository.AssignmentRepository
import java.text.SimpleDateFormat
import java.util.*

class SubmitAssignmentActivity : AppCompatActivity() {

    private val PICK_FILE_REQUEST = 1001
    private var fileUri: Uri? = null
    private var assignmentId: String? = null
    private var classId: String? = null
    private var studentId: String? = null
    private var assignment: Assignment? = null
    private var existingSubmission: Submission? = null
    private var submissionAcademicTerm: String? = null
    private var submissionAcademicYear: String? = null
    private var submissionSemester: String? = null
    private lateinit var tvAssignment: TextView
    private lateinit var tvSubmissionStatus: TextView
    private lateinit var tvDueDate: TextView
    private lateinit var btnChoose: Button
    private lateinit var btnSubmit: Button
    private lateinit var etNote: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSelectedFileName: TextView
    private lateinit var tvSubmittedFile: TextView
    private lateinit var btnOpenFile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_assignment)

        initializeViews()
        getIntentData()
        setupClickListeners()
        loadAssignmentDetails()
    }

    private fun initializeViews() {
        tvAssignment = findViewById(R.id.tvAssignmentTitle)
        tvSubmissionStatus = findViewById(R.id.tvSubmissionStatus)
        tvDueDate = findViewById(R.id.tvDueDate)
        btnChoose = findViewById(R.id.btnChooseSubmission)
        btnSubmit = findViewById(R.id.btnSubmitAssignment)
        etNote = findViewById(R.id.etSubmissionNote)
        progressBar = findViewById(R.id.progressBar)
        tvSelectedFileName = findViewById(R.id.tvSelectedFileName)
        tvSubmittedFile = findViewById(R.id.tvSubmittedFile)
        btnOpenFile = findViewById(R.id.btnOpenFile)

        // Hide submitted file section initially
        tvSubmittedFile.visibility = View.GONE
        btnOpenFile.visibility = View.GONE
    }

    private fun getIntentData() {
        assignmentId = intent.getStringExtra("assignmentId")
        classId = intent.getStringExtra("classId")
        studentId = FirebaseUtils.currentUid()
        submissionAcademicTerm = intent.getStringExtra("academicTerm")
        submissionAcademicYear = intent.getStringExtra("academicYear")
        submissionSemester = intent.getStringExtra("semester")

        if (assignmentId.isNullOrEmpty()) {
            showError("Assignment ID is missing")
            finish()
            return
        }

        if (classId.isNullOrEmpty()) {
            showError("Class ID is missing")
            finish()
            return
        }

        if (studentId.isNullOrEmpty()) {
            showError("Student ID is missing - please log in again")
            finish()
            return
        }

        tvAssignment.text = "Assignment: Loading..."
    }

    private fun loadAssignmentDetails() {
        val aid = assignmentId ?: return

        AssignmentRepository.getAssignmentById(aid) { loadedAssignment ->
            runOnUiThread {
                if (loadedAssignment != null) {
                    assignment = loadedAssignment
                    displayAssignmentDetails(loadedAssignment)
                    checkExistingSubmission()
                } else {
                    showError("Failed to load assignment details")
                    finish()
                }
            }
        }
    }

    private fun displayAssignmentDetails(assignment: Assignment) {
        tvAssignment.text = assignment.title

        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        val dueDateText = if (assignment.dueDateMillis > 0) {
            "Due: ${sdf.format(Date(assignment.dueDateMillis))}"
        } else {
            "No due date"
        }
        tvDueDate.text = dueDateText

        if (isAssignmentOverdue(assignment)) {
            tvDueDate.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            tvDueDate.text = "$dueDateText ⚠️ OVERDUE"
        } else {
            tvDueDate.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        }
    }

    private fun isAssignmentOverdue(assignment: Assignment): Boolean {
        return assignment.dueDateMillis > 0 && System.currentTimeMillis() > assignment.dueDateMillis
    }

    private fun setupClickListeners() {
        btnChoose.setOnClickListener {
            openFilePicker()
        }

        btnSubmit.setOnClickListener {
            submitAssignment()
        }

        btnOpenFile.setOnClickListener {
            openSubmittedFile()
        }
    }

    private fun checkExistingSubmission() {
        val aid = assignmentId ?: return
        val uid = studentId ?: return

        AssignmentRepository.getSubmissionByStudentAndAssignment(uid, aid) { submission ->
            runOnUiThread {
                existingSubmission = submission
                updateUIForSubmissionStatus(submission)
            }
        }
    }

    private fun updateUIForSubmissionStatus(submission: Submission?) {
        if (submission != null && submission.submittedAt > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val submittedDate = sdf.format(Date(submission.submittedAt))

            tvSubmissionStatus.text = "✅ Submitted on: $submittedDate"

            submission.grade?.let { grade ->
                tvSubmissionStatus.append("\n📊 Grade: $grade")
                submission.feedback?.let { feedback ->
                    tvSubmissionStatus.append("\n💬 Feedback: $feedback")
                }
            }

            // Show submitted file section - FIXED: Use safe calls
            val fileUrl = submission.fileUrl
            if (!fileUrl.isNullOrEmpty()) {
                tvSubmittedFile.visibility = View.VISIBLE
                btnOpenFile.visibility = View.VISIBLE

                // Get file name from URL or show generic text
                val fileName = getFileNameFromUrl(fileUrl)
                tvSubmittedFile.text = "Uploaded File: $fileName"
            }

            // Disable submission since it's already submitted
            btnSubmit.isEnabled = false
            btnSubmit.text = "Already Submitted"
            btnSubmit.backgroundTintList = resources.getColorStateList(android.R.color.darker_gray, null)
            btnChoose.isEnabled = false

        } else {
            tvSubmissionStatus.text = "📝 Not submitted yet"
            btnSubmit.text = "Submit Assignment"
            btnSubmit.isEnabled = true
            btnChoose.isEnabled = true
        }
    }

    private fun getFileNameFromUrl(fileUrl: String): String {
        return try {
            fileUrl.substringAfterLast('/').substringBefore('?')
        } catch (e: Exception) {
            "Submitted File"
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(Intent.createChooser(intent, "Choose file"), PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            fileUri = data?.data
            if (fileUri != null) {
                btnChoose.text = "Change File"
                try {
                    val fileName = getFileName(fileUri!!)
                    tvSelectedFileName.text = "Selected: $fileName"
                } catch (e: Exception) {
                    tvSelectedFileName.text = "File selected"
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = ""
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex("_display_name")
                if (displayNameIndex != -1) {
                    result = it.getString(displayNameIndex)
                }
            }
        }
        return result.ifEmpty { "Unknown File" }
    }

    private fun submitAssignment() {
        val aid = assignmentId ?: return showError("Assignment ID missing")
        val cid = classId ?: return showError("Class ID missing")
        val uid = studentId ?: return showError("Student ID missing")
        val currentAssignment = assignment ?: return showError("Assignment data not loaded")

        // Check if assignment is overdue
        if (isAssignmentOverdue(currentAssignment)) {
            showError("❌ This assignment is overdue and cannot be submitted. Please contact your teacher.")
            return
        }

        if (fileUri == null) {
            showError("Please select a file to submit")
            return
        }

        performSubmission(aid, cid, uid)
    }

    private fun performSubmission(assignmentId: String, classId: String, studentId: String) {
        val submissionNote = etNote.text.toString().trim()

        val submission = Submission(
            id = "", // New submission
            assignmentId = assignmentId,
            studentId = studentId,
            classId = classId,
            fileUrl = null,
            imageUrl = null,
            submittedAt = System.currentTimeMillis(),
            status = "submitted",
            isResubmitted = false,
            academicTerm = submissionAcademicTerm,
            academicYear = submissionAcademicYear,
            semester = submissionSemester
        )

        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
        btnChoose.isEnabled = false

        AssignmentRepository.submitAssignment(submission, fileUri) { success, error ->
            runOnUiThread {
                progressBar.visibility = View.GONE

                if (success) {
                    // Show success message and update UI
                    Toast.makeText(this, "✅ Assignment submitted successfully!", Toast.LENGTH_LONG).show()

                    // Update UI to show submission status
                    tvSubmissionStatus.text = "✅ Submitted just now"

                    // Show the submitted file
                    val currentFileUri = fileUri // Store in local variable to avoid smart cast issues
                    if (currentFileUri != null) {
                        try {
                            val fileName = getFileName(currentFileUri)
                            tvSubmittedFile.text = "Uploaded File: $fileName"
                            tvSubmittedFile.visibility = View.VISIBLE
                            btnOpenFile.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            tvSubmittedFile.text = "Uploaded File: Submitted File"
                            tvSubmittedFile.visibility = View.VISIBLE
                            btnOpenFile.visibility = View.VISIBLE
                        }
                    }

                    // Keep the submit button disabled since it's already submitted
                    btnSubmit.text = "Already Submitted"
                    btnSubmit.backgroundTintList = resources.getColorStateList(android.R.color.darker_gray, null)

                } else {
                    btnSubmit.isEnabled = true
                    btnChoose.isEnabled = true
                    showError("Submission failed: $error")
                }
            }
        }
    }

    private fun openSubmittedFile() {
        // Since we just submitted, we can open the local file URI
        val currentFileUri = fileUri // Store in local variable to avoid smart cast issues
        currentFileUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "*/*")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showError("No app available to open this file")
            }
        } ?: showError("No file available to open")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e("SUBMIT_ASSIGNMENT", message)
    }
}
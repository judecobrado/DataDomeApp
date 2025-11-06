package com.example.datadomeapp.teacher

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Submission
import com.example.datadomeapp.repository.AssignmentRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.ArrayList

class ViewSubmissionsActivity : AppCompatActivity() {

    private lateinit var lv: ListView
    private val submissions = mutableListOf<Submission>()
    private lateinit var adapter: ArrayAdapter<String>
    private var classId: String? = null  // Changed from assignmentId to classId
    private var className: String? = null
    private lateinit var tvEmpty: TextView
    private lateinit var tvSubmissionCount: TextView
    private lateinit var progressBar: ProgressBar

    private val firestore = FirebaseFirestore.getInstance()
    private val studentNamesMap = mutableMapOf<String, String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_submissions)

        lv = findViewById(R.id.lvSubmissions)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSubmissionCount = findViewById(R.id.tvSubmissionCount)
        progressBar = findViewById(R.id.progressBar)

        // FIX: This is actually a CLASS ID, not assignment ID
        classId = intent.getStringExtra("assignmentId")
        className = intent.getStringExtra("assignmentTitle")

        Log.d("SUBMISSIONS_DEBUG", "Class ID: $classId, Class Name: $className")

        title = "Submissions: ${className ?: ""}"

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        lv.adapter = adapter

        lv.setOnItemClickListener { _, _, position, _ ->
            if (position < submissions.size) {
                val submission = submissions[position]
                showGradeDialog(submission)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAssignmentsAndSubmissions()
    }

    private fun loadAssignmentsAndSubmissions() {
        val id = classId ?: return

        progressBar.visibility = View.VISIBLE
        tvEmpty.text = "Loading assignments and submissions..."
        tvSubmissionCount.text = "Loading..."

        Log.d("SUBMISSIONS_DEBUG", "🔍 Step 1: Finding assignments for class: $id")

        // First, find all assignments for this class
        firestore.collection("assignments")
            .whereEqualTo("classId", id)
            .get()
            .addOnSuccessListener { assignmentsSnapshot ->
                Log.d("SUBMISSIONS_DEBUG", "✅ Found ${assignmentsSnapshot.documents.size} assignments for class $id")

                if (assignmentsSnapshot.documents.isEmpty()) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvEmpty.text = "No assignments found for this class."
                        tvSubmissionCount.text = "0 assignments"
                        Toast.makeText(this, "No assignments created for this class yet", Toast.LENGTH_LONG).show()
                    }
                    return@addOnSuccessListener
                }

                // Get all assignment IDs
                val assignmentIds = assignmentsSnapshot.documents.map { it.id }
                Log.d("SUBMISSIONS_DEBUG", "📋 Assignment IDs: $assignmentIds")

                // Now find submissions for ALL assignments in this class
                loadSubmissionsForAssignments(assignmentIds)
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Log.e("SUBMISSIONS_DEBUG", "❌ Failed to load assignments: ${e.message}")
                    tvEmpty.text = "Error loading assignments: ${e.message}"
                }
            }
    }

    private fun loadSubmissionsForAssignments(assignmentIds: List<String>) {
        if (assignmentIds.isEmpty()) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                tvEmpty.text = "No assignments found."
                return@runOnUiThread
            }
        }

        Log.d("SUBMISSIONS_DEBUG", "🔍 Step 2: Finding submissions for ${assignmentIds.size} assignments")

        // Query submissions for ANY of these assignment IDs
        firestore.collection("submissions")
            .whereIn("assignmentId", assignmentIds)
            .get()
            .addOnSuccessListener { submissionsSnapshot ->
                runOnUiThread {
                    progressBar.visibility = View.GONE

                    Log.d("SUBMISSIONS_DEBUG", "✅ Found ${submissionsSnapshot.documents.size} submissions total")

                    if (submissionsSnapshot.documents.isEmpty()) {
                        tvEmpty.text = "No submissions found for any assignments in this class."
                        tvSubmissionCount.text = "0 submissions"
                        Toast.makeText(this, "No submissions found for this class", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }

                    processSubmissions(submissionsSnapshot.documents)
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Log.e("SUBMISSIONS_DEBUG", "❌ Failed to load submissions: ${e.message}")
                    tvEmpty.text = "Error loading submissions: ${e.message}"
                }
            }
    }

    private fun processSubmissions(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        submissions.clear()
        val displayList = mutableListOf<String>()

        Log.d("SUBMISSIONS_DEBUG", "🔄 Processing ${documents.size} submission documents")

        for (doc in documents) {
            try {
                Log.d("SUBMISSIONS_DEBUG", "📝 Processing submission: ${doc.id}")
                Log.d("SUBMISSIONS_DEBUG", "   Data: ${doc.data}")

                val submission = doc.toObject(Submission::class.java)
                if (submission != null) {
                    submission.id = doc.id
                    submissions.add(submission)

                    Log.d("SUBMISSIONS_DEBUG", "   ✅ Added: Student=${submission.studentId}, Assignment=${submission.assignmentId}")

                    // Add placeholder
                    displayList.add("Student: Loading...\nAssignment: ${submission.assignmentId}\nStatus: ${getStatusText(submission)}")
                } else {
                    Log.e("SUBMISSIONS_DEBUG", "   ❌ Failed to convert to Submission object")
                    // Manual fallback
                    val assignmentId = doc.getString("assignmentId") ?: "Unknown"
                    val studentId = doc.getString("studentId") ?: "Unknown"
                    displayList.add("Student: $studentId\nAssignment: $assignmentId\n❌ Data conversion failed")
                }

            } catch (e: Exception) {
                Log.e("SUBMISSIONS_DEBUG", "   💥 Error: ${e.message}")
            }
        }

        updateUIAfterProcessing(displayList)
    }

    private fun updateUIAfterProcessing(displayList: MutableList<String>) {
        adapter.clear()
        adapter.addAll(displayList)
        adapter.notifyDataSetChanged()

        if (submissions.isEmpty()) {
            tvEmpty.text = "No submissions found for this class."
            tvSubmissionCount.text = "0 submissions"
            Log.d("SUBMISSIONS_DEBUG", "❌ FINAL: No submissions processed")
        } else {
            tvEmpty.text = ""
            tvSubmissionCount.text = "${submissions.size} submission(s)"
            Log.d("SUBMISSIONS_DEBUG", "✅ FINAL: Successfully loaded ${submissions.size} submissions")

            // Load student names and assignment details for better display
            loadStudentAndAssignmentDetails()
        }
    }

    private fun loadStudentAndAssignmentDetails() {
        val studentIds = submissions.map { it.studentId }.distinct()
        val assignmentIds = submissions.map { it.assignmentId }.distinct()

        Log.d("SUBMISSIONS_DEBUG", "👤 Loading details for ${studentIds.size} students and ${assignmentIds.size} assignments")

        // Load student names
        studentIds.forEach { studentId ->
            firestore.collection("users").document(studentId).get()
                .addOnSuccessListener { userDoc ->
                    if (userDoc.exists()) {
                        val studentName = userDoc.getString("name") ?: userDoc.getString("email") ?: "Unknown Student"
                        studentNamesMap[studentId] = studentName
                    } else {
                        studentNamesMap[studentId] = "Student: $studentId"
                    }
                    updateSubmissionDisplayWithDetails()
                }
        }

        // You could also load assignment titles here if needed
    }

    private fun updateSubmissionDisplayWithDetails() {
        val displayList = mutableListOf<String>()
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())

        for (submission in submissions) {
            val studentName = studentNamesMap[submission.studentId] ?: "Student: ${submission.studentId}"
            val submittedDate = if (submission.submittedAt > 0) {
                sdf.format(Date(submission.submittedAt))
            } else {
                "Not submitted"
            }

            val displayText = buildString {
                append("👤 $studentName\n")
                append("📝 Assignment: ${submission.assignmentId}\n")
                append("📅 Submitted: $submittedDate\n")
                append("${getStatusText(submission)}")

                if (!submission.fileUrl.isNullOrEmpty()) {
                    append("\n📎 File submitted")
                }
            }

            displayList.add(displayText)
        }

        adapter.clear()
        adapter.addAll(displayList)
        adapter.notifyDataSetChanged()

        Log.d("SUBMISSIONS_DEBUG", "🎉 Updated UI with ${displayList.size} submissions")
    }

    private fun getStatusText(submission: Submission): String {
        return when {
            submission.grade != null -> "📊 Graded: ${submission.grade}/100"
            submission.submittedAt > 0 -> "✅ Submitted - Awaiting Grade"
            else -> "❌ Not submitted"
        }
    }

    private fun showGradeDialog(sub: Submission) {
        val view = layoutInflater.inflate(R.layout.item_submission, null)
        val tvStudent = view.findViewById<TextView>(R.id.tvStudentId)
        val tvFile = view.findViewById<TextView>(R.id.tvSubmissionFile)
        val tvSubmittedDate = view.findViewById<TextView>(R.id.tvSubmittedDate)
        val etGrade = view.findViewById<EditText>(R.id.etGrade)
        val etFeedback = view.findViewById<EditText>(R.id.etFeedback)
        val btnOpenFile = view.findViewById<Button>(R.id.btnOpenFile)
        val btnReopenSubmission = view.findViewById<Button>(R.id.btnReopenSubmission)

        val studentName = studentNamesMap[sub.studentId] ?: "Student: ${sub.studentId}"
        tvStudent.text = studentName

        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        val submittedDate = if (sub.submittedAt > 0) {
            sdf.format(Date(sub.submittedAt))
        } else {
            "Not submitted"
        }
        tvSubmittedDate.text = "Submitted: $submittedDate"

        tvFile.text = if (!sub.fileUrl.isNullOrEmpty()) {
            "File: ${getFileNameFromUrl(sub.fileUrl)}"
        } else {
            "No file submitted"
        }

        etGrade.setText(sub.grade?.toString() ?: "")
        etFeedback.setText(sub.feedback ?: "")

        btnOpenFile.setOnClickListener {
            val url = sub.fileUrl
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
                    AssignmentRepository.reopenSubmissionForStudent(sub.id) { success, error ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, "Submission reopened successfully!", Toast.LENGTH_SHORT).show()
                                loadAssignmentsAndSubmissions()
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
            .setTitle("Grade Submission")
            .setView(view)
            .setPositiveButton("Save Grade") { _, _ ->
                val gradeStr = etGrade.text.toString().trim()
                val grade = gradeStr.toDoubleOrNull()
                val feedback = etFeedback.text.toString().trim()

                if (grade == null && gradeStr.isNotEmpty()) {
                    Toast.makeText(this, "Please enter a valid grade or leave empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                AssignmentRepository.gradeSubmission(sub.id, grade ?: 0.0, feedback) { ok, err ->
                    runOnUiThread {
                        if (ok) {
                            Toast.makeText(this, "Grade saved successfully!", Toast.LENGTH_SHORT).show()
                            loadAssignmentsAndSubmissions()
                        } else {
                            Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getFileNameFromUrl(fileUrl: String?): String {
        return try {
            fileUrl?.substringAfterLast('/')?.substringBefore('?') ?: "Submitted File"
        } catch (e: Exception) {
            "Submitted File"
        }
    }
}
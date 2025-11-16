package com.example.datadomeapp.teacher

import android.content.Intent
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
import com.example.datadomeapp.repository.AssignmentRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.*

class AssignmentListActivity : AppCompatActivity() {

    private lateinit var lvAssignments: ListView
    private lateinit var tvAssignmentHeader: TextView
    private lateinit var btnAddAssignment: Button

    private var assignmentId: String? = null
    private var className: String? = null
    private val assignmentList = mutableListOf<Assignment>()
    private lateinit var assignmentAdapter: AssignmentAdapter

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assignment_list)

        // ✅ SAFE: Initialize views with error handling
        try {
            lvAssignments = findViewById(R.id.lvAssignments)
            tvAssignmentHeader = findViewById(R.id.tvAssignmentHeader)
            btnAddAssignment = findViewById(R.id.btnAddAssignment)
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error initializing views: ${e.message}")
            Toast.makeText(this, "Error initializing screen", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ✅ SAFE: Get intent data
        try {
            assignmentId = intent.getStringExtra("assignmentId")
            className = intent.getStringExtra("CLASS_NAME")

            // 🆕 DEBUG: Log the received data
            Log.d("AssignmentList", "📥 Received from intent:")
            Log.d("AssignmentList", "   assignmentId: $assignmentId")
            Log.d("AssignmentList", "   CLASS_NAME: $className")

            if (assignmentId.isNullOrEmpty()) {
                Toast.makeText(this, "No assignment ID found!", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            tvAssignmentHeader.text = "Assignments for: ${className ?: assignmentId}"
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error getting intent data: ${e.message}")
            Toast.makeText(this, "Error loading class data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize adapter
        try {
            assignmentAdapter = AssignmentAdapter(assignmentList)
            lvAssignments.adapter = assignmentAdapter
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error setting up adapter: ${e.message}")
            Toast.makeText(this, "Error setting up assignment list", Toast.LENGTH_LONG).show()
        }

        loadAssignments()

        // ✅ SAFE: Button click listeners with error handling
        btnAddAssignment.setOnClickListener {
            try {
                val intent = Intent(this, CreateAssignmentActivity::class.java)
                intent.putExtra("assignmentId", assignmentId)
                intent.putExtra("CLASS_NAME", className)

                // 🆕 DEBUG: Log what we're passing to CreateAssignment
                Log.d("AssignmentList", "📤 Passing to CreateAssignment:")
                Log.d("AssignmentList", "   assignmentId: $assignmentId")
                Log.d("AssignmentList", "   CLASS_NAME: $className")

                startActivity(intent)
            } catch (e: Exception) {
                Log.e("AssignmentList", "Error opening CreateAssignment: ${e.message}")
                Toast.makeText(this, "Error opening create assignment", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadAssignments() {
        // ✅ SAFE: Validate assignmentId
        if (assignmentId.isNullOrEmpty()) {
            Toast.makeText(this, "No assignment ID available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("AssignmentList", "🔄 Loading assignments for class: $assignmentId")
        Toast.makeText(this, "Loading assignments...", Toast.LENGTH_SHORT).show()

        AssignmentRepository.getAssignmentsForClass(assignmentId!!) { success, snapshot, error ->
            if (!success || snapshot == null) {
                val errorMessage = error ?: "Unknown error occurred"
                Log.e("AssignmentList", "❌ Failed to load assignments: $errorMessage")
                Toast.makeText(this, "Failed to load assignments: $errorMessage", Toast.LENGTH_LONG).show()
                return@getAssignmentsForClass
            }

            try {
                displayAssignments(snapshot)
            } catch (e: Exception) {
                Log.e("AssignmentList", "Error displaying assignments: ${e.message}")
                Toast.makeText(this, "Error displaying assignments", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayAssignments(snapshot: QuerySnapshot) {
        try {
            assignmentList.clear()

            Log.d("AssignmentList", "📋 Processing ${snapshot.documents.size} assignment documents")

            for (doc in snapshot.documents) {
                try {
                    val assignment = doc.toObject(Assignment::class.java)
                    if (assignment != null) {
                        assignment.id = doc.id
                        assignmentList.add(assignment)

                        // 🆕 DEBUG: Log each assignment found
                        Log.d("AssignmentList", "✅ Found assignment:")
                        Log.d("AssignmentList", "   ID: ${assignment.id}")
                        Log.d("AssignmentList", "   Title: ${assignment.title}")
                        Log.d("AssignmentList", "   Class ID: ${assignment.classId}")
                        Log.d("AssignmentList", "   Due Date: ${assignment.dueDateMillis}")

                        // 🆕 Check if this matches the known submission from screenshot
                        if (assignment.id == "2609c2d0-2b87-debe-814f-5f21b387bcdc") {
                            Log.d("AssignmentList", "   🎯 MATCHES KNOWN SUBMISSION ASSIGNMENT ID!")
                        }
                    } else {
                        Log.w("AssignmentList", "❌ Failed to parse assignment: ${doc.id}")
                        Log.w("AssignmentList", "   Document data: ${doc.data}")
                    }
                } catch (e: Exception) {
                    Log.e("AssignmentList", "❌ Error parsing assignment ${doc.id}: ${e.message}")
                }
            }

            if (assignmentList.isEmpty()) {
                Log.w("AssignmentList", "⚠️ No assignments found for this class")
                Toast.makeText(this, "No assignments found for this class", Toast.LENGTH_SHORT).show()
                assignmentAdapter.notifyDataSetChanged()
                return
            }

            // Sort by due date (soonest first)
            try {
                assignmentList.sortBy { it.dueDateMillis }
                Log.d("AssignmentList", "📊 Sorted ${assignmentList.size} assignments by due date")
            } catch (e: Exception) {
                Log.e("AssignmentList", "Error sorting assignments: ${e.message}")
            }

            // Load submission counts and extension counts for ALL assignments
            loadAssignmentDetails()

        } catch (e: Exception) {
            Log.e("AssignmentList", "Error in displayAssignments: ${e.message}")
            Toast.makeText(this, "Error loading assignment list", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAssignmentDetails() {
        // Load submission counts and extensions for all assignments
        Log.d("AssignmentList", "🔄 Loading details for ${assignmentList.size} assignments")
        assignmentList.forEach { assignment ->
            loadSubmissionCount(assignment)
            loadExtensionCount(assignment)
        }
    }

    private fun loadSubmissionCount(assignment: Assignment) {
        AssignmentRepository.getSubmissionsForAssignment(assignment.id) { success, snapshot, error ->
            if (success && snapshot != null) {
                val submissionCount = snapshot.documents.size
                assignment.submissionCount = submissionCount

                // 🆕 DEBUG: Log submission count
                Log.d("AssignmentList", "📊 Assignment '${assignment.title}': $submissionCount submissions")

                runOnUiThread {
                    assignmentAdapter.notifyDataSetChanged()
                }
            } else {
                Log.e("AssignmentList", "❌ Error loading submissions for ${assignment.id}: $error")
            }
        }
    }

    private fun loadExtensionCount(assignment: Assignment) {
        AssignmentRepository.getStudentExtensions(assignment.id) { extensions ->
            val extensionCount = extensions?.size ?: 0
            assignment.studentExtensions = extensions ?: mutableMapOf()

            // 🆕 DEBUG: Log extension count
            Log.d("AssignmentList", "📊 Assignment '${assignment.title}': $extensionCount extensions")

            runOnUiThread {
                assignmentAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun editAssignment(assignment: Assignment) {
        try {
            val intent = Intent(this, EditAssignmentActivity::class.java)
            intent.putExtra("assignment", assignment)
            intent.putExtra("assignmentId", assignmentId)
            intent.putExtra("CLASS_NAME", className)

            // 🆕 DEBUG: Log what we're passing to EditAssignment
            Log.d("AssignmentList", "📤 Passing to EditAssignment:")
            Log.d("AssignmentList", "   Assignment ID: ${assignment.id}")
            Log.d("AssignmentList", "   Assignment Title: ${assignment.title}")
            Log.d("AssignmentList", "   Class ID: $assignmentId")

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error opening EditAssignment: ${e.message}")
            Toast.makeText(this, "Error opening edit assignment", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteAssignment(assignment: Assignment) {
        try {
            AlertDialog.Builder(this)
                .setTitle("Delete Assignment")
                .setMessage("Are you sure you want to delete '${assignment.title}'? This action cannot be undone.")
                .setPositiveButton("Delete") { dialog, which ->
                    // 🆕 DEBUG: Log deletion attempt
                    Log.d("AssignmentList", "🗑️ Attempting to delete assignment:")
                    Log.d("AssignmentList", "   ID: ${assignment.id}")
                    Log.d("AssignmentList", "   Title: ${assignment.title}")

                    AssignmentRepository.deleteAssignment(assignment.id) { success, error ->
                        if (success) {
                            Log.d("AssignmentList", "✅ Successfully deleted assignment: ${assignment.id}")
                            Toast.makeText(this, "Assignment deleted successfully", Toast.LENGTH_SHORT).show()
                            // Remove from list and update UI
                            assignmentList.remove(assignment)
                            assignmentAdapter.notifyDataSetChanged()
                        } else {
                            val errorMessage = error ?: "Unknown error"
                            Log.e("AssignmentList", "❌ Failed to delete assignment: $errorMessage")
                            Toast.makeText(this, "Failed to delete assignment: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error showing delete dialog: ${e.message}")
            Toast.makeText(this, "Error showing delete confirmation", Toast.LENGTH_LONG).show()
        }
    }

    private fun manageStudentSubmissions(assignment: Assignment) {
        try {
            val intent = Intent(this, StudentSubmissionsActivity::class.java)
            intent.putExtra("assignment", assignment)
            intent.putExtra("assignmentId", assignment.id) // 🆕 FIX: Use assignment.id, not classId
            intent.putExtra("className", className)

            // 🆕 ADD DEBUG LOGGING
            Log.d("AssignmentList", "📤 Passing assignment to StudentSubmissions:")
            Log.d("AssignmentList", "   Assignment ID: ${assignment.id}")
            Log.d("AssignmentList", "   Assignment Title: ${assignment.title}")
            Log.d("AssignmentList", "   Class ID: ${assignment.classId}")
            Log.d("AssignmentList", "   Class Name: $className")
            Log.d("AssignmentList", "   Expected ID from screenshot: 2609c2d0-2b87-debe-814f-5f21b387bcdc")

            // 🆕 Check if this matches the known submission
            if (assignment.id != "2609c2d0-2b87-debe-814f-5f21b387bcdc") {
                Log.e("AssignmentList", "❌ ASSIGNMENT ID MISMATCH! Current: ${assignment.id}, Expected: 2609c2d0-2b87-debe-814f-5f21b387bcdc")
            } else {
                Log.d("AssignmentList", "✅ ASSIGNMENT ID MATCHES KNOWN SUBMISSION!")
            }

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error opening StudentSubmissions: ${e.message}")
            Toast.makeText(this, "Error opening student submissions & extensions", Toast.LENGTH_LONG).show()
        }
    }

    private fun viewSubmissions(assignment: Assignment) {
        try {
            val intent = Intent(this@AssignmentListActivity, ViewSubmissionsActivity::class.java)

            // 🆕 DEBUG: Log what we're passing to ViewSubmissions
            Log.d("AssignmentList", "📤 Passing to ViewSubmissions:")
            Log.d("AssignmentList", "   ASSIGNMENT_ID: ${assignment.id}")
            Log.d("AssignmentList", "   ASSIGNMENT_TITLE: ${assignment.title}")
            Log.d("AssignmentList", "   assignmentId (classId): $assignmentId")
            Log.d("AssignmentList", "   CLASS_NAME: $className")

            // Ipasa ang ID ng specific assignment na ito (assignment.id)
            intent.putExtra("ASSIGNMENT_ID", assignment.id)
            // Ipasa ang Title ng specific assignment na ito
            intent.putExtra("ASSIGNMENT_TITLE", assignment.title)

            // Optional: Ipasa pa rin ang Class ID/Name
            intent.putExtra("assignmentId", assignmentId)
            intent.putExtra("CLASS_NAME", className)

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error in View Submissions button: ${e.message}")
            Toast.makeText(this@AssignmentListActivity, "Error opening submissions", Toast.LENGTH_LONG).show()
        }
    }

    private inner class AssignmentAdapter(private val assignments: List<Assignment>) : BaseAdapter() {
        override fun getCount(): Int = assignments.size
        override fun getItem(position: Int): Assignment = assignments[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return try {
                val view = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_assignment, parent, false)

                val assignment = getItem(position)

                // ✅ SAFE: Find views with null checks
                val tvTitle = view.findViewById<TextView>(R.id.tvAssignmentTitle)
                val tvDueDate = view.findViewById<TextView>(R.id.tvAssignmentDueDate)
                val tvSubmissionCount = view.findViewById<TextView>(R.id.tvSubmissionCount)
                val tvExtensionsCount = view.findViewById<TextView>(R.id.tvExtensionsCount)
                val btnEdit = view.findViewById<ImageButton>(R.id.btnEditAssignment)
                val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteAssignment)
                val btnManageStudentSubmissions = view.findViewById<Button>(R.id.btnManageExtensions)
                //val btnViewSubmissions = view.findViewById<Button>(R.id.btnViewSubmissions)

                // ✅ SAFE: Set assignment data
                tvTitle.text = assignment.title ?: "Untitled Assignment"
                tvDueDate.text = "Due: ${formatDueDate(assignment.dueDateMillis)}"

                // Set submission count (show loading if not loaded yet)
                val submissionCount = assignment.submissionCount ?: -1
                tvSubmissionCount.text = if (submissionCount >= 0) "Submissions: $submissionCount" else "Submissions: Loading..."

                // Show extensions count
                val extensionsCount = assignment.studentExtensions?.size ?: -1
                if (extensionsCount >= 0) {
                    tvExtensionsCount.text = "Extensions: $extensionsCount"
                    tvExtensionsCount.visibility = if (extensionsCount > 0) View.VISIBLE else View.GONE
                } else {
                    tvExtensionsCount.text = "Extensions: Loading..."
                    tvExtensionsCount.visibility = View.VISIBLE
                }

                // Set due date color (red if overdue)
                try {
                    if (assignment.dueDateMillis > 0 && assignment.dueDateMillis < System.currentTimeMillis()) {
                        tvDueDate.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    } else {
                        tvDueDate.setTextColor(resources.getColor(android.R.color.black, null))
                    }
                } catch (e: Exception) {
                    Log.e("AssignmentList", "Error setting due date color: ${e.message}")
                }

                // ✅ SAFE: Button click listeners
                btnEdit.setOnClickListener {
                    try {
                        Log.d("AssignmentList", "✏️ Edit clicked for: ${assignment.title} (${assignment.id})")
                        editAssignment(assignment)
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in edit button: ${e.message}")
                    }
                }

                btnDelete.setOnClickListener {
                    try {
                        Log.d("AssignmentList", "🗑️ Delete clicked for: ${assignment.title} (${assignment.id})")
                        deleteAssignment(assignment)
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in delete button: ${e.message}")
                    }
                }

                // UPDATED: Combined functionality - Manage Student Submissions & Extensions
                btnManageStudentSubmissions.setOnClickListener {
                    try {
                        Log.d("AssignmentList", "👥 Manage Student Submissions clicked for: ${assignment.title} (${assignment.id})")
                        manageStudentSubmissions(assignment)
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in student submissions button: ${e.message}")
                    }
                }

                // Keep the original View Submissions button for traditional view
                //btnViewSubmissions.setOnClickListener {
                    //try {
                       // Log.d("AssignmentList", "📋 View Submissions clicked for: ${assignment.title} (${assignment.id})")
                        //viewSubmissions(assignment)
                   // } catch (e: Exception) {
                        //Log.e("AssignmentList", "Error in view submissions button: ${e.message}")
                   // }
               // }

                // Optional: Click on entire item to view submissions
                view.setOnClickListener {
                    try {
                        Toast.makeText(this@AssignmentListActivity, "Selected: ${assignment.title}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in item click: ${e.message}")
                    }
                }

                view
            } catch (e: Exception) {
                Log.e("AssignmentList", "Error in getView: ${e.message}")
                // Return a simple view if there's an error
                TextView(parent.context).apply {
                    text = "Error loading assignment"
                    setPadding(16, 16, 16, 16)
                }
            }
        }
    }

    private fun formatDueDate(millis: Long): String {
        return try {
            if (millis > 0) {
                SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    .format(Date(millis))
            } else "No due date"
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error formatting date: ${e.message}")
            "Invalid date"
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Log.d("AssignmentList", "🔄 onResume: Refreshing assignments list")
            loadAssignments() // Refresh when returning from creating/editing an assignment
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error in onResume: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources if needed
        assignmentList.clear()
        Log.d("AssignmentList", "🧹 Activity destroyed")
    }
}

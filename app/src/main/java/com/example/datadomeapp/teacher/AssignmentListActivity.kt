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
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.*

class AssignmentListActivity : AppCompatActivity() {

    private lateinit var lvAssignments: ListView
    private lateinit var tvAssignmentHeader: TextView
    private lateinit var btnAddAssignment: Button

    private var assignmentId: String? = null // ✅ CHANGED: Use assignmentId
    private var className: String? = null
    private val assignmentList = mutableListOf<Assignment>()
    private lateinit var assignmentAdapter: AssignmentAdapter

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
                intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
                intent.putExtra("CLASS_NAME", className)
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

        Toast.makeText(this, "Loading assignments...", Toast.LENGTH_SHORT).show()

        AssignmentRepository.getAssignmentsForClass(assignmentId!!) { success, snapshot, error ->
            if (!success || snapshot == null) {
                val errorMessage = error ?: "Unknown error occurred"
                Log.e("AssignmentList", "Failed to load assignments: $errorMessage")
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

            for (doc in snapshot.documents) {
                try {
                    val assignment = doc.toObject(Assignment::class.java)
                    if (assignment != null) {
                        assignment.id = doc.id
                        assignmentList.add(assignment)
                    } else {
                        Log.w("AssignmentList", "Failed to parse assignment: ${doc.id}")
                    }
                } catch (e: Exception) {
                    Log.e("AssignmentList", "Error parsing assignment ${doc.id}: ${e.message}")
                }
            }

            if (assignmentList.isEmpty()) {
                Toast.makeText(this, "No assignments found for this class", Toast.LENGTH_SHORT).show()
                assignmentAdapter.notifyDataSetChanged()
                return
            }

            // Sort by due date (soonest first)
            try {
                assignmentList.sortBy { it.dueDateMillis }
            } catch (e: Exception) {
                Log.e("AssignmentList", "Error sorting assignments: ${e.message}")
            }

            assignmentAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Loaded ${assignmentList.size} assignments", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("AssignmentList", "Error in displayAssignments: ${e.message}")
            Toast.makeText(this, "Error loading assignment list", Toast.LENGTH_LONG).show()
        }
    }

    private fun editAssignment(assignment: Assignment) {
        try {
            val intent = Intent(this, EditAssignmentActivity::class.java)
            intent.putExtra("assignment", assignment)
            intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
            intent.putExtra("CLASS_NAME", className)
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
                    AssignmentRepository.deleteAssignment(assignment.id) { success, error ->
                        if (success) {
                            Toast.makeText(this, "Assignment deleted successfully", Toast.LENGTH_SHORT).show()
                            // Remove from list and update UI
                            assignmentList.remove(assignment)
                            assignmentAdapter.notifyDataSetChanged()
                        } else {
                            val errorMessage = error ?: "Unknown error"
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

    private fun manageExtensions(assignment: Assignment) {
        try {
            val intent = Intent(this, StudentExtensionsActivity::class.java)
            intent.putExtra("assignment", assignment)
            intent.putExtra("assignmentId", assignmentId) // ✅ FIXED: Use assignmentId
            intent.putExtra("className", className)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error opening StudentExtensions: ${e.message}")
            Toast.makeText(this, "Error opening student extensions", Toast.LENGTH_LONG).show()
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
                val btnManageExtensions = view.findViewById<Button>(R.id.btnManageExtensions)

                // ✅ SAFE: Set assignment data
                tvTitle.text = assignment.title ?: "Untitled Assignment"
                tvDueDate.text = "Due: ${formatDueDate(assignment.dueDateMillis)}"
                tvSubmissionCount.text = "Submissions: ${assignment.submissionCount ?: 0}"

                // Show extensions count
                val extensionsCount = assignment.studentExtensions?.size ?: 0
                tvExtensionsCount.text = "Extensions: $extensionsCount"
                tvExtensionsCount.visibility = if (extensionsCount > 0) View.VISIBLE else View.GONE

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
                        editAssignment(assignment)
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in edit button: ${e.message}")
                    }
                }

                btnDelete.setOnClickListener {
                    try {
                        deleteAssignment(assignment)
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in delete button: ${e.message}")
                    }
                }

                btnManageExtensions.setOnClickListener {
                    try {
                        manageExtensions(assignment)
                    } catch (e: Exception) {
                        Log.e("AssignmentList", "Error in extensions button: ${e.message}")
                    }
                }

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
            loadAssignments() // Refresh when returning from creating/editing an assignment
        } catch (e: Exception) {
            Log.e("AssignmentList", "Error in onResume: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources if needed
        assignmentList.clear()
    }
}
package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Assignment
import com.example.datadomeapp.repository.AssignmentRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class EditAssignmentActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etInstructions: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var btnSelectDueDate: Button
    private lateinit var btnUploadFile: Button
    private lateinit var btnUpdate: Button
    private lateinit var btnCancel: Button
    private lateinit var tvCurrentFile: TextView

    private var fileUri: Uri? = null
    private var dueDateMillis: Long = 0L
    private var classId: String = ""
    private lateinit var originalAssignment: Assignment
    private val PICK_FILE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_assignment)

        // Initialize views
        etTitle = findViewById(R.id.etTitle)
        etInstructions = findViewById(R.id.etInstructions)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        btnSelectDueDate = findViewById(R.id.btnSelectDueDate)
        btnUploadFile = findViewById(R.id.btnUploadFile)
        btnUpdate = findViewById(R.id.btnUpdate)
        btnCancel = findViewById(R.id.btnCancel)
        tvCurrentFile = findViewById(R.id.tvCurrentFile)

        // Get assignment data from intent
        originalAssignment = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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

        classId = intent.getStringExtra("classId") ?: originalAssignment.classId

        // Populate fields with existing data
        populateFields()

        // Set listeners
        btnSelectDueDate.setOnClickListener { showDateTimePicker() }
        btnUploadFile.setOnClickListener { openFileChooser() }
        btnUpdate.setOnClickListener { updateAssignment() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun populateFields() {
        etTitle.setText(originalAssignment.title)
        etInstructions.setText(originalAssignment.instructions)

        dueDateMillis = originalAssignment.dueDateMillis
        if (dueDateMillis > 0) {
            val formatted = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                .format(Date(dueDateMillis))
            tvSelectedDate.text = formatted
        }

        // Show current file if exists
        originalAssignment.fileUrl?.let { fileUrl ->
            tvCurrentFile.text = "Current file: ${getFileNameFromUrl(fileUrl)}"
        } ?: run {
            tvCurrentFile.text = "No file attached"
        }
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            url.substringAfterLast("/").substringBefore("?")
        } catch (e: Exception) {
            "File"
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        // Set to current due date if exists, otherwise current time
        if (dueDateMillis > 0) {
            calendar.timeInMillis = dueDateMillis
        }

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                val timePicker = TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        dueDateMillis = calendar.timeInMillis

                        val formatted = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                            .format(Date(dueDateMillis))
                        tvSelectedDate.text = formatted
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                )
                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            fileUri = data?.data
            val fileName = fileUri?.lastPathSegment ?: "New file selected"
            btnUploadFile.text = "File: $fileName"
            tvCurrentFile.text = "New file: $fileName"
        }
    }

    private fun updateAssignment() {
        val title = etTitle.text.toString().trim()
        val instructions = etInstructions.text.toString().trim()

        // Validations
        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (dueDateMillis == 0L) {
            Toast.makeText(this, "Please select a due date", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if anything has changed
        if (!hasChanges(title, instructions)) {
            showNoChangesDialog()
            return
        }

        // Create updated assignment
        val updatedAssignment = originalAssignment.copy(
            title = title,
            instructions = instructions,
            dueDateMillis = dueDateMillis
        )

        Log.d("EditAssignment", "Updating assignment: ${updatedAssignment.title}")

        if (fileUri != null) {
            uploadFileAndUpdateAssignment(updatedAssignment, fileUri!!)
        } else {
            updateAssignmentInFirestore(updatedAssignment)
        }
    }

    private fun hasChanges(title: String, instructions: String): Boolean {
        // Check if title changed
        if (title != originalAssignment.title) return true

        // Check if instructions changed
        if (instructions != originalAssignment.instructions) return true

        // Check if due date changed
        if (dueDateMillis != originalAssignment.dueDateMillis) return true

        // Check if new file was selected
        if (fileUri != null) return true

        return false
    }

    private fun showNoChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Changes")
            .setMessage("You haven't made any changes to the assignment.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun uploadFileAndUpdateAssignment(assignment: Assignment, fileUri: Uri) {
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("assignment_files/${assignment.classId}/${assignment.id}/${UUID.randomUUID()}")

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    assignment.fileUrl = uri.toString()
                    updateAssignmentInFirestore(assignment)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("EditAssignment", "File upload failed", e)
            }
    }

    private fun updateAssignmentInFirestore(assignment: Assignment) {
        AssignmentRepository.updateAssignment(assignment) { success, error ->
            if (success) {
                Toast.makeText(this, "✅ Assignment updated successfully!", Toast.LENGTH_SHORT).show()
                Log.d("EditAssignment", "Assignment updated: ${assignment.title}")
                finish()
            } else {
                Toast.makeText(this, "❌ Error updating assignment: $error", Toast.LENGTH_SHORT).show()
                Log.e("EditAssignment", "Firestore update failed: $error")
            }
        }
    }
}
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class CreateAssignmentActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etInstructions: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var btnSelectDueDate: Button
    private lateinit var btnUploadFile: Button
    private lateinit var btnCreate: Button

    private var fileUri: Uri? = null
    private var dueDateMillis: Long = 0L
    private var assignmentId: String = ""
    private var className: String? = null
    private val PICK_FILE_REQUEST = 1001

    private val db = FirebaseFirestore.getInstance()
    private var loadingDialog: AlertDialog? = null

    private var academicTerm: String = ""
    private var academicYear: String = ""
    private var semester: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_assignment)

        // 🔹 Initialize views from XML
        etTitle = findViewById(R.id.etTitle)
        etInstructions = findViewById(R.id.etInstructions)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        btnSelectDueDate = findViewById(R.id.btnSelectDueDate)
        btnUploadFile = findViewById(R.id.btnUploadFile)
        btnCreate = findViewById(R.id.btnCreate)

        // ✅ GET assignmentId FROM INTENT
        assignmentId = intent.getStringExtra("assignmentId") ?: ""
        className = intent.getStringExtra("CLASS_NAME")

        // ✅ DEBUG: Check what we received
        Log.d("CreateAssignment", "Received assignmentId: $assignmentId, className: $className")
        if (assignmentId.isEmpty()) {
            Toast.makeText(this, "Warning: No assignment ID received", Toast.LENGTH_LONG).show()
            Log.e("CreateAssignment", "No assignmentId found in intent extras")
        } else {
            Toast.makeText(this, "Creating for class: $className", Toast.LENGTH_SHORT).show()
        }

        loadSystemSettings()

        // 🔹 Set listeners
        btnSelectDueDate.setOnClickListener { showDateTimePicker() }
        btnUploadFile.setOnClickListener { openFileChooser() }

        btnCreate.setOnClickListener {
            createAssignment()
        }
    }

    private fun loadSystemSettings() {
        db.collection("systemSettings").document("currentTerm").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    academicTerm = doc.getString("academicTerm") ?: ""
                    academicYear = doc.getString("academicYear") ?: ""
                    semester = doc.getString("semester") ?: ""
                    Log.d("SystemSettings", "Loaded: term=$academicTerm, year=$academicYear, sem=$semester")
                } else {
                    Log.w("SystemSettings", "No currentTerm document found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("SystemSettings", "Failed to load system settings: ${e.message}")
            }
    }

    /** 🔄 Show professional loading dialog */
    private fun showLoadingDialog(message: String = "Creating Assignment...") {
        hideLoadingDialog() // Ensure any existing dialog is closed

        val builder = AlertDialog.Builder(this)
        builder.setView(R.layout.dialog_loading_professional)
        builder.setCancelable(false)

        loadingDialog = builder.create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()

        // Set custom message
        loadingDialog?.findViewById<TextView>(R.id.tvLoadingMessage)?.text = message

        // Show sub-message for file uploads or slow connections
        val subMessage = loadingDialog?.findViewById<TextView>(R.id.tvSubMessage)
        if (message.contains("upload", ignoreCase = true)) {
            subMessage?.text = "Uploading file, please wait..."
        } else if (message.contains("saving", ignoreCase = true)) {
            subMessage?.text = "Saving to database..."
        } else {
            subMessage?.text = "This may take a moment..."
        }
    }

    /** 🔄 Hide loading dialog */
    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun createAssignment() {
        val title = etTitle.text.toString().trim()
        val instructions = etInstructions.text.toString().trim()

        // ✅ VALIDATIONS
        if (title.isEmpty()) {
            etTitle.error = "Title is required"
            return
        } else {
            etTitle.error = null
        }

        if (dueDateMillis == 0L) {
            tvSelectedDate.text = "Please select a due date"
            tvSelectedDate.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            return
        }

        // ✅ NEW: Validate due date is in the future
        val currentTime = System.currentTimeMillis()
        if (dueDateMillis <= currentTime) {
            tvSelectedDate.text = "Due date must be in the future"
            tvSelectedDate.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            return
        }

        // ✅ CRITICAL: Check if we have a valid assignmentId
        if (assignmentId.isEmpty()) {
            Toast.makeText(this, "Error: No class selected. Please go back and try again.", Toast.LENGTH_LONG).show()
            Log.e("CreateAssignment", "assignmentId is empty - cannot create assignment")
            return
        }

        val teacherId = FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                Toast.makeText(this, "Error: Not logged in", Toast.LENGTH_SHORT).show()
                return
            }

        // Disable create button to prevent multiple clicks
        btnCreate.isEnabled = false
        btnCreate.text = "Creating..."

        val newAssignmentId = UUID.randomUUID().toString()

        // ✅ CREATE ASSIGNMENT WITH PROPER assignmentId
        val assignment = Assignment(
            id = newAssignmentId,
            teacherId = teacherId,
            title = title,
            instructions = instructions,
            classId = assignmentId,
            dueDateMillis = dueDateMillis,
            createdAt = System.currentTimeMillis()
        )

        assignment.academicTerm = academicTerm
        assignment.academicYear = academicYear
        assignment.semester = semester

        Log.d("CreateAssignment", "Creating assignment: ${assignment.title} for assignmentId: ${assignment.classId}")

        // Show loading dialog
        showLoadingDialog("Creating assignment...")

        if (fileUri != null) {
            uploadFileAndCreateAssignment(assignment, fileUri!!)
        } else {
            createAssignmentInFirestore(assignment)
        }
    }

    /** 📅 Show date + time picker with 12-hour digital format */
    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()

        // Set minimum date to current date to prevent past dates
        val minDateCalendar = Calendar.getInstance()
        minDateCalendar.add(Calendar.DAY_OF_MONTH, 0) // Today

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)

                // ✅ NEW: Digital-style time picker with 12-hour format and AM/PM
                val timePicker = TimePickerDialog(
                    this,
                    TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)

                        val selectedTime = calendar.timeInMillis
                        val currentTime = System.currentTimeMillis()

                        // ✅ Validate if selected datetime is in the future
                        if (selectedTime <= currentTime) {
                            // ✅ CHANGED: Show error in the date TextView instead of Toast
                            val formatted = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                .format(Date(selectedTime))
                            tvSelectedDate.text = "$formatted (Must be future date)"
                            tvSelectedDate.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                            dueDateMillis = 0L // Reset due date since it's invalid
                        } else {
                            dueDateMillis = selectedTime

                            // ✅ NEW: 12-hour format with AM/PM
                            val formatted = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                .format(Date(dueDateMillis))
                            tvSelectedDate.text = formatted
                            tvSelectedDate.setTextColor(resources.getColor(android.R.color.black))
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false // ✅ CHANGED: false for 12-hour format with AM/PM
                )

                // ✅ Set title for the time picker
                timePicker.setTitle("Select Due Time")

                // ✅ Ensure digital style (this is the default on most modern Android devices)
                try {
                    // This method may vary by device, but setting to 12-hour format usually gives digital style
                    timePicker.updateTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
                } catch (e: Exception) {
                    Log.d("TimePicker", "Time picker setup completed")
                }

                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // ✅ NEW: Prevent selection of past dates
        datePicker.datePicker.minDate = minDateCalendar.timeInMillis
        datePicker.setTitle("Select Due Date")
        datePicker.show()
    }

    /** 📂 Open file chooser */
    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_FILE_REQUEST)
    }

    /** 📎 Handle chosen file */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            fileUri = data?.data
            val fileName = fileUri?.lastPathSegment ?: "File selected"
            btnUploadFile.text = "File: $fileName"
        }
    }

    /** ☁️ Upload file to Firebase Storage, then create assignment */
    private fun uploadFileAndCreateAssignment(assignment: Assignment, fileUri: Uri) {
        // Update loading message for file upload
        showLoadingDialog("Uploading file...")

        val storageRef = FirebaseStorage.getInstance()
            .reference.child("assignment_files/${assignment.classId}/${assignment.id}/${UUID.randomUUID()}")

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                // Update loading message for getting download URL
                showLoadingDialog("Finalizing assignment...")

                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    assignment.fileUrl = uri.toString()
                    createAssignmentInFirestore(assignment)
                }
                    .addOnFailureListener { e ->
                        hideLoadingDialog()
                        btnCreate.isEnabled = true
                        btnCreate.text = "Create Assignment"
                        Toast.makeText(this, "Failed to get file URL: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("CreateAssignment", "Download URL failed", e)
                    }
            }
            .addOnFailureListener { e ->
                hideLoadingDialog()
                btnCreate.isEnabled = true
                btnCreate.text = "Create Assignment"
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("CreateAssignment", "File upload failed", e)
            }
    }

    /** 🔥 Create Firestore document */
    private fun createAssignmentInFirestore(assignment: Assignment) {
        showLoadingDialog("Saving to database...")

        AssignmentRepository.createAssignment(assignment) { success, error ->
            hideLoadingDialog()
            btnCreate.isEnabled = true
            btnCreate.text = "Create Assignment"

            if (success) {
                Toast.makeText(this, "✅ Assignment created successfully for this class!", Toast.LENGTH_SHORT).show()
                Log.d("CreateAssignment", "Assignment saved with assignmentId: ${assignment.classId}")
                finish()
            } else {
                Toast.makeText(this, "❌ Error creating assignment: $error", Toast.LENGTH_SHORT).show()
                Log.e("CreateAssignment", "Firestore save failed: $error")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideLoadingDialog() // Prevent memory leaks
    }
}
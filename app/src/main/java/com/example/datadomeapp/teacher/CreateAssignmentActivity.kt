package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Assignment
import com.example.datadomeapp.repository.AssignmentRepository
import com.google.firebase.auth.FirebaseAuth
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
    private var assignmentId: String = "" // ✅ CHANGED: Use assignmentId instead of classId
    private var className: String? = null
    private val PICK_FILE_REQUEST = 1001

    private val db = FirebaseFirestore.getInstance()

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

        // ✅ GET assignmentId FROM INTENT (Updated)
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


    private fun createAssignment() {
        val title = etTitle.text.toString().trim()
        val instructions = etInstructions.text.toString().trim()

        // ✅ VALIDATIONS
        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (dueDateMillis == 0L) {
            Toast.makeText(this, "Please select a due date", Toast.LENGTH_SHORT).show()
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

        val newAssignmentId = UUID.randomUUID().toString()


        // ✅ CREATE ASSIGNMENT WITH PROPER assignmentId
        val assignment = Assignment(
            id = newAssignmentId,
            teacherId = teacherId,
            title = title,
            instructions = instructions,
            classId = assignmentId, // ✅ This is now the assignmentId from the class we selected
            dueDateMillis = dueDateMillis,
            createdAt = System.currentTimeMillis()
        )

        assignment.academicTerm = academicTerm
        assignment.academicYear = academicYear
        assignment.semester = semester

        Log.d("CreateAssignment", "Creating assignment: ${assignment.title} for assignmentId: ${assignment.classId}")

        if (fileUri != null) {
            uploadFileAndCreateAssignment(assignment, fileUri!!)
        } else {
            createAssignmentInFirestore(assignment)
        }
    }

    /** 📅 Show date + time picker */
    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
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
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("assignment_files/${assignment.classId}/${assignment.id}/${UUID.randomUUID()}")

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    assignment.fileUrl = uri.toString()
                    createAssignmentInFirestore(assignment)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("CreateAssignment", "File upload failed", e)
            }
    }

    /** 🔥 Create Firestore document */
    private fun createAssignmentInFirestore(assignment: Assignment) {
        AssignmentRepository.createAssignment(assignment) { success, error ->
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
}
package com.example.datadomeapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class EnrollmentActivity : AppCompatActivity() {

    // --- Student Info Fields ---
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var etDOB: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerYearLevel: Spinner

    // --- Guardian Info Fields ---
    private lateinit var etGuardianName: EditText
    private lateinit var etGuardianPhone: EditText

    private lateinit var btnSubmitEnrollment: Button

    private var docId: String? = null
    private val firestore = FirebaseFirestore.getInstance()

    private val courseList = ArrayList<String>()
    private val genderList = listOf("Choose Gender", "Male", "Female", "Prefer Not To Say")
    private val yearLevelList = listOf("Choose Year Level", "Regular - 1st Year", "Regular - 2nd Year", "Irregular")

    private lateinit var courseAdapter: ArrayAdapter<String>
    private lateinit var genderAdapter: ArrayAdapter<String>
    private lateinit var yearLevelAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enrollment)

        // --- View Binding ---
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        etDOB = findViewById(R.id.etDOB)
        spinnerGender = findViewById(R.id.spinnerGender)
        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerYearLevel = findViewById(R.id.spinnerYearLevel)
        etGuardianName = findViewById(R.id.etGuardianName)
        etGuardianPhone = findViewById(R.id.etGuardianPhone)
        btnSubmitEnrollment = findViewById(R.id.btnSubmitEnrollment)

        setupSpinners()
        loadCourses()

        // --- Prefill email & docId if coming from VerifyLinkActivity ---
        val emailFromIntent = intent.getStringExtra("email")
        docId = intent.getStringExtra("docId")
        if (!emailFromIntent.isNullOrEmpty()) {
            etEmail.setText(emailFromIntent)
            etEmail.isEnabled = false
            // Check if verified
            checkIfVerified(emailFromIntent)
        }

        etDOB.setOnClickListener { showDatePickerDialog() }
        etDOB.keyListener = null

        btnSubmitEnrollment.setOnClickListener { submitEnrollment() }

        // Load existing data if docId exists
        docId?.let { loadPendingEnrollment(it) }
    }

    private fun setupSpinners() {
        genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderList)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGender.adapter = genderAdapter
        spinnerGender.setSelection(0)

        yearLevelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yearLevelList)
        yearLevelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerYearLevel.adapter = yearLevelAdapter
        spinnerYearLevel.setSelection(0)

        courseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseList)
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCourse.adapter = courseAdapter
    }

    private fun loadCourses() {
        courseList.add("Choose a Course")
        firestore.collection("courses").get()
            .addOnSuccessListener { snapshot ->
                val prompt = courseList.firstOrNull()
                courseList.clear()
                if (prompt != null) courseList.add(prompt)
                for (doc in snapshot.documents) {
                    doc.getString("name")?.let { courseList.add(it) }
                }
                courseAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load courses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPendingEnrollment(docId: String) {
        firestore.collection("pending_enrollments").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etFirstName.setText(doc.getString("firstName") ?: "")
                    etMiddleName.setText(doc.getString("middleName") ?: "")
                    etLastName.setText(doc.getString("lastName") ?: "")
                    etPhone.setText(doc.getString("phone") ?: "")
                    etAddress.setText(doc.getString("address") ?: "")
                    etDOB.setText(doc.getString("dateOfBirth") ?: "")
                    etGuardianName.setText(doc.getString("guardianName") ?: "")
                    etGuardianPhone.setText(doc.getString("guardianPhone") ?: "")

                    spinnerGender.setSelection(genderList.indexOf(doc.getString("gender") ?: "Choose Gender").coerceAtLeast(0))
                    spinnerYearLevel.setSelection(yearLevelList.indexOf(doc.getString("yearLevel") ?: "Choose Year Level").coerceAtLeast(0))
                    spinnerCourse.setSelection(courseList.indexOf(doc.getString("course") ?: "Choose a Course").coerceAtLeast(0))
                }
            }
    }

    private fun checkIfVerified(email: String) {
        firestore.collection("pendingEnrollments")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val isVerified = doc.getBoolean("isVerified") ?: false
                    if (!isVerified) {
                        Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show()
                        finish() // exit if not verified
                    }
                }
            }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val formatted = String.format(Locale.getDefault(), "%02d/%02d/%d", month + 1, day, year)
                etDOB.setText(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun submitEnrollment() {
        val firstName = etFirstName.text.toString().trim()
        val middleName = etMiddleName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val dob = etDOB.text.toString().trim()
        val guardianName = etGuardianName.text.toString().trim()
        val guardianPhone = etGuardianPhone.text.toString().trim()

        val gender = spinnerGender.selectedItem.toString()
        val course = spinnerCourse.selectedItem.toString()
        val yearLevel = spinnerYearLevel.selectedItem.toString()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || address.isEmpty() || dob.isEmpty() ||
            guardianName.isEmpty() || guardianPhone.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields.", Toast.LENGTH_LONG).show()
            return
        }
        if (gender == "Choose Gender" || course == "Choose a Course" || yearLevel == "Choose Year Level") {
            Toast.makeText(this, "Please select valid options for Gender, Course, and Year Level.", Toast.LENGTH_LONG).show()
            return
        }

        val enrollmentData = hashMapOf(
            "firstName" to firstName,
            "middleName" to middleName,
            "lastName" to lastName,
            "email" to email,
            "phone" to phone,
            "address" to address,
            "dateOfBirth" to dob,
            "gender" to gender,
            "course" to course,
            "yearLevel" to yearLevel,
            "guardianName" to guardianName,
            "guardianPhone" to guardianPhone,
            "status" to "submitted", // mark as submitted when done
            "timestamp" to Timestamp.now(),
            "isVerified" to true
        )

        if (!docId.isNullOrEmpty()) {
            firestore.collection("pendingEnrollments").document(docId!!)
                .set(enrollmentData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Enrollment updated successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update enrollment: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            firestore.collection("pendingEnrollments")
                .add(enrollmentData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Enrollment submitted successfully! We will contact you soon.", Toast.LENGTH_LONG).show()
                    clearFields()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to submit enrollment: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun clearFields() {
        etFirstName.text.clear()
        etMiddleName.text.clear()
        etLastName.text.clear()
        etEmail.text.clear()
        etPhone.text.clear()
        etAddress.text.clear()
        etDOB.text.clear()
        etGuardianName.text.clear()
        etGuardianPhone.text.clear()
        spinnerGender.setSelection(0)
        spinnerCourse.setSelection(0)
        spinnerYearLevel.setSelection(0)
    }
}

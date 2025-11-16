package com.example.datadomeapp.enrollment

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.example.datadomeapp.enrollment.AlreadySubmittedActivity
import com.example.datadomeapp.R
import java.util.*

class EnrollmentActivity : AppCompatActivity() {

    // --- Student Info Fields ---
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etDOB: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerApplicationStatus: Spinner
    private lateinit var etGuardianName: EditText
    private lateinit var etGuardianPhone: EditText

    // --- Address Fields ---
    private lateinit var etRegion: EditText
    private lateinit var etProvince: EditText
    private lateinit var etMunicipality: EditText
    private lateinit var etBarangay: EditText
    private lateinit var etStreetAddress: EditText

    // --- Parent Information Fields ---
    private lateinit var etFatherFirstName: EditText
    private lateinit var etFatherMiddleName: EditText
    private lateinit var etFatherLastName: EditText
    private lateinit var etFatherDOB: EditText
    private lateinit var etFatherPhone: EditText
    private lateinit var etFatherOccupation: EditText
    private lateinit var etMotherFirstName: EditText
    private lateinit var etMotherMiddleName: EditText
    private lateinit var etMotherLastName: EditText
    private lateinit var etMotherDOB: EditText
    private lateinit var etMotherPhone: EditText
    private lateinit var etMotherOccupation: EditText
    private lateinit var spinnerGuardianRelationship: Spinner

    private lateinit var btnSubmitEnrollment: Button
    private lateinit var progressBar: ProgressBar

    private var docId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var isSubmitting = false

    // Course data
    private val courseDisplayList = ArrayList<CourseDisplay>()
    private val courseNameList = ArrayList<String>()

    // Gender options
    private val genderList = listOf("Choose Gender", "Male", "Female")

    // Application Status options
    private val applicationStatusList = listOf(
        "Choose Application Status",
        "New High School Graduate (Freshman)",
        "Transfer Student (Galing Ibang School)",
        "Returnee / Shifter"
    )

    // Guardian Relationship options
    private val relationshipList = listOf(
        "Choose Relationship",
        "Father",
        "Mother",
        "Grandfather",
        "Grandmother",
        "Uncle",
        "Aunt",
        "Brother",
        "Sister",
        "Other Relative",
        "Legal Guardian"
    )

    private lateinit var courseAdapter: ArrayAdapter<String>
    private lateinit var genderAdapter: ArrayAdapter<String>
    private lateinit var applicationStatusAdapter: ArrayAdapter<String>
    private lateinit var relationshipAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_form)

        initializeViews()
        setupSpinners()
        setupDatePickers()
        loadCourses()

        // Prefill email & docId if coming from VerifyLinkActivity
        val emailFromIntent = intent.getStringExtra("email")
        docId = intent.getStringExtra("docId")
        if (!emailFromIntent.isNullOrEmpty()) {
            etEmail.setText(emailFromIntent)
            etEmail.isEnabled = false
            checkIfVerified(emailFromIntent)
        }

        etDOB.setOnClickListener { showDatePickerDialog(etDOB) }

        btnSubmitEnrollment.setOnClickListener {
            if (!isSubmitting) {
                submitEnrollment()
            }
        }

        docId?.let { loadPendingEnrollment(it) }
    }

    private fun initializeViews() {
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etDOB = findViewById(R.id.etDOB)
        spinnerGender = findViewById(R.id.spinnerGender)
        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerApplicationStatus = findViewById(R.id.spinnerYearLevel)
        etGuardianName = findViewById(R.id.etGuardianName)
        etGuardianPhone = findViewById(R.id.etGuardianPhone)

        // Address Views
        etRegion = findViewById(R.id.etRegion)
        etProvince = findViewById(R.id.etProvince)
        etMunicipality = findViewById(R.id.etMunicipality)
        etBarangay = findViewById(R.id.etBarangay)
        etStreetAddress = findViewById(R.id.etStreetAddress)

        // Parent Information Views
        etFatherFirstName = findViewById(R.id.etFatherFirstName)
        etFatherMiddleName = findViewById(R.id.etFatherMiddleName)
        etFatherLastName = findViewById(R.id.etFatherLastName)
        etFatherDOB = findViewById(R.id.etFatherDOB)
        etFatherPhone = findViewById(R.id.etFatherPhone)
        etFatherOccupation = findViewById(R.id.etFatherOccupation)

        etMotherFirstName = findViewById(R.id.etMotherFirstName)
        etMotherMiddleName = findViewById(R.id.etMotherMiddleName)
        etMotherLastName = findViewById(R.id.etMotherLastName)
        etMotherDOB = findViewById(R.id.etMotherDOB)
        etMotherPhone = findViewById(R.id.etMotherPhone)
        etMotherOccupation = findViewById(R.id.etMotherOccupation)

        spinnerGuardianRelationship = findViewById(R.id.spinnerGuardianRelationship)

        btnSubmitEnrollment = findViewById(R.id.btnSubmitEnrollment)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupSpinners() {
        genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderList)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGender.adapter = genderAdapter
        spinnerGender.setSelection(0)

        applicationStatusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, applicationStatusList)
        applicationStatusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerApplicationStatus.adapter = applicationStatusAdapter
        spinnerApplicationStatus.setSelection(0)

        relationshipAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, relationshipList)
        relationshipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGuardianRelationship.adapter = relationshipAdapter
        spinnerGuardianRelationship.setSelection(0)

        courseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseNameList)
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCourse.adapter = courseAdapter
    }

    private fun setupDatePickers() {
        etDOB.keyListener = null
        etFatherDOB.keyListener = null
        etMotherDOB.keyListener = null

        etFatherDOB.setOnClickListener { showDatePickerDialog(etFatherDOB) }
        etMotherDOB.setOnClickListener { showDatePickerDialog(etMotherDOB) }
    }

    private fun loadCourses() {
        courseNameList.add("Choose a Course")
        courseDisplayList.add(CourseDisplay("", "Choose a Course"))

        firestore.collection("courses").get()
            .addOnSuccessListener { snapshot ->
                courseNameList.clear()
                courseDisplayList.clear()
                courseNameList.add("Choose a Course")
                courseDisplayList.add(CourseDisplay("", "Choose a Course"))

                for (doc in snapshot.documents) {
                    val code = doc.getString("code")
                    val name = doc.getString("name")

                    if (code != null && name != null) {
                        courseNameList.add(name)
                        courseDisplayList.add(CourseDisplay(code, name))
                    }
                }

                courseAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Loading courses... please try again", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPendingEnrollment(docId: String) {
        firestore.collection("pendingEnrollments").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etFirstName.setText(doc.getString("firstName") ?: "")
                    etMiddleName.setText(doc.getString("middleName") ?: "")
                    etLastName.setText(doc.getString("lastName") ?: "")
                    etPhone.setText(doc.getString("phone") ?: "")

                    // Address information
                    etRegion.setText(doc.getString("region") ?: "")
                    etProvince.setText(doc.getString("province") ?: "")
                    etMunicipality.setText(doc.getString("municipality") ?: "")
                    etBarangay.setText(doc.getString("barangay") ?: "")
                    etStreetAddress.setText(doc.getString("streetAddress") ?: "")

                    etDOB.setText(doc.getString("dateOfBirth") ?: "")
                    etGuardianName.setText(doc.getString("guardianName") ?: "")
                    etGuardianPhone.setText(doc.getString("guardianPhone") ?: "")

                    // Father information
                    etFatherFirstName.setText(doc.getString("fatherFirstName") ?: "")
                    etFatherMiddleName.setText(doc.getString("fatherMiddleName") ?: "")
                    etFatherLastName.setText(doc.getString("fatherLastName") ?: "")
                    etFatherDOB.setText(doc.getString("fatherDOB") ?: "")
                    etFatherPhone.setText(doc.getString("fatherPhone") ?: "")
                    etFatherOccupation.setText(doc.getString("fatherOccupation") ?: "")

                    // Mother information
                    etMotherFirstName.setText(doc.getString("motherFirstName") ?: "")
                    etMotherMiddleName.setText(doc.getString("motherMiddleName") ?: "")
                    etMotherLastName.setText(doc.getString("motherLastName") ?: "")
                    etMotherDOB.setText(doc.getString("motherDOB") ?: "")
                    etMotherPhone.setText(doc.getString("motherPhone") ?: "")
                    etMotherOccupation.setText(doc.getString("motherOccupation") ?: "")

                    val guardianRelFromDb = doc.getString("guardianRelationship") ?: "Choose Relationship"
                    spinnerGuardianRelationship.setSelection(relationshipList.indexOf(guardianRelFromDb).coerceAtLeast(0))

                    val applicationTypeFromDb = doc.getString("applicationType") ?: "Choose Application Status"
                    val courseNameFromDb = doc.getString("courseName") ?: doc.getString("course") ?: "Choose a Course"

                    spinnerGender.setSelection(genderList.indexOf(doc.getString("gender") ?: "Choose Gender").coerceAtLeast(0))
                    spinnerApplicationStatus.setSelection(applicationStatusList.indexOf(applicationTypeFromDb).coerceAtLeast(0))
                    spinnerCourse.setSelection(courseNameList.indexOf(courseNameFromDb).coerceAtLeast(0))
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
                    val status = doc.getString("status")

                    if (status == "submitted" && docId == null) {
                        Toast.makeText(this, "Your enrollment is already submitted and pending review.", Toast.LENGTH_LONG).show()
                        navigateToAlreadySubmittedActivity()
                    } else {
                        loadPendingEnrollment(doc.id)
                        docId = doc.id
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Enrollment", "Error checking verification status.", it)
            }
    }

    private fun navigateToAlreadySubmittedActivity() {
        val intent = Intent(this, AlreadySubmittedActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val formatted = String.format(Locale.getDefault(), "%02d/%02d/%d", month + 1, day, year)
                editText.setText(formatted)
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
        val dob = etDOB.text.toString().trim()
        val guardianName = etGuardianName.text.toString().trim()
        val guardianPhone = etGuardianPhone.text.toString().trim()

        // Address information
        val region = etRegion.text.toString().trim()
        val province = etProvince.text.toString().trim()
        val municipality = etMunicipality.text.toString().trim()
        val barangay = etBarangay.text.toString().trim()
        val streetAddress = etStreetAddress.text.toString().trim()

        // Father information
        val fatherFirstName = etFatherFirstName.text.toString().trim()
        val fatherMiddleName = etFatherMiddleName.text.toString().trim()
        val fatherLastName = etFatherLastName.text.toString().trim()
        val fatherDOB = etFatherDOB.text.toString().trim()
        val fatherPhone = etFatherPhone.text.toString().trim()
        val fatherOccupation = etFatherOccupation.text.toString().trim()

        // Mother information
        val motherFirstName = etMotherFirstName.text.toString().trim()
        val motherMiddleName = etMotherMiddleName.text.toString().trim()
        val motherLastName = etMotherLastName.text.toString().trim()
        val motherDOB = etMotherDOB.text.toString().trim()
        val motherPhone = etMotherPhone.text.toString().trim()
        val motherOccupation = etMotherOccupation.text.toString().trim()

        val guardianRelationship = spinnerGuardianRelationship.selectedItem.toString()

        val gender = spinnerGender.selectedItem.toString()
        val selectedCourseName = spinnerCourse.selectedItem.toString()
        val selectedIndex = spinnerCourse.selectedItemPosition
        val applicationType = spinnerApplicationStatus.selectedItem.toString()

        // Validation
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || dob.isEmpty() ||
            guardianName.isEmpty() || guardianPhone.isEmpty() || fatherFirstName.isEmpty() || fatherLastName.isEmpty() ||
            motherFirstName.isEmpty() || motherLastName.isEmpty() || fatherPhone.isEmpty() || fatherOccupation.isEmpty() ||
            motherPhone.isEmpty() || motherOccupation.isEmpty() || region.isEmpty() ||
            province.isEmpty() || municipality.isEmpty() || barangay.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields.", Toast.LENGTH_LONG).show()
            return
        }

        // Parent validation
        if (guardianRelationship == "Choose Relationship") {
            Toast.makeText(this, "Please select guardian relationship to student.", Toast.LENGTH_LONG).show()
            return
        }

        if (gender == "Choose Gender" || selectedCourseName == "Choose a Course" || applicationType == "Choose Application Status") {
            Toast.makeText(this, "Please select valid options for Gender, Course, and Application Status.", Toast.LENGTH_LONG).show()
            return
        }

        val courseCode = if (selectedIndex > 0 && selectedIndex < courseDisplayList.size) {
            courseDisplayList[selectedIndex].code
        } else {
            ""
        }

        val enrollmentType = when (applicationType) {
            "New High School Graduate (Freshman)" -> "Freshman"
            "Transfer Student (Galing Ibang School)" -> "Transfer"
            "Returnee / Shifter" -> "Returnee"
            else -> "Freshman"
        }

        val yearLevelToSave = "Pending Evaluation"

        // Build full address
        val fullAddress = buildString {
            if (streetAddress.isNotEmpty()) append("$streetAddress, ")
            append("$barangay, $municipality, $province, $region")
        }

        // Create enrollment object
        val enrollment = com.example.datadomeapp.admin.Enrollment(
            id = docId ?: "",
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            email = email,
            phone = phone,
            address = fullAddress,
            dateOfBirth = dob,
            gender = gender,
            course = selectedCourseName,
            yearLevel = yearLevelToSave,
            guardianName = guardianName,
            guardianPhone = guardianPhone,
            guardianRelationship = guardianRelationship,
            // Father information
            fatherFirstName = fatherFirstName,
            fatherMiddleName = fatherMiddleName,
            fatherLastName = fatherLastName,
            fatherDOB = fatherDOB,
            fatherPhone = fatherPhone,
            fatherOccupation = fatherOccupation,
            // Mother information
            motherFirstName = motherFirstName,
            motherMiddleName = motherMiddleName,
            motherLastName = motherLastName,
            motherDOB = motherDOB,
            motherPhone = motherPhone,
            motherOccupation = motherOccupation,
            // Address information
            region = region,
            province = province,
            municipality = municipality,
            barangay = barangay,
            streetAddress = streetAddress,
            fullAddress = fullAddress,
            // Course information
            courseName = selectedCourseName,
            courseCode = courseCode,
            enrollmentType = enrollmentType,
            applicationType = applicationType,
            status = "submitted",
            timestamp = Timestamp.now(),
            isVerified = true
        )

        isSubmitting = true
        setSubmitButtonState("Submitting...", false)
        progressBar.visibility = ProgressBar.VISIBLE

        // Convert to Firestore map
        val enrollmentData = enrollment.toFirestoreMap()

        if (!docId.isNullOrEmpty()) {
            firestore.collection("pendingEnrollments").document(docId!!)
                .set(enrollmentData)
                .addOnSuccessListener {
                    completeSubmission(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Enrollment", "Error updating enrollment", e)
                    completeSubmission(false)
                }
        } else {
            firestore.collection("pendingEnrollments")
                .add(enrollmentData)
                .addOnSuccessListener { documentReference ->
                    docId = documentReference.id
                    completeSubmission(true)
                }
                .addOnFailureListener { e ->
                    Log.e("Enrollment", "Error adding enrollment", e)
                    completeSubmission(false)
                }
        }
    }

    private fun completeSubmission(success: Boolean) {
        isSubmitting = false
        progressBar.visibility = ProgressBar.GONE

        if (success) {
            setSubmitButtonState("Submitted!", false)
            Toast.makeText(this, "Enrollment submitted successfully! We will contact you soon.", Toast.LENGTH_LONG).show()

            handler.postDelayed({
                val intent = Intent(this, AlreadySubmittedActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }, 1000)
        } else {
            setSubmitButtonState("Submit Enrollment", true)
            Toast.makeText(this, "Submission failed. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setSubmitButtonState(text: String, enabled: Boolean) {
        btnSubmitEnrollment.text = text
        btnSubmitEnrollment.isEnabled = enabled
        isSubmitting = !enabled
    }

    private fun clearFields() {
        etFirstName.text.clear()
        etMiddleName.text.clear()
        etLastName.text.clear()
        if (etEmail.isEnabled) {
            etEmail.text.clear()
        }
        etPhone.text.clear()
        etDOB.text.clear()
        etGuardianName.text.clear()
        etGuardianPhone.text.clear()

        // Address fields
        etRegion.text.clear()
        etProvince.text.clear()
        etMunicipality.text.clear()
        etBarangay.text.clear()
        etStreetAddress.text.clear()

        // Father information
        etFatherFirstName.text.clear()
        etFatherMiddleName.text.clear()
        etFatherLastName.text.clear()
        etFatherDOB.text.clear()
        etFatherPhone.text.clear()
        etFatherOccupation.text.clear()

        // Mother information
        etMotherFirstName.text.clear()
        etMotherMiddleName.text.clear()
        etMotherLastName.text.clear()
        etMotherDOB.text.clear()
        etMotherPhone.text.clear()
        etMotherOccupation.text.clear()

        spinnerGender.setSelection(0)
        spinnerCourse.setSelection(0)
        spinnerApplicationStatus.setSelection(0)
        spinnerGuardianRelationship.setSelection(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

data class CourseDisplay(val code: String, val name: String)
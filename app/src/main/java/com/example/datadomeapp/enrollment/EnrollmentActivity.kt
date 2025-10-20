package com.example.datadomeapp.enrollment

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.example.datadomeapp.enrollment.AlreadySubmittedActivity
import com.example.datadomeapp.R
import java.util.*

// ✅ NEW: Class para hawakan ang Course Code at Name
data class CourseDisplay(val code: String, val name: String)

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
    private lateinit var spinnerApplicationStatus: Spinner

    // --- Guardian Info Fields ---
    private lateinit var etGuardianName: EditText
    private lateinit var etGuardianPhone: EditText

    private lateinit var btnSubmitEnrollment: Button

    private var docId: String? = null
    private val firestore = FirebaseFirestore.getInstance()

    // 🛑 NEW: Palitan ang courseList (ArrayList<String>) ng mga sumusunod:
    private val courseDisplayList = ArrayList<CourseDisplay>() // Dito isasave ang code at name
    private val courseNameList = ArrayList<String>() // Para lang sa Spinner display

    private val genderList = listOf("Choose Gender", "Male", "Female")

    // 🛑 Mga pagpipilian para sa Application Status
    private val applicationStatusList = listOf(
        "Choose Application Status",
        "New High School Graduate (Freshman)",
        "Transfer Student (Galing Ibang School)",
        "Returnee / Shifter"
    )

    private lateinit var courseAdapter: ArrayAdapter<String>
    private lateinit var genderAdapter: ArrayAdapter<String>
    private lateinit var applicationStatusAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_form)

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
        spinnerApplicationStatus = findViewById(R.id.spinnerYearLevel)
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

        applicationStatusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, applicationStatusList)
        applicationStatusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerApplicationStatus.adapter = applicationStatusAdapter
        spinnerApplicationStatus.setSelection(0)

        // ✅ Gumamit ng courseNameList para sa initial setup
        courseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseNameList)
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCourse.adapter = courseAdapter
    }

    private fun loadCourses() {
        // ✅ Maglagay ng prompt sa dalawang listahan
        courseNameList.add("Choose a Course")
        courseDisplayList.add(CourseDisplay("", "Choose a Course"))

        firestore.collection("courses").get()
            .addOnSuccessListener { snapshot ->
                // Clear ang parehong listahan bago mag-populate
                courseNameList.clear()
                courseDisplayList.clear()
                courseNameList.add("Choose a Course")
                courseDisplayList.add(CourseDisplay("", "Choose a Course"))

                for (doc in snapshot.documents) {
                    // ✅ KUNIN ANG PAREHONG 'code' AT 'name'
                    val code = doc.getString("code")
                    val name = doc.getString("name")

                    if (code != null && name != null) {
                        courseNameList.add(name)
                        courseDisplayList.add(CourseDisplay(code, name))
                    }
                }

                // ✅ I-notify ang adapter (na gumagamit ng courseNameList)
                courseAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load courses: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    etAddress.setText(doc.getString("address") ?: "")
                    etDOB.setText(doc.getString("dateOfBirth") ?: "")
                    etGuardianName.setText(doc.getString("guardianName") ?: "")
                    etGuardianPhone.setText(doc.getString("guardianPhone") ?: "")

                    val applicationTypeFromDb = doc.getString("applicationType") ?: "Choose Application Status"
                    // ✅ Kinuha ang Course Name (o Course Code kung ito ang dati mong sinave sa 'course' field)
                    val courseNameFromDb = doc.getString("courseName") ?: doc.getString("course") ?: "Choose a Course"

                    spinnerGender.setSelection(genderList.indexOf(doc.getString("gender") ?: "Choose Gender").coerceAtLeast(0))
                    spinnerApplicationStatus.setSelection(applicationStatusList.indexOf(applicationTypeFromDb).coerceAtLeast(0))

                    // ✅ Gamitin ang courseNameList para sa pag-set ng selection
                    spinnerCourse.setSelection(courseNameList.indexOf(courseNameFromDb).coerceAtLeast(0))
                }
            }
    }

    // ... (sa loob ng EnrollmentActivity) ...

    // Baguhin ang logic na ito. Hayaan itong mag-check lang, walang finish()
    private fun checkIfVerified(email: String) {
        firestore.collection("pendingEnrollments")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val status = doc.getString("status")

                    // 🛑 FIX: KUNG ANG STATUS AY 'submitted' at WALANG DOCID (New submission), i-redirect.
                    if (status == "submitted" && docId == null) {
                        Toast.makeText(this, "Your enrollment is already submitted and pending review.", Toast.LENGTH_LONG).show()

                        // 🛑 TAWAG SA CUSTOM REDIRECT FUNCTION
                        navigateToAlreadySubmittedActivity()
                    } else {
                        // Kung submitted na pero may docId, ibig sabihin ina-update niya ang form. (Payagan)
                        // Kung 'verified' pa lang ang status at walang docId, tuloy ang submission. (Payagan)
                        loadPendingEnrollment(doc.id)
                        docId = doc.id
                    }
                } else {
                    // Walang nakitang record para sa email na ito. Payagan ang fresh submission.
                    // Walang gagawin, mananatili sa form.
                }
            }
            .addOnFailureListener {
                Log.e("Enrollment", "Error checking verification status.", it)
            }
    }

    // ✅ NEW: Function para sa redirection
    private fun navigateToAlreadySubmittedActivity() {
        val intent = Intent(this, AlreadySubmittedActivity::class.java)
        // Opsyonal: Lagyan ng flags para hindi na bumalik sa form
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
        val selectedCourseName = spinnerCourse.selectedItem.toString() // Full Course Name
        val selectedIndex = spinnerCourse.selectedItemPosition

        val applicationType = spinnerApplicationStatus.selectedItem.toString()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || address.isEmpty() || dob.isEmpty() ||
            guardianName.isEmpty() || guardianPhone.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields.", Toast.LENGTH_LONG).show()
            return
        }

        // ✅ Validation check
        if (gender == "Choose Gender" || selectedCourseName == "Choose a Course" || applicationType == "Choose Application Status") {
            Toast.makeText(this, "Please select valid options for Gender, Course, and Application Status.", Toast.LENGTH_LONG).show()
            return
        }

        // 🛑 FIX: KUNIN ANG MALINIS NA COURSE CODE GAMIT ANG INDEX
        val courseCode = if (selectedIndex > 0 && selectedIndex < courseDisplayList.size) {
            courseDisplayList[selectedIndex].code
        } else {
            // Ito ay magiging "" kung ang napiling item ay "Choose a Course" o kung may error.
            ""
        }

        val enrollmentType = when (applicationType) {
            "New High School Graduate (Freshman)" -> "Freshman"
            "Transfer Student (Galing Ibang School)" -> "Transfer"
            "Returnee / Shifter" -> "Returnee"
            else -> "Freshman"
        }

        val yearLevelToSave = "Pending Evaluation"

        val enrollmentData = hashMapOf(
            "firstName" to firstName,
            "middleName" to middleName,
            "lastName" to lastName,
            "email" to email,
            "phone" to phone,
            "address" to address,
            "dateOfBirth" to dob,
            "gender" to gender,

            // ✅ ISAVE ANG PAREHONG PANGALAN AT CODE
            "courseName" to selectedCourseName,
            "courseCode" to courseCode,         // ITO ANG GAGAMITIN NG ADMIN!
            "course" to selectedCourseName,     // I-keep ang 'course' para sa backward compatibility

            "yearLevel" to yearLevelToSave,
            "enrollmentType" to enrollmentType,
            "applicationType" to applicationType,
            "guardianName" to guardianName,
            "guardianPhone" to guardianPhone,
            "status" to "submitted",
            "timestamp" to Timestamp.now(),
            "isVerified" to true
        )

        // 🛑 Use Map<String, Any> casting
        if (!docId.isNullOrEmpty()) {
            firestore.collection("pendingEnrollments").document(docId!!)
                .set(enrollmentData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Enrollment updated successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update enrollment: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            firestore.collection("pendingEnrollments")
                .add(enrollmentData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Enrollment submitted successfully! We will contact you soon.", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, AlreadySubmittedActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
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
        // ✅ Huwag i-clear ang email kung naka-disable
        if (etEmail.isEnabled) {
            etEmail.text.clear()
        }
        etPhone.text.clear()
        etAddress.text.clear()
        etDOB.text.clear()
        etGuardianName.text.clear()
        etGuardianPhone.text.clear()
        spinnerGender.setSelection(0)
        spinnerCourse.setSelection(0)
        spinnerApplicationStatus.setSelection(0)
    }
}
    package com.example.datadomeapp.enrollment
    
    import android.app.DatePickerDialog
    import android.content.Intent
    import android.os.Bundle
    import android.os.Handler
    import android.os.Looper
    import android.text.Editable
    import android.text.TextWatcher
    import android.util.Log
    import android.view.View
    import android.widget.*
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.widget.addTextChangedListener
    import com.example.datadomeapp.R
    import com.example.datadomeapp.admin.Enrollment
    import com.google.firebase.Timestamp
    import com.google.firebase.firestore.FirebaseFirestore
    import java.util.*
    import com.example.datadomeapp.data.LagunaAddressData
    import java.util.regex.Pattern
    import android.text.SpannableString
    import android.text.style.ForegroundColorSpan
    import android.graphics.Color
    import android.text.Spannable
    import com.example.datadomeapp.MainActivity
    
    class EnrollmentActivity : AppCompatActivity() {
    
        // Page containers
        private lateinit var pageStudentInfo: LinearLayout
        private lateinit var pageParentInfo: LinearLayout
        private lateinit var pageGuardianInfo: LinearLayout
        private lateinit var pageReview: LinearLayout
        private val provinces = LagunaAddressData.provinces
        private val lagunaMunicipalities = LagunaAddressData.lagunaMunicipalities
        private lateinit var btnCancelEnrollment: Button
    
        // Navigation buttons
        private lateinit var btnNextToParents: Button
        private lateinit var btnBackToStudent: Button
        private lateinit var btnNextToGuardian: Button
        private lateinit var btnBackToParents: Button
        private lateinit var btnNextToReview: Button
        private lateinit var btnBackToGuardian: Button
        private lateinit var btnSubmitEnrollment: Button

        // Progress indicator
        private lateinit var progressStep1: View
        private lateinit var progressStep2: View
        private lateinit var progressStep3: View
        private lateinit var progressStep4: View
        private lateinit var textStep1: TextView
        private lateinit var textStep2: TextView
        private lateinit var textStep3: TextView
        private lateinit var textStep4: TextView
    
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
    
        // --- Address Fields ---
        private lateinit var spinnerProvince: Spinner
        private lateinit var spinnerMunicipality: Spinner
        private lateinit var spinnerBarangay: Spinner
        private lateinit var spinnerStreet: Spinner
    
        private lateinit var etPostalCode: EditText
    
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
    
        // --- Guardian Information Fields ---
        private lateinit var etGuardianName: EditText
        private lateinit var etGuardianPhone: EditText
        private lateinit var spinnerGuardianRelationship: Spinner

        private lateinit var spinnerStudentLastNameExtension: Spinner
        private lateinit var spinnerFatherLastNameExtension: Spinner
        private lateinit var spinnerMotherLastNameExtension: Spinner
    
        // Error TextViews
        private lateinit var errorFirstName: TextView
        private lateinit var errorLastName: TextView
        private lateinit var errorEmail: TextView
        private lateinit var errorPhone: TextView
        private lateinit var errorDOB: TextView
        private lateinit var errorGender: TextView
        private lateinit var errorCourse: TextView
        private lateinit var errorApplicationStatus: TextView
        private lateinit var errorMunicipality: TextView
        private lateinit var errorBarangay: TextView
        private lateinit var errorStreet: TextView
        private lateinit var errorFatherFirstName: TextView
        private lateinit var errorFatherLastName: TextView
        private lateinit var errorFatherPhone: TextView
        private lateinit var errorFatherOccupation: TextView
        private lateinit var errorMotherFirstName: TextView
        private lateinit var errorMotherLastName: TextView
        private lateinit var errorMotherPhone: TextView
        private lateinit var errorMotherOccupation: TextView
        private lateinit var errorGuardianName: TextView
        private lateinit var errorGuardianPhone: TextView
        private lateinit var errorGuardianRelationship: TextView
        // Scroll views for each page
        private lateinit var scrollViewStudentInfo: ScrollView
        private lateinit var scrollViewParentInfo: ScrollView
        private lateinit var scrollViewGuardianInfo: ScrollView
        private lateinit var scrollViewReview: ScrollView
        private lateinit var errorFatherDOB: TextView
        private lateinit var errorMotherDOB: TextView
    
        private lateinit var progressBar: ProgressBar
    
        // Add these variable declarations with other view declarations
        private lateinit var reviewStudentName: TextView
        private lateinit var reviewStudentEmail: TextView
        private lateinit var reviewStudentPhone: TextView
        private lateinit var reviewStudentGender: TextView
        private lateinit var reviewStudentDOB: TextView
        private lateinit var reviewStudentCourse: TextView
        private lateinit var reviewStudentAddress: TextView
        private lateinit var reviewFatherInfo: TextView
        private lateinit var reviewMotherInfo: TextView
        private lateinit var reviewGuardianInfo: TextView

        private lateinit var errorStudentLastNameExtension: TextView
        private lateinit var errorFatherLastNameExtension: TextView
        private lateinit var errorMotherLastNameExtension: TextView
    
        private var docId: String? = null
        private val firestore = FirebaseFirestore.getInstance()
        private val handler = Handler(Looper.getMainLooper())
        private var isSubmitting = false
    
        // Course data
        private val courseDisplayList = ArrayList<CourseDisplay>()
        private val courseNameList = ArrayList<String>()
    
        // Gender options
        private val genderList = listOf("Choose Sex", "Male", "Female")
    
        // Application Status options
        private val applicationStatusList = listOf(
            "Choose Application Status",
            "New Senior High School Graduate / Freshmen",
            "Transfer Student"
        )

        // Name extension options
        private val nameExtensionList = listOf(
            "Choose Suffix",
            "Jr.",
            "Sr.",
            "II",
            "III",
            "IV"
        )
    
        // Guardian Relationship options
        private val relationshipList = listOf(
            "Choose Relationship",
            "Aunt",
            "Brother",
            "Cousin",
            "Father",
            "Foster Father",
            "Foster Mother",
            "Grandfather",
            "Grandmother",
            "Legal Guardian",
            "Mother",
            "Nephew",
            "Niece",
            "Other Relative",
            "Sister",
            "Stepbrother",
            "Stepmother",
            "Stepsister",
            "Stepfather",
            "Uncle"
        )
    
        private lateinit var courseAdapter: ArrayAdapter<String>
        private lateinit var genderAdapter: ArrayAdapter<String>
        private lateinit var applicationStatusAdapter: ArrayAdapter<String>
        private lateinit var relationshipAdapter: ArrayAdapter<String>
        private lateinit var provinceAdapter: ArrayAdapter<String>
        private lateinit var municipalityAdapter: ArrayAdapter<String>
        private lateinit var barangayAdapter: ArrayAdapter<String>
        private lateinit var streetAdapter: ArrayAdapter<String>
    
    
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.enrollment_form)
    
            initializeViews()
            setupSpinners()
            setupDatePickers()
            setupTextWatchers()
            setupNavigation()
            loadCourses()
    
            showPage(1)
    
            // Prefill email & docId if coming from VerifyLinkActivity
            val emailFromIntent = intent.getStringExtra("email")
            docId = intent.getStringExtra("docId")
            if (!emailFromIntent.isNullOrEmpty()) {
                etEmail.setText(emailFromIntent)
                etEmail.isEnabled = false
                checkIfVerified(emailFromIntent)
            }
    
            val tvFirstName = findViewById<TextView>(R.id.tvFirstName)
            val tvLastName = findViewById<TextView>(R.id.tvLastName)
            val tvEmail = findViewById<TextView>(R.id.tvEmail)
            val tvPhone = findViewById<TextView>(R.id.tvPhone)
            val tvDOB = findViewById<TextView>(R.id.tvDOB)
            val tvGender = findViewById<TextView>(R.id.tvGender)
            val tvCourse = findViewById<TextView>(R.id.tvCourse)
            val tvApplicationStatus = findViewById<TextView>(R.id.tvApplicationStatus)
            val tvProvince = findViewById<TextView>(R.id.tvProvince)
            val tvMunicipality = findViewById<TextView>(R.id.tvMunicipality)
            val tvBarangay = findViewById<TextView>(R.id.tvBarangay)
            val tvStreet = findViewById<TextView>(R.id.tvStreet)
            val tvFatherDOB = findViewById<TextView>(R.id.tvFatherDOB)
            val tvMotherDOB = findViewById<TextView>(R.id.tvMotherDOB)
    
            // Call helper function to add red asterisk
            setRequiredLabel(tvFirstName, "First Name")
            setRequiredLabel(tvLastName, "Last Name")
            setRequiredLabel(tvEmail, "Email")
            setRequiredLabel(tvPhone, "Phone Number")
            setRequiredLabel(tvDOB, "Date of Birth")
            setRequiredLabel(tvGender, "Sex")
            setRequiredLabel(tvCourse, "Course")
            setRequiredLabel(tvApplicationStatus, "Application Status")
            setRequiredLabel(tvProvince, "Province")
            setRequiredLabel(tvMunicipality, "Municipality/City")
            setRequiredLabel(tvBarangay, "Barangay")
            setRequiredLabel(tvStreet, "Street/Purok/Sitio")
            setRequiredLabel(tvFatherDOB, "Date of Birth")
            setRequiredLabel(tvMotherDOB, "Date of Birth")

            // Parent Information required labels
            val tvFatherFirstName = findViewById<TextView>(R.id.tvFatherFirstName)
            val tvFatherLastName = findViewById<TextView>(R.id.tvFatherLastName)
            val tvFatherPhone = findViewById<TextView>(R.id.tvFatherPhone)
            val tvFatherOccupation = findViewById<TextView>(R.id.tvFatherOccupation)
            val tvMotherFirstName = findViewById<TextView>(R.id.tvMotherFirstName)
            val tvMotherLastName = findViewById<TextView>(R.id.tvMotherLastName)
            val tvMotherPhone = findViewById<TextView>(R.id.tvMotherPhone)
            val tvMotherOccupation = findViewById<TextView>(R.id.tvMotherOccupation)

            // Guardian Information required labels
            val tvGuardianName = findViewById<TextView>(R.id.tvGuardianName)
            val tvGuardianPhone = findViewById<TextView>(R.id.tvGuardianPhone)
            val tvGuardianRelationship = findViewById<TextView>(R.id.tvGuardianRelationship)

            // Call helper function to add red asterisk for parent fields
            setRequiredLabel(tvFatherFirstName, "First Name")
            setRequiredLabel(tvFatherLastName, "Last Name")
            setRequiredLabel(tvFatherPhone, "Contact Number")
            setRequiredLabel(tvFatherOccupation, "Occupation")
            setRequiredLabel(tvMotherFirstName, "First Name")
            setRequiredLabel(tvMotherLastName, "Last Name")
            setRequiredLabel(tvMotherPhone, "Contact Number")
            setRequiredLabel(tvMotherOccupation, "Occupation")

            // Call helper function to add red asterisk for guardian fields
            setRequiredLabel(tvGuardianName, "Guardian Name")
            setRequiredLabel(tvGuardianPhone, "Contact Number")
            setRequiredLabel(tvGuardianRelationship, "Relationship to Student")
    
            etDOB.setOnClickListener { showDatePickerDialog(etDOB, true) }
    
            btnSubmitEnrollment.setOnClickListener {
                if (!isSubmitting) {
                    submitEnrollment()
                }
            }
    
            docId?.let { loadPendingEnrollment(it) }
        }
    
        private fun scrollToView(view: View) {
            handler.post {
                val scrollView = when {
                    pageStudentInfo.visibility == View.VISIBLE -> scrollViewStudentInfo
                    pageParentInfo.visibility == View.VISIBLE -> scrollViewParentInfo
                    pageGuardianInfo.visibility == View.VISIBLE -> scrollViewGuardianInfo
                    else -> null
                }
    
                scrollView?.post {
                    try {
                        val location = IntArray(2)
                        view.getLocationInWindow(location)
                        val scrollY = location[1] - 200 // Offset to show some space above
                        scrollView.smoothScrollTo(0, Math.max(0, scrollY))
                    } catch (e: Exception) {
                        Log.e("Enrollment", "Error scrolling to view", e)
                    }
                }
            }
        }
    
        private fun setRequiredLabel(textView: TextView, label: String) {
            // Append * at the end with red color
            val spannable = SpannableString("$label *")
            val start = spannable.length - 1
            val end = spannable.length
            spannable.setSpan(ForegroundColorSpan(Color.RED), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            textView.text = spannable
        }
    
        private fun navigateToAlreadySubmittedActivity() {
            val intent = Intent(this, AlreadySubmittedActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
    
        private fun loadPendingEnrollment(docId: String) {
            firestore.collection("pendingEnrollments").document(docId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        // Basic student information
                        etFirstName.setText(doc.getString("firstName") ?: "")
                        etMiddleName.setText(doc.getString("middleName") ?: "")
                        etLastName.setText(doc.getString("lastName") ?: "")
                        etEmail.setText(doc.getString("email") ?: "")
                        etPhone.setText(doc.getString("phone") ?: "")
                        etDOB.setText(doc.getString("dateOfBirth") ?: "")
    
                        // Address information - USING SPINNERS
                        val provinceFromDb = doc.getString("province") ?: ""
                        val municipalityFromDb = doc.getString("municipality") ?: ""
                        val barangayFromDb = doc.getString("barangay") ?: ""
                        val streetFromDb = doc.getString("street") ?: ""
                        val studentLastNameExt = doc.getString("studentLastNameExtension") ?: ""
                        val fatherLastNameExt = doc.getString("fatherLastNameExtension") ?: ""
                        val motherLastNameExt = doc.getString("motherLastNameExtension") ?: ""

                        setExtensionSpinnerSelection(spinnerStudentLastNameExtension, studentLastNameExt)
                        setExtensionSpinnerSelection(spinnerFatherLastNameExtension, fatherLastNameExt)
                        setExtensionSpinnerSelection(spinnerMotherLastNameExtension, motherLastNameExt)

                        // Set province spinner
                        if (provinceFromDb.isNotEmpty()) {
                            val provincePosition = provinces.indexOfFirst { it.name == provinceFromDb }
                            if (provincePosition >= 0) {
                                spinnerProvince.setSelection(provincePosition + 1) // +1 for "Choose Province"
    
                                // Delay to allow municipality spinner to update
                                handler.postDelayed({
                                    // Set municipality spinner
                                    val selectedProvince = provinces[provincePosition]
                                    val municipalityPosition = selectedProvince.municipalities.indexOfFirst { it.name == municipalityFromDb }
                                    if (municipalityPosition >= 0) {
                                        spinnerMunicipality.setSelection(municipalityPosition + 1)
    
                                        // Delay to allow barangay spinner to update
                                        handler.postDelayed({
                                            // Set barangay spinner
                                            val selectedMunicipality = selectedProvince.municipalities[municipalityPosition]
                                            val barangayPosition = selectedMunicipality.barangays.indexOfFirst { it.name == barangayFromDb }
                                            if (barangayPosition >= 0) {
                                                spinnerBarangay.setSelection(barangayPosition + 1)
    
                                                // Delay to allow street spinner to update
                                                handler.postDelayed({
                                                    // Set street spinner
                                                    if (streetFromDb.isNotEmpty()) {
                                                        // Find the barangay and check if street exists
                                                        val selectedBarangay = selectedMunicipality.barangays[barangayPosition]
                                                        val streetPosition = selectedBarangay.streets.indexOfFirst { it == streetFromDb }
                                                        if (streetPosition >= 0) {
                                                            spinnerStreet.setSelection(streetPosition + 1) // +1 for "Choose Street"
                                                        } else {
                                                            // If street not found, add it and select
                                                            streetAdapter.insert(streetFromDb, 1)
                                                            spinnerStreet.setSelection(1)
                                                        }
                                                    }
                                                }, 300)
                                            }
                                        }, 300)
                                    }
                                }, 300)
                            }
                        }
    
                        etPostalCode.setText(doc.getString("postalCode") ?: "")
    
                        // Guardian information
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
    
                        // Set dropdown spinners
                        val guardianRelFromDb = doc.getString("guardianRelationship") ?: "Choose Relationship"
                        val guardianRelPosition = relationshipList.indexOf(guardianRelFromDb)
                        spinnerGuardianRelationship.setSelection(if (guardianRelPosition >= 0) guardianRelPosition else 0)
    
                        val genderFromDb = doc.getString("gender") ?: "Choose Gender"
                        val genderPosition = genderList.indexOf(genderFromDb)
                        spinnerGender.setSelection(if (genderPosition >= 0) genderPosition else 0)
    
                        val applicationTypeFromDb = doc.getString("applicationType") ?: "Choose Application Status"
                        val applicationPosition = applicationStatusList.indexOf(applicationTypeFromDb)
                        spinnerApplicationStatus.setSelection(if (applicationPosition >= 0) applicationPosition else 0)
    
                        // Set course spinner - wait for courses to load
                        val courseNameFromDb = doc.getString("courseName") ?: doc.getString("course") ?: "Choose a Course"
                        handler.postDelayed({
                            val coursePosition = courseNameList.indexOf(courseNameFromDb)
                            spinnerCourse.setSelection(if (coursePosition >= 0) coursePosition else 0)
                        }, 1000)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Enrollment", "Error loading pending enrollment", e)
                    Toast.makeText(this, "Error loading enrollment data", Toast.LENGTH_SHORT).show()
                }
        }

        private fun setExtensionSpinnerSelection(spinner: Spinner, value: String) {
            if (value.isNotEmpty()) {
                val position = nameExtensionList.indexOf(value)
                if (position >= 0) {
                    spinner.setSelection(position)
                }
            }
        }


        private fun initializeViews() {
            // Page containers
            pageStudentInfo = findViewById(R.id.pageStudentInfo)
            pageParentInfo = findViewById(R.id.pageParentInfo)
            pageGuardianInfo = findViewById(R.id.pageGuardianInfo)
            pageReview = findViewById(R.id.pageReview)
            btnCancelEnrollment = findViewById(R.id.btnCancelEnrollment)
    
            scrollViewStudentInfo = findViewById(R.id.scrollViewStudentInfo)
            scrollViewParentInfo = findViewById(R.id.scrollViewParentInfo)
            scrollViewGuardianInfo = findViewById(R.id.scrollViewGuardianInfo)
            scrollViewReview = findViewById(R.id.scrollViewReview)
    
            // Navigation buttons
            btnNextToParents = findViewById(R.id.btnNextToParents)
            btnBackToStudent = findViewById(R.id.btnBackToStudent)
            btnNextToGuardian = findViewById(R.id.btnNextToGuardian)
            btnBackToParents = findViewById(R.id.btnBackToParents)
            btnNextToReview = findViewById(R.id.btnNextToReview)
            btnBackToGuardian = findViewById(R.id.btnBackToGuardian)
            btnSubmitEnrollment = findViewById(R.id.btnSubmitEnrollment)

            // Progress indicator
            progressStep1 = findViewById(R.id.progressStep1)
            progressStep2 = findViewById(R.id.progressStep2)
            progressStep3 = findViewById(R.id.progressStep3)
            progressStep4 = findViewById(R.id.progressStep4)
            textStep1 = findViewById(R.id.textStep1)
            textStep2 = findViewById(R.id.textStep2)
            textStep3 = findViewById(R.id.textStep3)
            textStep4 = findViewById(R.id.textStep4)
    
            // Student Info Views
            etFirstName = findViewById(R.id.etFirstName)
            etMiddleName = findViewById(R.id.etMiddleName)
            etLastName = findViewById(R.id.etLastName)
            etEmail = findViewById(R.id.etEmail)
            etPhone = findViewById(R.id.etPhone)
            etDOB = findViewById(R.id.etDOB)
            spinnerGender = findViewById(R.id.spinnerGender)
            spinnerCourse = findViewById(R.id.spinnerCourse)
            spinnerApplicationStatus = findViewById(R.id.spinnerYearLevel)
    
            // Address Views
            spinnerProvince = findViewById(R.id.spinnerProvince)
            spinnerMunicipality = findViewById(R.id.spinnerMunicipality)
            spinnerBarangay = findViewById(R.id.spinnerBarangay)
            spinnerStreet  = findViewById(R.id.spinnerStreet)
            etPostalCode = findViewById(R.id.etPostalCode)
    
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
    
            // Guardian Information Views
            etGuardianName = findViewById(R.id.etGuardianName)
            etGuardianPhone = findViewById(R.id.etGuardianPhone)
            spinnerGuardianRelationship = findViewById(R.id.spinnerGuardianRelationship)

            spinnerStudentLastNameExtension = findViewById(R.id.spinnerStudentLastNameExtension)
            spinnerFatherLastNameExtension = findViewById(R.id.spinnerFatherLastNameExtension)
            spinnerMotherLastNameExtension = findViewById(R.id.spinnerMotherLastNameExtension)

            errorStudentLastNameExtension = findViewById(R.id.errorStudentLastNameExtension)
            errorFatherLastNameExtension = findViewById(R.id.errorFatherLastNameExtension)
            errorMotherLastNameExtension = findViewById(R.id.errorMotherLastNameExtension)
    
            // Error TextViews
            errorFirstName = findViewById(R.id.errorFirstName)
            errorLastName = findViewById(R.id.errorLastName)
            errorEmail = findViewById(R.id.errorEmail)
            errorPhone = findViewById(R.id.errorPhone)
            errorDOB = findViewById(R.id.errorDOB)
            errorGender = findViewById(R.id.errorGender)
            errorCourse = findViewById(R.id.errorCourse)
            errorApplicationStatus = findViewById(R.id.errorApplicationStatus)
            errorMunicipality = findViewById(R.id.errorMunicipality)
            errorBarangay = findViewById(R.id.errorBarangay)
            errorStreet = findViewById(R.id.errorStreet)
            errorFatherFirstName = findViewById(R.id.errorFatherFirstName)
            errorFatherLastName = findViewById(R.id.errorFatherLastName)
            errorFatherPhone = findViewById(R.id.errorFatherPhone)
            errorFatherOccupation = findViewById(R.id.errorFatherOccupation)
            errorMotherFirstName = findViewById(R.id.errorMotherFirstName)
            errorMotherLastName = findViewById(R.id.errorMotherLastName)
            errorMotherPhone = findViewById(R.id.errorMotherPhone)
            errorMotherOccupation = findViewById(R.id.errorMotherOccupation)
            errorGuardianName = findViewById(R.id.errorGuardianName)
            errorGuardianPhone = findViewById(R.id.errorGuardianPhone)
            errorGuardianRelationship = findViewById(R.id.errorGuardianRelationship)
            errorFatherDOB = findViewById(R.id.errorFatherDOB)
            errorMotherDOB = findViewById(R.id.errorMotherDOB)
    
            progressBar = findViewById(R.id.progressBar)
    
            reviewStudentName = findViewById(R.id.reviewStudentName)
            reviewStudentEmail = findViewById(R.id.reviewStudentEmail)
            reviewStudentPhone = findViewById(R.id.reviewStudentPhone)
            reviewStudentGender = findViewById(R.id.reviewStudentGender)
            reviewStudentDOB = findViewById(R.id.reviewStudentDOB)
            reviewStudentCourse = findViewById(R.id.reviewStudentCourse)
            reviewStudentAddress = findViewById(R.id.reviewStudentAddress)
            reviewFatherInfo = findViewById(R.id.reviewFatherInfo)
            reviewMotherInfo = findViewById(R.id.reviewMotherInfo)
            reviewGuardianInfo = findViewById(R.id.reviewGuardianInfo)
        }
    
    
        private fun setupSpinners() {
            // Gender Spinner - make "Choose Gender" non-selectable
            genderAdapter =
                NonSelectableArrayAdapter(this, android.R.layout.simple_spinner_item, genderList)
            genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerGender.adapter = genderAdapter
            spinnerGender.setSelection(0, false)

            // Application Status Spinner
            applicationStatusAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                applicationStatusList
            )
            applicationStatusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerApplicationStatus.adapter = applicationStatusAdapter
            spinnerApplicationStatus.setSelection(0, false)

            spinnerGuardianRelationship.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (position > 0) {
                        errorGuardianRelationship.visibility = View.GONE
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // Relationship Spinner
            relationshipAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                relationshipList
            )
            relationshipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerGuardianRelationship.adapter = relationshipAdapter
            spinnerGuardianRelationship.setSelection(0, false)

            // Course Spinner
            courseAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                courseNameList
            )
            courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCourse.adapter = courseAdapter
            spinnerCourse.setSelection(0, false)

            // Province Spinner - Set Laguna as default and make it non-selectable
            val provinceNames = mutableListOf("Choose Province")
            provinceNames.addAll(provinces.map { it.name })
            provinceAdapter =
                NonSelectableArrayAdapter(this, android.R.layout.simple_spinner_item, provinceNames)
            provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerProvince.adapter = provinceAdapter

            // INITIALIZE MUNICIPALITY ADAPTER FIRST BEFORE USING IT
            val initialMunicipalityList = mutableListOf("Choose Municipality/City")
            municipalityAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                initialMunicipalityList
            )
            municipalityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMunicipality.adapter = municipalityAdapter
            spinnerMunicipality.setSelection(0, false)

            // INITIALIZE BARANGAY ADAPTER
            val initialBarangayList = mutableListOf("Choose Barangay")
            barangayAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                initialBarangayList
            )
            barangayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerBarangay.adapter = barangayAdapter
            spinnerBarangay.setSelection(0, false)

            // INITIALIZE STREET ADAPTER
            val initialStreetList = mutableListOf("Choose Street/Purok/Sitio")
            streetAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                initialStreetList
            )
            streetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerStreet.adapter = streetAdapter
            spinnerStreet.setSelection(0, false)

            setupValidatedSpinner(spinnerGender, errorGender)
            setupValidatedSpinner(spinnerCourse, errorCourse)
            setupValidatedSpinner(spinnerApplicationStatus, errorApplicationStatus)
            setupValidatedSpinner(spinnerGuardianRelationship, errorGuardianRelationship)


            // Set Laguna as default (position 1) and make it non-selectable
            if (provinces.isNotEmpty()) {
                spinnerProvince.setSelection(1, false) // Laguna is at position 1
                // Auto-populate municipalities for Laguna
                updateMunicipalities(provinces[0]) // Laguna is the first province
            }

            spinnerProvince.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position > 0) {
                        updateMunicipalities(provinces[position - 1])
                        // I-clear ang municipality error
                        errorMunicipality.visibility = View.GONE
                    } else {
                        clearMunicipalitiesAndBarangays()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            spinnerMunicipality.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (position > 0) {
                            val selectedProvince =
                                provinces[spinnerProvince.selectedItemPosition - 1]
                            updateBarangays(selectedProvince.municipalities[position - 1])
                            // Clear municipality error when selection is made
                            errorMunicipality.visibility = View.GONE
                        } else {
                            clearBarangays()
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }


            spinnerBarangay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position > 0) {
                        val selectedProvince = provinces[spinnerProvince.selectedItemPosition - 1]
                        val selectedMunicipality =
                            selectedProvince.municipalities[spinnerMunicipality.selectedItemPosition - 1]
                        val barangayName = selectedMunicipality.barangays[position - 1].name
                        updateStreets(barangayName)
                        // Clear barangay error
                        errorBarangay.visibility = View.GONE
                    } else {
                        clearStreets()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            spinnerStreet.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position > 0) {
                        // Clear street error
                        errorStreet.visibility = View.GONE
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // Name Extension Spinners
            val extensionAdapter = NonSelectableArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                nameExtensionList
            )
            extensionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spinnerStudentLastNameExtension.adapter = extensionAdapter
            spinnerFatherLastNameExtension.adapter = extensionAdapter
            spinnerMotherLastNameExtension.adapter = extensionAdapter

            spinnerStudentLastNameExtension.setSelection(0, false)
            spinnerFatherLastNameExtension.setSelection(0, false)
            spinnerMotherLastNameExtension.setSelection(0, false)

            setupExtensionSpinnerValidation(spinnerStudentLastNameExtension, errorStudentLastNameExtension)
            setupExtensionSpinnerValidation(spinnerFatherLastNameExtension, errorFatherLastNameExtension)
            setupExtensionSpinnerValidation(spinnerMotherLastNameExtension, errorMotherLastNameExtension)
        }



        private fun setupValidatedSpinner(spinner: Spinner, errorView: TextView) {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    // I-clear ang error kapag may na-select na (position > 0)
                    if (position > 0) {
                        errorView.visibility = View.GONE
                    }
                }
    
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    
        private fun setupSimpleSpinner(spinner: Spinner) {
            // Empty listener - walang gagawin kapag nag-select
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    // WALANG validation dito - selection lang
                }
    
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    
        private fun updateMunicipalities(province: LagunaAddressData.Province) {
            val municipalityNames = mutableListOf("Choose Municipality/City")
            municipalityNames.addAll(province.municipalities.map { it.name })
    
            // Instead of creating new adapter, update the existing one
            municipalityAdapter.clear()
            municipalityAdapter.addAll(municipalityNames)
            municipalityAdapter.notifyDataSetChanged()
            spinnerMunicipality.setSelection(0)
            clearBarangays()
        }
    
        private fun updateBarangays(municipality: LagunaAddressData.Municipality) {
            val barangayNames = mutableListOf("Choose Barangay")
            barangayNames.addAll(municipality.barangays.map { it.name })
    
            // Update existing adapter instead of creating new one
            barangayAdapter.clear()
            barangayAdapter.addAll(barangayNames)
            barangayAdapter.notifyDataSetChanged()
            spinnerBarangay.setSelection(0)
    
            // Set postal code
            etPostalCode.setText(municipality.postalCode)
        }
    
        private fun updateStreets(barangayName: String) {
            val streetNames = mutableListOf("Choose Street/Purok/Sitio")
    
            // Get the selected municipality and barangay
            val selectedProvince = provinces[spinnerProvince.selectedItemPosition - 1]
            val selectedMunicipality = selectedProvince.municipalities[spinnerMunicipality.selectedItemPosition - 1]
    
            // Find the barangay and get its streets
            val barangay = selectedMunicipality.barangays.find { it.name == barangayName }
            if (barangay != null && barangay.streets.isNotEmpty()) {
                streetNames.addAll(barangay.streets)
            } else {
                // Fallback to common streets if barangay not found or no specific streets
                val commonStreets = getCommonStreetsForBarangay(selectedMunicipality.name, barangayName)
                streetNames.addAll(commonStreets)
            }
    
            // Update existing adapter instead of creating new one
            streetAdapter.clear()
            streetAdapter.addAll(streetNames)
            streetAdapter.notifyDataSetChanged()
            spinnerStreet.setSelection(0)
        }
    
        private fun clearMunicipalitiesAndBarangays() {
            municipalityAdapter.clear()
            municipalityAdapter.add("Choose Municipality/City")
            municipalityAdapter.notifyDataSetChanged()
            clearBarangays()
        }
    
        private fun clearBarangays() {
            barangayAdapter.clear()
            barangayAdapter.add("Choose Barangay")
            barangayAdapter.notifyDataSetChanged()
            etPostalCode.setText("")
        }
    
        private fun clearStreets() {
            streetAdapter.clear()
            streetAdapter.add("Choose Street/Purok/Sitio")
            streetAdapter.notifyDataSetChanged()
        }
    
        private fun setupDatePickers() {
            etDOB.keyListener = null
            etFatherDOB.keyListener = null
            etMotherDOB.keyListener = null
    
            etDOB.setOnClickListener { showDatePickerDialog(etDOB, true) }
            etFatherDOB.setOnClickListener { showDatePickerDialog(etFatherDOB, false) }
            etMotherDOB.setOnClickListener { showDatePickerDialog(etMotherDOB, false) }

            etDOB.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.isNotEmpty() == true) {
                        errorDOB.visibility = View.GONE
                    }
                }
            })
            etFatherDOB.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.isNotEmpty() == true) {
                        errorFatherDOB.visibility = View.GONE
                    }
                }
            })

            etMotherDOB.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.isNotEmpty() == true) {
                        errorMotherDOB.visibility = View.GONE
                    }
                }
            })
        }
    
        private fun setupTextWatchers() {
            // Name fields - auto capitalize first letter of each word
            setupNameTextWatcher(etFirstName)
            setupNameTextWatcher(etMiddleName)
            setupNameTextWatcher(etLastName)
            setupNameTextWatcher(etFatherFirstName)
            setupNameTextWatcher(etFatherMiddleName)
            setupNameTextWatcher(etFatherLastName)
            setupNameTextWatcher(etMotherFirstName)
            setupNameTextWatcher(etMotherMiddleName)
            setupNameTextWatcher(etMotherLastName)
            setupNameTextWatcher(etGuardianName)
    
            // Phone fields - auto format and limit to 11 digits
            setupPhoneTextWatcher(etPhone)
            setupPhoneTextWatcher(etFatherPhone)
            setupPhoneTextWatcher(etMotherPhone)
            setupPhoneTextWatcher(etGuardianPhone)
    
            // Occupation fields - capitalize first letter
            setupOccupationTextWatcher(etFatherOccupation)
            setupOccupationTextWatcher(etMotherOccupation)

            setupErrorClearingTextWatcher(etFirstName, errorFirstName)
            setupErrorClearingTextWatcher(etLastName, errorLastName)
            setupErrorClearingTextWatcher(etPhone, errorPhone)

            setupErrorClearingTextWatcher(etFatherFirstName, errorFatherFirstName)
            setupErrorClearingTextWatcher(etFatherLastName, errorFatherLastName)
            setupErrorClearingTextWatcher(etFatherPhone, errorFatherPhone)
            setupErrorClearingTextWatcher(etFatherOccupation, errorFatherOccupation)

            setupErrorClearingTextWatcher(etMotherFirstName, errorMotherFirstName)
            setupErrorClearingTextWatcher(etMotherLastName, errorMotherLastName)
            setupErrorClearingTextWatcher(etMotherPhone, errorMotherPhone)
            setupErrorClearingTextWatcher(etMotherOccupation, errorMotherOccupation)
            setupErrorClearingTextWatcher(etGuardianName, errorGuardianName)
            setupErrorClearingTextWatcher(etGuardianPhone, errorGuardianPhone)

        }

        private fun setupErrorClearingTextWatcher(editText: EditText, errorView: TextView) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.isNotEmpty() == true) {
                        errorView.visibility = View.GONE
                    }
                }
            })
        }
    
        private fun getCommonStreetsForBarangay(municipality: String, barangay: String): List<String> {
            // You can expand this with more specific street data for each barangay
            val commonStreets = mutableListOf<String>()
    
            // Add general street options
            commonStreets.add("Main Road")
            commonStreets.add("Poblacion")
            commonStreets.add("Town Proper")
            commonStreets.add("Near Barangay Hall")
            commonStreets.add("Near Public Market")
            commonStreets.add("Near School")
            commonStreets.add("Near Church")
    
            // Add purok/sitio options
            commonStreets.add("Purok 1")
            commonStreets.add("Purok 2")
            commonStreets.add("Purok 3")
            commonStreets.add("Purok 4")
            commonStreets.add("Purok 5")
            commonStreets.add("Sitio Proper")
            commonStreets.add("Sitio Bato")
            commonStreets.add("Sitio Maligaya")
            commonStreets.add("Sitio Pag-asa")
            commonStreets.add("Sitio Rizal")
    
            // Add municipality-specific streets for major cities
            when (municipality) {
                "Santa Rosa City" -> {
                    commonStreets.add("Balibago Road")
                    commonStreets.add("Tagapo Road")
                    commonStreets.add("Sinalhan Road")
                    commonStreets.add("Market Area")
                    commonStreets.add("Industrial Area")
                }
                "Calamba City" -> {
                    commonStreets.add("National Highway")
                    commonStreets.add("Turbina Area")
                    commonStreets.add("Pansol Area")
                    commonStreets.add("Canlubang Area")
                    commonStreets.add("Halang Road")
                }
                "San Pedro City" -> {
                    commonStreets.add("Pacita Complex")
                    commonStreets.add("Landayan Area")
                    commonStreets.add("United Bayanihan")
                    commonStreets.add("Nueva Area")
                }
                "Biñan City" -> {
                    commonStreets.add("Platero Area")
                    commonStreets.add("Malaban Area")
                    commonStreets.add("De La Paz Area")
                    commonStreets.add("Zapote Road")
                }
                "Cabuyao City" -> {
                    commonStreets.add("Mamatid Area")
                    commonStreets.add("Banlic Area")
                    commonStreets.add("Marinig Area")
                    commonStreets.add("Niugan Area")
                }
            }
    
            return commonStreets.distinct().sorted()
        }

        private fun setupNameTextWatcher(editText: EditText) {
            editText.addTextChangedListener(object : TextWatcher {
                private var isFormatting = false
                private var previousText = ""

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    if (!isFormatting) {
                        previousText = s?.toString() ?: ""
                    }
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (isFormatting) return

                    val currentText = s.toString()

                    // Allow letters, spaces, hyphens, and common name characters (ñÑ)
                    // Changed from: [^a-zA-ZñÑ ] to [^a-zA-ZñÑ\\- ]
                    val cleanedText = currentText.replace(Regex("[^a-zA-ZñÑ\\- ]"), "")

                    // Prevent exceeding 50 characters
                    if (cleanedText.length > 50) {
                        isFormatting = true
                        editText.removeTextChangedListener(this)
                        val limitedText = cleanedText.substring(0, 50)
                        editText.setText(limitedText)
                        editText.setSelection(limitedText.length)
                        editText.setSelection(previousText.length)
                        editText.addTextChangedListener(this)
                        isFormatting = false
                        return
                    }

                    if (cleanedText != currentText) {
                        isFormatting = true
                        editText.removeTextChangedListener(this)
                        editText.setText(cleanedText)
                        editText.setSelection(cleanedText.length)
                        editText.addTextChangedListener(this)
                        isFormatting = false
                        return
                    }

                    if (currentText == previousText) return

                    isFormatting = true

                    try {
                        val cursorPosition = editText.selectionStart

                        // Remove double spaces only, keep single spaces and hyphens
                        val textWithoutDoubleSpaces = currentText.replace(Regex("\\s{2,}"), " ")

                        // Capitalize first letter of each word (treat hyphenated names as separate words)
                        val words = textWithoutDoubleSpaces.split(" ", "-").mapIndexed { index, word ->
                            if (word.isNotEmpty()) {
                                word.substring(0, 1).uppercase() + word.substring(1)
                            } else {
                                word
                            }
                        }

                        // Rejoin words with spaces and hyphens properly
                        val formattedText = if (textWithoutDoubleSpaces.contains("-")) {
                            // For hyphenated names, preserve the hyphens
                            val parts = textWithoutDoubleSpaces.split("-")
                            parts.joinToString("-") { part ->
                                part.split(" ").joinToString(" ") { word ->
                                    if (word.isNotEmpty()) {
                                        word.substring(0, 1).uppercase() + word.substring(1)
                                    } else {
                                        word
                                    }
                                }
                            }
                        } else {
                            // For regular names
                            words.joinToString(" ")
                        }

                        if (currentText != formattedText) {
                            editText.removeTextChangedListener(this)
                            editText.setText(formattedText)

                            val newCursorPosition = if (formattedText.length > currentText.length) {
                                cursorPosition + (formattedText.length - currentText.length)
                            } else if (formattedText.length < currentText.length) {
                                cursorPosition - (currentText.length - formattedText.length)
                            } else {
                                cursorPosition
                            }

                            editText.setSelection(newCursorPosition.coerceIn(0, formattedText.length))
                            editText.addTextChangedListener(this)
                        }

                        previousText = formattedText

                    } finally {
                        isFormatting = false
                    }
                }
            })
        }
    
        private fun setupPhoneTextWatcher(editText: EditText) {
            // Add prefix hint
            editText.hint = "09XX XXX XXXX"
    
            editText.addTextChangedListener(object : TextWatcher {
                private var isFormatting = false
    
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    
                override fun afterTextChanged(s: Editable?) {
                    if (isFormatting) return
    
                    val currentText = s.toString()
                    val digitsOnly = currentText.replace(Regex("[^\\d]"), "")
    
                    // Always ensure "09" prefix
                    val processedDigits = if (digitsOnly.startsWith("09")) {
                        digitsOnly
                    } else {
                        "09" + digitsOnly.take(9) // Max 9 digits after 09
                    }
    
                    // Limit to 11 digits
                    val limitedDigits = if (processedDigits.length > 11) {
                        processedDigits.substring(0, 11)
                    } else {
                        processedDigits
                    }
    
                    if (currentText == limitedDigits) return
    
                    isFormatting = true
    
                    try {
                        // Format with spaces
                        val formatted = when {
                            limitedDigits.isEmpty() -> ""
                            limitedDigits.length <= 2 -> limitedDigits
                            limitedDigits.length <= 5 -> "${limitedDigits.substring(0, 2)} ${limitedDigits.substring(2)}"
                            limitedDigits.length <= 8 -> "${limitedDigits.substring(0, 2)} ${limitedDigits.substring(2, 5)} ${limitedDigits.substring(5)}"
                            else -> "${limitedDigits.substring(0, 2)} ${limitedDigits.substring(2, 5)} ${limitedDigits.substring(5, 8)} ${limitedDigits.substring(8)}"
                        }
    
                        if (currentText != formatted) {
                            editText.removeTextChangedListener(this)
                            editText.setText(formatted)
                            editText.setSelection(formatted.length)
                            editText.addTextChangedListener(this)
                        }
    
                    } finally {
                        isFormatting = false
                    }
                }
            })
        }
    
        private fun setupOccupationTextWatcher(editText: EditText) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
    
                    // Prevent exceeding 50 characters + show error
                    if (text.length > 50) {
                        editText.removeTextChangedListener(this)
                        // Keep only first 50 characters
                        val limitedText = text.substring(0, 50)
                        editText.setText(limitedText)
                        editText.setSelection(limitedText.length)
                        editText.error = "Maximum 50 characters reached"
                        editText.addTextChangedListener(this)
                        return
                    } else if (text.length > 45) {
                        // Show warning when approaching limit
                        editText.error = "${50 - text.length} characters remaining"
                    } else {
                        editText.error = null
                    }
    
                    // Capitalize first letter
                    if (text.length > 2) {
                        val formattedText = text.substring(0, 1).uppercase() + text.substring(1)
                        if (text != formattedText) {
                            editText.removeTextChangedListener(this)
                            editText.setText(formattedText)
                            editText.setSelection(formattedText.length)
                            editText.addTextChangedListener(this)
                        }
                    }
                }
            })
        }
    
        private fun setupNavigation() {
            btnNextToParents.setOnClickListener {
                if (validateStudentInfo()) {
                    showPage(2)
                }
                // If validation fails, the scroll will happen in validateStudentInfo()
            }
    
            btnBackToStudent.setOnClickListener {
                // Clear errors when going back
                clearStudentErrors()
                showPage(1)
            }
    
            btnNextToGuardian.setOnClickListener {
                if (validateParentInfo()) {
                    showPage(3)
                }
            }
    
            btnBackToParents.setOnClickListener {
                // Clear errors when going back
                clearParentErrors()
                showPage(2)
            }
    
            btnNextToReview.setOnClickListener {
                if (validateGuardianInfo()) {
                    showPage(4)
                }
            }
    
            btnBackToGuardian.setOnClickListener {
                // Clear errors when going back
                clearGuardianErrors()
                showPage(3)
            }
    
            btnSubmitEnrollment.setOnClickListener {
                if (!isSubmitting) {
                    submitEnrollment()
                }
            }
    
            btnCancelEnrollment.setOnClickListener {
                showCancelConfirmationDialog()
            }
        }
    
        private fun showCancelConfirmationDialog() {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Cancel Enrollment")
            builder.setMessage("Are you sure you want to cancel this enrollment? All entered data will be lost.")
    
            builder.setPositiveButton("Yes, Cancel") { dialog, which ->
                cancelEnrollment()
            }
    
            builder.setNegativeButton("No, Continue") { dialog, which ->
                dialog.dismiss()
            }
    
    
            val dialog = builder.create()
            dialog.show()
    
            // Optional: Style the buttons
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(android.R.color.holo_red_dark))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(android.R.color.darker_gray))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(resources.getColor(R.color.colorPrimary))
        }
    
        private fun cancelEnrollment() {
            // If there's a pending enrollment document, delete it
            docId?.let { id ->
                firestore.collection("pendingEnrollments").document(id)
                    .delete()
                    .addOnSuccessListener {
                        Log.d("Enrollment", "Pending enrollment cancelled and deleted")
                    }
                    .addOnFailureListener { e ->
                        Log.e("Enrollment", "Error deleting pending enrollment", e)
                    }
            }
    
            // Show cancellation message
            Toast.makeText(this, "Enrollment cancelled", Toast.LENGTH_SHORT).show()
    
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
    
        private fun showPage(pageNumber: Int) {
            // Hide all pages
            pageStudentInfo.visibility = View.GONE
            pageParentInfo.visibility = View.GONE
            pageGuardianInfo.visibility = View.GONE
            pageReview.visibility = View.GONE
    
            // Hide all scroll views
            scrollViewStudentInfo.visibility = View.GONE
            scrollViewParentInfo.visibility = View.GONE
            scrollViewGuardianInfo.visibility = View.GONE
            scrollViewReview.visibility = View.GONE
    
            // Clear errors when changing pages
            clearAllErrors()
    
            // Reset progress
            resetProgress()
    
            // Show selected page and update progress
            when (pageNumber) {
                1 -> {
                    pageStudentInfo.visibility = View.VISIBLE
                    scrollViewStudentInfo.visibility = View.VISIBLE
                    updateProgress(1)
                }
                2 -> {
                    pageParentInfo.visibility = View.VISIBLE
                    scrollViewParentInfo.visibility = View.VISIBLE
                    updateProgress(2)
                }
                3 -> {
                    pageGuardianInfo.visibility = View.VISIBLE
                    scrollViewGuardianInfo.visibility = View.VISIBLE
                    updateProgress(3)
                }
                4 -> {
                    pageReview.visibility = View.VISIBLE
                    scrollViewReview.visibility = View.VISIBLE
                    updateProgress(4)
                    displayReviewSummary()
                }
            }
        }
    
    
        private fun resetProgress() {
            progressStep1.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
            progressStep2.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
            progressStep3.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
            progressStep4.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
    
            textStep1.setTextColor(resources.getColor(android.R.color.darker_gray))
            textStep2.setTextColor(resources.getColor(android.R.color.darker_gray))
            textStep3.setTextColor(resources.getColor(android.R.color.darker_gray))
            textStep4.setTextColor(resources.getColor(android.R.color.darker_gray))
        }
    
        private fun updateProgress(step: Int) {
            when (step) {
                1 -> {
                    progressStep1.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    textStep1.setTextColor(resources.getColor(R.color.colorPrimary))
                }
                2 -> {
                    progressStep1.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    progressStep2.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    textStep1.setTextColor(resources.getColor(R.color.colorPrimary))
                    textStep2.setTextColor(resources.getColor(R.color.colorPrimary))
                }
                3 -> {
                    progressStep1.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    progressStep2.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    progressStep3.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    textStep1.setTextColor(resources.getColor(R.color.colorPrimary))
                    textStep2.setTextColor(resources.getColor(R.color.colorPrimary))
                    textStep3.setTextColor(resources.getColor(R.color.colorPrimary))
                }
                4 -> {
                    progressStep1.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    progressStep2.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    progressStep3.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    progressStep4.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    textStep1.setTextColor(resources.getColor(R.color.colorPrimary))
                    textStep2.setTextColor(resources.getColor(R.color.colorPrimary))
                    textStep3.setTextColor(resources.getColor(R.color.colorPrimary))
                    textStep4.setTextColor(resources.getColor(R.color.colorPrimary))
                }
            }
        }

        private fun displayReviewSummary() {
            // Student Information with extension
            val studentLastNameExt = if (spinnerStudentLastNameExtension.selectedItemPosition > 0) " ${spinnerStudentLastNameExtension.selectedItem}" else ""
            val fullName = "${etFirstName.text} ${etMiddleName.text} ${etLastName.text}$studentLastNameExt".trim()
            reviewStudentName.text = "Name: $fullName"

            // Father Information with extension
            val fatherLastNameExt = if (spinnerFatherLastNameExtension.selectedItemPosition > 0) " ${spinnerFatherLastNameExtension.selectedItem}" else ""
            val fatherFullName = "${etFatherFirstName.text} ${etFatherMiddleName.text} ${etFatherLastName.text}$fatherLastNameExt".trim()

            // Mother Information with extension
            val motherLastNameExt = if (spinnerMotherLastNameExtension.selectedItemPosition > 0) " ${spinnerMotherLastNameExtension.selectedItem}" else ""
            val motherFullName = "${etMotherFirstName.text} ${etMotherMiddleName.text} ${etMotherLastName.text}$motherLastNameExt".trim()

            // Guardian Information (no extension)
            val guardianFullName = etGuardianName.text.toString().trim()

            // Rest of the display code...
            reviewStudentEmail.text = "Email: ${etEmail.text}"
            reviewStudentPhone.text = "Phone: ${etPhone.text}"
            reviewStudentGender.text = "Gender: ${spinnerGender.selectedItem}"
            reviewStudentDOB.text = "Date of Birth: ${etDOB.text}"
            reviewStudentCourse.text = "Course: ${spinnerCourse.selectedItem}"

            val address = "${spinnerStreet.selectedItem}, ${spinnerBarangay.selectedItem}, ${spinnerMunicipality.selectedItem}, ${spinnerProvince.selectedItem}"
            reviewStudentAddress.text = "Address: $address"

            val fatherInfo = "$fatherFullName\n${etFatherPhone.text} • ${etFatherOccupation.text}"
            reviewFatherInfo.text = "Father: $fatherInfo"

            val motherInfo = "$motherFullName\n${etMotherPhone.text} • ${etMotherOccupation.text}"
            reviewMotherInfo.text = "Mother: $motherInfo"

            val guardianInfo = "$guardianFullName\n${etGuardianPhone.text} • ${spinnerGuardianRelationship.selectedItem}"
            reviewGuardianInfo.text = "Guardian: $guardianInfo"
        }
    
        // Validation functions
        private fun validateStudentInfo(): Boolean {
    
            clearStudentErrors()
    
            var isValid = true
            var firstErrorView: View? = null
    
            // First Name validation
            if (etFirstName.text.toString().trim().isEmpty()) {
                errorFirstName.text = "First name is required"
                errorFirstName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFirstName
            } else if (etFirstName.text.toString().trim().length < 2) {
                errorFirstName.text = "First name must be at least 2 characters"
                errorFirstName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFirstName
            } else {
                errorFirstName.visibility = View.GONE
            }
    
            // Last Name validation
            if (etLastName.text.toString().trim().isEmpty()) {
                errorLastName.text = "Last name is required"
                errorLastName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etLastName
            } else if (etLastName.text.toString().trim().length < 2) {
                errorLastName.text = "Last name must be at least 2 characters"
                errorLastName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etLastName
            } else {
                errorLastName.visibility = View.GONE
            }
    
            // Email validation
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                errorEmail.text = "Email is required"
                errorEmail.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etEmail
            } else if (!isValidEmail(email)) {
                errorEmail.text = "Please enter a valid email address"
                errorEmail.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etEmail
            } else {
                errorEmail.visibility = View.GONE
            }
    
            // Phone validation
            val phone = etPhone.text.toString().replace(Regex("[^\\d]"), "")
            if (phone.isEmpty()) {
                errorPhone.text = "Phone number is required"
                errorPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etPhone
            } else if (phone.length != 11 || !phone.startsWith("09")) {
                errorPhone.text = "Phone must be 11 digits starting with 09"
                errorPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etPhone
            } else {
                errorPhone.visibility = View.GONE
            }
    
            // Date of Birth validation
            if (etDOB.text.toString().trim().isEmpty()) {
                errorDOB.text = "Date of birth is required"
                errorDOB.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etDOB
            } else if (!isValidAge(etDOB.text.toString().trim(), true)) {
                errorDOB.text = "Must be at least 10 years old"
                errorDOB.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etDOB
            } else {
                errorDOB.visibility = View.GONE
            }
    
            // Gender validation
            if (spinnerGender.selectedItemPosition == 0) {
                errorGender.text = "Please select gender"
                errorGender.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerGender
            } else {
                errorGender.visibility = View.GONE
            }
    
            // Course validation
            if (spinnerCourse.selectedItemPosition == 0) {
                errorCourse.text = "Please select course"
                errorCourse.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerCourse
            } else {
                errorCourse.visibility = View.GONE
            }
    
            // Application Status validation
            if (spinnerApplicationStatus.selectedItemPosition == 0) {
                errorApplicationStatus.text = "Please select application status"
                errorApplicationStatus.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerApplicationStatus
            } else {
                errorApplicationStatus.visibility = View.GONE
            }
    
            // Municipality validation
            if (spinnerMunicipality.selectedItemPosition == 0) {
                errorMunicipality.text = "Please select municipality"
                errorMunicipality.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerMunicipality
            } else {
                errorMunicipality.visibility = View.GONE
            }
    
            // Barangay validation
            if (spinnerBarangay.selectedItemPosition == 0) {
                errorBarangay.text = "Please select barangay"
                errorBarangay.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerBarangay
            } else {
                errorBarangay.visibility = View.GONE
            }
    
            // Street validation
            if (spinnerStreet.selectedItemPosition == 0) {
                errorStreet.text = "Please select street/purok/sitio"
                errorStreet.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerStreet
            } else {
                errorStreet.visibility = View.GONE
            }
    
            // ONLY scroll if we're not valid AND this was called from navigation
            if (!isValid && firstErrorView != null) {
                // Add a small delay to ensure the UI is updated
                handler.postDelayed({
                    scrollToView(firstErrorView)
                }, 100)
            }
    
            return isValid
        }
    
        private fun validateParentInfo(): Boolean {
    
            clearParentErrors()
    
            var isValid = true
            var firstErrorView: View? = null
    
            // Father First Name validation
            if (etFatherFirstName.text.toString().trim().isEmpty()) {
                errorFatherFirstName.text = "Father's first name is required"
                errorFatherFirstName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherFirstName
            } else if (etFatherFirstName.text.toString().trim().length < 2) {
                errorFatherFirstName.text = "Father's first name must be at least 2 characters"
                errorFatherFirstName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherFirstName
            } else {
                errorFatherFirstName.visibility = View.GONE
            }
    
            // Father Last Name validation
            if (etFatherLastName.text.toString().trim().isEmpty()) {
                errorFatherLastName.text = "Father's last name is required"
                errorFatherLastName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherLastName
            } else if (etFatherLastName.text.toString().trim().length < 2) {
                errorFatherLastName.text = "Father's last name must be at least 2 characters"
                errorFatherLastName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherLastName
            } else {
                errorFatherLastName.visibility = View.GONE
            }
    
            // Father Phone validation
            val fatherPhone = etFatherPhone.text.toString().replace(Regex("[^\\d]"), "")
            if (fatherPhone.isEmpty()) {
                errorFatherPhone.text = "Father's phone number is required"
                errorFatherPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherPhone
            } else if (fatherPhone.length != 11 || !fatherPhone.startsWith("09")) {
                errorFatherPhone.text = "Father's phone must be 11 digits starting with 09"
                errorFatherPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherPhone
            } else {
                errorFatherPhone.visibility = View.GONE
            }
    
            // Father Occupation validation
            if (etFatherOccupation.text.toString().trim().isEmpty()) {
                errorFatherOccupation.text = "Father's occupation is required"
                errorFatherOccupation.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherOccupation
            } else if (etFatherOccupation.text.toString().trim().length < 2) {
                errorFatherOccupation.text = "Father's occupation must be at least 2 characters"
                errorFatherOccupation.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherOccupation
            } else {
                errorFatherOccupation.visibility = View.GONE
            }
    
            // Mother First Name validation
            if (etMotherFirstName.text.toString().trim().isEmpty()) {
                errorMotherFirstName.text = "Mother's first name is required"
                errorMotherFirstName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherFirstName
            } else if (etMotherFirstName.text.toString().trim().length < 2) {
                errorMotherFirstName.text = "Mother's first name must be at least 2 characters"
                errorMotherFirstName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherFirstName
            } else {
                errorMotherFirstName.visibility = View.GONE
            }
    
            // Mother Last Name validation
            if (etMotherLastName.text.toString().trim().isEmpty()) {
                errorMotherLastName.text = "Mother's last name is required"
                errorMotherLastName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherLastName
            } else if (etMotherLastName.text.toString().trim().length < 2) {
                errorMotherLastName.text = "Mother's last name must be at least 2 characters"
                errorMotherLastName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherLastName
            } else {
                errorMotherLastName.visibility = View.GONE
            }
    
            // Mother Phone validation
            val motherPhone = etMotherPhone.text.toString().replace(Regex("[^\\d]"), "")
            if (motherPhone.isEmpty()) {
                errorMotherPhone.text = "Mother's phone number is required"
                errorMotherPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherPhone
            } else if (motherPhone.length != 11 || !motherPhone.startsWith("09")) {
                errorMotherPhone.text = "Mother's phone must be 11 digits starting with 09"
                errorMotherPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherPhone
            } else {
                errorMotherPhone.visibility = View.GONE
            }
    
            // Mother Occupation validation
            if (etMotherOccupation.text.toString().trim().isEmpty()) {
                errorMotherOccupation.text = "Mother's occupation is required"
                errorMotherOccupation.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherOccupation
            } else if (etMotherOccupation.text.toString().trim().length < 2) {
                errorMotherOccupation.text = "Mother's occupation must be at least 2 characters"
                errorMotherOccupation.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherOccupation
            } else {
                errorMotherOccupation.visibility = View.GONE
            }
    
            // Father Date of Birth validation (now required)
            val fatherDOB = etFatherDOB.text.toString().trim()
            if (fatherDOB.isEmpty()) {
                errorFatherDOB.text = "Father's date of birth is required"
                errorFatherDOB.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherDOB
            } else if (!isValidAge(fatherDOB, false)) {
                errorFatherDOB.text = "Father must be at least 18 years old"
                errorFatherDOB.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etFatherDOB
            } else {
                errorFatherDOB.visibility = View.GONE
            }
    
            // Mother Date of Birth validation (now required)
            val motherDOB = etMotherDOB.text.toString().trim()
            if (motherDOB.isEmpty()) {
                errorMotherDOB.text = "Mother's date of birth is required"
                errorMotherDOB.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherDOB
            } else if (!isValidAge(motherDOB, false)) {
                errorMotherDOB.text = "Mother must be at least 18 years old"
                errorMotherDOB.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etMotherDOB
            } else {
                errorMotherDOB.visibility = View.GONE
            }
    
            // Scroll to first error if any
            if (!isValid && firstErrorView != null) {
                handler.postDelayed({
                    scrollToView(firstErrorView)
                }, 100)
            }
    
            return isValid
        }
    
        private fun validateGuardianInfo(): Boolean {
    
            clearGuardianErrors()
    
            var isValid = true
            var firstErrorView: View? = null
    
            // Guardian Name validation
            if (etGuardianName.text.toString().trim().isEmpty()) {
                errorGuardianName.text = "Guardian name is required"
                errorGuardianName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etGuardianName
            } else if (etGuardianName.text.toString().trim().length < 2) {
                errorGuardianName.text = "Guardian name must be at least 2 characters"
                errorGuardianName.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etGuardianName
            } else {
                errorGuardianName.visibility = View.GONE
            }
    
            // Guardian Phone validation
            val guardianPhone = etGuardianPhone.text.toString().replace(Regex("[^\\d]"), "")
            if (guardianPhone.isEmpty()) {
                errorGuardianPhone.text = "Guardian phone number is required"
                errorGuardianPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etGuardianPhone
            } else if (guardianPhone.length != 11 || !guardianPhone.startsWith("09")) {
                errorGuardianPhone.text = "Guardian phone must be 11 digits starting with 09"
                errorGuardianPhone.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = etGuardianPhone
            } else {
                errorGuardianPhone.visibility = View.GONE
            }
    
            // Guardian Relationship validation
            if (spinnerGuardianRelationship.selectedItemPosition == 0) {
                errorGuardianRelationship.text = "Please select relationship"
                errorGuardianRelationship.visibility = View.VISIBLE
                isValid = false
                if (firstErrorView == null) firstErrorView = spinnerGuardianRelationship
            } else {
                errorGuardianRelationship.visibility = View.GONE
            }
    
            // Scroll to first error if any
            if (!isValid && firstErrorView != null) {
                handler.postDelayed({
                    scrollToView(firstErrorView)
                }, 100)
            }
    
            return isValid
        }
    
        private fun clearStudentErrors() {
            errorFirstName.visibility = View.GONE
            errorLastName.visibility = View.GONE
            errorEmail.visibility = View.GONE
            errorPhone.visibility = View.GONE
            errorDOB.visibility = View.GONE
            errorGender.visibility = View.GONE
            errorCourse.visibility = View.GONE
            errorApplicationStatus.visibility = View.GONE
            errorMunicipality.visibility = View.GONE
            errorBarangay.visibility = View.GONE
            errorStreet.visibility = View.GONE
            errorStudentLastNameExtension.visibility = View.GONE
        }
    
        private fun clearParentErrors() {
            errorFatherFirstName.visibility = View.GONE
            errorFatherLastName.visibility = View.GONE
            errorFatherPhone.visibility = View.GONE
            errorFatherOccupation.visibility = View.GONE
            errorMotherFirstName.visibility = View.GONE
            errorMotherLastName.visibility = View.GONE
            errorMotherPhone.visibility = View.GONE
            errorMotherOccupation.visibility = View.GONE
            errorFatherDOB.visibility = View.GONE
            errorMotherDOB.visibility = View.GONE
            errorFatherLastNameExtension.visibility = View.GONE
            errorMotherLastNameExtension.visibility = View.GONE
        }
    
        private fun clearGuardianErrors() {
            errorGuardianName.visibility = View.GONE
            errorGuardianPhone.visibility = View.GONE
            errorGuardianRelationship.visibility = View.GONE
        }
    
        private fun isValidEmail(email: String): Boolean {
            val pattern = Pattern.compile(
                "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
                Pattern.CASE_INSENSITIVE
            )
            return pattern.matcher(email).matches()
        }
    
        private fun isValidAge(dateString: String, isStudent: Boolean): Boolean {
            return try {
                val parts = dateString.split("/")
                if (parts.size != 3) return false
    
                val month = parts[0].toInt()
                val day = parts[1].toInt()
                val year = parts[2].toInt()
    
                // Validate date components
                if (month < 1 || month > 12) return false
                if (day < 1 || day > 31) return false
                if (year < 1900) return false
    
                val calendar = Calendar.getInstance()
                val currentYear = calendar.get(Calendar.YEAR)
                val currentMonth = calendar.get(Calendar.MONTH) + 1
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    
                var age = currentYear - year
                if (currentMonth < month || (currentMonth == month && currentDay < day)) {
                    age--
                }
    
                if (isStudent) {
                    age >= 10 // Student must be at least 10 years old
                } else {
                    age >= 18 // Parents/guardians must be at least 18 years old
                }
            } catch (e: Exception) {
                false
            }
        }
    
        private fun showDatePickerDialog(editText: EditText, isStudent: Boolean) {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val formatted = String.format(Locale.getDefault(), "%02d/%02d/%d", month + 1, day, year)
                    editText.setText(formatted)
                    editText.error = null
                },
                currentYear - 15, // Default year (middle of range)
                currentMonth,
                currentDay
            )
    
            if (isStudent) {
                // FOR STUDENT: 1990 to 10 years ago from today
                val minCalendar = Calendar.getInstance()
                minCalendar.set(1990, 0, 1) // January 1, 1990
    
                val maxCalendar = Calendar.getInstance()
                maxCalendar.add(Calendar.YEAR, -10) // 10 years ago from today
    
                datePickerDialog.datePicker.minDate = minCalendar.timeInMillis
                datePickerDialog.datePicker.maxDate = maxCalendar.timeInMillis
    
                // Set default year for student (around 15-20 years old)
                datePickerDialog.datePicker.updateDate(currentYear - 15, currentMonth, currentDay)
    
            } else {
                // FOR PARENT: 1950 to 18 years ago (parents must be at least 18)
                val minCalendar = Calendar.getInstance()
                minCalendar.set(1950, 0, 1) // January 1, 1950
    
                val maxCalendar = Calendar.getInstance()
                maxCalendar.add(Calendar.YEAR, -18) // 18 years ago from today
    
                datePickerDialog.datePicker.minDate = minCalendar.timeInMillis
                datePickerDialog.datePicker.maxDate = maxCalendar.timeInMillis
    
                // Set default year for parent (around 30-40 years old)
                datePickerDialog.datePicker.updateDate(currentYear - 35, currentMonth, currentDay)
            }
    
            datePickerDialog.show()
        }

        private fun setupExtensionSpinnerValidation(spinner: Spinner, errorView: TextView) {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    // Clear error when a valid selection is made (position > 0)
                    if (position > 0) {
                        errorView.visibility = View.GONE
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    
        private fun submitEnrollment() {
            if (!validateStudentInfo() || !validateParentInfo() || !validateGuardianInfo()) {
                Toast.makeText(this, "Please fix all validation errors before submitting.", Toast.LENGTH_LONG).show()
                showPage(1) // Go back to first page to show errors
                return
            }
            val studentValid = validateStudentInfo()
            val parentValid = validateParentInfo()
            val guardianValid = validateGuardianInfo()

            val studentLastNameExtension = if (spinnerStudentLastNameExtension.selectedItemPosition > 0) spinnerStudentLastNameExtension.selectedItem.toString() else ""
            val fatherLastNameExtension = if (spinnerFatherLastNameExtension.selectedItemPosition > 0) spinnerFatherLastNameExtension.selectedItem.toString() else ""
            val motherLastNameExtension = if (spinnerMotherLastNameExtension.selectedItemPosition > 0) spinnerMotherLastNameExtension.selectedItem.toString() else ""
    
            if (!studentValid || !parentValid || !guardianValid) {
                Toast.makeText(this, "Please fix all validation errors before submitting.", Toast.LENGTH_LONG).show()
    
                when {
                    !studentValid -> showPage(1)
                    !parentValid -> showPage(2)
                    !guardianValid -> showPage(3)
                }
                return
            }
    
            val firstName = etFirstName.text.toString().trim()
            val middleName = etMiddleName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().replace(Regex("[^\\d]"), "")
            val dob = etDOB.text.toString().trim()
            val guardianName = etGuardianName.text.toString().trim()
            val guardianPhone = etGuardianPhone.text.toString().replace(Regex("[^\\d]"), "")
    
            // Address information
            val province = spinnerProvince.selectedItem.toString()
            val municipality = spinnerMunicipality.selectedItem.toString()
            val barangay = spinnerBarangay.selectedItem.toString()
            val street = spinnerStreet.selectedItem.toString()
            val postalCode = etPostalCode.text.toString().trim()
    
            // Father information
            val fatherFirstName = etFatherFirstName.text.toString().trim()
            val fatherMiddleName = etFatherMiddleName.text.toString().trim()
            val fatherLastName = etFatherLastName.text.toString().trim()
            val fatherDOB = etFatherDOB.text.toString().trim()
            val fatherPhone = etFatherPhone.text.toString().replace(Regex("[^\\d]"), "")
            val fatherOccupation = etFatherOccupation.text.toString().trim()
    
            // Mother information
            val motherFirstName = etMotherFirstName.text.toString().trim()
            val motherMiddleName = etMotherMiddleName.text.toString().trim()
            val motherLastName = etMotherLastName.text.toString().trim()
            val motherDOB = etMotherDOB.text.toString().trim()
            val motherPhone = etMotherPhone.text.toString().replace(Regex("[^\\d]"), "")
            val motherOccupation = etMotherOccupation.text.toString().trim()
    
            val guardianRelationship = spinnerGuardianRelationship.selectedItem.toString()
            val gender = spinnerGender.selectedItem.toString()
            val selectedCourseName = spinnerCourse.selectedItem.toString()
            val selectedIndex = spinnerCourse.selectedItemPosition
            val applicationType = spinnerApplicationStatus.selectedItem.toString()
    
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
                if (street.isNotEmpty() && street != "Choose Street/Purok/Sitio") {
                    append("$street, ")
                }
                append("$barangay, $municipality, $province")
                if (postalCode.isNotEmpty()) append(", $postalCode")
            }
    
            // Create enrollment object
            val enrollment = Enrollment(
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
                region = province, // Using province as region
                province = province,
                municipality = municipality,
                barangay = barangay,
                street = street,
                postalCode = postalCode,
                fullAddress = fullAddress,
                // Course information
                courseName = selectedCourseName,
                courseCode = courseCode,
                enrollmentType = enrollmentType,
                applicationType = applicationType,
                status = "submitted",
                timestamp = Timestamp.now(),
                isVerified = true,

                studentLastNameExtension = studentLastNameExtension,
                fatherLastNameExtension = fatherLastNameExtension,
                motherLastNameExtension = motherLastNameExtension,
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
                Toast.makeText(this, "Enrollment submitted successfully!", Toast.LENGTH_LONG).show()
    
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
    
        // Add this extension function after the EnrollmentActivity class
        fun Enrollment.toFirestoreMap(): Map<String, Any> {
            return mapOf(
                "id" to id,
                "firstName" to firstName,
                "middleName" to middleName,
                "lastName" to lastName,
                "email" to email,
                "phone" to phone,
                "address" to address,
                "dateOfBirth" to dateOfBirth,
                "gender" to gender,
                "course" to course,
                "yearLevel" to yearLevel,
                "guardianName" to guardianName,
                "guardianPhone" to guardianPhone,
                "guardianRelationship" to guardianRelationship,
                "fatherFirstName" to fatherFirstName,
                "fatherMiddleName" to fatherMiddleName,
                "fatherLastName" to fatherLastName,
                "fatherDOB" to fatherDOB,
                "fatherPhone" to fatherPhone,
                "fatherOccupation" to fatherOccupation,
                "motherFirstName" to motherFirstName,
                "motherMiddleName" to motherMiddleName,
                "motherLastName" to motherLastName,
                "motherDOB" to motherDOB,
                "motherPhone" to motherPhone,
                "motherOccupation" to motherOccupation,
                "region" to region,
                "province" to province,
                "municipality" to municipality,
                "barangay" to barangay,
                "street" to street,
                "postalCode" to postalCode,
                "fullAddress" to fullAddress,
                "courseName" to courseName,
                "courseCode" to courseCode,
                "enrollmentType" to enrollmentType,
                "applicationType" to applicationType,
                "status" to status,
                "timestamp" to timestamp,
                "isVerified" to isVerified,
                "studentLastNameExtension" to studentLastNameExtension,
                "fatherLastNameExtension" to fatherLastNameExtension,
                "motherLastNameExtension" to motherLastNameExtension
            ) as Map<String, Any>
        }
    
        private fun clearAllErrors() {
            clearStudentErrors()
            clearParentErrors()
            clearGuardianErrors()
        }
        private fun setSubmitButtonState(text: String, enabled: Boolean) {
            btnSubmitEnrollment.text = text
            btnSubmitEnrollment.isEnabled = enabled
            isSubmitting = !enabled
        }
    
        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
        }
    }
    
    data class CourseDisplay(val code: String, val name: String)
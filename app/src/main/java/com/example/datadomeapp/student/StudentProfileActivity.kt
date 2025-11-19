package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.datadomeapp.R
import com.example.datadomeapp.admin.Enrollment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class StudentProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // UI Elements
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var ivProfilePicture: ImageView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var errorCard: com.google.android.material.card.MaterialCardView

    // Personal Info
    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentId: TextView
    private lateinit var tvCourseYear: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvDateOfBirth: TextView
    private lateinit var tvGender: TextView
    private lateinit var tvAddress: TextView

    // Academic Info
    private lateinit var tvCourse: TextView
    private lateinit var tvYearLevel: TextView
    private lateinit var tvStatus: TextView

    // Father Info
    private lateinit var tvFatherName: TextView
    private lateinit var tvFatherDOB: TextView
    private lateinit var tvFatherPhone: TextView
    private lateinit var tvFatherOccupation: TextView

    // Mother Info
    private lateinit var tvMotherName: TextView
    private lateinit var tvMotherDOB: TextView
    private lateinit var tvMotherPhone: TextView
    private lateinit var tvMotherOccupation: TextView

    // Guardian Info
    private lateinit var tvGuardianName: TextView
    private lateinit var tvGuardianPhone: TextView
    private lateinit var tvGuardianRelationship: TextView

    private var studentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_profile)

        initializeViews()

        // Debug: See what's in Firestore
        debugFirestoreContents()

        studentId = intent.getStringExtra("STUDENT_ID")

        if (studentId.isNullOrEmpty()) {
            getStudentIdFromUser()
        } else {
            Log.d("PROFILE_DEBUG", "🎯 Student ID from intent: '$studentId'")
            loadStudentProfile(studentId!!)
        }
    }

    private fun initializeViews() {
        // Initialize all TextView references
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        loadingLayout = findViewById(R.id.loadingLayout)
        errorCard = findViewById(R.id.errorCard)

        // Personal Info
        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentId = findViewById(R.id.tvStudentId)
        tvCourseYear = findViewById(R.id.tvCourseYear)
        tvEmail = findViewById(R.id.tvEmail)
        tvPhone = findViewById(R.id.tvPhone)
        tvDateOfBirth = findViewById(R.id.tvDateOfBirth)
        tvGender = findViewById(R.id.tvGender)
        tvAddress = findViewById(R.id.tvAddress)

        // Academic Info
        tvCourse = findViewById(R.id.tvCourse)
        tvYearLevel = findViewById(R.id.tvYearLevel)
        tvStatus = findViewById(R.id.tvStatus)

        // Father Info
        tvFatherName = findViewById(R.id.tvFatherName)
        tvFatherDOB = findViewById(R.id.tvFatherDOB)
        tvFatherPhone = findViewById(R.id.tvFatherPhone)
        tvFatherOccupation = findViewById(R.id.tvFatherOccupation)

        // Mother Info
        tvMotherName = findViewById(R.id.tvMotherName)
        tvMotherDOB = findViewById(R.id.tvMotherDOB)
        tvMotherPhone = findViewById(R.id.tvMotherPhone)
        tvMotherOccupation = findViewById(R.id.tvMotherOccupation)

        // Guardian Info
        tvGuardianName = findViewById(R.id.tvGuardianName)
        tvGuardianPhone = findViewById(R.id.tvGuardianPhone)
        tvGuardianRelationship = findViewById(R.id.tvGuardianRelationship)
    }

    private fun debugFirestoreContents() {
        Log.d("PROFILE_DEBUG", "🔍 DEBUGGING FIRESTORE CONTENTS")

        // Check students collection
        firestore.collection("students")
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("PROFILE_DEBUG", "📚 Students collection has ${querySnapshot.size()} documents:")
                for (document in querySnapshot.documents) {
                    Log.d("PROFILE_DEBUG", "   📄 Student Document ID: '${document.id}'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_DEBUG", "❌ Failed to debug students collection", e)
            }

        // Check pendingEnrollments collection
        firestore.collection("pendingEnrollments")
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("PROFILE_DEBUG", "📝 PendingEnrollments collection has ${querySnapshot.size()} documents:")
                for (document in querySnapshot.documents) {
                    val id = document.getString("id") ?: "N/A"
                    Log.d("PROFILE_DEBUG", "   📄 Enrollment ID: '$id', Document ID: '${document.id}'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_DEBUG", "❌ Failed to debug pendingEnrollments", e)
            }
    }

    private fun getStudentIdFromUser() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("User not authenticated")
            return
        }

        showLoading(true)

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val fetchedStudentId = userDoc.getString("studentId")
                if (fetchedStudentId.isNullOrEmpty()) {
                    showError("Student ID not found. Please contact administrator.")
                } else {
                    studentId = fetchedStudentId
                    Log.d("PROFILE_DEBUG", "🎯 Student ID from user: '$fetchedStudentId'")
                    Log.d("PROFILE_DEBUG", "📏 ID length: ${fetchedStudentId.length}")
                    Log.d("PROFILE_DEBUG", "🔠 Uppercase: '${fetchedStudentId.uppercase()}'")
                    Log.d("PROFILE_DEBUG", "🔡 Lowercase: '${fetchedStudentId.lowercase()}'")
                    loadStudentProfile(fetchedStudentId)
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to load user data: ${e.message}")
                Log.e("PROFILE_DEBUG", "Failed to load user data", e)
            }
    }

    private fun loadStudentProfile(studentId: String) {
        showLoading(true)
        Log.d("PROFILE_DEBUG", "🔄 Loading profile for student: '$studentId'")

        // First, try exact match in students collection
        firestore.collection("students").document(studentId).get()
            .addOnSuccessListener { studentDoc ->
                if (studentDoc.exists()) {
                    Log.d("PROFILE_DEBUG", "✅ Found student with EXACT CASE MATCH in students collection")
                    displayStudentData(studentDoc)
                } else {
                    Log.d("PROFILE_DEBUG", "⚠️ No exact match in students collection, trying case-insensitive search")
                    searchStudentCaseInsensitive(studentId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_DEBUG", "❌ Failed to load from students collection", e)
                searchStudentCaseInsensitive(studentId)
            }
    }

    private fun searchStudentCaseInsensitive(studentId: String) {
        Log.d("PROFILE_DEBUG", "🔍 Starting case-insensitive search for: '$studentId'")

        // Try to find student by searching in all documents
        firestore.collection("students")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var found = false

                for (document in querySnapshot.documents) {
                    val docId = document.id
                    Log.d("PROFILE_DEBUG", "📄 Checking document: '$docId'")

                    // Check if document ID matches (case-insensitive)
                    if (docId.equals(studentId, ignoreCase = true)) {
                        Log.d("PROFILE_DEBUG", "✅ Found case-insensitive match: '$docId'")
                        displayStudentData(document)
                        found = true
                        break
                    }
                }

                if (!found) {
                    Log.d("PROFILE_DEBUG", "❌ No case-insensitive match found in students collection")
                    loadFromPendingEnrollments(studentId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_DEBUG", "❌ Failed to search students collection", e)
                loadFromPendingEnrollments(studentId)
            }
    }

    private fun loadFromPendingEnrollments(studentId: String) {
        Log.d("PROFILE_DEBUG", "🔍 Searching pendingEnrollments for: '$studentId'")

        // First try exact match
        firestore.collection("pendingEnrollments")
            .whereEqualTo("id", studentId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    Log.d("PROFILE_DEBUG", "✅ Found enrollment with EXACT CASE MATCH")
                    val enrollmentDoc = querySnapshot.documents.first()
                    processEnrollmentDocument(enrollmentDoc)
                } else {
                    Log.d("PROFILE_DEBUG", "⚠️ No exact match in pendingEnrollments, trying case-insensitive")
                    searchEnrollmentsCaseInsensitive(studentId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_DEBUG", "❌ Failed to load enrollment data", e)
                searchEnrollmentsCaseInsensitive(studentId)
            }
    }

    private fun searchEnrollmentsCaseInsensitive(studentId: String) {
        Log.d("PROFILE_DEBUG", "🔍 Starting case-insensitive enrollment search for: '$studentId'")

        firestore.collection("pendingEnrollments")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var found = false

                for (document in querySnapshot.documents) {
                    val enrollmentId = document.getString("id") ?: ""
                    Log.d("PROFILE_DEBUG", "📄 Checking enrollment: '$enrollmentId'")

                    // Check if enrollment ID matches (case-insensitive)
                    if (enrollmentId.equals(studentId, ignoreCase = true)) {
                        Log.d("PROFILE_DEBUG", "✅ Found case-insensitive enrollment match: '$enrollmentId'")
                        processEnrollmentDocument(document)
                        found = true
                        break
                    }
                }

                if (!found) {
                    Log.d("PROFILE_DEBUG", "❌ No enrollment found with ID: '$studentId'")
                    showError("Student record not found. Please contact administrator.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_DEBUG", "❌ Failed to search enrollments", e)
                showError("Failed to load student data: ${e.message}")
            }
    }

    private fun processEnrollmentDocument(enrollmentDoc: DocumentSnapshot) {
        val enrollmentData = enrollmentDoc.data ?: emptyMap()

        try {
            val enrollment = Enrollment.fromFirestore(enrollmentDoc.id, enrollmentData)
            Log.d("PROFILE_DEBUG", "✅ Processing enrollment data for: ${enrollment.firstName} ${enrollment.lastName}")
            displayEnrollmentData(enrollment)
        } catch (e: Exception) {
            Log.e("PROFILE_DEBUG", "❌ Error parsing enrollment data", e)
            showError("Error loading student data. Please try again.")
        }
    }

    private fun displayEnrollmentData(enrollment: Enrollment) {
        try {
            Log.d("PROFILE_DEBUG", "🔍 Starting to display enrollment data for: ${enrollment.id}")

            // Personal Information
            val fullName = buildString {
                append(enrollment.firstName)
                if (enrollment.middleName.isNotEmpty()) append(" ${enrollment.middleName}")
                append(" ${enrollment.lastName}")
            }.trim()

            Log.d("PROFILE_DEBUG", "👤 Name: $fullName")
            Log.d("PROFILE_DEBUG", "📧 Email: ${enrollment.email}")
            Log.d("PROFILE_DEBUG", "📞 Phone: ${enrollment.phone}")

            tvStudentName.text = fullName
            tvStudentId.text = "ID: ${enrollment.id}"

            val courseDisplay = if (enrollment.courseName.isNotEmpty()) enrollment.courseName else enrollment.course
            tvCourseYear.text = "$courseDisplay - ${enrollment.yearLevel}"

            tvEmail.text = enrollment.email
            tvPhone.text = enrollment.phone
            tvDateOfBirth.text = enrollment.dateOfBirth
            tvGender.text = enrollment.gender
            tvAddress.text = enrollment.address

            // Academic Information
            tvCourse.text = courseDisplay
            tvYearLevel.text = enrollment.yearLevel
            tvStatus.text = when (enrollment.status) {
                "submitted" -> "Pending Review"
                "admitted" -> "Admitted"
                "rejected" -> "Not Admitted"
                else -> enrollment.status
            }

            // Father Information
            val fatherFullName = buildString {
                append(enrollment.fatherFirstName)
                if (enrollment.fatherMiddleName.isNotEmpty()) append(" ${enrollment.fatherMiddleName}")
                append(" ${enrollment.fatherLastName}")
            }.trim()

            Log.d("PROFILE_DEBUG", "👨 Father: $fatherFullName")
            Log.d("PROFILE_DEBUG", "📞 Father Phone: ${enrollment.fatherPhone}")

            tvFatherName.text = if (fatherFullName.isNotEmpty()) fatherFullName else "Not Provided"
            tvFatherDOB.text = enrollment.fatherDOB.ifEmpty { "Not Provided" }
            tvFatherPhone.text = enrollment.fatherPhone.ifEmpty { "Not Provided" }
            tvFatherOccupation.text = enrollment.fatherOccupation.ifEmpty { "Not Provided" }

            // Mother Information
            val motherFullName = buildString {
                append(enrollment.motherFirstName)
                if (enrollment.motherMiddleName.isNotEmpty()) append(" ${enrollment.motherMiddleName}")
                append(" ${enrollment.motherLastName}")
            }.trim()

            Log.d("PROFILE_DEBUG", "👩 Mother: $motherFullName")
            Log.d("PROFILE_DEBUG", "📞 Mother Phone: ${enrollment.motherPhone}")

            tvMotherName.text = if (motherFullName.isNotEmpty()) motherFullName else "Not Provided"
            tvMotherDOB.text = enrollment.motherDOB.ifEmpty { "Not Provided" }
            tvMotherPhone.text = enrollment.motherPhone.ifEmpty { "Not Provided" }
            tvMotherOccupation.text = enrollment.motherOccupation.ifEmpty { "Not Provided" }

            // Guardian Information
            Log.d("PROFILE_DEBUG", "👤 Guardian: ${enrollment.guardianName}")
            Log.d("PROFILE_DEBUG", "📞 Guardian Phone: ${enrollment.guardianPhone}")

            tvGuardianName.text = enrollment.guardianName.ifEmpty { "Not Provided" }
            tvGuardianPhone.text = enrollment.guardianPhone.ifEmpty { "Not Provided" }
            tvGuardianRelationship.text = enrollment.guardianRelationship.ifEmpty { "Not Provided" }

            // Load profile picture for enrollment
            loadProfilePicture(enrollment.profileImageUrl)

            showLoading(false)
            Log.i("PROFILE_DEBUG", "🎉 Enrollment data displayed successfully for student: ${enrollment.id}")

        } catch (e: Exception) {
            Log.e("PROFILE_DEBUG", "❌ Error displaying enrollment data", e)
            showError("Error displaying student information")
        }
    }

    private fun displayStudentData(studentDoc: DocumentSnapshot) {
        try {
            Log.d("PROFILE_DEBUG", "🔍 Starting to display student data for: ${studentDoc.id}")

            // Personal Information
            val firstName = studentDoc.getString("firstName") ?: ""
            val middleName = studentDoc.getString("middleName") ?: ""
            val lastName = studentDoc.getString("lastName") ?: ""
            val fullName = buildString {
                append(firstName)
                if (middleName.isNotEmpty()) append(" $middleName")
                append(" $lastName")
            }.trim()

            Log.d("PROFILE_DEBUG", "👤 Name: $fullName")
            Log.d("PROFILE_DEBUG", "📧 Email: ${studentDoc.getString("email")}")

            tvStudentName.text = fullName
            tvStudentId.text = "ID: ${studentDoc.id}"

            val course = studentDoc.getString("courseCode") ?: studentDoc.getString("course") ?: "N/A"
            val yearLevel = studentDoc.getString("yearLevel") ?: "N/A"
            tvCourseYear.text = "$course - $yearLevel"

            tvEmail.text = studentDoc.getString("email") ?: "N/A"
            tvPhone.text = studentDoc.getString("phone") ?: "N/A"
            tvDateOfBirth.text = studentDoc.getString("dateOfBirth") ?: "N/A"
            tvGender.text = studentDoc.getString("gender") ?: "N/A"
            tvAddress.text = studentDoc.getString("address") ?: "N/A"

            // Academic Information
            tvCourse.text = course
            tvYearLevel.text = yearLevel
            tvStatus.text = when {
                studentDoc.getBoolean("isEnrolled") == true -> "Currently Enrolled"
                studentDoc.getString("status") == "Admitted" -> "Admitted"
                else -> studentDoc.getString("status") ?: "N/A"
            }

            // Father Information
            val fatherFirstName = studentDoc.getString("fatherFirstName") ?: ""
            val fatherMiddleName = studentDoc.getString("fatherMiddleName") ?: ""
            val fatherLastName = studentDoc.getString("fatherLastName") ?: ""
            val fatherFullName = buildString {
                append(fatherFirstName)
                if (fatherMiddleName.isNotEmpty()) append(" $fatherMiddleName")
                append(" $fatherLastName")
            }.trim()

            Log.d("PROFILE_DEBUG", "👨 Father: $fatherFullName")
            Log.d("PROFILE_DEBUG", "📞 Father Phone: ${studentDoc.getString("fatherPhone")}")

            tvFatherName.text = if (fatherFullName.isNotEmpty()) fatherFullName else "Not Provided"
            tvFatherDOB.text = studentDoc.getString("fatherDOB") ?: "Not Provided"
            tvFatherPhone.text = studentDoc.getString("fatherPhone") ?: "Not Provided"
            tvFatherOccupation.text = studentDoc.getString("fatherOccupation") ?: "Not Provided"

            // Mother Information
            val motherFirstName = studentDoc.getString("motherFirstName") ?: ""
            val motherMiddleName = studentDoc.getString("motherMiddleName") ?: ""
            val motherLastName = studentDoc.getString("motherLastName") ?: ""
            val motherFullName = buildString {
                append(motherFirstName)
                if (motherMiddleName.isNotEmpty()) append(" $motherMiddleName")
                append(" $motherLastName")
            }.trim()

            Log.d("PROFILE_DEBUG", "👩 Mother: $motherFullName")
            Log.d("PROFILE_DEBUG", "📞 Mother Phone: ${studentDoc.getString("motherPhone")}")

            tvMotherName.text = if (motherFullName.isNotEmpty()) motherFullName else "Not Provided"
            tvMotherDOB.text = studentDoc.getString("motherDOB") ?: "Not Provided"
            tvMotherPhone.text = studentDoc.getString("motherPhone") ?: "Not Provided"
            tvMotherOccupation.text = studentDoc.getString("motherOccupation") ?: "Not Provided"

            // Guardian Information
            Log.d("PROFILE_DEBUG", "👤 Guardian: ${studentDoc.getString("guardianName")}")
            Log.d("PROFILE_DEBUG", "📞 Guardian Phone: ${studentDoc.getString("guardianPhone")}")

            tvGuardianName.text = studentDoc.getString("guardianName") ?: "Not Provided"
            tvGuardianPhone.text = studentDoc.getString("guardianPhone") ?: "Not Provided"
            tvGuardianRelationship.text = studentDoc.getString("guardianRelationship") ?: "Not Provided"

            // Load profile picture for student
            val profileImageUrl = studentDoc.getString("profileImageUrl")
            loadProfilePicture(profileImageUrl)

            showLoading(false)
            Log.i("PROFILE_DEBUG", "🎉 Student data displayed successfully for: ${studentDoc.id}")

        } catch (e: Exception) {
            Log.e("PROFILE_DEBUG", "❌ Error displaying student data", e)
            showError("Error displaying student information")
        }
    }

    // Load profile picture using Glide
    private fun loadProfilePicture(profileImageUrl: String?) {
        try {
            if (!profileImageUrl.isNullOrEmpty() && profileImageUrl != "null") {
                Log.d("PROFILE_DEBUG", "🖼️ Loading profile picture from: $profileImageUrl")

                Glide.with(this)
                    .load(profileImageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(ivProfilePicture)
            } else {
                // Set default placeholder if no profile picture
                ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                Log.d("PROFILE_DEBUG", "ℹ️ No profile picture URL provided, using placeholder")
            }
        } catch (e: Exception) {
            Log.e("PROFILE_DEBUG", "❌ Error loading profile picture", e)
            ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            loadingLayout.visibility = View.VISIBLE
            errorCard.visibility = View.GONE
        } else {
            loadingLayout.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        loadingLayout.visibility = View.GONE
        tvError.text = message
        errorCard.visibility = View.VISIBLE
        Log.e("PROFILE_DEBUG", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
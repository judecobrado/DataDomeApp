package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.student.StudentDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EnrollExistingStudentActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var spnSubject: Spinner
    private lateinit var spnSemester: Spinner
    private lateinit var spnYear: Spinner
    private lateinit var btnEnroll: Button

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_existing_student)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        spnSubject = findViewById(R.id.spnSubject)
        spnSemester = findViewById(R.id.spnSemester)
        spnYear = findViewById(R.id.spnYear)
        btnEnroll = findViewById(R.id.btnEnroll)

        // Example static lists
        val subjects = listOf("Select Subject", "BSIT101", "BSIT102", "BSIT103")
        val semesters = listOf("1st Semester", "2nd Semester")
        val years = listOf("1st Year", "2nd Year", "3rd Year", "4th Year")

        spnSubject.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, subjects)
        spnSemester.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, semesters)
        spnYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)

        btnEnroll.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter login credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginAndEnroll(email, password)
        }
    }

    private fun loginAndEnroll(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                firestore.collection("students").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val isRegular = doc.getBoolean("isRegular") ?: true // assume true if not set

                        if (isRegular) {
                            autoEnroll(uid)
                        } else {
                            Toast.makeText(
                                this,
                                "Go to office first for evaluation (Irregular Student)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun autoEnroll(uid: String) {
        val subject = spnSubject.selectedItem.toString()
        val sem = spnSemester.selectedItem.toString()
        val year = spnYear.selectedItem.toString()

        if (subject == "Select Subject") {
            Toast.makeText(this, "Please select a subject", Toast.LENGTH_SHORT).show()
            return
        }

        // Create enrollment data
        val data = mapOf(
            "studentUid" to uid,
            "subjectCode" to subject,
            "semester" to sem,
            "yearLevel" to year,
            "status" to "enrolled",
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("enrollments")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Enrollment successful!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, StudentDashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to enroll: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}

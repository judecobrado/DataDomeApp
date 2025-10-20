package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnVerify: Button

    private val firestore = FirebaseFirestore.getInstance()
    // Tiyakin na ang collection name ay 'pendingEnrollments' (Tulad ng ginamit mo sa ibang files)
    private val PENDING_COLLECTION = "pendingEnrollments"
    private val realtimeDb = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollement_verify_email)

        etEmail = findViewById(R.id.etEmail)
        btnVerify = findViewById(R.id.btnVerify)

        btnVerify.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            } else {
                checkEmail(email)
            }
        }
    }

    private fun checkEmail(email: String) {
        // 1️⃣ Check students collection (Registered users)
        firestore.collection("students").whereEqualTo("email", email).get()
            .addOnSuccessListener { students ->
                if (!students.isEmpty) {
                    Toast.makeText(
                        this,
                        "Email already exists in system. Cannot enroll again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                // 2️⃣ Check pending enrollments
                firestore.collection(PENDING_COLLECTION).whereEqualTo("email", email).get()
                    .addOnSuccessListener { pending ->
                        if (!pending.isEmpty) {
                            val doc = pending.documents[0]
                            val docId = doc.id
                            val status = doc.getString("status") ?: "pending"

                            when (status) {
                                "submitted" -> {
                                    // Status: SUBMITTED (Hindi na pwedeng i-submit ulit)
                                    val intent = Intent(this, AlreadySubmittedActivity::class.java)
                                    startActivity(intent)
                                }

                                "pending" -> {
                                    // Status: PENDING (Verified na ang email pero hindi pa sinubmit ang form)
                                    // Direkta na sa Enrollment Activity, hindi na kailangan ng verification code
                                    Toast.makeText(this, "Verification already completed. Redirecting to form.", Toast.LENGTH_LONG).show()
                                    navigateToEnrollment(email, docId)
                                }

                                else -> {
                                    // Handle unexpected status (fallback to sending code or new enrollment logic)
                                    sendVerificationCode(email, docId)
                                }
                            }
                        } else {
                            // Walang existing record. Magpadala ng code para sa bagong enrollment.
                            sendVerificationCode(email)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error checking pending status.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking student status.", Toast.LENGTH_SHORT).show()
            }
    }

    // ✅ NEW: Helper function para i-direct sa Enrollment Activity
    private fun navigateToEnrollment(email: String, docId: String) {
        val intent = Intent(this, EnrollmentActivity::class.java)
        intent.putExtra("email", email)
        intent.putExtra("docId", docId)
        startActivity(intent)
    }


    private fun sendVerificationCode(email: String, docId: String? = null) {
        val code = Random.nextInt(100000, 999999).toString()
        val timestamp = System.currentTimeMillis()
        val safeEmail = email.replace(".", "_")

        val data = mapOf(
            "email" to email,
            "code" to code,
            "timestamp" to timestamp,
            "verified" to false,
            "docId" to (docId ?: "")
        )

        realtimeDb.child("email_verifications").child(safeEmail)
            .setValue(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Verification code sent to $email", Toast.LENGTH_LONG).show()
                val intent = Intent(this, EnterCodeActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send code: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
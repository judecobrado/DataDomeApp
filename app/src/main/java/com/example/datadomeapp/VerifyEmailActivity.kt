package com.example.datadomeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import java.util.*

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnVerify: Button

    private val db = FirebaseFirestore.getInstance()
    private val CODE_LENGTH = 6
    private val CODE_EXPIRE_MINUTES = 10L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

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
        // 1️⃣ Check students
        db.collection("students").whereEqualTo("email", email).get()
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
                db.collection("pending_enrollments").whereEqualTo("email", email).get()
                    .addOnSuccessListener { pending ->
                        if (!pending.isEmpty) {
                            val doc = pending.documents[0]
                            val status = doc.getString("status") ?: "pending"
                            when (status) {
                                "pending" -> navigateToEnrollmentForm(email, doc.id)
                                "submitted" -> Toast.makeText(
                                    this,
                                    "You have already submitted your enrollment.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            // New email → go to enrollment form
                            navigateToEnrollmentForm(email)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToEnrollmentForm(email: String, docId: String? = null) {
        val intent = Intent(this, EnrollmentActivity::class.java)
        intent.putExtra("email", email)
        if (docId != null) intent.putExtra("docId", docId)
        startActivity(intent)
    }


    //private fun checkEmail(email: String) {
        // 1️⃣ Check students
        //db.collection("students").whereEqualTo("email", email).get()
            //.addOnSuccessListener { students ->
                //if (!students.isEmpty) {
                    //Toast.makeText(
                        //this,
                        //"Email already exists in system. Cannot enroll again.",
                        //Toast.LENGTH_LONG
                    //).show()
                    //return@addOnSuccessListener
                //}

                // 2️⃣ Check pending enrollments
                //db.collection("pending_enrollments").whereEqualTo("email", email).get()
                    //.addOnSuccessListener { pending ->
                        //if (!pending.isEmpty) {
                            //val doc = pending.documents[0]
                            //val status = doc.getString("status") ?: "pending"
                            //when (status) {
                                //"pending" -> sendVerificationCode(email, doc.id)
                                //"submitted" -> {
                                    //val intent = Intent(this, AlreadySubmittedActivity::class.java)
                                    //startActivity(intent)
                                //}
                            //}
                        //} else {
                            // New email → send verification
                            //sendVerificationCode(email)
                        //}
                    //}
                    //.addOnFailureListener { e ->
                        //Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    //}
            //}
            //.addOnFailureListener { e ->
                //Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            //}
    //}

    private fun sendVerificationCode(email: String, docId: String? = null) {
        val code = Random.nextInt(100000, 999999).toString()
        val timestamp = Date()

        val verificationData = hashMapOf(
            "email" to email,
            "code" to code,
            "timestamp" to timestamp,
            "verified" to false,
            "docId" to docId
        )

        db.collection("email_verifications").document(email)
            .set(verificationData)
            .addOnSuccessListener {
                Toast.makeText(this, "Verification code sent to $email", Toast.LENGTH_LONG).show()
                val intent = Intent(this, EnterCodeActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send code: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val PENDING_COLLECTION = "pendingEnrollments"
    private val realtimeDb = FirebaseDatabase.getInstance().reference
    private val gmailSender = GmailSender()

    private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollement_verify_email)

        etEmail = findViewById(R.id.etEmail)
        btnVerify = findViewById(R.id.btnVerify)

        btnVerify.setOnClickListener {
            if (isProcessing) return@setOnClickListener

            val email = etEmail.text.toString().trim()
            when {
                email.isEmpty() -> {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                }
                !isValidEmail(email) -> {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    checkEmail(email)
                }
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun checkEmail(email: String) {
        isProcessing = true
        setButtonState("Checking email...", false)
        Toast.makeText(this, "Checking email availability...", Toast.LENGTH_SHORT).show()

        firestore.collection("students").whereEqualTo("email", email).get()
            .addOnSuccessListener { students ->
                if (!students.isEmpty) {
                    resetButton()
                    Toast.makeText(
                        this,
                        "❌ Email already registered. Cannot enroll again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                firestore.collection(PENDING_COLLECTION).whereEqualTo("email", email).get()
                    .addOnSuccessListener { pending ->
                        if (!pending.isEmpty) {
                            val doc = pending.documents[0]
                            val docId = doc.id
                            val status = doc.getString("status") ?: "pending"

                            when (status) {
                                "submitted" -> {
                                    // ALWAYS send verification code for security, even for submitted enrollments
                                    sendVerificationCode(email, docId)
                                }
                                else -> {
                                    // ALWAYS send a new verification code for security
                                    // regardless of whether email was previously verified or not
                                    sendVerificationCode(email, docId)
                                }
                            }
                        } else {
                            // New email - send verification code
                            sendVerificationCode(email)
                        }
                    }
                    .addOnFailureListener {
                        // On error, still proceed with sending verification code
                        sendVerificationCode(email)
                    }
            }
            .addOnFailureListener {
                // On error, still proceed with sending verification code
                sendVerificationCode(email)
            }
    }

    private fun sendVerificationCode(email: String, docId: String? = null) {
        val code = Random.nextInt(100000, 999999).toString()
        val timestamp = System.currentTimeMillis()
        val safeEmail = email.replace(".", "_")

        isProcessing = true
        setButtonState("Sending code...", false)

        // Always reset verification data for security
        val data = mapOf(
            "email" to email,
            "code" to code,
            "timestamp" to timestamp,
            "verified" to false,
            "docId" to (docId ?: ""),
            "attempts" to 0  // Reset attempts counter
        )

        realtimeDb.child("email_verifications").child(safeEmail)
            .setValue(data)
            .addOnSuccessListener {
                attemptEmailSend(email, code, docId, safeEmail)
            }
            .addOnFailureListener { exception ->
                // Even if Firebase fails, attempt to send email and proceed
                Toast.makeText(this, "Setting up verification...", Toast.LENGTH_SHORT).show()
                attemptEmailSend(email, code, docId, safeEmail)
            }
    }

    private fun attemptEmailSend(email: String, code: String, docId: String?, safeEmail: String) {
        var retryCount = 0
        val maxRetries = 2

        fun trySend() {
            val attemptText = if (retryCount == 0) "Sending code..." else "Sending code... (${retryCount + 1})"
            setButtonState(attemptText, false)

            gmailSender.sendVerificationCode(email, code, object : EmailSendCallback {
                override fun onSending() {
                    runOnUiThread {
                        Toast.makeText(this@VerifyEmailActivity, "📧 Sending verification code...", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onSuccess() {
                    runOnUiThread {
                        completeEmailProcess(email, docId, true)
                    }
                }

                override fun onComplete(success: Boolean) {
                    runOnUiThread {
                        if (success) {
                            completeEmailProcess(email, docId, true)
                        } else {
                            if (retryCount < maxRetries) {
                                retryCount++
                                handler.postDelayed({
                                    trySend()
                                }, 3000)
                            } else {
                                completeEmailProcess(email, docId, false)
                            }
                        }
                    }
                }
            })
        }

        trySend()
    }

    private fun completeEmailProcess(email: String, docId: String?, success: Boolean) {
        resetButton()

        if (success) {
            Toast.makeText(this, "✅ Verification code sent to $email", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Proceeding to code entry...", Toast.LENGTH_LONG).show()
        }

        // Navigate to EnterCodeActivity for ALL cases
        val intent = Intent(this, EnterCodeActivity::class.java)
        intent.putExtra("email", email)
        docId?.let { intent.putExtra("docId", it) }
        startActivity(intent)
    }

    private fun setButtonState(text: String, enabled: Boolean) {
        btnVerify.text = text
        btnVerify.isEnabled = enabled
    }

    private fun resetButton() {
        isProcessing = false
        setButtonState("Verify Email", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
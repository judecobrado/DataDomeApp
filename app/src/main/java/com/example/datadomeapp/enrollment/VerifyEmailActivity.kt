package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnVerify: Button
    private val firebaseAuth = FirebaseAuth.getInstance()
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
        disableInputs()
        Toast.makeText(this, "Checking email availability...", Toast.LENGTH_SHORT).show()
        println("🔍 [DEBUG] Starting email check for: $email")

        // DIRECT Firestore check for authentication users
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { users ->
                println("✅ [DEBUG] Users collection check - found: ${users.documents.size}")

                if (!users.isEmpty) {
                    println("🚫 [DEBUG] Email FOUND in users collection")
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Registration Error")
                        .setMessage("This email address is already exist. Please use a different email.")
                        .setPositiveButton("Understand") { dialog, _ -> dialog.dismiss() }
                        .setCancelable(false)
                        .show()
                } else {
                    // Check students and pending enrollments
                    checkFirestoreCollections(email)
                }
            }
            .addOnFailureListener {
                println("❌ [DEBUG] Users collection check failed - proceeding")
                checkFirestoreCollections(email)
            }
    }

    private fun checkFirestoreCollections(email: String) {
        println("🔍 [DEBUG] Checking pending enrollments for: $email")

        // First check pending enrollments
        firestore.collection(PENDING_COLLECTION)
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { pending ->
                println("✅ [DEBUG] Pending enrollments check - found: ${pending.documents.size}")

                if (!pending.isEmpty) {
                    val doc = pending.documents[0]
                    val docId = doc.id
                    println("🚫 [DEBUG] Email already exists in pending enrollments with ID: $docId")

                    // DON'T send verification code - show error instead
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Registration Error")
                        .setMessage("This email address already has a pending enrollment. Please wait for approval or use a different email.")
                        .setPositiveButton("Understand") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    println("🔍 [DEBUG] No pending enrollment found, checking students collection")
                    // Check students collection only if no pending enrollment exists
                    checkStudentsCollection(email)
                }
            }
            .addOnFailureListener { exception ->
                println("❌ [DEBUG] Pending enrollments check failed: ${exception.message}")
                // If pending check fails, still check students collection
                checkStudentsCollection(email)
            }
    }

    private fun checkStudentsCollection(email: String) {
        firestore.collection("students")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { students ->
                println("✅ [DEBUG] Students collection check - found: ${students.documents.size}")

                if (!students.isEmpty) {
                    println("🚫 [DEBUG] Email FOUND in students collection")
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Registration Error")
                        .setMessage("This email address is already exist. Please use a different email.")
                        .setPositiveButton("Understand") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    println("✅ [DEBUG] Email is available, sending verification code")
                    // No pending and no student - new email, send verification code without docId
                    sendVerificationCode(email)
                }
            }
            .addOnFailureListener { exception ->
                println("❌ [DEBUG] Students collection check failed: ${exception.message}")
                // On error, proceed with sending verification code without docId
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
            val toast = Toast.makeText(this, "Verification code sent to $email", Toast.LENGTH_LONG)
            val textView = toast.view?.findViewById<android.widget.TextView>(android.R.id.message)
            textView?.setTextColor(android.graphics.Color.GREEN)
            textView?.setBackgroundColor(android.graphics.Color.DKGRAY)
            toast.show()
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

    private fun disableInputs() {
        btnVerify.isEnabled = false
        etEmail.isEnabled = false
        btnVerify.alpha = 0.5f
        etEmail.alpha = 0.5f
    }

    private fun enableInputs() {
        btnVerify.isEnabled = true
        etEmail.isEnabled = true
        btnVerify.alpha = 1.0f
        etEmail.alpha = 1.0f
    }

    private fun resetButton() {
        isProcessing = false
        enableInputs()
        setButtonState("Verify Email", true)
        handler.postDelayed({
            enableInputs()
            setButtonState("Verify Email", true)
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
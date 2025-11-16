package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlin.random.Random
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class EnterCodeActivity : AppCompatActivity() {

    private lateinit var etCode: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnResend: Button
    private lateinit var tvTimer: TextView
    private lateinit var tvEmail: TextView

    private val realtimeDb = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var email: String
    private var docId: String? = null
    private var isVerifying = false
    private var isResending = false

    private val RESEND_INTERVAL = 60000L // 60 seconds
    private val CODE_EXPIRY = 10 * 60 * 1000L // 10 minutes
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_enter_code)

        initViews()
        getIntentData()
        setupClickListeners()
        startResendTimer()
    }

    private fun initViews() {
        etCode = findViewById(R.id.etCode)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnResend = findViewById(R.id.btnResend)
        tvTimer = findViewById(R.id.tvTimer)
        tvEmail = findViewById(R.id.tvEmail)
    }

    private fun getIntentData() {
        email = intent.getStringExtra("email") ?: ""
        docId = intent.getStringExtra("docId")

        if (email.isEmpty()) {
            Toast.makeText(this, "Email not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvEmail.text = "Verification code sent to: $email"
    }

    private fun setupClickListeners() {
        btnSubmit.setOnClickListener {
            if (isVerifying) return@setOnClickListener

            val code = etCode.text.toString().trim()
            if (code.isEmpty() || code.length != 6) {
                Toast.makeText(this, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
            } else {
                verifyCode(code)
            }
        }

        btnResend.setOnClickListener {
            if (!isResending && btnResend.isEnabled) {
                resendVerificationCode()
            }
        }
    }

    private fun verifyCode(enteredCode: String) {
        isVerifying = true
        setSubmitButtonState("Verifying...", false)

        val safeEmail = email.replace(".", "_")
        val verificationRef = realtimeDb.child("email_verifications").child(safeEmail)

        verificationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Code expired or not found
                    handleVerificationFailure("Verification code expired. Please request a new one.")
                    return
                }

                val storedCode = snapshot.child("code").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0
                val attempts = snapshot.child("attempts").getValue(Int::class.java) ?: 0
                val verified = snapshot.child("verified").getValue(Boolean::class.java) ?: false
                val storedDocId = snapshot.child("docId").getValue(String::class.java) ?: ""

                // Use the docId from verification data if available
                if (storedDocId.isNotEmpty()) {
                    docId = storedDocId
                }

                // Check if code is expired (10 minutes)
                val currentTime = System.currentTimeMillis()
                val timeDifference = currentTime - timestamp

                if (timeDifference > CODE_EXPIRY) {
                    // Code expired
                    verificationRef.removeValue()
                    handleVerificationFailure("Verification code expired. Please request a new one.")
                    return
                }

                // Check if too many attempts
                if (attempts >= 5) {
                    handleVerificationFailure("Too many failed attempts. Please request a new code.")
                    return
                }

                // Check if already verified
                if (verified) {
                    handleVerificationFailure("Code already used. Please request a new one.")
                    return
                }

                if (enteredCode == storedCode) {
                    // Code is correct - mark as verified and proceed
                    markAsVerifiedAndProceed(safeEmail)
                } else {
                    // Code is incorrect - increment attempts
                    verificationRef.child("attempts").setValue(attempts + 1)
                    handleVerificationFailure("Invalid verification code. Please try again.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                handleVerificationFailure("Verification failed: ${error.message}")
            }
        })
    }

    private fun markAsVerifiedAndProceed(safeEmail: String) {
        setSubmitButtonState("Finalizing...", false)

        // Mark as verified in Realtime Database
        realtimeDb.child("email_verifications").child(safeEmail)
            .child("verified").setValue(true)
            .addOnSuccessListener {
                // Update pending enrollment and check status
                updatePendingEnrollmentAndCheckStatus()
            }
            .addOnFailureListener {
                // Still proceed with status check even if update fails
                updatePendingEnrollmentAndCheckStatus()
            }
    }

    private fun updatePendingEnrollmentAndCheckStatus() {
        if (!docId.isNullOrEmpty()) {
            // Update existing pending enrollment and get current status
            val updateData = hashMapOf<String, Any>(
                "isVerified" to true,
                "lastVerifiedAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection("pendingEnrollments").document(docId!!)
                .update(updateData)
                .addOnSuccessListener {
                    // Now check the current status
                    checkCurrentEnrollmentStatus()
                }
                .addOnFailureListener {
                    // Still check status even if update fails
                    checkCurrentEnrollmentStatus()
                }
        } else {
            // Create new pending enrollment (always new enrollment)
            createNewPendingEnrollment()
        }
    }

    private fun createNewPendingEnrollment() {
        val enrollmentData = hashMapOf(
            "email" to email,
            "isVerified" to true,
            "status" to "pending",
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis(),
            "lastVerifiedAt" to System.currentTimeMillis()
        )

        firestore.collection("pendingEnrollments")
            .add(enrollmentData)
            .addOnSuccessListener { documentReference ->
                docId = documentReference.id
                // New enrollment - always go to enrollment form
                navigateToEnrollmentActivity()
            }
            .addOnFailureListener {
                // Proceed to enrollment form even if creation fails
                navigateToEnrollmentActivity()
            }
    }

    private fun checkCurrentEnrollmentStatus() {
        if (docId.isNullOrEmpty()) {
            navigateToEnrollmentActivity()
            return
        }

        firestore.collection("pendingEnrollments").document(docId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status") ?: "pending"

                    when (status) {
                        "submitted" -> {
                            // Redirect to already submitted screen
                            Toast.makeText(this, "Enrollment already submitted", Toast.LENGTH_LONG).show()
                            val intent = Intent(this, AlreadySubmittedActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
                            finish()
                        }
                        else -> {
                            // Go to enrollment form
                            navigateToEnrollmentActivity()
                        }
                    }
                } else {
                    // Document doesn't exist - go to enrollment form
                    navigateToEnrollmentActivity()
                }
            }
            .addOnFailureListener {
                // On error, proceed to enrollment form
                navigateToEnrollmentActivity()
            }
    }

    private fun navigateToEnrollmentActivity() {
        setSubmitButtonState("Verified!", false)
        Toast.makeText(this, "✅ Email verified successfully!", Toast.LENGTH_SHORT).show()

        // Remove OTP data after successful verification
        val safeEmail = email.replace(".", "_")
        realtimeDb.child("email_verifications").child(safeEmail).removeValue()

        handler.postDelayed({
            val intent = Intent(this, EnrollmentActivity::class.java)
            intent.putExtra("email", email)
            docId?.let { intent.putExtra("docId", it) }
            startActivity(intent)
            finish()
        }, 1000)
    }

    private fun resendVerificationCode() {
        isResending = true
        setResendButtonState("Sending...", false)
        Toast.makeText(this, "Sending new verification code...", Toast.LENGTH_SHORT).show()

        val newCode = Random.nextInt(100000, 999999).toString()
        val timestamp = System.currentTimeMillis()
        val safeEmail = email.replace(".", "_")

        val verificationData = hashMapOf<String, Any>(
            "email" to email,
            "code" to newCode,
            "timestamp" to timestamp,
            "verified" to false,
            "docId" to (docId ?: ""),
            "attempts" to 0
        )

        realtimeDb.child("email_verifications").child(safeEmail)
            .setValue(verificationData)
            .addOnSuccessListener {
                // ACTUALLY SEND THE EMAIL
                sendVerificationEmail(newCode)
            }
            .addOnFailureListener { exception ->
                completeResendProcess(false, "Failed to update code: ${exception.message}")
            }
    }

    private fun sendVerificationEmail(code: String) {
        val gmailSender = GmailSender()

        gmailSender.sendVerificationCode(email, code, object : EmailSendCallback {
            override fun onSending() {
                runOnUiThread {
                    Toast.makeText(this@EnterCodeActivity, "📧 Sending new verification code...", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onSuccess() {
                runOnUiThread {
                    completeResendProcess(true, "✅ New verification code sent to $email")
                }
            }

            override fun onComplete(success: Boolean) {
                runOnUiThread {
                    if (success) {
                        completeResendProcess(true, "✅ New verification code sent to $email")
                    } else {
                        completeResendProcess(false, "⚠️ Failed to send email, but code was updated. Try entering the code.")
                    }
                }
            }
        })
    }

    private fun completeResendProcess(success: Boolean, message: String) {
        isResending = false

        if (success) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            startResendTimer()
            etCode.text.clear()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            setResendButtonState("Resend Code", true)
            startResendTimer()
        }
    }

    private fun startResendTimer() {
        btnResend.isEnabled = false
        timer?.cancel()
        timer = object : CountDownTimer(RESEND_INTERVAL, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = "Resend available in ${seconds}s"
            }

            override fun onFinish() {
                btnResend.isEnabled = true
                tvTimer.text = "Click to resend code"
            }
        }.start()
    }

    private fun handleVerificationFailure(message: String) {
        resetSubmitButton()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setSubmitButtonState(text: String, enabled: Boolean) {
        btnSubmit.text = text
        btnSubmit.isEnabled = enabled
        isVerifying = !enabled
    }

    private fun setResendButtonState(text: String, enabled: Boolean) {
        btnResend.text = text
        btnResend.isEnabled = enabled
        isResending = !enabled
    }

    private fun resetSubmitButton() {
        isVerifying = false
        setSubmitButtonState("Submit", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
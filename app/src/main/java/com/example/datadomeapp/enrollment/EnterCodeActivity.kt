package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.database.FirebaseDatabase
import kotlin.random.Random

class EnterCodeActivity : AppCompatActivity() {

    private lateinit var etCode: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnResend: Button
    private lateinit var tvTimer: TextView

    private val realtimeDb = FirebaseDatabase.getInstance().reference
    private lateinit var email: String
    private var docId: String? = null

    private val RESEND_INTERVAL = 60000L // 60 seconds
    private val CODE_EXPIRY = 5 * 60 * 1000 // 5 minutes
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_enter_code)

        etCode = findViewById(R.id.etCode)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnResend = findViewById(R.id.btnResend)
        tvTimer = findViewById(R.id.tvTimer)

        email = intent.getStringExtra("email") ?: ""
        docId = intent.getStringExtra("docId")

        startResendTimer()

        btnSubmit.setOnClickListener {
            val inputCode = etCode.text.toString().trim()
            if (inputCode.isEmpty()) {
                Toast.makeText(this, "Enter the code", Toast.LENGTH_SHORT).show()
            } else {
                verifyCode(inputCode)
            }
        }

        btnResend.setOnClickListener {
            sendNewCode()
        }
    }

    private fun verifyCode(inputCode: String) {
        val safeEmail = email.replace(".", "_")
        realtimeDb.child("email_verifications").child(safeEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this, "No verification code found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val code = snapshot.child("code").value?.toString() ?: ""
                val timestamp = snapshot.child("timestamp").value?.toString()?.toLongOrNull() ?: 0L
                val verified = snapshot.child("verified").value?.toString()?.toBoolean() ?: false
                val now = System.currentTimeMillis()

                val expired = (now - timestamp) > CODE_EXPIRY

                if (expired) {
                    Toast.makeText(this, "Code expired. Please resend.", Toast.LENGTH_SHORT).show()
                    realtimeDb.child("email_verifications").child(safeEmail).removeValue()
                    return@addOnSuccessListener
                }

                if (verified) {
                    Toast.makeText(this, "This code was already verified.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                if (inputCode == code) {
                    // Verified → delete OTP
                    realtimeDb.child("email_verifications").child(safeEmail).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Email verified!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, EnrollmentActivity::class.java)
                            intent.putExtra("email", email)
                            docId?.let { intent.putExtra("docId", it) }
                            startActivity(intent)
                            finish()
                        }
                } else {
                    Toast.makeText(this, "Incorrect code. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendNewCode() {
        val newCode = Random.nextInt(100000, 999999).toString()
        val timestamp = System.currentTimeMillis()
        val safeEmail = email.replace(".", "_")

        realtimeDb.child("email_verifications").child(safeEmail)
            .updateChildren(mapOf("code" to newCode, "timestamp" to timestamp, "verified" to false))
            .addOnSuccessListener {
                Toast.makeText(this, "New code sent!", Toast.LENGTH_SHORT).show()
                startResendTimer()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to resend: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startResendTimer() {
        btnResend.isEnabled = false
        timer?.cancel()
        timer = object : CountDownTimer(RESEND_INTERVAL, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = "Resend available in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                btnResend.isEnabled = true
                tvTimer.text = "You can resend the code now."
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}

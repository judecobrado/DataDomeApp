package com.example.datadomeapp

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import kotlin.random.Random

class EnterCodeActivity : AppCompatActivity() {

    private lateinit var etCode: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnResend: Button
    private lateinit var tvTimer: TextView

    private val db = FirebaseFirestore.getInstance()
    private lateinit var email: String
    private var docId: String? = null

    private val RESEND_INTERVAL = 60000L // 60 seconds
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter_code)

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
        db.collection("email_verifications").document(email).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "No verification code found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val code = doc.getString("code") ?: ""
                val timestamp = doc.getDate("timestamp") ?: Date()
                val now = Date()

                val diffMinutes = (now.time - timestamp.time) / 60000

                if (diffMinutes > 10) {
                    Toast.makeText(this, "Code expired. Please resend.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                if (inputCode == code) {
                    // Mark verified
                    db.collection("email_verifications").document(email)
                        .update("verified", true)
                        .addOnSuccessListener {
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
    }

    private fun sendNewCode() {
        val newCode = Random.nextInt(100000, 999999).toString()
        db.collection("email_verifications").document(email)
            .update("code", newCode, "timestamp", Date())
            .addOnSuccessListener {
                Toast.makeText(this, "New code sent!", Toast.LENGTH_SHORT).show()
                startResendTimer()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to resend: ${e.message}", Toast.LENGTH_SHORT).show()
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

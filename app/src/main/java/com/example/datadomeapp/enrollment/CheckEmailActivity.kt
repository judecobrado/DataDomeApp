package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore

class CheckEmailActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private val db = FirebaseFirestore.getInstance()
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_check_email)

        tvInfo = findViewById(R.id.tvInfo)

        // Get email from previous activity
        email = intent.getStringExtra("email") ?: ""

        tvInfo.text = "A verification link has been sent to $email.\n" +
                "Please check your inbox and click the link to verify your email before enrolling."

        // Optionally, you can provide a "Resend" button here
    }

    override fun onResume() {
        super.onResume()
        // Check Firestore to see if email has already been verified
        if (email.isNotEmpty()) {
            db.collection("emailVerifications").document(email)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val verified = doc.getBoolean("verified") ?: false
                        if (verified) {
                            Toast.makeText(this, "Email verified! Proceeding to enrollment.", Toast.LENGTH_SHORT).show()
                            startEnrollment()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error checking verification: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startEnrollment() {
        val intent = Intent(this, EnrollmentActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
        finish()
    }
}

package com.example.datadomeapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var resetButton: Button
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        emailEditText = findViewById(R.id.emailEditText)
        resetButton = findViewById(R.id.resetButton)

        resetButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // First check if email exists in Firestore
            firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(this, "Email not found in the system", Toast.LENGTH_SHORT).show()
                    } else {
                        // Email exists in Firestore → send password reset email
                        auth.sendPasswordResetEmail(email)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                                    finish() // Close activity
                                } else {
                                    Toast.makeText(this, "Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error fetching user: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}

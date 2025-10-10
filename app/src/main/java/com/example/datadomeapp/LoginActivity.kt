package com.example.datadomeapp

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Hardcoded admin credentials
    private val adminEmail = "nellza.cobrado@gmail.com"
    private val adminPassword = "1232"

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.tvForgotPassword)

        progressDialog = ProgressDialog(this).apply {
            setMessage("Logging in...")
            setCancelable(false)
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Hardcoded admin login
            if (email == adminEmail && password == adminPassword) {
                Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
                return@setOnClickListener
            }

            // Show loading
            progressDialog.show()

            // Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            progressDialog.dismiss()
                            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }

                        // Try cache first to reduce delay
                        firestore.collection("users").document(currentUser.uid)
                            .get(Source.CACHE)
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    handleRole(doc.getString("role") ?: "")
                                } else {
                                    // fallback to server if not in cache
                                    firestore.collection("users").document(currentUser.uid)
                                        .get(Source.SERVER)
                                        .addOnSuccessListener { serverDoc ->
                                            if (serverDoc.exists()) {
                                                handleRole(serverDoc.getString("role") ?: "")
                                            } else {
                                                progressDialog.dismiss()
                                                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            progressDialog.dismiss()
                                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }

                    } else {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun handleRole(role: String) {
        progressDialog.dismiss()
        when (role.lowercase()) {
            "student" -> startActivity(Intent(this, StudentDashboardActivity::class.java))
            "teacher" -> startActivity(Intent(this, TeacherDashboardActivity::class.java))
            "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
            "canteen_staff" -> startActivity(Intent(this, CanteenStaffDashboardActivity::class.java))
            else -> Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

package com.example.datadomeapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show progress
            progressBar.visibility = View.VISIBLE

            // Hardcoded admin login
            if (email == adminEmail && password == adminPassword) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
                return@setOnClickListener
            }

            // Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    if (task.isSuccessful) {
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            Toast.makeText(this, "Login successful but user not found", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }

                        // Open dashboard immediately
                        startDashboard(currentUser.uid)

                        // Fetch role in background
                        firestore.collection("users").document(currentUser.uid)
                            .get()
                            .addOnSuccessListener { doc ->
                                val role = doc.getString("role") ?: "unknown"
                                // Optional: cache role in SharedPreferences
                                getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                                    .putString("role", role)
                                    .apply()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error fetching user role: ${e.message}", Toast.LENGTH_SHORT).show()
                            }

                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun startDashboard(uid: String) {
        // Check cached role first
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val role = prefs.getString("role", "") ?: ""

        val intent = when (role.lowercase()) {
            "student" -> Intent(this, StudentDashboardActivity::class.java)
            "teacher" -> Intent(this, TeacherDashboardActivity::class.java)
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "canteen_staff" -> Intent(this, CanteenStaffDashboardActivity::class.java)
            else -> Intent(this, StudentDashboardActivity::class.java) // default fallback
        }

        startActivity(intent)
        finish()
    }
}

package com.example.datadomeapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.canteen.CanteenStaffDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Hardcoded admin credentials
    private val adminEmail = "1"
    private val adminPassword = "1"

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        loginButton = findViewById(R.id.btnLogin)
        forgotPasswordText = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)

        // 1. Forgot Password Navigation Fix
        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
            // Hindi na kailangan i-finish() ang LoginActivity dito
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. KRITIKAL: I-disable ang UI at I-show ang progress bar AGAD
            showLoading()

            // Hardcoded admin login
            if (email == adminEmail && password == adminPassword) {
                hideLoading()
                Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
                return@setOnClickListener
            }

            // Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            // I-reset ang UI sa error path
                            resetUI()
                            Toast.makeText(this, "Login successful but user not found", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }
                        // Magpapatuloy sa Firestore fetch (fetchUserRoleAndStartDashboard)
                        fetchUserRoleAndStartDashboard(currentUser.uid)

                    } else {
                        // Kung FAILED ang Auth, i-reset ang UI
                        resetUI()
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun fetchUserRoleAndStartDashboard(uid: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                // KRITIKAL: Dito lang i-hide ang progress bar at i-enable ang button
                hideLoading()

                val role = doc.getString("role") ?: "unknown"

                getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                    .putString("role", role)
                    .apply()

                startDashboard(role)
            }
            .addOnFailureListener { e ->
                // I-reset ang UI kapag may error sa Firestore
                resetUI()
                Toast.makeText(this, "Error fetching user role: ${e.message}", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    private fun startDashboard(role: String) {

        val intent = when (role.lowercase()) {
            "student" -> Intent(this, StudentDashboardActivity::class.java)
            "teacher" -> Intent(this, TeacherDashboardActivity::class.java)
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "canteen_staff" -> Intent(this, CanteenStaffDashboardActivity::class.java)
            else -> {
                Toast.makeText(this, "Role not recognized. Defaulting to MainActivity.", Toast.LENGTH_LONG).show()
                Intent(this, MainActivity::class.java)
            }
        }

        startActivity(intent)
        finish()
    }

    // --- Utility functions for faster UI handling ---
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false // Disable ang button
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true // Enable ulit
    }

    private fun resetUI() {
        // Ginagamit sa error path
        hideLoading()
    }
}
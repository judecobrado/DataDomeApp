package com.example.datadomeapp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.admin.AdminDashboardActivity
import com.example.datadomeapp.canteen.CanteenStaffDashboardActivity
import com.example.datadomeapp.student.StudentDashboardActivity
import com.example.datadomeapp.teacher.TeacherDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var progressBar: ProgressBar

    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI elements
        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        loginButton = findViewById(R.id.btnLogin)
        forgotPasswordText = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading()

            // Standard Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            resetUI()
                            Toast.makeText(this, "Login successful", Toast.LENGTH_LONG).show()
                            return@addOnCompleteListener
                        }
                        fetchUserRoleAndStartDashboard(currentUser.uid)

                    } else {
                        // Auth failed
                        resetUI()
                        AlertDialog.Builder(this)
                            .setTitle("Login Failed")
                            .setMessage("The email or password you entered is incorrect.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
        }
    }

    private fun fetchUserRoleAndStartDashboard(uid: String) {
        // Fetch the role from the 'users' collection in Firestore
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    resetUI()
                    Log.e(TAG, "User document does not exist for UID: $uid")
                    AlertDialog.Builder(this)
                        .setTitle("Incomplete User Data")
                        .setMessage("User data setup incomplete. Please contact admin.")
                        .setPositiveButton("OK", null)
                        .show()
                    auth.signOut()
                    return@addOnSuccessListener
                }

                val role = doc.getString("role")

                // CRITICAL DEBUG LOGGING
                Log.d(TAG, "Fetched role from Firestore: $role")

                val finalRole = role ?: "unknown"

                // Save role to shared preferences for later use in the app
                getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                    .putString("role", finalRole)
                    .apply()

                hideLoading()
                startDashboard(finalRole, uid)
            }
            .addOnFailureListener { e ->
                // Firestore fetch failed
                resetUI()
                Log.e(TAG, "Error fetching user role for UID $uid: ${e.message}")
                AlertDialog.Builder(this)
                    .setTitle("Something Went Wrong!")
                    .setMessage("Please contact admin.")
                    .setPositiveButton("OK", null)
                    .show()
                auth.signOut()
            }
    }

    private fun startDashboard(role: String, uid: String) {
        // Use lowercase to match roles consistently
        val roleLower = role.lowercase(Locale.getDefault())

        val intent = when (roleLower) {
            "student" -> Intent(this, StudentDashboardActivity::class.java)
            "teacher" -> Intent(this, TeacherDashboardActivity::class.java)
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "canteen_staff" -> Intent(this, CanteenStaffDashboardActivity::class.java)
            else -> {
                Intent(this, MainActivity::class.java)
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // Ipinapasa ang UID sa Intent para gamitin sa dashboard
        intent.putExtra("USER_UID", uid)

        startActivity(intent)
        finish() // Prevent returning to the LoginActivity
    }

    // --- Utility functions for UI handling ---
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false
        forgotPasswordText.isEnabled = false
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true
        forgotPasswordText.isEnabled = true
    }

    private fun resetUI() {
        hideLoading()
    }
}
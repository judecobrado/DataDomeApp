package com.example.datadomeapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log // Added for debugging
import android.widget.Button
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.admin.AdminDashboardActivity
import com.example.datadomeapp.canteen.CanteenStaffDashboardActivity
import com.example.datadomeapp.enrollment.ChooseStudentTypeActivity
import com.example.datadomeapp.student.StudentDashboardActivity
import com.example.datadomeapp.teacher.TeacherDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // 1. IMPORT FIRESTORE

class MainActivity : AppCompatActivity() {

    private lateinit var btnLogin: Button
    private lateinit var btnSignup: Button
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance() // 2. INITIALIZE FIRESTORE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = auth.currentUser

        // KRITIKAL: Check if user is logged in
        if (currentUser != null) {
            // User is logged in, fetch their role and redirect
            fetchUserRoleAndStartDashboard(currentUser.uid)
            // Note: We don't call setContentView or finish() yet.
            // We'll call finish() inside fetchUserRoleAndStartDashboard() upon success.
            return
        }

        // Only show login/signup UI if no user is logged in
        setContentView(R.layout.activity_main)

        btnLogin = findViewById(R.id.btnLogin)
        btnSignup = findViewById(R.id.btnSignup)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, ChooseStudentTypeActivity::class.java))
        }

        // Add this inside onCreate(), after btnSignup is initialized
        firestore.collection("appSettings").document("mainActivity")
            .get()
            .addOnSuccessListener { doc ->
                val signupEnabled = doc?.getBoolean("signupEnabled") ?: true
                if (signupEnabled) {
                    btnSignup.visibility = View.VISIBLE
                    btnSignup.setOnClickListener {
                        startActivity(Intent(this, ChooseStudentTypeActivity::class.java))
                    }
                } else {
                    btnSignup.visibility = View.GONE // completely hides the button
                }
            }
            .addOnFailureListener { e ->
                // Default behavior if Firestore fails
                btnSignup.visibility = View.VISIBLE
                btnSignup.setOnClickListener {
                    startActivity(Intent(this, ChooseStudentTypeActivity::class.java))
                }
            }// Inside onCreate() AFTER setContentView(...)
        btnSignup = findViewById(R.id.btnSignup)

// Real-time listener for signup toggle
        firestore.collection("appSettings").document("mainActivity")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Firestore failed, default to showing button
                    btnSignup.visibility = View.VISIBLE
                    btnSignup.setOnClickListener {
                        startActivity(Intent(this, ChooseStudentTypeActivity::class.java))
                    }
                    return@addSnapshotListener
                }

                val signupEnabled = snapshot?.getBoolean("signupEnabled") ?: true
                if (signupEnabled) {
                    btnSignup.visibility = View.VISIBLE
                    btnSignup.setOnClickListener {
                        startActivity(Intent(this, ChooseStudentTypeActivity::class.java))
                    }
                } else {
                    btnSignup.visibility = View.GONE
                }
            }

    }






















    // --- New Logic: Same as LoginActivity's role check ---

    private fun fetchUserRoleAndStartDashboard(uid: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "unknown"
                Log.d("MainActivity", "Logged in user role: $role") // Debugging line

                // Save role to SharedPreferences (Good practice)
                getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                    .putString("role", role)
                    .apply()

                startDashboard(role)
            }
            .addOnFailureListener { e ->
                // Handle error if Firestore is unreachable or document lookup fails
                Toast.makeText(this, "Error: Could not retrieve user role.", Toast.LENGTH_LONG).show()
                auth.signOut()
                // Redirect to LoginActivity after sign out
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
    }

    private fun startDashboard(role: String) {
        // Use the same robust logic as in LoginActivity
        val intent = when (role.lowercase()) {
            "student" -> Intent(this, StudentDashboardActivity::class.java)
            "teacher" -> Intent(this, TeacherDashboardActivity::class.java)
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "canteen_staff" -> Intent(this, CanteenStaffDashboardActivity::class.java)
            else -> {
                // If role is unrecognized, log them out for security and consistency
                Toast.makeText(this, "Unrecognized role. Logging out.", Toast.LENGTH_LONG).show()
                auth.signOut()
                Intent(this, LoginActivity::class.java) // Redirect to login
            }
        }

        startActivity(intent)
        finish() // Crucial: Closes MainActivity so user can't press 'Back' to return here
    }
}
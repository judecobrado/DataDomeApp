package com.example.datadomeapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.net.Uri
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
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var btnLogin: Button
    private lateinit var btnSignup: Button
    // NEW: Declare the map button
    private lateinit var btnViewMap: Button

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "MainActivity" // Added for consistent logging

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KRITIKAL: Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserRoleAndStartDashboard(currentUser.uid)
            return
        }

        // Fetch location data early
        fetchSchoolLocation()

        // Only show login/signup UI if no user is logged in
        setContentView(R.layout.activity_main)

        btnLogin = findViewById(R.id.btnLogin)
        btnSignup = findViewById(R.id.btnSignup)

        // NEW: Initialize the map button
        btnViewMap = findViewById(R.id.btnViewMap)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // --- NEW: Add click listener for the map button ---
        btnViewMap.setOnClickListener {
            // Retrieve saved coordinates from SharedPreferences (or fallback to default)
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val latitude = prefs.getFloat("school_lat", 40.7259f).toDouble()
            val longitude = prefs.getFloat("school_lon", -74.0003f).toDouble()
            val label = prefs.getString("school_name", "DataDome School") ?: "DataDome School"

            // Build URI to launch Google Maps app
            val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }

            // Try to start Google Maps, fallback to browser if app not available
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
                startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        }

        // --------------------------------------------------

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
            }

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

    // Function to fetch and log the school location from Firestore
    private fun fetchSchoolLocation() {
        firestore.collection("appSettings").document("schoolLocation")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    val schoolName = document.getString("name") ?: "School"

                    if (latitude != null && longitude != null) {
                        Log.i(TAG, "School Location Data Fetched: $schoolName")
                        Log.i(TAG, "API Location Coordinates: Lat: $latitude, Lon: $longitude")

                        // Store in SharedPreferences for SchoolMapActivity to easily retrieve
                        getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                            .putFloat("school_lat", latitude.toFloat())
                            .putFloat("school_lon", longitude.toFloat())
                            .putString("school_name", schoolName)
                            .apply()
                    } else {
                        Log.w(TAG, "School location document exists but is missing 'latitude' or 'longitude' fields.")
                    }
                } else {
                    Log.w(TAG, "School location document (appSettings/schoolLocation) does not exist in Firestore.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to retrieve school location from Firestore: ", exception)
            }
    }

    // --- Existing Logic: Same as LoginActivity's role check ---

    private fun fetchUserRoleAndStartDashboard(uid: String) {
        // ... (existing implementation)
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "unknown"
                Log.d(TAG, "Logged in user role: $role")

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
        // ... (existing implementation)
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
        finish()
    }
}
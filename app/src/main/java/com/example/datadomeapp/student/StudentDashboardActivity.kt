package com.example.datadomeapp.student

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.UserLibraryActivity
import com.google.firebase.auth.FirebaseAuth

class StudentDashboardActivity : AppCompatActivity() {

    // ✅ Logout functionality is already correctly implemented in your original code.
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_student)

        // Set the welcome text
        val tvDashboard = findViewById<TextView>(R.id.tvDashboard)
        tvDashboard.text = "Welcome Student!"

        // Setup all feature buttons
        setupFeatureButtons()

        // Setup Logout button (already working)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            auth.signOut() // Logout from Firebase
            // Assuming LoginActivity exists for re-login
            val intent = Intent(this, LoginActivity::class.java)
            // These flags ensure the user can't press 'back' to get to the dashboard after logging out
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish() // Close the dashboard activity
        }
    }

    private fun setupFeatureButtons() {
        val features = listOf(
            R.id.btnAttendance,
            R.id.btnAssignments,
            R.id.btnOnlineClasses,
            R.id.btnNotes,
            R.id.btnToDoList,
            R.id.btnCanteen
            // R.id.btnLibrary is removed from this list to get its own click listener
        )

        // Set up the "Coming Soon!" Toast for most buttons
        for (buttonId in features) {
            findViewById<Button>(buttonId).setOnClickListener {
                val buttonText = findViewById<Button>(buttonId).text
                Toast.makeText(this, "$buttonText: Coming Soon!", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Specific setup for the Library button to navigate to LibraryActivity
        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            val intent = Intent(this, UserLibraryActivity::class.java)
            startActivity(intent)

        }
    }
}
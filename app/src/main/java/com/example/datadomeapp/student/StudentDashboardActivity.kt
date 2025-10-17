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

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_student)

        // Set the welcome text
        val tvDashboard = findViewById<TextView>(R.id.tvDashboard)
        tvDashboard.text = "Welcome Student!"

        // Setup all feature buttons
        setupFeatureButtons()

        // Setup Logout button
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupFeatureButtons() {
        val features = listOf(
            R.id.btnAttendance,
            R.id.btnAssignments,
            R.id.btnOnlineClasses,
            R.id.btnCanteen
            // 🛑 R.id.btnLibrary is NOT in the list below.
            // R.id.btnSchedule is also NOT in the list below.
        )

        // Set up the "Coming Soon!" Toast for the generic buttons
        for (buttonId in features) {
            findViewById<Button>(buttonId).setOnClickListener {
                val buttonText = findViewById<Button>(buttonId).text
                Toast.makeText(this, "$buttonText: Coming Soon!", Toast.LENGTH_SHORT).show()
            }
        }

        // -----------------------------------------------------------------
        // 🛑 NEW: Specific setup for Student Schedule (Assuming ID: btnSchedule)
        // -----------------------------------------------------------------
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            val intent = Intent(this, StudentScheduleActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnNotes).setOnClickListener {
            val intent = Intent(this, StudentNotesActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnToDoList).setOnClickListener {
            val intent = Intent(this, StudentToDoListActivity::class.java)
            startActivity(intent)
        }

        // Specific setup for the Library button
        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            val intent = Intent(this, UserLibraryActivity::class.java)
            startActivity(intent)
        }
    }
}
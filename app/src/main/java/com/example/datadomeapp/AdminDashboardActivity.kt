package com.example.datadomeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_admin)

        // Manage Students
        findViewById<Button>(R.id.btnManageStudents).setOnClickListener {
            startActivity(Intent(this, ManageStudentsActivity::class.java))
        }

        // Manage Teachers
        findViewById<Button>(R.id.btnManageTeachers).setOnClickListener {
            startActivity(Intent(this, ManageTeachersActivity::class.java))
        }

        // Manage Canteen Staff
        findViewById<Button>(R.id.btnManageCanteenStaff).setOnClickListener {
            startActivity(Intent(this, ManageCanteenStaffActivity::class.java))
        }

        // Manage Courses
        findViewById<Button>(R.id.btnManageCourses).setOnClickListener {
            startActivity(Intent(this, ManageCoursesActivity::class.java))
        }

        // Manage Library
        findViewById<Button>(R.id.btnManageLibrary).setOnClickListener {
            startActivity(Intent(this, ManageLibraryActivity::class.java))
        }

        // Logout button
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut() // Firebase sign out
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish() // Prevent back button returning here
        }
    }
}

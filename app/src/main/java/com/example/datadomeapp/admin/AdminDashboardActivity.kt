package com.example.datadomeapp.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_dashboard)

        // Existing buttons...
        //findViewById<Button>(R.id.btnManageStudents).setOnClickListener {
        //    startActivity(Intent(this, ManageStudentsActivity::class.java))
        //}
        findViewById<Button>(R.id.btnManageEnrollment).setOnClickListener {
            startActivity(Intent(this, ManageEnrollmentsActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageTeachers).setOnClickListener {
            startActivity(Intent(this, ManageTeachersActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageCanteenStaff).setOnClickListener {
            startActivity(Intent(this, ManageCanteenStaffActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageCourses).setOnClickListener {
            startActivity(Intent(this, ManageCoursesActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageLibrary).setOnClickListener {
            startActivity(Intent(this, ManageLibrary::class.java))
        }

        // 🛑 NEW: Curriculum and Schedule Management
        findViewById<Button>(R.id.btnManageCurriculum).setOnClickListener {
            startActivity(Intent(this, ManageCurriculumActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageSchedules).setOnClickListener {
            startActivity(Intent(this, ManageSchedulesActivity::class.java))
        }

        // Logout button
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}
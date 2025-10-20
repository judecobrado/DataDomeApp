package com.example.datadomeapp.teacher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth

// ✅ NEW: Import ang mga bagong Activity (Assumed na gagawin mo ang mga ito)

class TeacherDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private var teacherUid: String? = null // Para i-store ang UID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tacher_dashboard)

        // Kukunin ang UID ng teacher na naka-login
        teacherUid = auth.currentUser?.uid

        val tvDashboard = findViewById<TextView>(R.id.tvDashboard)
        // ✅ Personalize ang Welcome message (Optional: Pwede ring i-load ang pangalan mula sa Firestore)
        tvDashboard.text = "Welcome, Teacher!"

        // --- CORE FEATURES ---

        // 1. Manage Classes/Sections Button (Para makita ang listahan ng mga klase)
        val btnManageClasses = findViewById<Button>(R.id.btnManageClasses)
        btnManageClasses.setOnClickListener {
            // ✅ Dapat mag-filter ang Activity na ito ng classes gamit ang teacherUid
            val intent = Intent(this, ManageClassesActivity::class.java)
            startActivity(intent)
        }

        // 2. Record Attendance Button
        val btnRecordAttendance = findViewById<Button>(R.id.btnMySchedule)
        btnRecordAttendance.setOnClickListener {
            val intent = Intent(this, TeacherScheduleMatrixActivity::class.java)
            startActivity(intent)
        }

        // 3. Manage Grades Button
        val btnManageGrades = findViewById<Button>(R.id.btnManageGrades)
        btnManageGrades.setOnClickListener {
            val intent = Intent(this, ManageGradesActivity::class.java)
            startActivity(intent)
        }

        // 4. Voice Detection Button (Nasa original code)
        val btnVoiceDetection = findViewById<Button>(R.id.btnVoiceDetection)

        btnVoiceDetection.setOnClickListener {
            val intent = Intent(this, VoiceDetectionActivity::class.java)
            startActivity(intent)
        }

        // 5. Logout Button (Nasa original code)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            auth.signOut() // Logout from Firebase
            val intent = Intent(this, LoginActivity::class.java)
            // Nililinis ang back stack para hindi na makabalik sa dashboard
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}
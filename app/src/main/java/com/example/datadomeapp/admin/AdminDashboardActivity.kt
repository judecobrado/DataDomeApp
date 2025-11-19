package com.example.datadomeapp.admin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // UI Components for quick overview
    private lateinit var tvStudentCount: TextView
    private lateinit var tvTeacherCount: TextView
    private lateinit var cardStudents: MaterialCardView
    private lateinit var cardTeachers: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_dashboard)

        // Initialize quick overview components
        initializeQuickOverview()

        // Load real-time counts
        loadStudentCount()
        loadTeacherCount()

        // Setup animations
        setupAnimations()

        // Existing button click listeners
        setupButtonClickListeners()
    }

    private fun initializeQuickOverview() {
        tvStudentCount = findViewById(R.id.tvStudentCount)
        tvTeacherCount = findViewById(R.id.tvTeacherCount)
        cardStudents = findViewById(R.id.cardStudents)
        cardTeachers = findViewById(R.id.cardTeachers)

        // Set click listeners for quick overview cards
        cardStudents.setOnClickListener {
            startActivity(Intent(this, ManageStudentsActivity::class.java))
        }

        cardTeachers.setOnClickListener {
            startActivity(Intent(this, ManageTeachersActivity::class.java))
        }
    }

    private fun setupAnimations() {
        try {
            // Welcome card animation
            val welcomeCard = findViewById<MaterialCardView>(R.id.cardWelcome)
            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            welcomeCard.startAnimation(fadeIn)

            // Quick overview cards animation with delay
            Handler(Looper.getMainLooper()).postDelayed({
                val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
                cardStudents.startAnimation(slideUp)
                cardTeachers.startAnimation(slideUp)
            }, 200)

            // Management tools animation with staggered delay
            val managementButtons = listOf(
                R.id.btnManageStudents,
                R.id.btnManageTeachers,
                R.id.btnManageEnrollment,
                //R.id.btnManageCourses,
                R.id.btnManageSchedules,
                R.id.btnManageCurriculum,
                //R.id.btnManageCanteenStaff
            )

            managementButtons.forEachIndexed { index, buttonId ->
                Handler(Looper.getMainLooper()).postDelayed({
                    val button = findViewById<MaterialCardView>(buttonId)
                    val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
                    button.startAnimation(slideUp)
                }, 400 + (index * 50L)) // Staggered animation
            }
        } catch (e: Exception) {
            // Handle animation errors gracefully
            e.printStackTrace()
        }
    }

    private fun loadStudentCount() {
        firestore.collection("users")
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val count = querySnapshot.documents.size.toString()
                tvStudentCount.text = count

                // Add count change animation
                try {
                    val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                    tvStudentCount.startAnimation(scaleAnimation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                tvStudentCount.text = "0"
            }
    }

    private fun loadTeacherCount() {
        firestore.collection("users")
            .whereEqualTo("role", "teacher")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val count = querySnapshot.documents.size.toString()
                tvTeacherCount.text = count

                // Add count change animation
                try {
                    val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                    tvTeacherCount.startAnimation(scaleAnimation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                tvTeacherCount.text = "0"
            }
    }

    private fun setupButtonClickListeners() {
        // Existing button listeners
        findViewById<MaterialCardView>(R.id.btnManageStudents).setOnClickListener {
            animateButtonClick(it) {
                startActivity(Intent(this, ManageStudentsActivity::class.java))
            }
        }

        findViewById<MaterialCardView>(R.id.btnManageTeachers).setOnClickListener {
            animateButtonClick(it) {
                startActivity(Intent(this, ManageTeachersActivity::class.java))
            }
        }

        findViewById<MaterialCardView>(R.id.btnManageEnrollment).setOnClickListener {
            animateButtonClick(it) {
                startActivity(Intent(this, ManageEnrollmentsActivity::class.java))
            }
        }

        findViewById<MaterialCardView>(R.id.btnManageCourses).setOnClickListener {
            animateButtonClick(it) {
                startActivity(Intent(this, ManageCoursesActivity::class.java))
            }
        }

        //findViewById<MaterialCardView>(R.id.btnManageLibrary).setOnClickListener {
            //animateButtonClick(it) {
                //startActivity(Intent(this, ManageLibrary::class.java))
            //}
        //}

        findViewById<MaterialCardView>(R.id.btnManageCurriculum).setOnClickListener {
            animateButtonClick(it) {
                startActivity(Intent(this, ManageCurriculumActivity::class.java))
            }
        }

        findViewById<MaterialCardView>(R.id.btnManageSchedules).setOnClickListener {
            animateButtonClick(it) {
                startActivity(Intent(this, ManageSchedulesActivity::class.java))
            }
        }

        //findViewById<MaterialCardView>(R.id.btnManageCanteenStaff).setOnClickListener {
            //animateButtonClick(it) {
                //startActivity(Intent(this, ManageCanteenStaffActivity::class.java))
            //}
        //}

        // Logout button with animation
        findViewById<MaterialCardView>(R.id.btnLogout).setOnClickListener {
            animateButtonClick(it) {
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun animateButtonClick(view: View, action: () -> Unit) {
        // Scale down animation
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                // Scale back up and execute action
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction {
                        action()
                    }
            }
    }

    override fun onResume() {
        super.onResume()
        // Refresh counts when returning to dashboard
        loadStudentCount()
        loadTeacherCount()
    }
}
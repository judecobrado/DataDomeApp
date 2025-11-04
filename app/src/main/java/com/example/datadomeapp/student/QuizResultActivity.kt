package com.example.datadomeapp.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.datadomeapp.R
import com.example.datadomeapp.databinding.ActivityQuizResultBinding

class QuizResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Kunin ang lahat ng data mula sa Intent
        val rawScore = intent.getIntExtra("SCORE", 0)
        val totalQuestions = intent.getIntExtra("TOTAL_QUESTIONS", 0)
        val cheatCount = intent.getIntExtra("CHEAT_COUNT", 0)
        val quizId = intent.getStringExtra("QUIZ_ID") ?: "" // Kritikal para sa navigation

        // ⭐ CRITICAL: Kunin ang result type mula sa StudentQuizListActivity
        val resultType = intent.getStringExtra("RESULT_TYPE") ?: "ATTEMPTED"

        // Check for MISSED or REVOKED Status first
        if (resultType == "MISSED" || resultType == "REVOKED") {
            handleNonAttemptStatus(resultType, quizId)
            return // Tapusin ang function dito
        }

        // --- ATTEMPTED/PASSED/FAILED LOGIC ---

        // Define the minimum passing threshold for the TRANSMUTED score
        val requiredPassingScore = 75.0

        // 1. Calculate the Transmuted Score (Equivalent Grade)
        val rawTransmutedScore: Double = if (totalQuestions > 0) {
            // Formula: (Raw Score / Total Items) * 50 + 50
            (rawScore.toDouble() / totalQuestions.toDouble()) * 50.0 + 50.0
        } else 50.0

        // 2. Determine the Final Displayed Score (Percentage)
        val percentage: Double
        val isFailedDueToCheating = cheatCount >= 5

        if (isFailedDueToCheating) {
            // Override: Cheating penalty (Grade set to 50, using the 75-100 scale analogy)
            percentage = 50.0
        } else {
            percentage = rawTransmutedScore
        }

        // 3. Determine Pass/Fail Status and Failure Reason Category
        val isPassed = (percentage >= requiredPassingScore) && !isFailedDueToCheating
        val failureCategory = when {
            isFailedDueToCheating -> "CHEATING_FAIL"
            percentage < requiredPassingScore -> "SCORE_FAIL"
            else -> "OTHER"
        }

        // --- Display Results ---

        binding.tvFinalScore.text = "Raw Score: $rawScore / $totalQuestions"
        binding.tvPercentage.text = String.format("Transmuted Grade: %.2f", percentage)
        binding.tvCheatCount.text = "Cheat Attempts: $cheatCount"

        // Display the Pass/Fail Status
        if (isPassed) {
            binding.tvStatus.text = "Status: PASSED ✅ (Grade $\\ge$ ${requiredPassingScore}%)"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            // Determine reason for failure for display
            val failureReasonText = when (failureCategory) {
                "CHEATING_FAIL" -> "FAILED ❌ (Max cheats reached, Grade set to 50)"
                "SCORE_FAIL" -> "FAILED ❌ (Grade $<$ ${requiredPassingScore}%)"
                else -> "FAILED ❌"
            }

            binding.tvStatus.text = "Status: $failureReasonText"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }

        // Apply visual warnings for cheating
        if (isFailedDueToCheating) {
            binding.tvCheatCount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.tvCheatCount.text = "Cheat Attempts: $cheatCount (Max attempts reached, Grade set to 50)"
        } else if (cheatCount > 0) {
            binding.tvCheatCount.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }

        // --- DYNAMIC BUTTON LOGIC (Added) ---
        when {
            isPassed -> {
                binding.btnFinish.text = "Go to Dashboard 🏠"
                binding.btnFinish.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                binding.btnFinish.setOnClickListener { navigateToDashboard() }
            }
            failureCategory == "SCORE_FAIL" -> {
                binding.btnFinish.text = "View Reviewer 📖"
                binding.btnFinish.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
                binding.btnFinish.setOnClickListener { navigateToReviewer(quizId) }
            }
            failureCategory == "CHEATING_FAIL" -> {
                binding.btnFinish.text = "Contact Teacher 📧"
                binding.btnFinish.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                binding.btnFinish.setOnClickListener { contactTeacher() }
            }
            else -> {
                binding.btnFinish.text = "Go to Dashboard 🏠"
                binding.btnFinish.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                binding.btnFinish.setOnClickListener { navigateToDashboard() }
            }
        }
    }

    // --- Helper Functions ---

    private fun handleNonAttemptStatus(resultType: String, quizId: String) {
        val statusText = when (resultType) {
            "MISSED" -> "MISSED QUIZ 🙁"
            "REVOKED" -> "ACCESS REVOKED 🔒"
            else -> "STATUS UNAVAILABLE"
        }

        val explanationText = when (resultType) {
            "MISSED" -> "The quiz deadline has expired without an attempt. Your teacher has been notified."
            "REVOKED" -> "Your access to this quiz was revoked by the teacher."
            else -> "Please check with your teacher."
        }

        // Update main result fields with non-score information
        binding.tvFinalScore.text = "Quiz ID: $quizId"
        binding.tvPercentage.text = "Details: $explanationText"
        binding.tvCheatCount.visibility = View.GONE // I-hide ang cheat count

        // Set status and color
        binding.tvStatus.text = "Status: $statusText"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        // Set Button to Go to Dashboard
        binding.btnFinish.text = "Go to Dashboard 🏠"
        binding.btnFinish.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
        binding.btnFinish.setOnClickListener { navigateToDashboard() }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, StudentDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToReviewer(quizId: String) {
        Toast.makeText(this, "Opening Reviewer for Quiz ID: $quizId", Toast.LENGTH_SHORT).show()
        // TODO: Launch StudentReviewerActivity (or relevant review page)
    }

    private fun contactTeacher() {
        Toast.makeText(this, "Opening contact form/email for teacher", Toast.LENGTH_SHORT).show()
        // TODO: Launch Contact Teacher Activity/Intent
    }

    // Prevent going back to the quiz from the result screen via the system back button
    override fun onBackPressed() {
        navigateToDashboard()
    }
}
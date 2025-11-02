package com.example.datadomeapp.student

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.databinding.ActivityQuizResultBinding
import com.example.datadomeapp.student.StudentDashboardActivity

class QuizResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rawScore = intent.getIntExtra("SCORE", 0) // Raw score from ViewModel
        val totalQuestions = intent.getIntExtra("TOTAL_QUESTIONS", 0)
        val cheatCount = intent.getIntExtra("CHEAT_COUNT", 0)

        // Define the minimum passing threshold for the TRANSMUTED score
        val requiredPassingScore = 75.0

        // 1. Calculate the Transmuted Score (Equivalent Grade)
        val rawTransmutedScore: Double = if (totalQuestions > 0) {
            // Formula: (Raw Score / Total Items) * 50 + 50
            (rawScore.toDouble() / totalQuestions.toDouble()) * 50.0 + 50.0
        } else 50.0 // Default to 50 if no questions, preventing division by zero

        // 2. Determine the Final Displayed Score (Percentage)
        val percentage: Double
        val isFailedDueToCheating = cheatCount >= 5

        if (isFailedDueToCheating) {
            // Override: Cheating penalty is absolute (0% final grade)
            percentage = 50.0
        } else {
            // Use the calculated transmuted score
            percentage = rawTransmutedScore
        }

        // 3. Determine Pass/Fail Status
        // A passing status requires the TRANSMUTED score to be 75.0 or higher AND no max cheats.
        val isPassed = (percentage >= requiredPassingScore) && !isFailedDueToCheating

        // Note: We use the raw score for display (e.g., 25/50) but the transmuted score for percentage/status.

        // --- Display Results ---

        // Display raw score, but calculated percentage
        binding.tvFinalScore.text = "Raw Score: $rawScore / $totalQuestions"
        binding.tvPercentage.text = String.format("Transmuted Grade: %.2f", percentage)
        binding.tvCheatCount.text = "Cheat Attempts: $cheatCount"

        // Display the Pass/Fail Status
        if (isPassed) {
            binding.tvStatus.text = "Status: PASSED (Grade $\\ge$ ${requiredPassingScore}%)"
            binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            // Determine reason for failure for display
            val failureReason = when {
                isFailedDueToCheating -> "FAILED (Max cheats reached, Grade set to 0)"
                percentage < requiredPassingScore -> "FAILED (Grade $<$ ${requiredPassingScore}%)"
                else -> "FAILED" // Fallback
            }

            binding.tvStatus.text = "Status: $failureReason"
            binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }

        // Apply visual warnings for cheating
        if (isFailedDueToCheating) {
            // Severe penalty message
            binding.tvCheatCount.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            binding.tvCheatCount.text = "Cheat Attempts: $cheatCount (Max attempts reached, Grade set to 0)"
        } else if (cheatCount > 0) {
            // Minor warning color change
            binding.tvCheatCount.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        // --- Navigation ---

        // Handle back to home button
        binding.btnFinish.setOnClickListener {
            val intent = Intent(this, StudentDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    // Prevent going back to the quiz from the result screen via the system back button
    override fun onBackPressed() {
        val intent = Intent(this, StudentDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
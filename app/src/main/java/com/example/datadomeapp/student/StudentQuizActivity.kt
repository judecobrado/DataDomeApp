package com.example.datadomeapp.student

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.databinding.ActivityStudentQuizBinding
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz

class StudentQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentQuizBinding

    // 1. Initialize ViewModel using a Factory
    private val quiz: Quiz by lazy {
        requireNotNull(intent.getParcelableExtra<Quiz>("QUIZ")) {
            // This message will be shown if the required "QUIZ" extra is missing.
            "FATAL: Missing 'QUIZ' extra in Intent. StudentQuizActivity cannot start."
        }
    }

    // 2. Pass the safely initialized, non-nullable 'quiz' object to the ViewModel
    private val viewModel: StudentQuizViewModel by viewModels {
        StudentQuizViewModelFactory(quiz)
    }

    private fun requestDndPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please grant 'Do Not Disturb' access to start the quiz.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetDoNotDisturb() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Check if we have permission before attempting to change the setting
        if (notificationManager.isNotificationPolicyAccessGranted) {
            // Set back to INTERRUPTION_FILTER_ALL (all notifications allowed)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure DND is turned off when the quiz activity is finished
        resetDoNotDisturb()
    }

    private fun setDoNotDisturb() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            // This is equivalent to Total Silence on some Android versions.
            // You might need to experiment with INTERRUPTION_FILTER_ALARMS or INTERRUPTION_FILTER_PRIORITY
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            // You should also have logic to turn it back on in onPause/onDestroy!
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStudentQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleCheatAttempt("Back gesture detected")
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

        // **Anti-Cheating / Security Measures**
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // 2. Setup Observers
        setupLiveDataObservers()

        // 3. Start the quiz flow
        showTermsAndConditions()
    }

    // --- Observer Setup and UI Listeners ---

    private fun setupLiveDataObservers() {
        // Observe timer changes
        viewModel.timerText.observe(this) { time ->
            binding.tvTimer.text = time
        }

        // Observe current question changes
        viewModel.currentQuestion.observe(this) { question ->
            showQuestion(question, viewModel.currentQuestionIndex.value ?: 0)
        }

        // Observe UI messages
        viewModel.uiMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Observe quiz finish signal
        viewModel.quizFinished.observe(this) { finished ->
            if (finished) {
                Toast.makeText(this, "Quiz Finished!", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // Setup Submit/Next Button Listener
        binding.btnSubmit.setOnClickListener {
            val currentQuestion = viewModel.currentQuestion.value
            val currentIndex = viewModel.currentQuestionIndex.value!!

            if (currentQuestion is Question.Matching) {
                val answerPairs = mutableListOf<String>()
                for (i in 0 until binding.matchingLayout.childCount) {
                    val row = binding.matchingLayout.getChildAt(i) as LinearLayout
                    val left = (row.getChildAt(0) as TextView).text.toString()
                    val selected = (row.getChildAt(1) as Spinner).selectedItem.toString()
                    answerPairs.add("$left=$selected")
                }
                viewModel.recordMatchingAnswers(currentIndex, answerPairs)
            }

            // Tell the ViewModel to advance or submit
            viewModel.nextQuestion()
        }
    }

    // --- Core Quiz Flow ---

    private fun showTermsAndConditions() {
        AlertDialog.Builder(this)
            .setTitle("Terms and Conditions")
            .setMessage(
                "1. You cannot leave the quiz screen.\n" +
                        "2. Screenshots, recording, or multi-window count as cheat attempts.\n" +
                        "3. Max 5 cheat attempts; exceeding = 0 score.\n" +
                        "4. Quiz will auto-submit when time is up.\n" +
                        "5. All answers are logged in real-time."
            )
            .setCancelable(false)
            .setPositiveButton("I Agree") { _, _ ->
                requestDndPermission()

                setDoNotDisturb()

                viewModel.fetchServerTime()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }

    // NOTE: The broken shuffleQuestionsAndAnswers() function is DELETED.

    private fun showQuestion(q: Question, index: Int) {
        binding.tvQuestionText.text = q.questionText

        // Hide all option buttons and clear the matching layout before drawing
        val optionButtons = listOf(binding.btnOption1, binding.btnOption2, binding.btnOption3, binding.btnOption4)
        optionButtons.forEach { it.visibility = View.GONE }
        binding.matchingLayout.removeAllViews()

        when (q) {
            is Question.MultipleChoice -> {
                optionButtons.forEachIndexed { i, btn ->
                    if (i < q.options.size) {
                        btn.visibility = View.VISIBLE
                        btn.text = q.options[i]
                        btn.setBackgroundColor(Color.parseColor("#FFBB33"))
                        // Answer recording calls ViewModel directly
                        btn.setOnClickListener { viewModel.recordAnswer(index, q.options[i]) }
                    }
                }
            }

            is Question.TrueFalse -> {
                binding.btnOption1.apply {
                    visibility = View.VISIBLE
                    text = "TRUE"
                    setBackgroundColor(Color.parseColor("#FFBB33"))
                    setOnClickListener { viewModel.recordAnswer(index, "true") }
                }
                binding.btnOption2.apply {
                    visibility = View.VISIBLE
                    text = "FALSE"
                    setBackgroundColor(Color.parseColor("#FFBB33"))
                    setOnClickListener { viewModel.recordAnswer(index, "false") }
                }
            }

            is Question.Matching -> {
                // The right side terms are randomized for presentation
                val shuffledRights = q.matches.shuffled()

                // q.options (the left terms) are already shuffled in the ViewModel
                q.options.forEach { leftText ->
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, 10, 0, 10)

                    val tvLeft = TextView(this)
                    tvLeft.text = leftText
                    tvLeft.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val spinner = Spinner(this)
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, shuffledRights)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapter
                    spinner.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    row.addView(tvLeft)
                    row.addView(spinner)
                    binding.matchingLayout.addView(row)
                }
            }
        }
    }

    // NOTE: All redundant logic methods (recordAnswer, recordMatchingAnswers, submitQuiz, calculateScore) are DELETED.

    // --- Anti-Cheating Handlers (Calls ViewModel) ---
    private fun handleCheatAttempt(reason: String) {
        viewModel.handleCheatAttempt(reason)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()  // ✅ call super
        handleCheatAttempt("Switched app or pressed home button")
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)  // ✅ call super
        if (isInMultiWindowMode) handleCheatAttempt("Entered multi-window mode")
    }

    override fun onPause() {
        super.onPause()
        handleCheatAttempt("App paused")
    }
}
package com.example.datadomeapp.student

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.example.datadomeapp.student.StudentQuizViewModel.QuizResultData
import android.content.pm.ActivityInfo
import android.widget.AdapterView
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.databinding.ActivityStudentQuizBinding
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import android.content.res.Configuration

class StudentQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentQuizBinding
    private var cheatOverlay: View? = null

    private var spinnerActive = false
    private val prefs by lazy { getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE) }
    private val quiz: Quiz by lazy {
        requireNotNull(intent.getParcelableExtra<Quiz>("QUIZ")) {
            "FATAL: Missing 'QUIZ' extra in Intent. StudentQuizActivity cannot start."
        }
    }


    private var termsAccepted: Boolean
        get() = prefs.getBoolean("termsAccepted_${quiz.quizId}", false)
        set(value) = prefs.edit().putBoolean("termsAccepted_${quiz.quizId}", value).apply()


    private val viewModel: StudentQuizViewModel by viewModels {
        StudentQuizViewModelFactory(application, quiz)
    }

    private var quizFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityStudentQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lock orientation & prevent screenshots
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Disable Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@StudentQuizActivity, "Back is disabled during the quiz", Toast.LENGTH_SHORT).show()
            }
        })

        setupLiveDataObservers()


        if (termsAccepted) {
            lockQuizScreen()
            requestDndPermission()
            viewModel.fetchServerTime()
        } else {
            showTermsAndConditions()
        }
    }

    // --- Anti-Cheat Functions ---

    private fun lockQuizScreen() {
        startScreenPinning()
        setDoNotDisturb()
    }

    private fun startScreenPinning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask()
                Toast.makeText(
                    this,
                    "Screen pinned. Leaving the quiz will count as cheating.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Screen pinning failed. Security may be compromised.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setDoNotDisturb() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    override fun onResume() {
        super.onResume()

        if (cheatOverlay != null) {
        }
    }


    private fun resetDoNotDisturb() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }


    private fun setupMatchingSpinners() {
        for (i in 0 until binding.matchingLayout.childCount) {
            val row = binding.matchingLayout.getChildAt(i) as LinearLayout
            (row.getChildAt(1) as? Spinner)?.let { spinner ->
                spinner.setOnTouchListener { _, _ ->
                    spinnerActive = true
                    false
                }
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        spinnerActive = false
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        spinnerActive = false
                    }
                }
            }
        }
    }

    private fun isSpinnerOpen(): Boolean {
        return spinnerActive
    }

    private fun showCheatOverlay(reason: String) {
        if (cheatOverlay != null) return

        cheatOverlay = View(this).apply {
            setBackgroundColor(Color.parseColor("#AA000000")) // semi-transparent black
            isClickable = true
            isFocusable = true
        }

        addContentView(
            cheatOverlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun removeCheatOverlay() {
        cheatOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            cheatOverlay = null
        }
    }

    private fun resetQuiz() {
        Toast.makeText(this, "Quiz restarted due to cheating.", Toast.LENGTH_LONG).show()
        removeCheatOverlay()
        viewModel.resetQuizProgress() // we'll define this in ViewModel
        viewModel.fetchServerTime()   // restart timer from server
    }

    override fun onDestroy() {
        super.onDestroy()
        resetDoNotDisturb()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!quizFinished) {
            if (!hasFocus && !isSpinnerOpen()) {
                viewModel.handleCheatAttempt("Notification shade / overlay detected")
            } else if (hasFocus) {
                viewModel.resetCheat()
            }
        }
    }


    override fun onPause() {
        super.onPause()

        if (!quizFinished) {
            if (!isSpinnerOpen()) {
                viewModel.handleCheatAttempt("App backgrounded / Home button pressed")
            }
        }
    }


    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode) {
            viewModel.handleCheatAttempt("Entered multi-window mode")
        } else {
            viewModel.resetCheat()
        }
    }

    // --- Quiz Flow ---

    private fun setupLiveDataObservers() {
        viewModel.timerText.observe(this) { binding.tvTimer.text = it }
        viewModel.currentQuestion.observe(this) { showQuestion(it, viewModel.currentQuestionIndex.value ?: 0) }
        viewModel.uiMessage.observe(this) { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }

        // NEW: Observe for QuizResultData to launch the result screen
        viewModel.quizResultData.observe(this) { resultData ->
            if (resultData != null) {
                quizFinished = true
                // 1. Release security features
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    stopLockTask() // Release screen pinning
                }
                resetDoNotDisturb()

                // 2. Launch QuizResultActivity
                val intent = Intent(this, QuizResultActivity::class.java).apply {
                    putExtra("SCORE", resultData.score)
                    putExtra("TOTAL_QUESTIONS", resultData.totalQuestions)
                    putExtra("CHEAT_COUNT", resultData.cheatCount)
                    // ⭐ KRITIKAL: Ipasa ang RESULT_TYPE = "ATTEMPTED"
                    putExtra("RESULT_TYPE", "ATTEMPTED")
                    // Add FLAG_ACTIVITY_CLEAR_TOP to prevent going back to the quiz
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(intent)
                finish() // Remove this activity from the back stack
            }
        }

        // Removed the old quizFinished observer logic, as it is now handled by quizResultData.

        viewModel.isCheating.observe(this) { cheating ->
            if (cheating) {
                if (cheatOverlay == null) showCheatOverlay("Cheating detected!")
            } else {
                removeCheatOverlay()
            }
        }

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
            viewModel.nextQuestion()
            spinnerActive = false
        }
    }

    private fun showTermsAndConditions() {
        if (termsAccepted) return

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
                termsAccepted = true   // mark agreed
                lockQuizScreen()
                requestDndPermission()
                viewModel.fetchServerTime()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun showQuestion(q: Question, index: Int) {
        val questionType: String

        when (q) {
            is Question.MultipleChoice -> questionType = "Question Type: Multiple Choice"
            is Question.TrueFalse -> questionType = "Question Type: True or False"
            is Question.Matching -> questionType = "Question Type: Matching Type"
        }

        binding.tvQuestionType.text = questionType

        binding.tvQuestionText.text = "${index + 1}. ${q.questionText}"
        val optionButtons = listOf(binding.btnOption1, binding.btnOption2, binding.btnOption3, binding.btnOption4)
        optionButtons.forEach { it.visibility = View.GONE }
        binding.matchingLayout.removeAllViews()

        val recordedAnswer = viewModel.getRecordedAnswer(index)

        // Define colors
        val defaultColor = Color.parseColor("#FFBB33")
        val selectedColor = Color.parseColor("#33A2FF")

        // NEW T/F Colors
        val trueDefaultColor = Color.parseColor("#4CAF50")
        val falseDefaultColor = Color.parseColor("#F44336")
        val tfSelectedColor = Color.parseColor("#008000")

        when (q) {
            is Question.MultipleChoice -> {
                optionButtons.forEachIndexed { i, btn ->
                    if (i < q.options.size) {
                        val optionText = q.options[i]
                        btn.visibility = View.VISIBLE
                        btn.text = optionText

                        // Logic: Light Blue kung napili, Orange kung hindi
                        if (recordedAnswer == optionText) {
                            btn.setBackgroundColor(selectedColor)
                        } else {
                            btn.setBackgroundColor(defaultColor)
                        }

                        // OnClick: I-record ang sagot at i-reload ang tanong para sa UI update
                        btn.setOnClickListener {
                            viewModel.recordAnswer(index, optionText)
                            showQuestion(q, index)
                        }
                    }
                }
            }

            is Question.TrueFalse -> {
                // TRUE Button
                binding.btnOption1.apply {
                    visibility = View.VISIBLE
                    text = "TRUE"
                    val answer = "true"

                    // Logic: Dark Green (selected) o Light Green (default)
                    if (recordedAnswer == answer) {
                        setBackgroundColor(tfSelectedColor)
                    } else {
                        setBackgroundColor(trueDefaultColor)
                    }

                    setOnClickListener {
                        viewModel.recordAnswer(index, answer)
                        showQuestion(q, index)
                    }
                }

                // FALSE Button
                binding.btnOption2.apply {
                    visibility = View.VISIBLE
                    text = "FALSE"
                    val answer = "false"

                    // Logic: Dark Green (selected) o Red (default)
                    if (recordedAnswer == answer) {
                        setBackgroundColor(tfSelectedColor)
                    } else {
                        setBackgroundColor(falseDefaultColor)
                    }

                    setOnClickListener {
                        viewModel.recordAnswer(index, answer)
                        showQuestion(q, index)
                    }
                }
            }

            is Question.Matching -> {
                val shuffledRights = q.matches.shuffled()

                q.options.forEach { leftText ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,10,0,10) }
                    val tvLeft = TextView(this).apply {
                        text = leftText
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val spinner = Spinner(this).apply {
                        val adapter = ArrayAdapter(this@StudentQuizActivity, android.R.layout.simple_spinner_item, shuffledRights)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        this.adapter = adapter
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(tvLeft)
                    row.addView(spinner)
                    binding.matchingLayout.addView(row)
                }
                setupMatchingSpinners()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Check if the detected configuration is different from the portrait mode we locked it to.
        // Since the orientation is locked in onCreate, any change event is suspicious.
        // We specifically check for landscape (or undefined) if it attempts to change.
        if (newConfig.orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            // Log the cheat attempt
            viewModel.handleCheatAttempt("Screen orientation was changed.")
        }
    }

    private fun requestDndPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant 'Do Not Disturb' access to start the quiz.", Toast.LENGTH_LONG).show()
        }
    }
}

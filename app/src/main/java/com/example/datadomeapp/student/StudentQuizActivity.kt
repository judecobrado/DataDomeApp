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
    private var homeButtonPressed = false
    private var isRequestingDndPermission = false
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
            if (hasExistingQuizState()) {
                showRestoreDialog()
            } else {
                lockQuizScreen()
                requestDndPermission()
            }
        } else {
            showTermsAndConditions()
        }
    }


    // --- Check for existing quiz state ---
    private fun hasExistingQuizState(): Boolean {
        val progressPrefs = getSharedPreferences("quiz_progress", Context.MODE_PRIVATE)
        return progressPrefs.getLong("start_time_${quiz.quizId}", 0L) > 0L
    }

    private fun showRestoreDialog() {
        // Check if quiz time has expired
        if (viewModel.isQuizTimeExpired()) {
            AlertDialog.Builder(this)
                .setTitle("Quiz Time Expired")
                .setMessage("The quiz time has ended. Your answers will be automatically submitted.")
                .setPositiveButton("OK") { _, _ ->
                    // Force auto-submit
                    viewModel.forceAutoSubmit()
                }
                .setCancelable(false)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Restore Quiz Progress")
                .setMessage("We found an unfinished quiz attempt. Do you want to continue where you left off?")
                .setPositiveButton("Continue") { _, _ ->
                    // Continue with restored state
                    lockQuizScreen()
                    requestDndPermission()
                }
                .setCancelable(false)
                .show()
        }
    }

    // --- Anti-Cheat Functions ---

    private fun lockQuizScreen() {
        startScreenPinning()
        setDoNotDisturb()
    }

    private fun startQuizAttempt() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 🚨 CRITICAL: Check if DND is granted before starting quiz
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "DND permission not granted. Cannot start quiz.", Toast.LENGTH_LONG).show()
            return
        }

        // Only proceed if DND is granted
        viewModel.fetchServerTime()
        viewModel.startQuizTracking()
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

        if (isRequestingDndPermission) {
            isRequestingDndPermission = false
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.isNotificationPolicyAccessGranted) {
                // DND granted, start quiz
                startQuizAttempt()
            } else {
                // DND not granted, show blocking dialog
                AlertDialog.Builder(this)
                    .setTitle("DND Permission Required")
                    .setMessage("You must grant Do Not Disturb permission to continue with the quiz. Please restart the app.")
                    .setPositiveButton("Exit") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
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
        removeCheatOverlay()

        viewModel.resetQuizProgress(keepCheatCount = true)

        viewModel.currentQuestion.value?.let {
            showQuestion(it, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!quizFinished) {
            // I-save ang current cheat count sa SharedPreferences
            viewModel.saveCurrentState()

            // Optional: I-log lang pero huwag mag-trigger ng bagong cheat attempt

            // HUWAG tumawag ng handleCheatAttempt() dito kasi magre-reset ng quiz
            // viewModel.handleCheatAttempt("App closed/destroyed during quiz")
        }

        resetDoNotDisturb()
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!quizFinished && !isRequestingDndPermission) {
            if (!hasFocus && !isSpinnerOpen()) {
                // Set flag to indicate home button was pressed
                homeButtonPressed = true
                viewModel.handleCheatAttempt("CHEATING DETECTED")
            } else if (hasFocus) {
                viewModel.resetCheat()
                homeButtonPressed = false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!quizFinished && !isRequestingDndPermission && !isSpinnerOpen()) {

            viewModel.saveCurrentState()

            if (!homeButtonPressed) {
                viewModel.handleCheatAttempt("CHEATING DETECTED")
            }
        }
        homeButtonPressed = false // Reset the flag
    }

    override fun onStop() {
        super.onStop()

        if (!quizFinished) {
            viewModel.saveCurrentState()
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
        viewModel.currentQuestion.observe(this) { question ->
            // 🚨 ADD DND CHECK HERE - BEFORE SHOWING ANY QUESTION
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Toast.makeText(this, "Quiz paused: DND permission required", Toast.LENGTH_SHORT).show()
                return@observe // STOP HERE if no DND permission
            }
            showQuestion(question, viewModel.currentQuestionIndex.value ?: 0)
        }

        viewModel.questionCounter.observe(this) { counterText ->
            binding.tvQuestionCounter.text = counterText
        }

        viewModel.uiMessage.observe(this) { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }

        viewModel.cheatCount.observe(this) { count ->
            updateCheatCounter(count)
        }

        viewModel.quizResultData.observe(this) { resultData ->
            if (resultData != null) {
                quizFinished = true
                // 1. Release security features
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    stopLockTask() // Release screen pinning
                }
                resetDoNotDisturb()

                // 2. Clear any persisted state
                clearPersistedState()

                // 3. Launch QuizResultActivity
                val intent = Intent(this, QuizResultActivity::class.java).apply {
                    putExtra("SCORE", resultData.score)
                    putExtra("TOTAL_QUESTIONS", resultData.totalQuestions)
                    putExtra("CHEAT_COUNT", resultData.cheatCount)
                    putExtra("RESULT_TYPE", "ATTEMPTED")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(intent)
                finish()
            }
        }

        viewModel.isCheating.observe(this) { cheating ->
            if (cheating) {
                if (cheatOverlay == null) showCheatOverlay("Cheating detected!")

                Handler(Looper.getMainLooper()).postDelayed({
                    removeCheatOverlay()
                    viewModel.resetCheat()

                    viewModel.currentQuestion.value?.let {
                        showQuestion(it, 0)
                    }
                }, 2000)
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
            val isLastQuestion = currentIndex == totalQuestions - 1
            if (isLastQuestion) {
                // Submit the quiz
                viewModel.submitQuiz()
            } else {
                // Proceed to next question
                viewModel.nextQuestion()
            }
            spinnerActive = false
        }
    }

    private val totalQuestions: Int by lazy {
        quiz.questions.size
    }

    private fun clearPersistedState() {
        val progressPrefs = getSharedPreferences("quiz_progress", Context.MODE_PRIVATE)
        val editor = progressPrefs.edit()
        editor.remove("cheat_count_${quiz.quizId}")
        editor.remove("current_question_${quiz.quizId}")
        editor.remove("answers_${quiz.quizId}")
        editor.remove("start_time_${quiz.quizId}")
        editor.apply()
    }

    private fun updateCheatCounter(count: Int) {
        if (count > 0) {
            binding.tvCheatCounter.visibility = View.VISIBLE
            binding.tvCheatCounter.text = "Cheats: $count/5"


            when (count) {
                0 -> binding.tvCheatCounter.setBackgroundColor(Color.parseColor("#757575")) // Gray for 0
                1, 2 -> binding.tvCheatCounter.setBackgroundColor(Color.parseColor("#FF9800")) // Orange
                3, 4 -> binding.tvCheatCounter.setBackgroundColor(Color.parseColor("#FF5722")) // Dark Orange
                5 -> binding.tvCheatCounter.setBackgroundColor(Color.parseColor("#D32F2F")) // Red
            }
        } else {
            binding.tvCheatCounter.visibility = View.GONE
        }
    }

    private fun showTermsAndConditions() {
        if (termsAccepted) return

        AlertDialog.Builder(this)
            .setTitle("Terms and Conditions")
            .setMessage(
                "1. You cannot leave the screen.\n" +
                        "2. Screenshots, recording, or multi-window count as cheat attempts.\n" +
                        "3. Max 5 cheat attempts; exceeding = 0 score.\n" +
                        "4. Will auto-submit when time is up.\n" +
                        "5. All answers are logged in real-time.\n" +
                        "6. Closing the app counts as 1 cheat attempt and resets the quiz."
            )
            .setCancelable(false)
            .setPositiveButton("I Agree") { _, _ ->
                termsAccepted = true
                lockQuizScreen()
                requestDndPermission()
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

        spinnerActive = false

        binding.tvQuestionType.text = questionType

        binding.tvQuestionText.text = "${index + 1}. ${q.questionText}"
        val isLastQuestion = index == totalQuestions - 1
        binding.btnSubmit.text = if (isLastQuestion) "SUBMIT QUIZ" else "NEXT QUESTION"
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

                        if (recordedAnswer == optionText) {
                            btn.setBackgroundColor(selectedColor)
                        } else {
                            btn.setBackgroundColor(defaultColor)
                        }

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
                binding.tvQuestionType.text = "Question Type: Matching Type"

                // Clear previous views
                binding.matchingLayout.removeAllViews()

                val recordedAnswer = viewModel.getRecordedAnswer(index)

                // Parse previously recorded answer if exists
                val previousSelections = mutableMapOf<String, String>()
                if (!recordedAnswer.isNullOrEmpty()) {
                    recordedAnswer.split(";").forEach { pair ->
                        val parts = pair.split("=")
                        if (parts.size == 2) {
                            previousSelections[parts[0]] = parts[1]
                        }
                    }
                }

                // Create matching pairs - shuffle only the right side for display
                val shuffledRights = q.matches.shuffled().toMutableList()

                q.options.forEachIndexed { rowIndex, leftText ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 16, 0, 16)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Left item (fixed)
                    val tvLeft = TextView(this).apply {
                        text = leftText
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setPadding(16, 8, 16, 8)
                        setBackgroundColor(Color.parseColor("#F5F5F5"))
                        gravity = android.view.Gravity.CENTER
                    }

                    // Right item spinner
                    val spinner = Spinner(this).apply {
                        val adapter = ArrayAdapter(this@StudentQuizActivity, android.R.layout.simple_spinner_item, shuffledRights)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        this.adapter = adapter

                        // Set previous selection if exists
                        previousSelections[leftText]?.let { selectedValue ->
                            val position = shuffledRights.indexOf(selectedValue)
                            if (position >= 0) {
                                setSelection(position)
                            }
                        }

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

        if (newConfig.orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            viewModel.handleCheatAttempt("Screen orientation was changed.")
        }
    }

    private fun requestDndPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            isRequestingDndPermission = true
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant 'Do Not Disturb' access to start.", Toast.LENGTH_LONG).show()
        } else {
            // DND already granted, start quiz immediately
            startQuizAttempt()
        }
    }
}
package com.example.datadomeapp.student

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.shuffle
import kotlin.collections.toMutableList

class StudentQuizViewModel(application: Application, private val initialQuiz: Quiz) : ViewModel() {

    data class QuizResultData(val score: Int, val totalQuestions: Int, val cheatCount: Int)
    private val _quizResultData = MutableLiveData<QuizResultData?>()
    val quizResultData: LiveData<QuizResultData?> = _quizResultData
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _currentQuestion = MutableLiveData<Question>()
    val currentQuestion: LiveData<Question> = _currentQuestion
    private val _isCheating = MutableLiveData<Boolean>(false)
    val isCheating: LiveData<Boolean> = _isCheating
    private val _currentQuestionIndex = MutableLiveData(0)
    val currentQuestionIndex: LiveData<Int> = _currentQuestionIndex
    private val _timerText = MutableLiveData<String>()
    val timerText: LiveData<String> = _timerText
    private val cheatCooldown = 500L
    private val _cheatCount = MutableLiveData(0)
    val cheatCount: LiveData<Int> = _cheatCount
    private val _questionCounter = MutableLiveData<String>()
    val questionCounter: LiveData<String> = _questionCounter

    private val totalQuestions = initialQuiz.questions.size
    private val _uiMessage = MutableLiveData<String>()
    val uiMessage: LiveData<String> = _uiMessage

    // --- Quiz Finished Flag ---
    private var quizFinished = false

    // --- Persistence ---
    private val prefs = application.getSharedPreferences("quiz_progress", Context.MODE_PRIVATE)

    private var persistedCheatCount: Int
        get() = prefs.getInt("cheat_count_${initialQuiz.quizId}", 0)
        set(value) = prefs.edit().putInt("cheat_count_${initialQuiz.quizId}", value).apply()

    private fun updateQuestionCounter() {
        val currentIndex = _currentQuestionIndex.value ?: 0
        _questionCounter.value = "Q: ${currentIndex + 1}/$totalQuestions"
    }

    private var persistedCurrentQuestion: Int
        get() = prefs.getInt("current_question_${initialQuiz.quizId}", 0)
        set(value) = prefs.edit().putInt("current_question_${initialQuiz.quizId}", value).apply()

    private var persistedAnswers: Map<Int, String>
        get() {
            val answersMap = mutableMapOf<Int, String>()
            val answersString = prefs.getString("answers_${initialQuiz.quizId}", "") ?: ""

            if (answersString.isNotEmpty()) {
                answersString.split(";").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        try {
                            val questionIndex = parts[0].toInt()
                            val answer = parts[1]
                            answersMap[questionIndex] = answer
                        } catch (e: NumberFormatException) {
                            // Skip invalid entries instead of crashing
                            android.util.Log.e("StudentQuizViewModel", "Invalid question index format: ${parts[0]}")
                        }
                    }
                }
            }
            return answersMap
        }
        set(value) {
            val answersString = value.entries.joinToString(";") { "${it.key}:${it.value}" }
            prefs.edit().putString("answers_${initialQuiz.quizId}", answersString).apply()
        }


    private var persistedStartTime: Long
        get() = prefs.getLong("start_time_${initialQuiz.quizId}", 0L)
        set(value) = prefs.edit().putLong("start_time_${initialQuiz.quizId}", value).apply()

    private var isQuizRestored = false

    // --- Internal State ---
    private var mutableQuestions = initialQuiz.questions.toMutableList()
    private var quiz = initialQuiz.copy(questions = mutableQuestions)
    private var serverTimeListenerRegistration: ListenerRegistration? = null
    private var quizResultListenerRegistration: ListenerRegistration? = null
    private val studentAnswers = mutableListOf<Pair<Int, String>>()
    private val maxCheatAttempts = 5
    private var lastCheatTimestamp = 0L

    private val cheatLogList = mutableListOf<String>()
    private var submitAttempts = 0

    // Auto-save handler
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            if (!quizFinished) {
                // Auto-save current progress
                persistedCurrentQuestion = _currentQuestionIndex.value ?: 0
                persistedCheatCount = _cheatCount.value ?: 0
                persistedAnswers = studentAnswers.associate { it.first to it.second }
                persistedCheatLog = cheatLogList
            }
            autoSaveHandler.postDelayed(this, 30000) // Save every 30 seconds
        }
    }

    private var serverEndTime: Long = initialQuiz.scheduledEndDateTime

    private val _serverTime = MutableLiveData<Long>()
    val serverTime: LiveData<Long> = _serverTime

    init {
        // Restore previous state first
        restoreQuizState()
        // Then shuffle questions
        shuffleQuestionsAndAnswers()
        setupRetakeStatusListener()
        // Start auto-save
        autoSaveHandler.postDelayed(autoSaveRunnable, 30000)
        updateQuestionCounter()
    }

    fun saveCurrentState() {
        // I-save ang current state sa SharedPreferences
        persistedCheatCount = _cheatCount.value ?: 0
        persistedCurrentQuestion = _currentQuestionIndex.value ?: 0
        persistedAnswers = studentAnswers.associate { it.first to it.second }

        persistedCheatLog = cheatLogList

        // Kung may start time na, i-save rin
        if (persistedStartTime == 0L) {
            persistedStartTime = System.currentTimeMillis()
        }

    }

    private var persistedCheatLog: List<String>
        get() {
            // Kunin ang string at i-split gamit ang delimiter
            val logString = prefs.getString("cheat_log_${initialQuiz.quizId}", "") ?: ""
            // Gumamit ng malabong delimiter (e.g., "||") na hindi madalas gamitin sa loob ng log entry
            return if (logString.isNotEmpty()) logString.split("||") else emptyList()
        }
        set(value) {
            // I-join ang listahan sa isang string bago i-save
            val logString = value.joinToString("||")
            prefs.edit().putString("cheat_log_${initialQuiz.quizId}", logString).apply()
        }

    private fun restoreQuizState() {
        val savedCheatCount = persistedCheatCount
        val savedCurrentQuestion = persistedCurrentQuestion
        val savedAnswers = persistedAnswers
        val savedStartTime = persistedStartTime
        val savedCheatLog = persistedCheatLog

        if (savedStartTime > 0L) {

            _cheatCount.value = savedCheatCount
            _currentQuestionIndex.value = savedCurrentQuestion
            studentAnswers.clear()
            studentAnswers.addAll(savedAnswers.map { it.key to it.value })

            cheatLogList.clear()
            cheatLogList.addAll(savedCheatLog)

            if (savedCurrentQuestion < mutableQuestions.size) {
                _currentQuestion.value = mutableQuestions[savedCurrentQuestion]
            }

            isQuizRestored = true
            _uiMessage.value = "Quiz progress restored from previous session"
        }
        if (savedCurrentQuestion < mutableQuestions.size) {
            _currentQuestion.value = mutableQuestions[savedCurrentQuestion]
        }

        updateQuestionCounter() // 🆕 Update counter after restoration
        isQuizRestored = true
        _uiMessage.value = "Quiz progress restored from previous session"
    }


    private fun setupRetakeStatusListener() {
        val studentId = auth.currentUser?.uid ?: return
        val quizId = initialQuiz.quizId
        val documentRef = firestore.collection("quizResults").document("${quizId}_$studentId")

        // Gumamit ng addSnapshotListener para makinig sa pagbabago ng status
        quizResultListenerRegistration = documentRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                // Log error
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val status = snapshot.getString("status")
                // CRITICAL FIX 2a: Kunin ang retakeDeadline na galing sa Teacher App
                val retakeDeadline = snapshot.getLong("retakeDeadline") ?: 0L

                if (status == "EXAM_READY") {
                    _uiMessage.value = "This is an Exam. Access is restricted until the teacher starts the Exam."

                    // CRITICAL: Prevent quiz from starting/ticking but DO NOT exit the activity.
                    // Instead, i-set lang ang _quizResultData.value to null para hindi mag-exit,
                    // at hintayin ang update mula sa guro.

                    // I-stop ang timer para hindi mag-auto-submit
                    handler.removeCallbacks(tickRunnable)
                    isTicking = false
                    _timerText.value = "--:--"

                    // Huwag tumawag sa handleTimeExpired() para hindi mag-exit ang activity.
                    return@addSnapshotListener
                }

                // 1. CHECK: ACCESS_REVOKED
                if (status == "ACCESS_REVOKED") {
                    _uiMessage.value = "Access to the quiz has been revoked by the teacher."
                    handleTimeExpired()
                    return@addSnapshotListener
                }

                // 2. CHECK: RETAKE_GRANTED
                if (status == "RETAKE_GRANTED") {
                    // Tiyakin na ang retakeDeadline ay valid at mas malaki sa kasalukuyang oras
                    if (retakeDeadline > System.currentTimeMillis()) {

                        val currentAttempt = snapshot.getLong("attemptCount")?.toInt() ?: 1
                        val previousScore = snapshot.getLong("score")?.toInt() ?: 0

                        // CRITICAL FIX 1: Gumawa ng record ng nakaraang attempt
                        val newAttemptCount = currentAttempt + 1
                        val newAttemptKey = "attempt_${currentAttempt}"

                        val updates = hashMapOf<String, Any>(
                            "status" to "IN_PROGRESS",
                            "timestamp" to System.currentTimeMillis(),
                            "retakeDeadline" to retakeDeadline, // I-set ulit ang deadline
                            "attemptCount" to newAttemptCount, // Increment ang count
                            newAttemptKey to previousScore, // I-store ang previous score (ex: "attempt_1": 15)
                        )

                        serverEndTime = retakeDeadline

                        _uiMessage.value = "Retake granted by teacher. Resetting quiz..."
                        shuffleQuestionsAndAnswers()
                        resetQuizProgress(keepCheatCount = false)

                        // 3. Update status pabalik sa IN_PROGRESS
                        documentRef.update("status", "IN_PROGRESS", "timestamp", System.currentTimeMillis())
                            .addOnSuccessListener {
                                _uiMessage.value = "Quiz reset complete. New deadline set! Start now."
                                startQuizTracking() // I-restart ang tracking
                            }
                    } else {
                        // Kung ang retake deadline ay tapos na, i-update ang status
                        if (retakeDeadline > 0L) {
                            documentRef.update("status", "TIME_EXPIRED")
                        }
                    }
                    return@addSnapshotListener
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            // Tumawag sa updateTimer gamit ang simulated local time
            updateTimer(System.currentTimeMillis())
            handler.postDelayed(this, 1000)
        }
    }
    private var isTicking = false

    // --- Data and State Management ---

    private fun shuffleQuestionsAndAnswers() {
        // 1. Paghiwalayin ang mga tanong ayon sa uri
        val mcQuestions = mutableQuestions.filterIsInstance<Question.MultipleChoice>().toMutableList()
        val tfQuestions = mutableQuestions.filterIsInstance<Question.TrueFalse>().toMutableList()
        val matchingQuestions = mutableQuestions.filterIsInstance<Question.Matching>().toMutableList()

        // 2. I-shuffle ang bawat set ng tanong (random order within category)
        mcQuestions.shuffle()
        tfQuestions.shuffle()
        matchingQuestions.shuffle()

        // 3. I-shuffle ang options sa loob ng Multiple Choice tanong
        mcQuestions.forEachIndexed { index, q ->
            val mutableOptions = q.options.toMutableList()
            // Tiyakin na ang correct answer index ay na-u-update o nako-correct sa adapter logic.
            // Para sa simpleng shuffle, ang buong listahan ng options ay i-sha-shuffle dito:
            mutableOptions.shuffle()
            mcQuestions[index] = q.copy(options = mutableOptions)
        }

        // 4. Pagsamahin muli ang mga tanong sa tamang pagkakasunod-sunod: MC -> TF -> Matching
        mutableQuestions.clear()
        mutableQuestions.addAll(mcQuestions)
        mutableQuestions.addAll(tfQuestions)
        mutableQuestions.addAll(matchingQuestions)

        // Initialize the first question
        _currentQuestion.value = mutableQuestions.firstOrNull()
            ?: throw IllegalStateException("Questions cannot be empty after setup.")
    }

    fun fetchServerTime() {
        // Cancel the previous listener if it exists to prevent memory leaks or duplicate calls
        serverTimeListenerRegistration?.remove()

        // TRY DIFFERENT DOCUMENT PATHS - Server time might be stored differently
        val timeDocRef = firestore.collection("serverTime").document("current")
        // OR try: firestore.collection("systemSettings").document("serverTime")
        // OR try: firestore.collection("timestamp").document("current")

        // Gumamit ng addSnapshotListener para sa real-time updates
        serverTimeListenerRegistration = timeDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                _uiMessage.value = "Error listening to server time: ${e.message}"
                // Fallback to device time if server time fails
                _uiMessage.value = "Using device time as fallback"
                startLocalTimer()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // Try different field names
                val serverTs = snapshot.getTimestamp("timestamp")?.toDate()?.time
                    ?: snapshot.getTimestamp("serverTime")?.toDate()?.time
                    ?: snapshot.getTimestamp("currentTime")?.toDate()?.time
                    ?: snapshot.getLong("timestamp")
                    ?: snapshot.getLong("serverTime")
                    ?: snapshot.getLong("currentTime")

                if (serverTs != null) {
                    updateTimer(serverTs)

                    if (!isTicking) {
                        isTicking = true
                        handler.post(tickRunnable)
                    }
                } else {
                    startLocalTimer()
                }
            } else {
                startLocalTimer()
            }
        }
    }

    // ADD THIS NEW FUNCTION AS FALLBACK
    private fun startLocalTimer() {
        val currentTime = System.currentTimeMillis()
        updateTimer(currentTime)

        if (!isTicking) {
            isTicking = true
            handler.post(tickRunnable)
        }
    }

    override fun onCleared() {
        super.onCleared()
        serverTimeListenerRegistration?.remove()
        quizResultListenerRegistration?.remove()
        handler.removeCallbacks(tickRunnable)
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
    }

    fun updateTimer(currentServerTime: Long) {
        // Ang 'serverEndTime' ay gumagamit na ngayon ng retake deadline kung na-override ito.
        val remaining = serverEndTime - currentServerTime
        if (remaining <= 0L) {
            _timerText.value = "00:00"
            handler.removeCallbacks(tickRunnable)
            isTicking = false
            handleTimeExpired()
        } else {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
            _timerText.value = String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun handleTimeExpired() {
        val studentUid = auth.currentUser?.uid ?: return
        val quizId = quiz.quizId

        val totalItems = calculateTotalMaxPoints()

        firestore.collection("quizResults").document("${quizId}_$studentUid")
            .get().addOnSuccessListener { snapshot ->
                val status = snapshot.getString("status")

                if (status == "IN_PROGRESS" || status == "RETAKE_GRANTED") {
                    // FIX 2a: Kung IN_PROGRESS (nagsimula na ang estudyante), auto-submit na may score.
                    _uiMessage.value = "Time's up! Auto-submitting..."
                    submitQuiz()
                } else {
                    // FIX 2b: Kung hindi pa nagsisimula o COMPLETED na, i-update lang ang status.
                    _uiMessage.value = "Quiz deadline has passed. Submission is no longer possible."

                    // Siguraduhin na ang app ay lalabas pagkatapos magbigay ng mensahe.
                    _quizResultData.value = QuizResultData(0, totalItems, _cheatCount.value ?: 0)
                }

                // I-update ang status sa database
                firestore.collection("quizResults").document("${quizId}_$studentUid")
                    .update("status", "TIME_EXPIRED")
            }
    }

    fun startQuizTracking() {
        val studentUid = auth.currentUser?.uid ?: return
        val quizId = quiz.quizId
        val totalItems = calculateTotalMaxPoints()

        // Only set start time if this is a new attempt, not a restoration
        if (!isQuizRestored) {
            persistedStartTime = System.currentTimeMillis()
        }

        // Gumawa ng initial record na may "IN_PROGRESS" status para makita ng guro sa real-time.
        firestore.collection("quizResults").document("${quizId}_$studentUid")
            .set(
                mapOf(
                    "studentId" to studentUid,
                    "quizId" to quizId,
                    "assignmentId" to quiz.assignmentId,
                    "status" to "IN_PROGRESS",
                    "cheatCount" to persistedCheatCount, // Use persisted value
                    "attemptCount" to 1,
                    "startTime" to persistedStartTime, // Use persisted value
                    "isRestored" to isQuizRestored
                ),
                SetOptions.merge()
            ).addOnFailureListener {
                _uiMessage.value = "Error starting quiz tracking: ${it.message}"
            }

        isQuizRestored = false
    }

    fun recordAnswer(questionIndex: Int, answer: String) {
        val existingIndex = studentAnswers.indexOfFirst { it.first == questionIndex }
        if (existingIndex >= 0) studentAnswers[existingIndex] = questionIndex to answer
        else studentAnswers.add(questionIndex to answer)

        // Persist answers immediately
        persistedAnswers = studentAnswers.associate { it.first to it.second }
        _uiMessage.value = "Answer recorded."
    }

    fun recordMatchingAnswers(questionIndex: Int, answerPairs: List<String>) {
        val answerStr = answerPairs.joinToString(";")
        recordAnswer(questionIndex, answerStr)
    }

    fun nextQuestion() {
        val currentIndex = _currentQuestionIndex.value!!

        val nextIndex = currentIndex + 1
        if (nextIndex < mutableQuestions.size) {
            _currentQuestionIndex.value = nextIndex
            persistedCurrentQuestion = nextIndex // Persist current question
            _currentQuestion.value = mutableQuestions[nextIndex]
            updateQuestionCounter()
        } else {
            submitQuiz()
        }
    }

    fun resetQuizProgress(keepCheatCount: Boolean = false) {
        _currentQuestionIndex.value = 0
        persistedCurrentQuestion = 0
        _currentQuestion.value = mutableQuestions.firstOrNull()
        studentAnswers.clear()
        persistedAnswers = emptyMap()

        updateQuestionCounter()

            if (!keepCheatCount) {
            _cheatCount.value = 0
            persistedCheatCount = 0
            cheatLogList.clear()
        }

        submitAttempts = 0
        _quizResultData.value = null
        persistedStartTime = 0L
        quizFinished = false

        _timerText.value = "00:00"
        handler.removeCallbacks(tickRunnable)
        isTicking = false

        if (keepCheatCount) {
            fetchServerTime()
        }
    }

    fun resetCheat() {
        _isCheating.value = false
    }

    fun handleCheatAttempt(reason: String) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastCheatTimestamp < cheatCooldown) {
            _isCheating.value = true
            return
        }

        lastCheatTimestamp = currentTime

        val currentCount = _cheatCount.value ?: 0
        val newCount = currentCount + 1
        _cheatCount.value = newCount
        persistedCheatCount = newCount // Persist cheat count

        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentTime))
        val currentQuestionIndex = _currentQuestionIndex.value ?: 0
        val humanReadableQuestionNumber = currentQuestionIndex + 1
        val logEntry = "[$timestamp] ${reason} (Q:${humanReadableQuestionNumber})"
        cheatLogList.add(logEntry)

        persistedCheatLog = cheatLogList

        saveCurrentState()

        _isCheating.value = true

        if (newCount >= maxCheatAttempts) {
            _uiMessage.value = "Maximum cheat attempts reached. Auto-submit = 0"
            submitQuiz()
        } else {
            resetToQuestionOne()
        }
    }

    private fun resetToQuestionOne() {
        _currentQuestionIndex.value = 0
        persistedCurrentQuestion = 0
        _currentQuestion.value = mutableQuestions.firstOrNull()
        studentAnswers.clear()
        persistedAnswers = emptyMap()
        updateQuestionCounter()
        _uiMessage.value = "Quiz reset to Question 1 due to cheating"
    }

    fun getRecordedAnswer(questionIndex: Int): String? {
        return studentAnswers.find { it.first == questionIndex }?.second
    }

    // 🆕 NEW FUNCTION: Check if quiz time has expired
    fun isQuizTimeExpired(): Boolean {
        return System.currentTimeMillis() >= serverEndTime
    }

    // 🆕 NEW FUNCTION: Force auto-submit
    fun forceAutoSubmit() {
        handleTimeExpired()
    }

    private fun calculateScore(): Int {
        var score = 0
        // Use mutableQuestions for calculation
        mutableQuestions.forEachIndexed { i, q ->
            val answer = studentAnswers.find { it.first == i }?.second
            when (q) {
                is Question.MultipleChoice -> {
                    // Tiyakin na ang logic mo sa pag-shuffle ng options ay tama ang pagko-compute
                    // Gamit ang text ng sagot, hindi index, para maging safe.
                    if (answer == q.options.getOrNull(q.correctAnswerIndex))
                        score++
                }
                is Question.TrueFalse -> {
                    if (answer?.toBoolean() == q.answer)
                        score++
                }
                is Question.Matching -> {
                    val correctMap = q.options.zip(q.matches).toMap()
                    val studentPairs = answer?.split(";") ?: emptyList()
                    studentPairs.forEach { pair ->
                        val parts = pair.split("=")
                        if (parts.size == 2) {
                            val left = parts[0]
                            val selected = parts[1]
                            // Magbigay ng partial score kung tama ang match
                            if (correctMap[left] == selected) score++
                        }
                    }
                }
            }
        }
        return score
    }

    private fun fetchCurrentTerm(onSuccess: (Map<String, Any>) -> Unit, onFailure: (Exception) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("systemSettings")
            .document("currentTerm")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val termData = mapOf(
                        "academicTerm" to (document.getString("academicTerm") ?: ""),
                        "academicYear" to (document.getString("academicYear") ?: ""),
                        "semester" to (document.getString("semester") ?: "")
                    )
                    onSuccess(termData)
                } else {
                    onFailure(Exception("currentTerm document not found."))
                }
            }
            .addOnFailureListener(onFailure)
    }

    private fun calculateTotalMaxPoints(): Int {
        var totalPoints = 0
        mutableQuestions.forEach { q ->
            when (q) {
                is Question.MultipleChoice, is Question.TrueFalse -> {
                    // MC at T/F ay 1 point/item
                    totalPoints += 1
                }
                is Question.Matching -> {
                    totalPoints += q.matches.size
                }
                else -> {
                    // Default to 1 point for other types, to be safe
                    totalPoints += 1
                }
            }
        }
        return totalPoints
    }

    private fun clearPersistedState() {
        val editor = prefs.edit()
        editor.remove("cheat_count_${initialQuiz.quizId}")
        editor.remove("current_question_${initialQuiz.quizId}")
        editor.remove("answers_${initialQuiz.quizId}")
        editor.remove("start_time_${initialQuiz.quizId}")
        editor.remove("cheat_log_${initialQuiz.quizId}")

        editor.apply()
    }

    fun submitQuiz() {
        quizFinished = true // Mark quiz as finished

        val studentUid = auth.currentUser?.uid ?: "unknown"
        val rawScore = calculateScore()
        val totalQuestions = calculateTotalMaxPoints()
        val cheatCount = _cheatCount.value ?: 0

        var finalScore = rawScore

        if (cheatCount >= maxCheatAttempts) {
            finalScore = 0
            _uiMessage.value = "Maximum cheat attempts reached. Auto-submit = 0"
        } else {
            // Kung nag-auto-submit dahil sa oras, o natapos niya, gamitin ang rawScore.
            _uiMessage.value = "Quiz submitted! Score: $rawScore"
        }
        // Stop all timers and listeners immediately upon submission attempt
        serverTimeListenerRegistration?.remove()
        quizResultListenerRegistration?.remove()
        handler.removeCallbacks(tickRunnable)
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        isTicking = false

        if (submitAttempts >= 3) {
            _uiMessage.value = "Failed to submit quiz after multiple attempts."
            _quizResultData.value = QuizResultData(finalScore, totalQuestions, cheatCount)
            clearPersistedState() // Clear state even on failure
            return
        }
        submitAttempts++

        val answerMap = studentAnswers.associate { it.first.toString() to it.second }

        // ⭐ HAKBANG 1: I-FETCH MUNA ANG CURRENT TERM DATA
        fetchCurrentTerm(
            onSuccess = { currentTermData ->
                // HAKBANG 2: I-COMPOSE ANG BASE RESULT MAP
                val baseResultData = mutableMapOf(
                    "studentId" to studentUid,
                    "quizId" to quiz.quizId,
                    "assignmentId" to quiz.assignmentId,
                    "answers" to answerMap,
                    "score" to finalScore,
                    "status" to "COMPLETED",
                    "cheatCount" to _cheatCount.value,
                    "timestamp" to System.currentTimeMillis(),
                    "cheatLog" to cheatLogList
                ) as MutableMap<String, Any> // Explicit cast para sa putAll

                // HAKBANG 3: I-MERGE ANG TERM DATA
                baseResultData.putAll(currentTermData)

                // HAKBANG 4: I-SAVE ANG FINAL MAP SA FIRESTORE
                firestore.collection("quizResults").document("${quiz.quizId}_$studentUid")
                    .set(baseResultData)
                    .addOnSuccessListener {
                        _uiMessage.value = "Quiz submitted! Score: $finalScore"
                        _quizResultData.value = QuizResultData(finalScore, totalQuestions, cheatCount)
                        clearPersistedState() // Clear persisted state after successful submission
                    }
                    .addOnFailureListener {
                        _uiMessage.value = "Failed to submit quiz. Retrying..."
                        submitQuiz() // Recursive retry
                    }
            },
            onFailure = { e ->
                // Handle error fetching term data (e.g., alert the user)
                _uiMessage.value = "Error: Failed to fetch academic term data (${e.message}). Retrying submission..."
                // Mag-try ulit para hindi mawala ang quiz result
                submitQuiz() // Recursive retry
            }
        )
    }
}

class StudentQuizViewModelFactory(private val application: Application, private val quiz: Quiz) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudentQuizViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudentQuizViewModel(application, quiz) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
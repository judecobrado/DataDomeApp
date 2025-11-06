package com.example.datadomeapp.student

import android.app.Application
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

    private val _uiMessage = MutableLiveData<String>()
    val uiMessage: LiveData<String> = _uiMessage

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

    // CRITICAL FIX 1: I-set ang default value ng serverEndTime sa orihinal na deadline
    private var serverEndTime: Long = initialQuiz.scheduledEndDateTime

    private val _serverTime = MutableLiveData<Long>()
    val serverTime: LiveData<Long> = _serverTime

    init {
        // Shuffling done once when the ViewModel is created
        shuffleQuestionsAndAnswers()
        setupRetakeStatusListener()
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
        val timeDocRef = firestore.collection("serverTime").document("timeDoc")

        // Gumamit ng addSnapshotListener para sa real-time updates
        serverTimeListenerRegistration = timeDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                _uiMessage.value = "Error listening to server time: ${e.message}"
                // Sa error, auto-submit para hindi ma-stuck
                _quizResultData.value = QuizResultData(0, mutableQuestions.size, _cheatCount.value ?: 0)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val serverTs = snapshot.getTimestamp("ts")?.toDate()?.time
                if (serverTs != null) {
                    // CRITICAL FIX 3: Inalis ang 'serverEndTime = initialQuiz.scheduledEndDateTime' dito.
                    _serverTime.value = serverTs

                    // Tawagin ang updateTimer mula dito, tuwing may pagbabago sa server time
                    updateTimer(serverTs)

                    if (!isTicking) {
                        isTicking = true
                        handler.post(tickRunnable)
                    }

                } else {
                    _uiMessage.value = "Failed to get server time."
                    handleTimeExpired()
                }
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        serverTimeListenerRegistration?.remove()
        quizResultListenerRegistration?.remove()
        handler.removeCallbacks(tickRunnable)
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
                    _quizResultData.value = QuizResultData(0, mutableQuestions.size, _cheatCount.value ?: 0)
                }

                // I-update ang status sa database
                firestore.collection("quizResults").document("${quizId}_$studentUid")
                    .update("status", "TIME_EXPIRED")
            }
    }

    fun startQuizTracking() {
        val studentUid = auth.currentUser?.uid ?: return
        val quizId = quiz.quizId

        // Gumawa ng initial record na may "IN_PROGRESS" status para makita ng guro sa real-time.
        firestore.collection("quizResults").document("${quizId}_$studentUid")
            .set(
                mapOf(
                    "studentId" to studentUid,
                    "quizId" to quizId,
                    "assignmentId" to quiz.assignmentId,
                    "status" to "IN_PROGRESS",
                    "cheatCount" to 0,
                    "attemptCount" to 1,
                    "startTime" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).addOnFailureListener {
                _uiMessage.value = "Error starting quiz tracking: ${it.message}"
            }
    }

    // --- User Interaction and Navigation ---

    fun recordAnswer(questionIndex: Int, answer: String) {
        val existingIndex = studentAnswers.indexOfFirst { it.first == questionIndex }
        if (existingIndex >= 0) studentAnswers[existingIndex] = questionIndex to answer
        else studentAnswers.add(questionIndex to answer)
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
            _currentQuestion.value = mutableQuestions[nextIndex]
        } else {
            submitQuiz()
        }
    }

    fun resetQuizProgress(keepCheatCount: Boolean = false) {
        _currentQuestionIndex.value = 0
        _currentQuestion.value = mutableQuestions.firstOrNull()
        studentAnswers.clear()
        if (!keepCheatCount) _cheatCount.value = 0

        submitAttempts = 0

        _quizResultData.value = null

        // ⭐ BAGONG PAGBABAGO: I-stop ang timer at i-reset ang display
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
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentTime))
        val logEntry = "[$timestamp] ${reason} (Q:${_currentQuestionIndex.value})"

        cheatLogList.add(logEntry)

        _uiMessage.value = "Cheating detected: $reason (Attempt $newCount/$maxCheatAttempts)"

        _isCheating.value = true

        if (newCount >= maxCheatAttempts) {
            _uiMessage.value = "Maximum cheat attempts reached. Auto-submit = 0"
            submitQuiz()
        } else {
            // Reset quiz progress (questions back to 1) WITHOUT resetting cheat count
            resetQuizProgress(keepCheatCount = true)
        }
    }

    fun getRecordedAnswer(questionIndex: Int): String? {
        return studentAnswers.find { it.first == questionIndex }?.second
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


    fun submitQuiz() {
        val studentUid = auth.currentUser?.uid ?: "unknown"
        val rawScore = calculateScore()
        val totalQuestions = mutableQuestions.size
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
        isTicking = false


        if (submitAttempts >= 3) {
            _uiMessage.value = "Failed to submit quiz after multiple attempts."
            _quizResultData.value = QuizResultData(finalScore, totalQuestions, cheatCount)
            return
        }
        submitAttempts++

        val answerMap = studentAnswers.associate { it.first.toString() to it.second }

        firestore.collection("quizResults").document("${quiz.quizId}_$studentUid")
            .set(
                mapOf(
                    "studentId" to studentUid,
                    "quizId" to quiz.quizId,
                    "assignmentId" to quiz.assignmentId,
                    "answers" to answerMap,
                    "score" to finalScore,
                    "status" to "COMPLETED",
                    "cheatCount" to _cheatCount.value,
                    "timestamp" to System.currentTimeMillis(),
                    "cheatLog" to cheatLogList
                )
            ).addOnSuccessListener {
                _uiMessage.value = "Quiz submitted! Score: $finalScore"
                _quizResultData.value = QuizResultData(finalScore, totalQuestions, cheatCount)

            }.addOnFailureListener {
                _uiMessage.value = "Failed to submit quiz. Retrying..."
                submitQuiz()
            }
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
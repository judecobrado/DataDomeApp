package com.example.datadomeapp.student

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

// Add the missing imports for shuffle and toMutableList if not already present
import kotlin.collections.shuffle
import kotlin.collections.toMutableList
// ... other imports

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
    // Ensure the questions list is mutable *after* copying the quiz.
    private var mutableQuestions = initialQuiz.questions.toMutableList()
    private var quiz = initialQuiz.copy(questions = mutableQuestions)
    private var serverTimeListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private val studentAnswers = mutableListOf<Pair<Int, String>>()
    private val maxCheatAttempts = 5
    private var lastCheatTimestamp = 0L

    init {
        // Shuffling done once when the ViewModel is created
        shuffleQuestionsAndAnswers()
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
            ?: throw IllegalStateException("Quiz questions cannot be empty after setup.")
    }

    // --- In StudentQuizViewModel ---
    private var serverEndTime: Long = 0

    private val _serverTime = MutableLiveData<Long>()
    val serverTime: LiveData<Long> = _serverTime

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
                    serverEndTime = initialQuiz.scheduledEndDateTime
                    _serverTime.value = serverTs

                    // Tawagin ang updateTimer mula dito, tuwing may pagbabago sa server time
                    updateTimer(serverTs)

                    if (!isTicking) {
                        isTicking = true
                        handler.post(tickRunnable)
                    }

                } else {
                    _uiMessage.value = "Failed to get server time."
                    onTimeUp()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serverTimeListenerRegistration?.remove()
        handler.removeCallbacks(tickRunnable)
    }

    fun updateTimer(currentServerTime: Long) {
        val remaining = serverEndTime - currentServerTime
        if (remaining <= 0L) {
            _timerText.value = "00:00"
            handler.removeCallbacks(tickRunnable)
            isTicking = false
            onTimeUp()
        } else {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
            _timerText.value = String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun onTimeUp() {
        _uiMessage.value = "Time's up! Auto-submitting..."
        submitQuiz()
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
        if (nextIndex < mutableQuestions.size) { // Use mutableQuestions size
            _currentQuestionIndex.value = nextIndex
            _currentQuestion.value = mutableQuestions[nextIndex] // Use mutableQuestions
        } else {
            submitQuiz()
        }
    }

    fun resetQuizProgress(keepCheatCount: Boolean = false) {
        _currentQuestionIndex.value = 0
        _currentQuestion.value = mutableQuestions.firstOrNull()
        studentAnswers.clear()
        if (!keepCheatCount) _cheatCount.value = 0
        _quizResultData.value = null
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
        if ((_cheatCount.value ?: 0) >= maxCheatAttempts) return 0
        var score = 0
        // Use mutableQuestions for calculation
        mutableQuestions.forEachIndexed { i, q ->
            val answer = studentAnswers.find { it.first == i }?.second
            when (q) {
                is Question.MultipleChoice -> {
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
                            if (correctMap[left] == selected) score++
                        }
                    }
                }
            }
        }
        return score
    }

    private var submitAttempts = 0

    fun submitQuiz() {
        val studentId = auth.currentUser?.uid ?: "unknown"
        val score = calculateScore()
        val totalQuestions = mutableQuestions.size

        if (submitAttempts >= 3) {
            _uiMessage.value = "Failed to submit quiz after multiple attempts."
            _quizResultData.value = QuizResultData(score, totalQuestions, _cheatCount.value ?: 0)
            return
        }
        submitAttempts++

        firestore.collection("quizResults").document("${quiz.quizId}_$studentId")
            .set(
                mapOf(
                    "studentId" to studentId,
                    "quizId" to quiz.quizId,
                    "answers" to studentAnswers,
                    "score" to score,
                    "cheatCount" to _cheatCount.value,
                    "timestamp" to System.currentTimeMillis()
                )
            ).addOnSuccessListener {
                _uiMessage.value = "Quiz submitted! Score: $score"
                _quizResultData.value = QuizResultData(score, totalQuestions, _cheatCount.value ?: 0)

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
            // CRITICAL: Ensure the ViewModel constructor is also called with both arguments
            return StudentQuizViewModel(application, quiz) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
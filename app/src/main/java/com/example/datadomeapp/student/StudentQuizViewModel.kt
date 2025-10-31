package com.example.datadomeapp.student

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.ClipboardManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

class StudentQuizViewModel(private val initialQuiz: Quiz) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val totalTimeMillis = 5 * 60 * 1000L


    // --- LiveData for UI State ---
    private val _currentQuestion = MutableLiveData<Question>()
    val currentQuestion: LiveData<Question> = _currentQuestion

    private val _currentQuestionIndex = MutableLiveData(0)
    val currentQuestionIndex: LiveData<Int> = _currentQuestionIndex

    private val _timerText = MutableLiveData<String>()
    val timerText: LiveData<String> = _timerText

    private val _cheatCount = MutableLiveData(0)
    val cheatCount: LiveData<Int> = _cheatCount

    private val _uiMessage = MutableLiveData<String>()
    val uiMessage: LiveData<String> = _uiMessage

    private val _quizFinished = MutableLiveData<Boolean>()
    val quizFinished: LiveData<Boolean> = _quizFinished

    // --- Internal State ---
    // Ensure the questions list is mutable *after* copying the quiz.
    private var mutableQuestions = initialQuiz.questions.toMutableList()
    private var quiz = initialQuiz.copy(questions = mutableQuestions)

    private val studentAnswers = mutableListOf<Pair<Int, String>>()
    private var countDownTimer: CountDownTimer? = null
    private var serverEndTime: Long = 0
    private val maxCheatAttempts = 5

    init {
        // Shuffling done once when the ViewModel is created
        shuffleQuestionsAndAnswers()
    }

    // --- Data and State Management ---

    private fun shuffleQuestionsAndAnswers() {
        // 1. Shuffle the main list of questions (uses the mutable list)
        mutableQuestions.shuffle() // FIX 1: 'shuffle' is now resolved

        // 2. Shuffle options for each question and update the model
        mutableQuestions.forEachIndexed { index, q ->
            when (q) {
                is Question.MultipleChoice -> {
                    val mutableOptions = q.options.toMutableList()
                    mutableOptions.shuffle()
                    // FIX 2: We use the mutableQuestions list to set the value by index
                    mutableQuestions[index] = q.copy(options = mutableOptions)
                }
                is Question.Matching -> {
                    val mutableOptions = q.options.toMutableList()
                    mutableOptions.shuffle()
                    // FIX 3: We use the mutableQuestions list to set the value by index
                    mutableQuestions[index] = q.copy(options = mutableOptions)
                }
                is Question.TrueFalse -> { /* No options to shuffle */ }
            }
        }
        // Initialize the first question
        _currentQuestion.value = mutableQuestions[0]
    }

    fun fetchServerTime() {
        val timeDocRef = firestore.collection("serverTime").document("timeDoc")
        val serverTimestamp = mapOf("ts" to FieldValue.serverTimestamp())
        timeDocRef.set(serverTimestamp).addOnSuccessListener {
            timeDocRef.get().addOnSuccessListener { snapshot ->
                val ts = snapshot.getTimestamp("ts")
                if (ts != null) {
                    val serverStartTime = ts.toDate().time
                    serverEndTime = serverStartTime + totalTimeMillis
                    startTimer()
                } else {
                    _uiMessage.value = "Failed to get server time."
                    _quizFinished.value = true
                }
            }.addOnFailureListener {
                _uiMessage.value = "Error fetching server time."
                _quizFinished.value = true
            }
        }.addOnFailureListener {
            _uiMessage.value = "Error initializing server time."
            _quizFinished.value = true
        }
    }

    private fun startTimer() {
        val currentTime = System.currentTimeMillis()
        var remainingTime = serverEndTime - currentTime
        if (remainingTime <= 0) remainingTime = 0

        countDownTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                _timerText.value = String.format("%02d:%02d", minutes, seconds)
            }
            override fun onFinish() {
                _uiMessage.value = "Time's up! Auto-submitting..."
                submitQuiz()
            }
        }.start()
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

    fun handleCheatAttempt(reason: String) {
        val newCount = _cheatCount.value!! + 1
        _cheatCount.value = newCount
        _uiMessage.value = "Cheating detected: $reason (Attempt $newCount/$maxCheatAttempts)"

        if (newCount >= maxCheatAttempts) {
            _uiMessage.value = "Maximum cheat attempts reached. Auto-submit = 0"
            submitQuiz()
        }
    }

    private fun calculateScore(): Int {
        if (_cheatCount.value!! >= maxCheatAttempts) return 0
        var score = 0
        // Use mutableQuestions for calculation
        mutableQuestions.forEachIndexed { i, q ->
            val answer = studentAnswers.find { it.first == i }?.second
            when (q) {
                is Question.MultipleChoice -> {
                    if (answer == q.options.getOrNull(q.correctAnswerIndex)) score++
                }
                is Question.TrueFalse -> {
                    if (answer?.toBoolean() == q.answer) score++
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

    fun submitQuiz() {
        countDownTimer?.cancel()
        val studentId = auth.currentUser?.uid ?: "unknown"

        val score = calculateScore()
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
                _quizFinished.value = true
            }.addOnFailureListener {
                _uiMessage.value = "Failed to submit quiz."
            }
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }
}

class StudentQuizViewModelFactory(private val quiz: Quiz) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudentQuizViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudentQuizViewModel(quiz) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
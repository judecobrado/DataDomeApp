package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// Data class na gagamitin sa Adapter
data class StudentQuizItem(
    val quiz: Quiz,
    var studentStatus: String = "NOT_STARTED",
    var retakeDeadline: Long = 0L,
    var rawScore: Int = 0,
    var totalQuestions: Int = 0,
    var cheatCount: Int = 0,
    var courseCode: String = "",
    var subjectTitle: String = ""
)

class StudentQuizListActivity : AppCompatActivity() {

    private lateinit var rvQuizzes: RecyclerView
    private lateinit var quizAdapter: StudentQuizAdapter
    private val quizItemList = mutableListOf<StudentQuizItem>()

    // BAGONG MGA VIEWS PARA SA MODERN LAYOUT
    private lateinit var tvTotalQuizzes: TextView
    private lateinit var tvCompletedQuizzes: TextView
    private lateinit var tvQuizCount: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var progressCard: MaterialCardView
    private lateinit var emptyState: android.widget.LinearLayout
    private lateinit var progressBar: ProgressBar

    private var quizTypeFilter: String? = null
    private val studentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_quiz_list_activity) // GAMITIN ANG BAGONG LAYOUT

        if (studentUid.isEmpty()) {
            Toast.makeText(this, "User not logged in. Please re-login.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        loadAllQuizzesFromRTDB()
    }

    // BAGONG METHOD PARA INITIALIZE NG MGA VIEWS
    private fun initializeViews() {
        rvQuizzes = findViewById(R.id.recyclerViewQuizzes)

        // INITIALIZE MGA BAGONG VIEWS MULA SA MODERN LAYOUT
        tvTotalQuizzes = findViewById(R.id.tvTotalQuizzes)
        tvCompletedQuizzes = findViewById(R.id.tvCompletedQuizzes)
        tvQuizCount = findViewById(R.id.tvQuizCount)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressCard = findViewById(R.id.progressCard)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        val tvQuizTitle = findViewById<TextView>(R.id.tvQuizTitle)

        quizTypeFilter = intent.getStringExtra("QUIZ_TYPE") ?: "Quiz"

        // SETUP REFRESH BUTTON
        btnRefresh.setOnClickListener {
            loadAllQuizzesFromRTDB()
        }

        // FIXED: Ipapasa na ang buong quizItem sa checkQuizStatusAndLaunch
        quizAdapter = StudentQuizAdapter(quizItemList) { quizItem ->
            checkQuizStatusAndLaunch(quizItem)
        }

        when (quizTypeFilter?.lowercase() ?: "quiz") {
            "quiz" -> {
                tvQuizTitle.text = "QUIZZES"
            }
            "exam" -> {
                tvQuizTitle.text = "EXAM"
            }
            else -> {
                tvQuizTitle.text = quizTypeFilter?.uppercase() ?: "QUIZ"
            }
        }

        btnRefresh.setOnClickListener {
            loadAllQuizzesFromRTDB()
        }

        quizAdapter = StudentQuizAdapter(quizItemList) { quizItem ->
            checkQuizStatusAndLaunch(quizItem)
        }
    }

    private fun setupRecyclerView() {
        rvQuizzes.apply {
            layoutManager = LinearLayoutManager(this@StudentQuizListActivity)
            adapter = quizAdapter
            setHasFixedSize(true)
        }
    }

    // BAGONG METHODS PARA SA LOADING AT EMPTY STATE
    private fun showLoading(show: Boolean) {
        if (show) {
            progressCard.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            rvQuizzes.visibility = View.GONE
        } else {
            progressCard.visibility = View.GONE
        }
    }

    private fun showEmptyState(show: Boolean) {
        if (show) {
            emptyState.visibility = View.VISIBLE
            rvQuizzes.visibility = View.GONE
            progressCard.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            rvQuizzes.visibility = View.VISIBLE
        }
    }

    // BAGONG METHOD PARA I-UPDATE ANG STATS
    private fun updateStats(quizzes: List<Quiz>, completedCount: Int) {
        tvTotalQuizzes.text = quizzes.size.toString()
        tvCompletedQuizzes.text = completedCount.toString()
        tvQuizCount.text = "${quizzes.size} active"
    }

    private fun loadAllQuizzesFromRTDB() {
        showLoading(true)

        val quizzesRef = FirebaseDatabase.getInstance().getReference("quizzes")

        quizzesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fetchedQuizzes = mutableListOf<Quiz>()

                snapshot.children.forEach { childSnapshot ->
                    val map = childSnapshot.value as? Map<String, Any> ?: return@forEach
                    val quiz = Quiz(
                        quizId = map["quizId"] as? String ?: "",
                        assignmentId = map["assignmentId"] as? String ?: "",
                        title = map["title"] as? String ?: "",
                        quizType = map["quizType"] as? String ?: "Quiz",
                        description = map["description"] as? String ?: "",
                        scheduledDateTime = (map["scheduledDateTime"] as? Long) ?: 0L,
                        scheduledEndDateTime = (map["scheduledEndDateTime"] as? Long) ?: 0L,
                        isPublished = map["isPublished"] as? Boolean ?: false,
                        questions = ((map["questions"] as? List<Map<String, Any>>)?.mapNotNull { deserializeQuestion(it) }) ?: emptyList()
                    )
                    if (quiz.isPublished) {
                        fetchedQuizzes.add(quiz)
                    }
                }

                val filteredQuizzes = fetchedQuizzes.filter { it.quizType.equals(quizTypeFilter, true) }

                // UPDATE STATS
                updateStats(filteredQuizzes, 0) // 0 muna, i-update later sa fetchStudentStatuses

                // NEW STEP: Fetch student statuses and update the adapter
                fetchStudentStatuses(filteredQuizzes)
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                showEmptyState(true)
                Toast.makeText(this@StudentQuizListActivity, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Assumed function for Question deserialization
    private fun deserializeQuestion(map: Map<String, Any>): Question? {
        val type = map["type"] as? String ?: return null
        val questionText = map["questionText"] as? String ?: ""

        return when (type) {
            "TF" -> Question.TrueFalse(questionText, map["answer"] as? Boolean ?: false)

            "MC" -> {
                val optionsList = (map["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                Question.MultipleChoice(
                    questionText,
                    optionsList,
                    (map["correctAnswerIndex"] as? Number)?.toInt() ?: 0
                )
            }

            "MATCHING" -> {
                // CRITICAL FIX: Properly deserialize matching type questions
                val leftItems = (map["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val rightItems = (map["matches"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                // Ensure we have valid data for matching type
                if (leftItems.isNotEmpty() && rightItems.isNotEmpty()) {
                    Question.Matching(
                        questionText,
                        leftItems,
                        rightItems
                    )
                } else {
                    // Log error and return null if data is invalid
                    Log.e("QuizList", "Invalid matching question data: left=$leftItems, right=$rightItems")
                    null
                }
            }

            else -> {
                Log.e("QuizList", "Unknown question type: $type")
                null
            }
        }
    }

    // FIXED: Nilagyan ng logic para i-load ang score, total questions, at cheat count
    private fun fetchStudentStatuses(quizzes: List<Quiz>) {
        // I-set ang totalQuestions dito bago ang fetch
        val quizItems = quizzes.map {
            StudentQuizItem(
                quiz = it,
                totalQuestions = it.questions.size // I-set ang total questions dito
            )
        }.toMutableList()

        var countdown = quizItems.size
        if (countdown == 0) {
            quizAdapter.updateList(quizItems)
            showLoading(false)
            showEmptyState(quizItems.isEmpty())
            return
        }

        var completedCount = 0

        quizItems.forEachIndexed { index, item ->
            val quizId = item.quiz.quizId
            val assignmentId = item.quiz.assignmentId // KUNIN ANG ASSIGNMENT ID
            val isExamType = item.quiz.quizType.equals("Exam", true)
            FirebaseFirestore.getInstance().collection("quizResults")
                .document("${quizId}_$studentUid")
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val status = doc.getString("status")
                        val deadline = doc.getLong("retakeDeadline") ?: 0L

                        // KRITIKAL NA PAGDADAGDAG: Load Score Data Mula sa Firestore
                        val rawScore = (doc.get("score") as? Number)?.toInt() ?: 0
                        val cheatCount = (doc.get("cheatCount") as? Number)?.toInt() ?: 0

                        item.studentStatus = status ?: "NOT_STARTED"
                        item.retakeDeadline = deadline
                        // I-update ang item na may score
                        item.rawScore = rawScore
                        item.cheatCount = cheatCount

                        // COUNT COMPLETED QUIZZES
                        if (status == "COMPLETED") {
                            completedCount++
                        }
                    } else {
                        // Walang record. Check kung expired na ang time
                        val currentTime = System.currentTimeMillis()

                        item.studentStatus = when {
                            // Priority 1: Kung Exam, default status ay EXAM_READY
                            isExamType -> "EXAM_READY"
                            // Priority 2: Kung tapos na ang oras, MISSED
                            currentTime > item.quiz.scheduledEndDateTime && item.quiz.scheduledEndDateTime > 0L -> "UNATTEMPTED_TIME_EXPIRED"
                            // Priority 3: Kung hindi Exam at hindi pa tapos ang oras
                            else -> "NOT_STARTED"
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("QuizList", "Failed to fetch status for ${quizId}: ${it.message}")
                    item.studentStatus = "FETCH_ERROR"
                }
                .continueWithTask { task ->
                    // STEP 2: FETCH ASSIGNMENT DETAILS PARA SA COURSE CODE AT SUBJECT TITLE
                    if (!assignmentId.isNullOrEmpty()) {
                        FirebaseFirestore.getInstance().collection("classAssignments")
                            .document(assignmentId)
                            .get()
                            .addOnSuccessListener { assignmentDoc ->
                                if (assignmentDoc.exists()) {
                                    item.courseCode = assignmentDoc.getString("subjectCode") ?: ""
                                    item.subjectTitle = assignmentDoc.getString("subjectTitle") ?: ""
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("QuizList", "Failed to fetch assignment details: ${e.message}")
                            }
                    }
                    task
                }
                .addOnCompleteListener {
                    countdown--
                    if (countdown == 0) {
                        quizAdapter.updateList(quizItems)
                        showLoading(false)
                        showEmptyState(quizItems.isEmpty())

                        // UPDATE STATS WITH ACTUAL COMPLETED COUNT
                        updateStats(quizzes, completedCount)
                    }
                }
        }
    }

    // FIXED: Tumatanggap na ng StudentQuizItem
    private fun checkQuizStatusAndLaunch(item: StudentQuizItem) {
        val quiz = item.quiz
        if (quiz.scheduledDateTime == 0L || quiz.scheduledEndDateTime == 0L) {
            Toast.makeText(this, " Schedule is invalid. Please contact your teacher.", Toast.LENGTH_LONG).show()
            return
        }

        val currentTime = System.currentTimeMillis()
        val startTime = quiz.scheduledDateTime
        val endTime = quiz.scheduledEndDateTime

        when {
            // Case 1: NOT YET AVAILABLE
            currentTime < startTime -> {
                val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                Toast.makeText(this, "Will start on ${sdf.format(Date(startTime))}.", Toast.LENGTH_LONG).show()
            }

            // Case 2: ONGOING or EXPIRED - Titingnan ang result sa Firestore
            else -> {
                // FIXED: Ipasa ang buong item
                checkQuizResultsAndDetermineAccess(item, currentTime, endTime)
            }
        }
    }

    // FIXED: Tumatanggap na ng StudentQuizItem at inayos ang logic flow para sa IN_PROGRESS
    private fun checkQuizResultsAndDetermineAccess(item: StudentQuizItem, currentTime: Long, originalEndTime: Long) {
        val quiz = item.quiz
        val isExamType = item.quiz.quizType.equals("Exam", true) // ⭐ DINAGDAG ITO
        // Tumingin sa quizResults kung may record
        FirebaseFirestore.getInstance().collection("quizResults")
            .document("${quiz.quizId}_$studentUid")
            .get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    when {
                        // 1. Walang record, pero Exam type - HARANGAN DITO.
                        isExamType -> {
                            Toast.makeText(this, "Exam access is restricted until the teacher starts it.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        // 2. Walang record, tapos na ang oras - MISSED
                        currentTime > originalEndTime -> {
                            launchStudentResultActivity(item, "MISSED")
                            return@addOnSuccessListener
                        }
                        // 3. Walang record, hindi Exam, at ONGOING ang oras - START QUIZ
                        else -> {
                            launchStudentQuizActivity(quiz)
                            return@addOnSuccessListener
                        }
                    }
                }

                val status = doc.getString("status")
                val retakeDeadline = doc.getLong("retakeDeadline") ?: 0L

                val isRetakeGrantedAndValid = status == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime < retakeDeadline
                val isRevoked = status == "ACCESS_REVOKED"

                // ⭐ [CRITICAL FIX] isFinishedAttempt: Statuses na nagpapahiwatig ng tapos na attempt.
                // In-exclude ang "IN_PROGRESS".
                val isFinishedAttempt = doc.exists() && status in setOf("COMPLETED", "CHEATING", "TIME_EXPIRED")

                // [NEW] isInProgress: Status na nagpapahiwatig na nagsimula na pero hindi pa tapos.
                val isInProgress = doc.exists() && status == "IN_PROGRESS"

                val isOriginalTimeValid = currentTime <= originalEndTime

                when {
                    // A. ACCESS GRANTED (RETAKE/REOPEN)
                    isRetakeGrantedAndValid -> {
                        launchStudentQuizActivity(quiz)
                    }

                    // ⭐ [FIXED CASE] ONGOING and IN_PROGRESS: Dapat makabalik sa Quiz.
                    isOriginalTimeValid && isInProgress -> {
                        launchStudentQuizActivity(quiz)
                    }

                    // B. ONGOING (Original Time) at HINDI pa nag-attempt
                    // Kung hindi pa nagsimula (NOT_STARTED) at ONGOING pa ang time.
                    isOriginalTimeValid && !isFinishedAttempt && !isInProgress -> {
                        launchStudentQuizActivity(quiz)
                    }

                    // C. REVOKED ACCESS
                    isRevoked -> {
                        // FIXED: Ipasa ang item
                        launchStudentResultActivity(item, "REVOKED")
                    }

                    // D. ATTEMPTED/FINISHED (Gumamit na ng isFinishedAttempt)
                    isFinishedAttempt -> {
                        // FIXED: Ipasa ang item
                        launchStudentResultActivity(item, "ATTEMPTED")
                    }

                    // E. TIME EXPIRED (Original Time) at WALANG ATTEMPT
                    currentTime > originalEndTime && status in setOf("NOT_STARTED", null) -> {
                        // FIXED: Ipasa ang item
                        launchStudentResultActivity(item, "MISSED")
                    }

                    // F. RETAKE EXPIRED
                    status == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime >= retakeDeadline -> {
                        // FIXED: Ipasa ang item
                        launchStudentResultActivity(item, "ATTEMPTED") // Treat as ATTEMPTED/FINISHED
                    }

                    // G. DEFAULT CATCH-ALL (e.g., tapos na ang time at wala pang attempt/record)
                    else -> {
                        Toast.makeText(this, "Not available (Status: $status).", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("StudentQuizList", "Error checking result: ${e.message}")
                Toast.makeText(this, "Error checking status. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun launchStudentQuizActivity(quiz: Quiz) {
        val intent = Intent(this, StudentQuizActivity::class.java).apply {
            putExtra("QUIZ", quiz)
        }
        startActivity(intent)
    }

    // FIXED: Tumatanggap na ng StudentQuizItem at ipinapasa ang score data
    private fun launchStudentResultActivity(item: StudentQuizItem, resultStatus: String) {
        val intent = Intent(this, QuizResultActivity::class.java).apply {
            putExtra("QUIZ_ID", item.quiz.quizId)
            putExtra("RESULT_TYPE", resultStatus)

            // KRITIKAL: Ipasa ang Score Data mula sa item
            putExtra("SCORE", item.rawScore)
            putExtra("TOTAL_QUESTIONS", item.totalQuestions)
            putExtra("CHEAT_COUNT", item.cheatCount)
        }
        startActivity(intent)
    }
}
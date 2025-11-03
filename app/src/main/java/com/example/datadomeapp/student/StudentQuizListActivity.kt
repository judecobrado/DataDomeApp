package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.example.datadomeapp.databinding.ActivityQuizResultBinding
import android.content.Intent
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import android.view.View
import android.widget.ProgressBar
import com.google.firebase.database.*
import com.google.firebase.auth.FirebaseAuth

import android.widget.Button
import android.graphics.Color
import android.widget.TextView


class StudentQuizListActivity : AppCompatActivity() {

    private lateinit var rvQuizzes: RecyclerView
    private lateinit var quizAdapter: StudentQuizAdapter
    private val quizzesList = mutableListOf<Quiz>()
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private var quizTypeFilter: String? = null
    private lateinit var tvHeader: TextView

    private val studentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_quiz_list_activity)

        tvHeader = findViewById(R.id.tvHeader)
        rvQuizzes = findViewById(R.id.recyclerViewQuizzes)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        quizTypeFilter = intent.getStringExtra("QUIZ_TYPE") ?: "Quiz"

        // Update header dynamically
        tvHeader.text = quizTypeFilter
        tvHeader.setBackgroundColor(
            if (quizTypeFilter.equals("Exam", true)) Color.parseColor("#C62828")
            else Color.parseColor("#1B5E20")
        )

        quizAdapter = StudentQuizAdapter(quizzesList) { quiz ->

            checkQuizStatusAndLaunch(quiz, studentUid)

            if (studentUid.isEmpty()) {
                Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
                return@StudentQuizAdapter
            }

            checkQuizStatusAndLaunch(quiz, studentUid)

            if (quiz.scheduledDateTime != 0L && quiz.scheduledEndDateTime != 0L) {
                val currentTime = System.currentTimeMillis()
                val startTime = quiz.scheduledDateTime
                val endTime = quiz.scheduledEndDateTime

                if (currentTime in startTime..endTime) {
                    // Quiz is ONGOING: Start the quiz activity
                    val intent = Intent(this, StudentQuizActivity::class.java).apply {
                        // **IMPORTANT:** Pass the whole Quiz object (it must be Serializable)
                        putExtra("QUIZ", quiz)
                    }
                    startActivity(intent)
                } else if (currentTime > endTime) {
                    // Quiz is FINISHED: Show results/status (Optional: Implement a Results Activity)
                    Toast.makeText(this, "Quiz finished. You can view your results now.", Toast.LENGTH_LONG).show()
                    // You would typically launch a StudentResultActivity here
                } else {
                    // Quiz is NOT YET AVAILABLE
                    Toast.makeText(this, "The quiz is not yet available.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Quiz schedule is invalid.", Toast.LENGTH_SHORT).show()
            }
        }

        rvQuizzes.apply {
            layoutManager = LinearLayoutManager(this@StudentQuizListActivity)
            adapter = quizAdapter
        }

        loadAllQuizzesFromRTDB()
    }

    private fun loadAllQuizzesFromRTDB() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val quizzesRef = FirebaseDatabase.getInstance().getReference("quizzes")

        quizzesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val quizList = mutableListOf<Quiz>()

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
                    quizList.add(quiz)
                }

                // Filter by type
                val filtered = quizList.filter { it.quizType.equals(quizTypeFilter, true) }
                quizAdapter.updateList(filtered.toMutableList())

                progressBar.visibility = View.GONE  // Show loading
                tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@StudentQuizListActivity, "Failed to load quizzes.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
            }
        })
    }

    private fun deserializeQuestion(map: Map<String, Any>): Question? {
        val type = map["type"] as? String ?: return null
        val questionText = map["questionText"] as? String ?: ""

        val optionsList = (map["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val matchesList = (map["matches"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        return when (type) {
            "TF" -> Question.TrueFalse(questionText, map["answer"] as? Boolean ?: false)
            "MC" -> Question.MultipleChoice(
                questionText,
                optionsList,
                (map["correctAnswerIndex"] as? Number)?.toInt() ?: 0
            )
            "MATCHING" -> Question.Matching(
                questionText,
                (map["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                (map["matches"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
            else -> null
        }
    }
    private fun checkQuizStatusAndLaunch(quiz: Quiz, studentUid: String) {
        if (quiz.scheduledDateTime == 0L || quiz.scheduledEndDateTime == 0L) {
            Toast.makeText(this, "Quiz schedule is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentTime = System.currentTimeMillis()
        val startTime = quiz.scheduledDateTime
        val endTime = quiz.scheduledEndDateTime

        when {
            // Case 1: ONGOING (Pwedeng mag-umpisa)
            currentTime in startTime..endTime -> {
                // ✅ Logic para sa ONGOING: Titingnan sa result kung may "RETAKE_GRANTED"
                checkIfRetakeGranted(quiz, studentUid)
            }

            // Case 2: NOT YET AVAILABLE
            currentTime < startTime -> {
                Toast.makeText(this, "The quiz will start on ${SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(startTime))}.", Toast.LENGTH_LONG).show()
            }

            // Case 3: FINISHED (Tapos na ang oras)
            currentTime > endTime -> {
                // ✅ Logic para sa FINISHED: Titingnan ang result
                checkIfQuizIsAttempted(quiz, studentUid, true)
            }
        }
    }

    private fun checkIfRetakeGranted(quiz: Quiz, studentUid: String) {
        // 1. Tumingin sa quizResults kung may record
        FirebaseFirestore.getInstance().collection("quizResults")
            .document("${quiz.quizId}_$studentUid")
            .get()
            .addOnSuccessListener { doc ->
                val status = doc.getString("status")
                val retakeDeadline = doc.getLong("retakeDeadline") ?: 0L // ⭐ NEW: Kuhanin ang deadline
                val currentTime = System.currentTimeMillis()

                val isRetakeGrantedAndValid = status == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime < retakeDeadline

                // May Attempt na siya O Tapos na ang Retake Deadline
                if (doc.exists() && status != "RETAKE_GRANTED") {
                    // Case A: Nag-attempt na siya at HINDI Retake Granted: I-launch ang result view.
                    Toast.makeText(this, "You have already attempted this quiz.", Toast.LENGTH_SHORT).show()
                    // LUNCH RESULT ACTIVITY

                } else if (isRetakeGrantedAndValid) {
                    // Case B: RETAKE GRANTED at HINDI PA EXPIRED ang deadline: Pwede mag-start.
                    launchStudentQuizActivity(quiz)

                } else if (status == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime >= retakeDeadline) {
                    // Case C: RETAKE GRANTED PERO EXPIRED NA ang deadline.
                    Toast.makeText(this, "The retake window has expired.", Toast.LENGTH_LONG).show()
                    // HINDI DAPAT MAO-OPEN ANG QUIZ. I-launch ang Result Activity na may status na Expired.

                } else {
                    // Case D: Walang record, o normal na quiz (hindi retake ang isyu)
                    // Ang orihinal na schedule (quiz.scheduledDateTime) ang gagamitin.
                    val startTime = quiz.scheduledDateTime
                    val endTime = quiz.scheduledEndDateTime

                    if (currentTime in startTime..endTime) {
                        launchStudentQuizActivity(quiz)
                    } else {
                        Toast.makeText(this, "Quiz is not available.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking quiz status.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkIfQuizIsAttempted(quiz: Quiz, studentUid: String, isExpired: Boolean) {
        // Tumingin sa quizResults kung may record (Attempted ba?)
        FirebaseFirestore.getInstance().collection("quizResults")
            .document("${quiz.quizId}_$studentUid")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // May record: Nag-attempt. I-launch ang result view.
                    Toast.makeText(this, "Quiz finished. View your final results.", Toast.LENGTH_LONG).show()
                    // LUNCH RESULT ACTIVITY
                } else {
                    // Walang record: Missed/Unattempted. Huwag payagan mag-start.
                    Toast.makeText(this, "You missed the quiz. The schedule has expired.", Toast.LENGTH_LONG).show()
                    // I-launch ang Result Activity na nagpapakita ng 0/Missed.
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking quiz status.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun launchStudentQuizActivity(quiz: Quiz) {
        val intent = Intent(this, StudentQuizActivity::class.java).apply {
            putExtra("QUIZ", quiz)
        }
        startActivity(intent)
    }
}

package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import android.content.Intent
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import android.view.View
import android.widget.ProgressBar
import com.google.firebase.database.*
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
            // --- NEW: Start StudentQuizActivity ---
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

        return when (type) {
            "TF" -> Question.TrueFalse(questionText, map["answer"] as? Boolean ?: false)
            "MC" -> Question.MultipleChoice(
                questionText,
                (map["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                (map["correctAnswerIndex"] as? Long)?.toInt() ?: 0
            )
            "MATCHING" -> Question.Matching(
                questionText,
                (map["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                (map["matches"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
            else -> null
        }
    }
}

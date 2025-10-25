package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuizAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

import java.text.SimpleDateFormat
import java.util.*

class ManageQuizzesActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddQuiz: Button
    private lateinit var tvNoQuizzes: TextView

    private val quizList = mutableListOf<Quiz>()
    private lateinit var adapter: QuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_quizzes)

        recyclerView = findViewById(R.id.recyclerViewQuizzes)
        btnAddQuiz = findViewById(R.id.btnAddQuiz)
        tvNoQuizzes = findViewById(R.id.tvNoQuizzes)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuizAdapter(
            quizList,
            editClickListener = { quiz ->
                (quiz) },
            deleteClickListener = { quiz -> deleteQuiz(quiz) },
            publishClickListener = { quiz -> togglePublish(quiz) },
            setTimeClickListener = { quiz -> setScheduledTime(quiz) }
        )
        recyclerView.adapter = adapter

        btnAddQuiz.setOnClickListener { openQuizEditor(null) }

        loadQuizzes()
    }

    private fun loadQuizzes() {
        val teacherUid = auth.currentUser?.uid ?: return

        db.child("quizzes").orderByChild("teacherUid").equalTo(teacherUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    quizList.clear()
                    for (child in snapshot.children) {
                        val quizId = child.child("quizId").getValue(String::class.java) ?: continue
                        val assignmentId = child.child("assignmentId").getValue(String::class.java) ?: ""
                        val title = child.child("title").getValue(String::class.java) ?: ""
                        val isPublished = child.child("isPublished").getValue(Boolean::class.java) ?: false
                        val scheduledDateTime = child.child("scheduledDateTime").getValue(Long::class.java) ?: 0L

                        val questionsSnapshot = child.child("questions")
                        val questionsList = mutableListOf<com.example.datadomeapp.models.Question>()
                        for (qSnap in questionsSnapshot.children) {
                            val type = qSnap.child("type").getValue(String::class.java)
                            val questionText = qSnap.child("questionText").getValue(String::class.java) ?: ""
                            when (type) {
                                "truefalse" -> {
                                    val answer = qSnap.child("answer").getValue(Boolean::class.java) ?: true
                                    questionsList.add(com.example.datadomeapp.models.Question.TrueFalse(questionText, answer))
                                }
                                "matching" -> {
                                    val options = qSnap.child("options").getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                                    val matches = qSnap.child("matches").getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                                    questionsList.add(com.example.datadomeapp.models.Question.Matching(questionText, options, matches))
                                }
                            }
                        }

                        val quiz = com.example.datadomeapp.models.Quiz(
                            quizId = quizId,
                            assignmentId = assignmentId,
                            teacherUid = teacherUid,
                            title = title,
                            questions = questionsList,
                            isPublished = isPublished,
                            scheduledDateTime = scheduledDateTime
                        )

                        quizList.add(quiz)
                    }
                    adapter.notifyDataSetChanged()
                    toggleEmptyMessage()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ManageQuizzesActivity, "Failed to load quizzes", Toast.LENGTH_SHORT).show()
                }
            })
    }


    private fun toggleEmptyMessage() {
        if (quizList.isEmpty()) {
            fadeIn(tvNoQuizzes)
            recyclerView.visibility = View.GONE
        } else {
            fadeOut(tvNoQuizzes)
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun fadeIn(view: View) {
        view.visibility = View.VISIBLE
        val fade = AlphaAnimation(0f, 1f)
        fade.duration = 300
        view.startAnimation(fade)
    }

    private fun fadeOut(view: View) {
        val fade = AlphaAnimation(1f, 0f)
        fade.duration = 300
        fade.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                view.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        view.startAnimation(fade)
    }

    private fun openQuizEditor(quiz: Quiz?) {
        val intent = Intent(this, CreateQuizActivity::class.java)
        quiz?.let { intent.putExtra("QUIZ_ID", it.quizId) }
        startActivity(intent)
    }

    private fun deleteQuiz(quiz: Quiz) {
        db.child("quizzes").child(quiz.quizId).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete quiz", Toast.LENGTH_SHORT).show()
            }
    }

    private fun togglePublish(quiz: Quiz) {
        val newStatus = !quiz.isPublished
        db.child("quizzes").child(quiz.quizId).child("isPublished").setValue(newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, if (newStatus) "Quiz Published" else "Quiz Unpublished", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update publish status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setScheduledTime(quiz: Quiz) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                        val timestamp = calendar.timeInMillis
                        db.child("quizzes").child(quiz.quizId).child("scheduledDateTime").setValue(timestamp)
                            .addOnSuccessListener {
                                val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                Toast.makeText(this, "Quiz scheduled: ${format.format(Date(timestamp))}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to set time: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

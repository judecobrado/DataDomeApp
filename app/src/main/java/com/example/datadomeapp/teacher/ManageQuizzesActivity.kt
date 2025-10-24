package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuizAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class ManageQuizzesActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddQuiz: Button
    private val quizList = mutableListOf<Quiz>()
    private lateinit var adapter: QuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_quizzes)

        recyclerView = findViewById(R.id.recyclerViewQuizzes)
        btnAddQuiz = findViewById(R.id.btnAddQuiz)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuizAdapter(
            quizList,
            editClickListener = { quiz -> openQuizEditor(quiz) },
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
            .get()
            .addOnSuccessListener { snapshot ->
                quizList.clear()
                for (child in snapshot.children) {
                    val quiz = child.getValue(Quiz::class.java)
                    quiz?.let { quizList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load quizzes", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openQuizEditor(quiz: Quiz?) {
        val intent = Intent(this, QuizEditorActivity::class.java)
        quiz?.let { intent.putExtra("QUIZ_ID", it.quizId) }
        startActivity(intent)
    }

    private fun deleteQuiz(quiz: Quiz) {
        db.child("quizzes").child(quiz.quizId).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz deleted", Toast.LENGTH_SHORT).show()
                val index = quizList.indexOf(quiz)
                if (index != -1) {
                    quizList.removeAt(index)
                    adapter.notifyItemRemoved(index)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete quiz", Toast.LENGTH_SHORT).show()
            }
    }

    private fun togglePublish(quiz: Quiz) {
        val newStatus = !quiz.isPublished
        db.child("quizzes").child(quiz.quizId).child("isPublished").setValue(newStatus)
            .addOnSuccessListener {
                val index = quizList.indexOf(quiz)
                if (index != -1) {
                    quizList[index] = quiz.copy(isPublished = newStatus)
                    adapter.notifyItemChanged(index)
                    Toast.makeText(this, if (newStatus) "Quiz Published" else "Quiz Unpublished", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update publish status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setScheduledTime(quiz: Quiz) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _: android.widget.DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                TimePickerDialog(
                    this,
                    { _, hourOfDay: Int, minute: Int ->
                        calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                        val timestamp = calendar.timeInMillis
                        db.child("quizzes").child(quiz.quizId).child("scheduledDateTime").setValue(timestamp)
                            .addOnSuccessListener {
                                val index = quizList.indexOf(quiz)
                                if (index != -1) {
                                    quizList[index] = quiz.copy(scheduledDateTime = timestamp)
                                    adapter.notifyItemChanged(index)
                                    val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                    Toast.makeText(this, "Quiz scheduled: ${format.format(Date(timestamp))}", Toast.LENGTH_SHORT).show()
                                }
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

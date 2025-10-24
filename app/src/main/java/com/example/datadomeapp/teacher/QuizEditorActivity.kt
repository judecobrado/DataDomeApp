package com.example.datadomeapp.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuestionAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class QuizEditorActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddTF: Button
    private lateinit var btnAddMatching: Button
    private lateinit var btnSaveQuiz: Button
    private lateinit var etQuizTitle: EditText

    private var quizId: String? = null
    private var assignmentId: String? = null
    private val questionList = mutableListOf<Question>()
    private lateinit var adapter: QuestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_editor)

        recyclerView = findViewById(R.id.recyclerViewQuestions)
        btnAddTF = findViewById(R.id.btnAddTF)
        btnAddMatching = findViewById(R.id.btnAddMatching)
        btnSaveQuiz = findViewById(R.id.btnSaveQuiz)
        etQuizTitle = findViewById(R.id.etQuizTitle)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuestionAdapter(
            questionList,
            editClickListener = { question -> editQuestion(question) },
            deleteClickListener = { question -> deleteQuestion(question) }
        )
        recyclerView.adapter = adapter

        quizId = intent.getStringExtra("QUIZ_ID")
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")

        quizId?.let { loadQuiz(it) }

        btnAddTF.setOnClickListener { addTFQuestion() }
        btnAddMatching.setOnClickListener { addMatchingQuestion() }
        btnSaveQuiz.setOnClickListener { saveQuiz() }
    }

    private fun loadQuiz(id: String) {
        db.child("quizzes").child(id).get().addOnSuccessListener { snapshot ->
            val quiz = snapshot.getValue(Quiz::class.java)
            quiz?.let {
                etQuizTitle.setText(it.title)
                questionList.clear()
                questionList.addAll(it.questions)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun addTFQuestion() {
        val input = EditText(this).apply { hint = "Enter True/False question" }
        AlertDialog.Builder(this)
            .setTitle("Add True/False Question")
            .setView(input)
            .setPositiveButton("Next") { dialog, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) askTFAnswer(text)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askTFAnswer(questionText: String) {
        val options = arrayOf("True", "False")
        AlertDialog.Builder(this)
            .setTitle("Select Correct Answer")
            .setItems(options) { _, which ->
                val answer = options[which] == "True"
                questionList.add(Question.TrueFalse(questionText, answer))
                adapter.notifyDataSetChanged()
            }.show()
    }

    private fun addMatchingQuestion() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_matching_question, null)
        val etLeft = view.findViewById<EditText>(R.id.etLeft)
        val etRight = view.findViewById<EditText>(R.id.etRight)

        AlertDialog.Builder(this)
            .setTitle("Add Matching Question (Comma separated)")
            .setView(view)
            .setPositiveButton("Add") { dialog, _ ->
                val left = etLeft.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val right = etRight.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (left.size != right.size || left.isEmpty()) {
                    Toast.makeText(this, "Both sides must have same number of items", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                questionList.add(Question.Matching("Matching Question", left, right))
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editQuestion(question: Question) {
        when (question) {
            is Question.TrueFalse -> editTFQuestion(question)
            is Question.Matching -> editMatchingQuestion(question)
        }
    }

    private fun editTFQuestion(question: Question.TrueFalse) {
        val input = EditText(this).apply { setText(question.questionText) }
        AlertDialog.Builder(this)
            .setTitle("Edit TF Question")
            .setView(input)
            .setPositiveButton("Next") { dialog, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    val options = arrayOf("True", "False")
                    AlertDialog.Builder(this)
                        .setTitle("Select Correct Answer")
                        .setItems(options) { _, which ->
                            val index = questionList.indexOf(question)
                            if (index != -1) {
                                questionList[index] = Question.TrueFalse(newText, options[which] == "True")
                                adapter.notifyItemChanged(index)
                            }
                        }.show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editMatchingQuestion(question: Question.Matching) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_matching_question, null)
        val etLeft = view.findViewById<EditText>(R.id.etLeft)
        val etRight = view.findViewById<EditText>(R.id.etRight)

        etLeft.setText(question.options.joinToString(","))
        etRight.setText(question.matches.joinToString(","))

        AlertDialog.Builder(this)
            .setTitle("Edit Matching Question")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val left = etLeft.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val right = etRight.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (left.size != right.size || left.isEmpty()) {
                    Toast.makeText(this, "Both sides must have same number of items", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val index = questionList.indexOf(question)
                if (index != -1) {
                    questionList[index] = Question.Matching(question.questionText, left, right)
                    adapter.notifyItemChanged(index)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteQuestion(question: Question) {
        val index = questionList.indexOf(question)
        if (index != -1) {
            questionList.removeAt(index)
            adapter.notifyItemRemoved(index)
        }
    }

    private fun saveQuiz() {
        val title = etQuizTitle.text.toString().trim()
        if (title.isEmpty() || questionList.isEmpty()) {
            Toast.makeText(this, "Quiz must have a title and at least one question", Toast.LENGTH_SHORT).show()
            return
        }

        val id = quizId ?: db.child("quizzes").push().key ?: return
        val quiz = Quiz(
            quizId = id,
            assignmentId = assignmentId ?: "",
            teacherUid = auth.currentUser?.uid ?: "",
            title = title,
            questions = questionList.toList(),
            isPublished = false,
            scheduledDateTime = 0L
        )

        db.child("quizzes").child(id).setValue(quiz)
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save quiz: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

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

class CreateQuizActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddTF: Button
    private lateinit var btnAddMatching: Button
    private lateinit var btnAddMC: Button
    private lateinit var btnSaveQuiz: Button
    private lateinit var etQuizTitle: EditText

    private val questionList = mutableListOf<Question>()
    private lateinit var adapter: QuestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_quiz)

        recyclerView = findViewById(R.id.recyclerViewQuestions)
        btnAddTF = findViewById(R.id.btnAddTF)
        btnAddMatching = findViewById(R.id.btnAddMatching)
        btnAddMC = findViewById(R.id.btnAddMC)
        btnSaveQuiz = findViewById(R.id.btnSaveQuiz)
        etQuizTitle = findViewById(R.id.etQuizTitle)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuestionAdapter(
            questionList,
            editClickListener = { editQuestion(it) },
            deleteClickListener = { deleteQuestion(it) }
        )
        recyclerView.adapter = adapter

        btnAddTF.setOnClickListener { addTFQuestion() }
        btnAddMatching.setOnClickListener { addMatchingQuestion() }
        btnAddMC.setOnClickListener { addMCQuestion() }
        btnSaveQuiz.setOnClickListener { saveQuiz() }
    }

    // ------------------ True/False ------------------
    private fun addTFQuestion(existing: Question.TrueFalse? = null) {
        val input = EditText(this).apply {
            setText(existing?.questionText ?: "")
            hint = "Enter True/False question"
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit TF Question" else "Add True/False Question")
            .setView(input)
            .setPositiveButton("Next") { dialog, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) askTFAnswer(text, existing)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askTFAnswer(questionText: String, existing: Question.TrueFalse? = null) {
        val options = arrayOf("True", "False")
        AlertDialog.Builder(this)
            .setTitle("Select Correct Answer")
            .setItems(options) { _, which ->
                val answer = options[which] == "True"
                val newQuestion = Question.TrueFalse(questionText, answer)
                updateQuestion(existing, newQuestion)
            }.show()
    }

    // ------------------ Matching ------------------
    private fun addMatchingQuestion(existing: Question.Matching? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_matching_question, null)
        val etLeft = view.findViewById<EditText>(R.id.etLeft)
        val etRight = view.findViewById<EditText>(R.id.etRight)

        existing?.let {
            etLeft.setText(it.options.joinToString(","))
            etRight.setText(it.matches.joinToString(","))
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Matching Question" else "Add Matching Question")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val left = etLeft.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val right = etRight.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (left.size != right.size || left.isEmpty()) {
                    Toast.makeText(this, "Both sides must have same number of items", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newQuestion = Question.Matching(existing?.questionText ?: "Matching Question", left, right)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------ Multiple Choice ------------------
    private fun addMCQuestion(existing: Question.MultipleChoice? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_multiple_choice, null)

        val etQuestion = view.findViewById<EditText>(R.id.etQuestion)
        val et1 = view.findViewById<EditText>(R.id.etOption1)
        val et2 = view.findViewById<EditText>(R.id.etOption2)
        val et3 = view.findViewById<EditText>(R.id.etOption3)
        val et4 = view.findViewById<EditText>(R.id.etOption4)

        val rb1 = view.findViewById<RadioButton>(R.id.rbOption1)
        val rb2 = view.findViewById<RadioButton>(R.id.rbOption2)
        val rb3 = view.findViewById<RadioButton>(R.id.rbOption3)
        val rb4 = view.findViewById<RadioButton>(R.id.rbOption4)

        existing?.let {
            etQuestion.setText(it.questionText)
            it.options.getOrNull(0)?.let { s -> et1.setText(s) }
            it.options.getOrNull(1)?.let { s -> et2.setText(s) }
            it.options.getOrNull(2)?.let { s -> et3.setText(s) }
            it.options.getOrNull(3)?.let { s -> et4.setText(s) }
            when (it.correctAnswerIndex) {
                0 -> rb1.isChecked = true
                1 -> rb2.isChecked = true
                2 -> rb3.isChecked = true
                3 -> rb4.isChecked = true
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Multiple Choice" else "Add Multiple Choice")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val questionText = etQuestion.text.toString().trim()
                val options = listOf(et1.text.toString(), et2.text.toString(), et3.text.toString(), et4.text.toString())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val correctIndex = when {
                    rb1.isChecked -> 0
                    rb2.isChecked -> 1
                    rb3.isChecked -> 2
                    rb4.isChecked -> 3
                    else -> -1
                }

                if (questionText.isEmpty() || options.size < 2 || correctIndex !in options.indices) {
                    Toast.makeText(this, "Provide question, at least 2 options, and select the correct answer", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newQuestion = Question.MultipleChoice(questionText, options, correctIndex)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------ Edit / Delete ------------------
    private fun editQuestion(question: Question) {
        when (question) {
            is Question.TrueFalse -> addTFQuestion(question)
            is Question.Matching -> addMatchingQuestion(question)
            is Question.MultipleChoice -> addMCQuestion(question)
        }
    }

    private fun deleteQuestion(question: Question) {
        val index = questionList.indexOf(question)
        if (index != -1) {
            questionList.removeAt(index)
            adapter.notifyItemRemoved(index)
        }
    }

    private fun updateQuestion(existing: Question?, newQuestion: Question) {
        if (existing != null) {
            val index = questionList.indexOf(existing)
            if (index != -1) {
                questionList[index] = newQuestion
                adapter.notifyItemChanged(index)
                return
            }
        }
        questionList.add(newQuestion)
        adapter.notifyItemInserted(questionList.size - 1)
    }

    // ------------------ Save ------------------
    private fun saveQuiz() {
        val title = etQuizTitle.text.toString().trim()
        if (title.isEmpty() || questionList.isEmpty()) {
            Toast.makeText(this, "Quiz must have a title and at least one question", Toast.LENGTH_SHORT).show()
            return
        }

        val id = db.child("quizzes").push().key ?: return
        val quiz = Quiz(
            quizId = id,
            assignmentId = "",
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

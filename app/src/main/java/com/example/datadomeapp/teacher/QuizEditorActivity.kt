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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase

class QuizEditorActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddTF: Button
    private lateinit var btnAddMatching: Button
    private lateinit var btnAddMC: Button
    private lateinit var btnSaveQuiz: Button
    private lateinit var etQuizTitle: EditText

    private var quizId: String? = null
    private var assignmentId: String? = null
    private val questionList = mutableListOf<Question>()
    private lateinit var adapter: QuestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_editor)

        initViews()
        setupRecyclerView()
        loadIntentData()
        setupListeners()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewQuestions)
        btnAddTF = findViewById(R.id.btnAddTF)
        btnAddMatching = findViewById(R.id.btnAddMatching)
        btnAddMC = findViewById(R.id.btnAddMC)
        btnSaveQuiz = findViewById(R.id.btnSaveQuiz)
        etQuizTitle = findViewById(R.id.etQuizTitle)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuestionAdapter(questionList, ::editQuestion, ::deleteQuestion)
        recyclerView.adapter = adapter
    }

    private fun loadIntentData() {
        quizId = intent.getStringExtra("QUIZ_ID")
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        quizId?.let { loadQuizFromFirebase(it) }
    }

    private fun setupListeners() {
        btnAddTF.setOnClickListener { showTFQuestionDialog() }
        btnAddMatching.setOnClickListener { showMatchingQuestionDialog() }
        btnAddMC.setOnClickListener { showMCQuestionDialog() }
        btnSaveQuiz.setOnClickListener { saveQuiz() }
    }

    // ------------------ LOAD QUIZ ------------------
    private fun loadQuizFromFirebase(quizId: String) {
        val quizRef = db.child("quizzes").child(quizId)
        quizRef.get().addOnSuccessListener { snapshot ->
            val questions = snapshot.child("questions").children.mapNotNull { it.toQuestion() }
            questionList.clear()
            questionList.addAll(questions)
            adapter.updateQuestions(questionList)  // ✅ Updated
            etQuizTitle.setText(snapshot.child("title").getValue(String::class.java) ?: "")
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to load quiz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun DataSnapshot.toQuestion(): Question? {
        val map = this.value as? Map<String, Any> ?: return null
        val type = map["type"] as? String ?: return null
        val questionText = map["questionText"] as? String ?: ""

        return when (type) {
            "TF" -> Question.TrueFalse(
                questionText = questionText,
                answer = map["answer"] as? Boolean ?: false
            )
            "MC" -> Question.MultipleChoice(
                questionText = questionText,
                options = (map["options"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                correctAnswerIndex = (map["correctAnswerIndex"] as? Long)?.toInt() ?: 0
            )
            "MATCHING" -> Question.Matching(
                questionText = questionText,
                options = (map["options"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                matches = (map["matches"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            else -> null
        }
    }

    // ------------------ EDIT / DELETE ------------------
    private fun editQuestion(question: Question) {
        when (question) {
            is Question.TrueFalse -> showTFQuestionDialog(question)
            is Question.Matching -> showMatchingQuestionDialog(question)
            is Question.MultipleChoice -> showMCQuestionDialog(question)
        }
    }

    private fun deleteQuestion(question: Question) {
        val index = questionList.indexOf(question)
        if (index != -1) {
            questionList.removeAt(index)
            adapter.updateQuestions(questionList)  // update adapter
        }
    }

    private fun updateQuestion(existing: Question?, newQuestion: Question) {
        if (existing != null) {
            val index = questionList.indexOf(existing)
            if (index != -1) {
                questionList[index] = newQuestion
                adapter.updateQuestions(questionList)
                return
            }
        }
        questionList.add(newQuestion)
        adapter.updateQuestions(questionList)
    }

    // ------------------ SAVE QUIZ ------------------
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

    // ------------------ TRUE/FALSE ------------------
    private fun showTFQuestionDialog(existing: Question.TrueFalse? = null) { /* same */ }
    private fun showTFAnswerDialog(questionText: String, existing: Question.TrueFalse? = null) { /* same */ }

    // ------------------ MATCHING ------------------
    private fun showMatchingQuestionDialog(existing: Question.Matching? = null) { /* same */ }

    // ------------------ MULTIPLE CHOICE ------------------
    private fun showMCQuestionDialog(existing: Question.MultipleChoice? = null) { /* same */ }
}

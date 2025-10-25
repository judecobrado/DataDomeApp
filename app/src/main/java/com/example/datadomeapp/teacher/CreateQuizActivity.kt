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
import com.example.datadomeapp.teacher.adapters.MatchingPairAdapter
import com.google.firebase.database.*

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

    private var editingQuizId: String? = null  // for editing existing quiz

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
        adapter = QuestionAdapter(questionList,
            editClickListener = { editQuestion(it) },
            deleteClickListener = { deleteQuestion(it) })
        recyclerView.adapter = adapter

        btnAddTF.setOnClickListener { addTFQuestion() }
        btnAddMatching.setOnClickListener { addMatchingQuestion() }
        btnAddMC.setOnClickListener { addMCQuestion() }
        btnSaveQuiz.setOnClickListener { saveQuiz() }

        // ----------------- Check if editing -----------------
        editingQuizId = intent.getStringExtra("QUIZ_ID")
        editingQuizId?.let { loadExistingQuiz(it) }
    }

    private fun loadExistingQuiz(quizId: String) {
        db.child("quizzes").child(quizId).get().addOnSuccessListener { snapshot ->
            val title = snapshot.child("title").getValue(String::class.java) ?: ""
            etQuizTitle.setText(title)

            val questionsSnapshot = snapshot.child("questions")
            questionList.clear()
            for (qSnap in questionsSnapshot.children) {
                val type = qSnap.child("type").getValue(String::class.java)
                val text = qSnap.child("questionText").getValue(String::class.java) ?: ""

                when (type?.lowercase()) {
                    "tf", "truefalse" -> {
                        val answer = qSnap.child("answer").getValue(Boolean::class.java) ?: true
                        questionList.add(Question.TrueFalse(text, answer))
                    }
                    "matching" -> {
                        val options = qSnap.child("options").getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                        val matches = qSnap.child("matches").getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                        questionList.add(Question.Matching(text, options, matches))
                    }
                    "mc", "multiplechoice" -> {
                        val options = qSnap.child("options").getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                        val correctIndex = qSnap.child("correctAnswerIndex").getValue(Int::class.java) ?: 0
                        questionList.add(Question.MultipleChoice(text, options, correctIndex))
                    }
                }
            }
            adapter.notifyDataSetChanged()
        }
    }

    // -------------------- Add / Edit Questions --------------------
    private fun addTFQuestion(existing: Question.TrueFalse? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_true_false, null)
        val etQuestion = view.findViewById<EditText>(R.id.etTFQuestion)
        val rbTrue = view.findViewById<RadioButton>(R.id.rbTrue)
        val rbFalse = view.findViewById<RadioButton>(R.id.rbFalse)

        // Populate if editing
        existing?.let {
            etQuestion.setText(it.questionText)
            if (it.answer) rbTrue.isChecked = true else rbFalse.isChecked = true
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit True/False Question" else "Add True/False Question")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val questionText = etQuestion.text.toString().trim()
                val answer = when {
                    rbTrue.isChecked -> true
                    rbFalse.isChecked -> false
                    else -> null
                }

                if (questionText.isEmpty() || answer == null) {
                    Toast.makeText(this, "Enter question and select answer", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newQuestion = Question.TrueFalse(questionText, answer)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addMatchingQuestion(existing: Question.Matching? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_matching_question, null)

        // UI Elements based on the new structure
        val recyclerViewPairs = view.findViewById<RecyclerView>(R.id.recyclerViewMatchingPairs)
        val btnAddPair = view.findViewById<Button>(R.id.btnAddPair)

        val questionTitleEditText = view.findViewById<EditText>(R.id.etMatchingQuestionTitle) // I-assume na idinagdag mo ito sa XML

        val pairList = mutableListOf<MatchingPair>()

        // Populate if editing
        existing?.let {
            questionTitleEditText.setText(it.questionText)
            it.options.zip(it.matches).forEach { (left, right) ->
                pairList.add(MatchingPair(left, right))
            }
        }

        // Default na 3 pares kung walang laman
        if (pairList.isEmpty()) { repeat(3) { pairList.add(MatchingPair()) } }

        // Callback para sa pagtanggal ng pares
        val removeCallback: (Int) -> Unit = { position ->
            pairList.removeAt(position)
            recyclerViewPairs.adapter?.notifyItemRemoved(position)
        }

        // I-setup ang Adapter
        val pairAdapter = MatchingPairAdapter(pairList, removeCallback)
        recyclerViewPairs.layoutManager = LinearLayoutManager(this)
        recyclerViewPairs.adapter = pairAdapter

        // I-set up ang Add Pair button
        btnAddPair.setOnClickListener {
            pairList.add(MatchingPair())
            pairAdapter.notifyItemInserted(pairList.size - 1)
            recyclerViewPairs.scrollToPosition(pairList.size - 1)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Matching Question" else "Add Matching Question")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->

                // Kolektahin ang data mula sa adapter at i-filter ang mga walang laman
                val finalPairs = pairList.filter { it.leftTerm.isNotBlank() && it.rightMatch.isNotBlank() }
                val questionText = questionTitleEditText.text.toString().trim()

                if (finalPairs.isEmpty() || questionText.isEmpty()) {
                    Toast.makeText(this, "Quiz must have a question title and at least one complete matching pair.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val leftOptions = finalPairs.map { it.leftTerm }
                val rightMatches = finalPairs.map { it.rightMatch }

                val newQuestion = Question.Matching(questionText, leftOptions, rightMatches)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
        val radioButtons = listOf(rb1, rb2, rb3, rb4)

        // Manual single selection
        radioButtons.forEachIndexed { index, rb ->
            rb.setOnClickListener {
                radioButtons.forEachIndexed { i, otherRb -> otherRb.isChecked = i == index }
            }
        }

        // Populate if editing
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
                val options = listOf(et1, et2, et3, et4).map { it.text.toString().trim() }.filter { it.isNotEmpty() }
                val correctIndex = radioButtons.indexOfFirst { it.isChecked }

                if (questionText.isEmpty() || options.size < 2 || correctIndex == -1) {
                    Toast.makeText(this, "Enter question, at least 2 options, and select correct answer", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newQuestion = Question.MultipleChoice(questionText, options, correctIndex)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editQuestion(question: Question) {
        when (question) {
            is Question.TrueFalse -> addTFQuestion(question)
            is Question.Matching -> addMatchingQuestion(question)
            is Question.MultipleChoice -> addMCQuestion(question)
        }
    }

    private fun deleteQuestion(question: Question) {
        val index = questionList.indexOf(question)
        if (index != -1) { questionList.removeAt(index); adapter.notifyItemRemoved(index) }
    }

    private fun updateQuestion(existing: Question?, newQuestion: Question) {
        if (existing != null) {
            val index = questionList.indexOf(existing)
            if (index != -1) { questionList[index] = newQuestion; adapter.notifyItemChanged(index); return }
        }
        questionList.add(newQuestion)
        adapter.notifyItemInserted(questionList.size - 1)
    }

    private fun saveQuiz() {
        val title = etQuizTitle.text.toString().trim()
        if (title.isEmpty() || questionList.isEmpty()) { Toast.makeText(this, "Quiz must have a title and at least one question", Toast.LENGTH_SHORT).show(); return }

        val quizId = editingQuizId ?: db.child("quizzes").push().key ?: return
        val quiz = Quiz(quizId = quizId, assignmentId = "", teacherUid = auth.currentUser?.uid ?: "", title = title, questions = questionList.toList(), isPublished = false, scheduledDateTime = 0L)
        db.child("quizzes").child(quizId).setValue(quiz)
            .addOnSuccessListener { Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show(); finish() }
            .addOnFailureListener { e -> Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}

// Sa loob ng CreateQuizActivity.kt o sa sariling file, kasama ng models
data class MatchingPair(
    var leftTerm: String = "",
    var rightMatch: String = ""
)
package com.example.datadomeapp.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question // Assumed to be the FLATTENED data class
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuestionAdapter
import com.google.firebase.auth.FirebaseAuth
import com.example.datadomeapp.teacher.adapters.MatchingPairAdapter
import com.google.firebase.database.*
import com.google.firebase.database.GenericTypeIndicator

// 🛑 IMPORTANT: Your Question model MUST be the FLATTENED data class for this code to work:
/*
data class Question(
    val questionText: String = "",
    val type: String = "", // "TF", "MC", "MATCHING"
    val answer: Boolean? = null,
    val options: List<String>? = null,
    val correctAnswerIndex: Int? = null,
    val matches: List<String>? = null
)
*/

class CreateQuizActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddTF: Button
    private lateinit var btnAddMatching: Button
    private lateinit var btnAddMC: Button
    private lateinit var btnSaveQuiz: Button
    private lateinit var etQuizTitle: EditText
    private var currentAssignmentId: String? = null
    private val questionList = mutableListOf<Question>()
    private lateinit var adapter: QuestionAdapter

    private var editingQuizId: String? = null

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
        // QuestionAdapter also needs to be updated to accept the single Question data class
        adapter = QuestionAdapter(
            questionList,
            editClickListener = { editQuestion(it) },
            deleteClickListener = { deleteQuestion(it) })
        recyclerView.adapter = adapter

        btnAddTF.setOnClickListener { addTFQuestion() }
        btnAddMatching.setOnClickListener { addMatchingQuestion() }
        btnAddMC.setOnClickListener { addMCQuestion() }
        btnSaveQuiz.setOnClickListener { saveQuiz() }

        // ----------------- Check if editing -----------------
        editingQuizId = intent.getStringExtra("QUIZ_ID")
        currentAssignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        editingQuizId?.let { loadExistingQuiz(it) }

        if (currentAssignmentId.isNullOrEmpty() && editingQuizId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Quiz must be associated with a class.", Toast.LENGTH_LONG)
                .show()
            finish()
            return
        }
    }

    // --------------------------------------------------------
    // ✅ FIX: Load data directly into the single Question data class
    // --------------------------------------------------------
    private fun loadExistingQuiz(quizId: String) {
        // Since the Question model is now a single concrete data class,
        // the default Firebase mapper can load it directly.
        db.child("quizzes").child(quizId).get().addOnSuccessListener { snapshot ->
            val fetchedQuiz = snapshot.getValue(Quiz::class.java)

            if (fetchedQuiz == null) {
                Toast.makeText(this, "Error loading existing quiz data.", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            etQuizTitle.setText(fetchedQuiz.title)
            questionList.clear()
            // The questions list is automatically loaded correctly by the Firebase mapper
            // because Question is now a concrete data class.
            questionList.addAll(fetchedQuiz.questions)
            adapter.notifyDataSetChanged()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to load quiz: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // --------------------------------------------------------
    // ✅ FIX: Creation functions now instantiate the single FLATTENED Question class
    // --------------------------------------------------------
    private fun addTFQuestion(existing: Question? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_true_false, null)
        val etQuestion = view.findViewById<EditText>(R.id.etTFQuestion)
        val rbTrue = view.findViewById<RadioButton>(R.id.rbTrue)
        val rbFalse = view.findViewById<RadioButton>(R.id.rbFalse)

        // Populate if editing (Check type safety is not needed, just read the fields)
        existing?.let {
            etQuestion.setText(it.questionText)
            it.answer?.let { answer ->
                if (answer) rbTrue.isChecked = true else rbFalse.isChecked = true
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit True/False Question" else "Add True/False Question")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val questionText = etQuestion.text.toString().trim()
                val answer: Boolean? = when {
                    rbTrue.isChecked -> true
                    rbFalse.isChecked -> false
                    else -> null
                }

                if (questionText.length < 2) {
                    Toast.makeText(this, "Question must have at least 2 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (answer == null) {
                    Toast.makeText(this, "Please select either True or False as the answer.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 🎯 CONSTRUCT THE FLATTENED QUESTION OBJECT
                val newQuestion = Question(
                    questionText = questionText,
                    type = "TF",
                    answer = answer, // Only this is set
                    options = null,
                    correctAnswerIndex = null,
                    matches = null
                )
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addMatchingQuestion(existing: Question? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_matching_question, null)

        val recyclerViewPairs = view.findViewById<RecyclerView>(R.id.recyclerViewMatchingPairs)
        val btnAddPair = view.findViewById<Button>(R.id.btnAddPair)
        val questionTitleEditText = view.findViewById<EditText>(R.id.etMatchingQuestionTitle)

        val pairList = mutableListOf<MatchingPair>()
        val maxPairs = 20
        val minPairs = 2

        // Populate if editing
        existing?.let {
            questionTitleEditText.setText(it.questionText)
            // Use safe calls since options and matches are now nullable List<String>?
            if (it.options != null && it.matches != null && it.options.size == it.matches.size) {
                it.options.zip(it.matches).forEach { (left, right) ->
                    pairList.add(MatchingPair(left, right))
                }
            }
        }

        if (pairList.isEmpty()) {
            repeat(minPairs) { pairList.add(MatchingPair()) }
        }

        val removeCallback: (Int) -> Unit = { position ->
            pairList.removeAt(position)
            recyclerViewPairs.adapter?.notifyItemRemoved(position)
            btnAddPair.isEnabled = pairList.size < maxPairs
        }

        val pairAdapter = MatchingPairAdapter(pairList, removeCallback)
        recyclerViewPairs.layoutManager = LinearLayoutManager(this)
        recyclerViewPairs.adapter = pairAdapter

        btnAddPair.isEnabled = pairList.size < maxPairs
        btnAddPair.setOnClickListener {
            if (pairList.size < maxPairs) {
                pairList.add(MatchingPair())
                pairAdapter.notifyItemInserted(pairList.size - 1)
                recyclerViewPairs.scrollToPosition(pairList.size - 1)
                btnAddPair.isEnabled = pairList.size < maxPairs
            } else {
                Toast.makeText(this, "Maximum of $maxPairs matching pairs reached.", Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Matching Question" else "Add Matching Question")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val questionText = questionTitleEditText.text.toString().trim()
                val finalPairs = pairList.filter { it.leftTerm.length >= 1 && it.rightMatch.length >= 1 }

                if (questionText.length < 2) {
                    Toast.makeText(this, "Question title must have at least 2 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (finalPairs.size < minPairs) {
                    Toast.makeText(this, "Matching Quiz must have at least $minPairs complete matching pairs (1 character min per term).", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val leftOptions = finalPairs.map { it.leftTerm }
                val rightMatches = finalPairs.map { it.rightMatch }

                // 🎯 CONSTRUCT THE FLATTENED QUESTION OBJECT
                val newQuestion = Question(
                    questionText = questionText,
                    type = "MATCHING",
                    answer = null,
                    options = leftOptions, // Used for Matching terms (left side)
                    correctAnswerIndex = null,
                    matches = rightMatches // Used for Matching answers (right side)
                )
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addMCQuestion(existing: Question? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_multiple_choice, null)

        val etQuestion = view.findViewById<EditText>(R.id.etQuestion)
        val etOptions = listOf<EditText>(
            view.findViewById(R.id.etOption1),
            view.findViewById(R.id.etOption2),
            view.findViewById(R.id.etOption3),
            view.findViewById(R.id.etOption4)
        )

        val radioButtons = listOf<RadioButton>(
            view.findViewById(R.id.rbOption1),
            view.findViewById(R.id.rbOption2),
            view.findViewById(R.id.rbOption3),
            view.findViewById(R.id.rbOption4)
        )

        val minOptionChars = 1

        radioButtons.forEachIndexed { index, rb ->
            rb.setOnClickListener {
                val optionText = etOptions[index].text.toString().trim()
                if (optionText.length < minOptionChars) {
                    Toast.makeText(this, "The selected option must have at least $minOptionChars character.", Toast.LENGTH_SHORT).show()
                    rb.isChecked = false
                } else {
                    radioButtons.forEachIndexed { i, otherRb -> otherRb.isChecked = i == index }
                }
            }
        }

        etOptions.forEachIndexed { index, et ->
            et.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = et.text.toString().trim()
                    if (text.length < minOptionChars && radioButtons[index].isChecked) {
                        radioButtons[index].isChecked = false
                        Toast.makeText(this, "The option is too short and has been unchecked.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Populate if editing
        existing?.let {
            etQuestion.setText(it.questionText)
            // Use safe call for options since it is nullable
            it.options?.forEachIndexed { index, s ->
                etOptions.getOrNull(index)?.setText(s)
            }
            it.correctAnswerIndex?.let { index ->
                radioButtons.getOrNull(index)?.isChecked = true
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Multiple Choice" else "Add Multiple Choice")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val questionText = etQuestion.text.toString().trim()
                val allOptions = etOptions.map { it.text.toString().trim() }
                val validOptions = allOptions.filter { it.length >= minOptionChars }
                val correctIndex = radioButtons.indexOfFirst { it.isChecked }

                if (questionText.length < 2) {
                    Toast.makeText(this, "Question must have at least 2 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (validOptions.size < 2) {
                    Toast.makeText(this, "You need at least 2 valid options (min $minOptionChars character each).", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (correctIndex == -1) {
                    Toast.makeText(this, "Please select the correct answer.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Re-calculate the correct index based *only* on the validOptions list
                val newCorrectIndex = validOptions.indexOf(allOptions[correctIndex])
                if (newCorrectIndex == -1) {
                    Toast.makeText(this, "Correct answer is invalid or too short. Please re-select.", Toast.LENGTH_SHORT).show()
                    radioButtons[correctIndex].isChecked = false
                    return@setOnClickListener
                }

                // 🎯 CONSTRUCT THE FLATTENED QUESTION OBJECT
                val newQuestion = Question(
                    questionText = questionText,
                    type = "MC",
                    answer = null,
                    options = validOptions,
                    correctAnswerIndex = newCorrectIndex, // Index relative to validOptions list
                    matches = null
                )
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun editQuestion(question: Question) {
        // Since Question is one class, use the 'type' field to determine which dialog to open
        when (question.type) {
            "TF" -> addTFQuestion(question)
            "MATCHING" -> addMatchingQuestion(question)
            "MC" -> addMCQuestion(question)
            else -> Toast.makeText(this, "Unknown question type.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteQuestion(question: Question) {
        val index = questionList.indexOf(question)
        if (index != -1) {
            questionList.removeAt(index); adapter.notifyItemRemoved(index)
        }
    }

    private fun updateQuestion(existing: Question?, newQuestion: Question) {
        if (existing != null) {
            val index = questionList.indexOf(existing)
            if (index != -1) {
                questionList[index] = newQuestion; adapter.notifyItemChanged(index); return
            }
        }
        questionList.add(newQuestion)
        adapter.notifyItemInserted(questionList.size - 1)
    }

    // --------------------------------------------------------
    // ✅ SAVE LOGIC: Minor adjustments for robustness.
    // --------------------------------------------------------

    private fun saveQuiz() {
        val title = etQuizTitle.text.toString().trim()

        if (title.isEmpty() || title.length < 2) {
            Toast.makeText(this, "Quiz title must have at least 2 characters.", Toast.LENGTH_SHORT).show();
            return
        }
        if (questionList.isEmpty()) {
            Toast.makeText(this, "Quiz must have at least one question.", Toast.LENGTH_SHORT).show();
            return
        }

        val quizId = editingQuizId ?: db.child("quizzes").push().key ?: return

        val intentAssignmentId = currentAssignmentId ?: ""
        if (intentAssignmentId.isEmpty() && editingQuizId == null) {
            Toast.makeText(this, "Error: Cannot save quiz without a class assignment ID.", Toast.LENGTH_LONG).show()
            return
        }

        db.child("quizzes").child(quizId).get().addOnSuccessListener { snapshot ->

            // Get existing metadata or use defaults
            val oldIsPublished = snapshot.child("isPublished").getValue(Boolean::class.java) ?: false
            val oldScheduledDateTime = snapshot.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
            val oldScheduledEndDateTime = snapshot.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L

            // Prioritize the assignmentId from the database if editing, otherwise use the Intent ID
            val finalAssignmentId = snapshot.child("assignmentId").getValue(String::class.java)
                .takeIf { !it.isNullOrEmpty() }
                ?: intentAssignmentId

            val quiz = Quiz(
                quizId = quizId,
                assignmentId = finalAssignmentId,
                teacherUid = auth.currentUser?.uid ?: "",
                title = title,
                questions = questionList.toList(),
                isPublished = oldIsPublished,
                scheduledDateTime = oldScheduledDateTime,
                scheduledEndDateTime = oldScheduledEndDateTime
                // totalPoints defaults to 0, which is fine before grading
            )

            db.child("quizzes").child(quizId).setValue(quiz)
                .addOnSuccessListener {
                    Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}

// Data class for Matching pairs (no change needed here)
data class MatchingPair(
    var leftTerm: String = "",
    var rightMatch: String = ""
)
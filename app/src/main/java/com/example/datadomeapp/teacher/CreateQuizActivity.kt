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

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit True/False Question" else "Add True/False Question")
            .setView(view)
            .setPositiveButton("Save", null) // Set null listener initially
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

                // *** VALIDATION LOGIC FOR TRUE/FALSE (Question: min 2 chars) ***
                if (questionText.length < 2) {
                    Toast.makeText(this, "Question must have at least 2 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Don't close dialog
                }
                if (answer == null) {
                    Toast.makeText(this, "Please select either True or False as the answer.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Don't close dialog
                }
                // ***************************************

                val newQuestion = Question.TrueFalse(questionText, answer)
                updateQuestion(existing, newQuestion)
                dialog.dismiss() // Close dialog on success
            }
        }
        dialog.show()
    }

    private fun addMatchingQuestion(existing: Question.Matching? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_matching_question, null)

        val recyclerViewPairs = view.findViewById<RecyclerView>(R.id.recyclerViewMatchingPairs)
        val btnAddPair = view.findViewById<Button>(R.id.btnAddPair)
        val questionTitleEditText = view.findViewById<EditText>(R.id.etMatchingQuestionTitle)

        val pairList = mutableListOf<MatchingPair>()
        val maxPairs = 20
        val minPairs = 2 // Bagong requirement

        // Populate if editing
        existing?.let {
            questionTitleEditText.setText(it.questionText)
            it.options.zip(it.matches).forEach { (left, right) ->
                pairList.add(MatchingPair(left, right))
            }
        }

        // Default na 3 pares (o minPairs kung mas mataas) kung walang laman
        if (pairList.isEmpty()) { repeat(minPairs) { pairList.add(MatchingPair()) } }

        val removeCallback: (Int) -> Unit = { position ->
            pairList.removeAt(position)
            recyclerViewPairs.adapter?.notifyItemRemoved(position)
            btnAddPair.isEnabled = pairList.size < maxPairs // Re-enable if needed
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
            .setPositiveButton("Save", null) // Set null listener initially
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val questionText = questionTitleEditText.text.toString().trim()

                // Kolektahin ang data mula sa adapter at i-filter ang mga walang laman
                // Validation for Left/Right: at least 1 character
                val finalPairs = pairList.filter { it.leftTerm.length >= 1 && it.rightMatch.length >= 1 }

                // *** VALIDATION LOGIC FOR MATCHING ***
                if (questionText.length < 2) {
                    Toast.makeText(this, "Question title must have at least 2 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // I-check kung naabot ang minimum na 2 pares
                if (finalPairs.size < minPairs) {
                    Toast.makeText(this, "Matching Quiz must have at least $minPairs complete matching pairs (1 character min per term).", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // ***************************************

                val leftOptions = finalPairs.map { it.leftTerm }
                val rightMatches = finalPairs.map { it.rightMatch }

                val newQuestion = Question.Matching(questionText, leftOptions, rightMatches)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addMCQuestion(existing: Question.MultipleChoice? = null) {
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

        // Option character requirement
        val minOptionChars = 1

        // Manual single selection + Validation for Radio Button
        radioButtons.forEachIndexed { index, rb ->
            rb.setOnClickListener {
                val optionText = etOptions[index].text.toString().trim()
                if (optionText.length < minOptionChars) {
                    Toast.makeText(this, "The selected option must have at least $minOptionChars character.", Toast.LENGTH_SHORT).show()
                    rb.isChecked = false // Prevent selecting if invalid
                } else {
                    // Allow single selection
                    radioButtons.forEachIndexed { i, otherRb -> otherRb.isChecked = i == index }
                }
            }
        }

        // Validation: If an option becomes empty/too short, uncheck its radio button
        etOptions.forEachIndexed { index, et ->
            et.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) { // When focus leaves
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
            it.options.forEachIndexed { index, s ->
                etOptions.getOrNull(index)?.setText(s)
            }
            it.correctAnswerIndex.let { index ->
                radioButtons.getOrNull(index)?.isChecked = true
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Multiple Choice" else "Add Multiple Choice")
            .setView(view)
            .setPositiveButton("Save", null) // Set null listener initially
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val questionText = etQuestion.text.toString().trim()

                val allOptions = etOptions.map { it.text.toString().trim() }

                // Keep only valid options (1+ character) for final list
                val validOptions = allOptions.filter { it.length >= minOptionChars }

                val correctIndex = radioButtons.indexOfFirst { it.isChecked }

                // *** VALIDATION LOGIC FOR MULTIPLE CHOICE (Question: min 2 chars; Options: min 1 char) ***
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
                    // This means the selected radio button corresponds to an option that is now invalid
                    Toast.makeText(this, "Correct answer is invalid or too short. Please re-select.", Toast.LENGTH_SHORT).show()
                    radioButtons[correctIndex].isChecked = false // Uncheck it
                    return@setOnClickListener
                }
                // *******************************************

                val newQuestion = Question.MultipleChoice(questionText, validOptions, newCorrectIndex)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
            }
        }
        dialog.show()
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
        // Quiz Title validation remains 2 characters
        if (title.isEmpty() || title.length < 2) {
            Toast.makeText(this, "Quiz title must have at least 2 characters.", Toast.LENGTH_SHORT).show();
            return
        }
        if (questionList.isEmpty()) {
            Toast.makeText(this, "Quiz must have at least one question.", Toast.LENGTH_SHORT).show();
            return
        }

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
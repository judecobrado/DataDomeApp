package com.example.datadomeapp.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuestionAdapter
import com.example.datadomeapp.teacher.adapters.MatchingPairAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.BufferedReader
import java.io.InputStreamReader

class CreateQuizActivity : AppCompatActivity() {
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddTF: Button
    private lateinit var btnAddMatching: Button
    private lateinit var btnAddMC: Button
    private lateinit var btnSaveQuiz: Button
    private lateinit var btnUploadQuiz: Button // NEW
    private lateinit var etQuizTitle: EditText
    private var currentAssignmentId: String? = null
    private val questionList = mutableListOf<Question>()
    private lateinit var adapter: QuestionAdapter
    private var editingQuizId: String? = null

    // NEW: File picker for upload
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                processUploadedFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_quiz)

        recyclerView = findViewById(R.id.recyclerViewQuestions)
        btnAddTF = findViewById(R.id.btnAddTF)
        btnAddMatching = findViewById(R.id.btnAddMatching)
        btnAddMC = findViewById(R.id.btnAddMC)
        btnSaveQuiz = findViewById(R.id.btnSaveQuiz)
        btnUploadQuiz = findViewById(R.id.btnUploadQuiz) // NEW
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
        btnUploadQuiz.setOnClickListener { openFilePicker() } // NEW

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

    // NEW: Open file picker for TXT files
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "text/plain"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        filePickerLauncher.launch(Intent.createChooser(intent, "Select Quiz TXT File"))
    }

    // NEW: Process uploaded TXT file
    // NEW: Process uploaded TXT file
    private fun processUploadedFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()

                var uploadedCount = 0
                var skippedCount = 0
                var currentMatchingQuestion: MutableList<Pair<String, String>>? = null
                var currentMatchingTitle = "Matching Question"

                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) {
                        // Save current matching question if exists when encountering empty line
                        currentMatchingQuestion?.let { pairs ->
                            if (pairs.size >= 2) {
                                val leftOptions = pairs.map { it.first }
                                val rightMatches = pairs.map { it.second }
                                questionList.add(Question.Matching(currentMatchingTitle, leftOptions, rightMatches))
                                uploadedCount++
                            } else {
                                skippedCount += pairs.size
                            }
                            currentMatchingQuestion = null
                        }
                        continue
                    }

                    val parts = when {
                        line.contains("|") -> line.split("|").map { it.trim() }
                        line.contains(",") -> line.split(",").map { it.trim() }
                        else -> line.split("\t").map { it.trim() }
                    }

                    when (parts.size) {
                        // True/False format: Question|True|False|Answer
                        4 -> {
                            // Save current matching question if exists before processing TF
                            currentMatchingQuestion?.let { pairs ->
                                if (pairs.size >= 2) {
                                    val leftOptions = pairs.map { it.first }
                                    val rightMatches = pairs.map { it.second }
                                    questionList.add(Question.Matching(currentMatchingTitle, leftOptions, rightMatches))
                                    uploadedCount++
                                } else {
                                    skippedCount += pairs.size
                                }
                                currentMatchingQuestion = null
                            }

                            val questionText = parts[0]
                            val option1 = parts[1]
                            val option2 = parts[2]
                            val answerText = parts[3]

                            // Check if it's True/False format
                            if ((option1.equals("True", true) && option2.equals("False", true)) ||
                                (option1.equals("False", true) && option2.equals("True", true))) {

                                val answer = when (answerText.uppercase()) {
                                    "TRUE", "T" -> true
                                    "FALSE", "F" -> false
                                    else -> null
                                }

                                if (answer != null && questionText.length >= 2) {
                                    questionList.add(Question.TrueFalse(questionText, answer))
                                    uploadedCount++
                                } else {
                                    skippedCount++
                                }
                            } else {
                                skippedCount++
                            }
                        }

                        // Multiple Choice format: Question|Option1|Option2|Option3|Option4|Answer
                        6 -> {
                            // Save current matching question if exists before processing MC
                            currentMatchingQuestion?.let { pairs ->
                                if (pairs.size >= 2) {
                                    val leftOptions = pairs.map { it.first }
                                    val rightMatches = pairs.map { it.second }
                                    questionList.add(Question.Matching(currentMatchingTitle, leftOptions, rightMatches))
                                    uploadedCount++
                                } else {
                                    skippedCount += pairs.size
                                }
                                currentMatchingQuestion = null
                            }

                            val questionText = parts[0]
                            val options = parts.subList(1, 5)
                            val answer = parts[5]

                            // Find correct answer index
                            val correctIndex = when (answer.uppercase()) {
                                "A", options[0].uppercase() -> 0
                                "B", options[1].uppercase() -> 1
                                "C", options[2].uppercase() -> 2
                                "D", options[3].uppercase() -> 3
                                else -> -1
                            }

                            if (questionText.length >= 2 && options.all { it.length >= 1 } && correctIndex != -1) {
                                questionList.add(Question.MultipleChoice(questionText, options, correctIndex))
                                uploadedCount++
                            } else {
                                skippedCount++
                            }
                        }

                        // Matching format: LeftTerm|RightMatch
                        2 -> {
                            val leftTerm = parts[0]
                            val rightMatch = parts[1]

                            if (leftTerm.length >= 1 && rightMatch.length >= 1) {
                                // Initialize or add to current matching question
                                if (currentMatchingQuestion == null) {
                                    currentMatchingQuestion = mutableListOf()
                                }

                                // Check if current batch is full (20 pairs)
                                if (currentMatchingQuestion!!.size >= 20) {
                                    // Save current batch and start new one
                                    val leftOptions = currentMatchingQuestion!!.map { it.first }
                                    val rightMatches = currentMatchingQuestion!!.map { it.second }
                                    questionList.add(Question.Matching(currentMatchingTitle, leftOptions, rightMatches))
                                    uploadedCount++

                                    // Start new batch
                                    currentMatchingQuestion = mutableListOf()
                                    currentMatchingTitle = "Matching Question ${uploadedCount + 1}"
                                }

                                currentMatchingQuestion!!.add(Pair(leftTerm, rightMatch))
                            } else {
                                skippedCount++
                            }
                        }

                        else -> {
                            skippedCount++
                        }
                    }
                }

                // Save any remaining matching question after processing all lines
                currentMatchingQuestion?.let { pairs ->
                    if (pairs.size >= 2) {
                        val leftOptions = pairs.map { it.first }
                        val rightMatches = pairs.map { it.second }
                        questionList.add(Question.Matching(currentMatchingTitle, leftOptions, rightMatches))
                        uploadedCount++
                    } else {
                        skippedCount += pairs.size
                    }
                }

                adapter.notifyDataSetChanged()

                Toast.makeText(
                    this,
                    "Uploaded: $uploadedCount questions, Skipped: $skippedCount lines",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    // NEW: Parse question from text line
    private fun parseQuestionFromLine(line: String): Question? {
        return try {
            // Try different delimiters
            val parts = when {
                line.contains("|") -> line.split("|").map { it.trim() }
                line.contains(",") -> line.split(",").map { it.trim() }
                else -> line.split("\t").map { it.trim() }
            }

            when (parts.size) {
                // True/False format: Question|True|False|Answer
                4 -> {
                    val questionText = parts[0]
                    val option1 = parts[1]
                    val option2 = parts[2]
                    val answerText = parts[3]

                    // Check if it's True/False format
                    if ((option1.equals("True", true) && option2.equals("False", true)) ||
                        (option1.equals("False", true) && option2.equals("True", true))) {

                        val answer = when (answerText.uppercase()) {
                            "TRUE", "T" -> true
                            "FALSE", "F" -> false
                            else -> null
                        }

                        if (answer != null && questionText.length >= 2) {
                            Question.TrueFalse(questionText, answer)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

                // Multiple Choice format: Question|Option1|Option2|Option3|Option4|Answer
                6 -> {
                    val questionText = parts[0]
                    val options = parts.subList(1, 5)
                    val answer = parts[5]

                    // Find correct answer index
                    val correctIndex = when (answer.uppercase()) {
                        "A", options[0].uppercase() -> 0
                        "B", options[1].uppercase() -> 1
                        "C", options[2].uppercase() -> 2
                        "D", options[3].uppercase() -> 3
                        else -> -1
                    }

                    if (questionText.length >= 2 && options.all { it.length >= 1 } && correctIndex != -1) {
                        Question.MultipleChoice(questionText, options, correctIndex)
                    } else {
                        null
                    }
                }

                // Matching format: LeftTerm|RightMatch (multiple lines for one matching question)
                2 -> {
                    val leftTerm = parts[0]
                    val rightMatch = parts[1]

                    if (leftTerm.length >= 1 && rightMatch.length >= 1) {
                        // For now, create individual matching pairs
                        // You might want to group these differently based on your needs
                        Question.Matching(
                            "Matching Question",
                            listOf(leftTerm),
                            listOf(rightMatch)
                        )
                    } else {
                        null
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
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
                        val options = qSnap.child("options")
                            .getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                        val matches = qSnap.child("matches")
                            .getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                        questionList.add(Question.Matching(text, options, matches))
                    }
                    "mc", "multiplechoice" -> {
                        val options = qSnap.child("options")
                            .getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
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
                    Toast.makeText(
                        this,
                        "Question must have at least 2 characters.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (answer == null) {
                    Toast.makeText(
                        this,
                        "Please select either True or False as the answer.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val newQuestion = Question.TrueFalse(questionText, answer)
                updateQuestion(existing, newQuestion)
                dialog.dismiss()
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
        val minPairs = 2

        // Populate if editing
        existing?.let {
            questionTitleEditText.setText(it.questionText)
            it.options.zip(it.matches).forEach { (left, right) ->
                pairList.add(MatchingPair(left, right))
            }
        }

        // Default na 3 pares kung walang laman
        if (pairList.isEmpty()) {
            repeat(minPairs) {
                pairList.add(MatchingPair())
            }
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
                Toast.makeText(
                    this,
                    "Maximum of $maxPairs matching pairs reached.",
                    Toast.LENGTH_SHORT
                ).show()
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
                    Toast.makeText(
                        this,
                        "Question title must have at least 2 characters.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (finalPairs.size < minPairs) {
                    Toast.makeText(
                        this,
                        "Matching Quiz must have at least $minPairs complete matching pairs (1 character min per term).",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

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

        val minOptionChars = 1

        // Manual single selection + Validation for Radio Button
        radioButtons.forEachIndexed { index, rb ->
            rb.setOnClickListener {
                val optionText = etOptions[index].text.toString().trim()
                if (optionText.length < minOptionChars) {
                    Toast.makeText(
                        this,
                        "The selected option must have at least $minOptionChars character.",
                        Toast.LENGTH_SHORT
                    ).show()
                    rb.isChecked = false
                } else {
                    radioButtons.forEachIndexed { i, otherRb ->
                        otherRb.isChecked = i == index
                    }
                }
            }
        }

        // Validation: If an option becomes empty/too short, uncheck its radio button
        etOptions.forEachIndexed { index, et ->
            et.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = et.text.toString().trim()
                    if (text.length < minOptionChars && radioButtons[index].isChecked) {
                        radioButtons[index].isChecked = false
                        Toast.makeText(
                            this,
                            "The option is too short and has been unchecked.",
                            Toast.LENGTH_SHORT
                        ).show()
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
                    Toast.makeText(
                        this,
                        "Question must have at least 2 characters.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (validOptions.size < 2) {
                    Toast.makeText(
                        this,
                        "You need at least 2 valid options (min $minOptionChars character each).",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (correctIndex == -1) {
                    Toast.makeText(this, "Please select the correct answer.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                val newCorrectIndex = validOptions.indexOf(allOptions[correctIndex])
                if (newCorrectIndex == -1) {
                    Toast.makeText(
                        this,
                        "Correct answer is invalid or too short. Please re-select.",
                        Toast.LENGTH_SHORT
                    ).show()
                    radioButtons[correctIndex].isChecked = false
                    return@setOnClickListener
                }

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

    private fun saveQuiz() {
        val title = etQuizTitle.text.toString().trim()
        if (title.isEmpty() || title.length < 2) {
            Toast.makeText(this, "Quiz title must have at least 2 characters.", Toast.LENGTH_SHORT)
                .show(); return
        }
        if (questionList.isEmpty()) {
            Toast.makeText(this, "Quiz must have at least one question.", Toast.LENGTH_SHORT)
                .show(); return
        }

        val quizId = editingQuizId ?: db.child("quizzes").push().key ?: return
        val intentAssignmentId = currentAssignmentId ?: ""

        if (intentAssignmentId.isEmpty() && editingQuizId == null) {
            Toast.makeText(
                this,
                "Error: Cannot save quiz without a class assignment ID.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        db.child("quizzes").child(quizId).get().addOnSuccessListener { snapshot ->
            val oldIsPublished = snapshot.child("isPublished").getValue(Boolean::class.java) ?: false
            val oldScheduledDateTime = snapshot.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
            val oldScheduledEndDateTime = snapshot.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L

            val finalAssignmentId = snapshot.child("assignmentId").getValue(String::class.java)
                .takeIf { !it.isNullOrEmpty() } ?: intentAssignmentId

            val quiz = Quiz(
                quizId = quizId,
                assignmentId = finalAssignmentId,
                teacherUid = auth.currentUser?.uid ?: "",
                title = title,
                questions = questionList.toList(),
                isPublished = oldIsPublished,
                scheduledDateTime = oldScheduledDateTime,
                scheduledEndDateTime = oldScheduledEndDateTime
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

// Data class for matching pairs
data class MatchingPair(
    var leftTerm: String = "",
    var rightMatch: String = ""
)
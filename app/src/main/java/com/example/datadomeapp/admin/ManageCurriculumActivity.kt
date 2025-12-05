package com.example.datadomeapp.admin

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Curriculum
import com.example.datadomeapp.models.SubjectEntry
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class ManageCurriculumActivity : AppCompatActivity() {

    // --- Firebase Setup ---
    private val firestore = FirebaseFirestore.getInstance()
    private val curriculumCollection = firestore.collection("curriculums")

    // --- View Declarations ---
    private lateinit var spnCourse: Spinner
    private lateinit var spnYear: Spinner
    private lateinit var llSubjectsContainer: LinearLayout
    private lateinit var etCode: EditText
    private lateinit var etTitle: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnDelete: Button
    private lateinit var btnBackToDashboard: MaterialButton // 🟢 CHANGED: MaterialButton type
    private lateinit var tvSubjectCount: TextView

    // --- Data & State ---
    private val yearLevels = arrayOf("1st Year")
    private val courseList = mutableListOf<String>()
    private val currentSubjectList = mutableListOf<SubjectEntry>()

    // Tracks the index of the subject selected for removal
    private var selectedSubjectIndex: Int = -1
    private var selectedSubjectView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_curiculum_management)

        // 1. Initialize Views
        spnCourse = findViewById(R.id.spnCurriculumCourse)
        spnYear = findViewById(R.id.spnCurriculumYear)
        llSubjectsContainer = findViewById(R.id.llRequiredSubjectsContainer)
        etCode = findViewById(R.id.etSubjectCode)
        etTitle = findViewById(R.id.etSubjectTitle)
        btnAdd = findViewById(R.id.btnAddSubjectToCurriculum)
        btnDelete = findViewById(R.id.btnDeleteCurriculumSubject)
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard) // 🟢 CHANGED: ID matches XML
        tvSubjectCount = findViewById(R.id.tvSubjectCount)

        // 2. Setup Spinners
        spnYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearLevels)

        // 3. Load Data & Listeners
        loadCourses()
        setupListeners()
    }

    private fun loadCourses() {
        firestore.collection("courses").get()
            .addOnSuccessListener { snapshot ->
                courseList.clear()
                snapshot.documents.forEach { doc ->
                    doc.getString("code")?.let { courseList.add(it) }
                }
                spnCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseList)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load courses: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupListeners() {
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                clearSelection()
                loadCurriculum()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { /* Do nothing */ }
        }
        spnCourse.onItemSelectedListener = listener
        spnYear.onItemSelectedListener = listener

        btnAdd.setOnClickListener { addSubject() }
        btnDelete.setOnClickListener { removeSubject() }

        // ============= BACK BUTTON FUNCTIONALITY =============
        btnBackToDashboard.setOnClickListener {
            finish() // Close current activity and return to previous screen
        }
    }

    private fun loadCurriculum() {
        if (spnCourse.selectedItem == null || spnYear.selectedItem == null) return
        val courseCode = spnCourse.selectedItem.toString()
        val yearLevel = spnYear.selectedItem.toString()
        val docId = "${courseCode}_${yearLevel.replace(" ", "")}"

        curriculumCollection.document(docId).get()
            .addOnSuccessListener { doc ->
                currentSubjectList.clear()
                val curriculum = doc.toObject(Curriculum::class.java)
                curriculum?.requiredSubjects?.let {
                    currentSubjectList.addAll(it)
                }
                updateSubjectListView()
            }
            .addOnFailureListener {
                currentSubjectList.clear()
                updateSubjectListView()
            }
    }

    private fun updateSubjectListView() {
        llSubjectsContainer.removeAllViews()
        clearSelection()

        // Update subject count
        tvSubjectCount.text = "${currentSubjectList.size} subjects"

        // Calculate 1dp height for the divider on the fly
        val oneDpInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            resources.displayMetrics
        ).toInt()

        val dividerColor = Color.parseColor("#E8E8E8")

        currentSubjectList.forEachIndexed { index, subject ->
            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "${subject.subjectCode} - ${subject.subjectTitle}"
                textSize = 16f
                setPadding(24, 16, 24, 16)
                tag = index

                // Custom click listener for selection
                setOnClickListener { selectSubject(it, index) }
                setBackgroundColor(Color.TRANSPARENT)
            }
            llSubjectsContainer.addView(textView)

            // Manually add dividers
            if (index < currentSubjectList.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        oneDpInPx
                    )
                    setBackgroundColor(dividerColor)
                }
                llSubjectsContainer.addView(divider)
            }
        }
    }

    private fun selectSubject(view: View, index: Int) {
        clearSelection()

        // Highlight color
        val highlightColor = ContextCompat.getColor(this, android.R.color.holo_blue_light)
        view.setBackgroundColor(highlightColor)

        // Update state
        selectedSubjectIndex = index
        selectedSubjectView = view
    }

    private fun clearSelection() {
        selectedSubjectView?.setBackgroundColor(Color.TRANSPARENT)
        selectedSubjectIndex = -1
        selectedSubjectView = null
    }

    private fun saveCurriculum() {
        if (spnCourse.selectedItem == null || spnYear.selectedItem == null) return
        val courseCode = spnCourse.selectedItem.toString()
        val yearLevel = spnYear.selectedItem.toString()
        val docId = "${courseCode}_${yearLevel.replace(" ", "")}"

        val curriculum = Curriculum(courseCode, yearLevel, currentSubjectList)
        curriculumCollection.document(docId).set(curriculum)
            .addOnSuccessListener {
                Toast.makeText(this, "Curriculum saved for $courseCode $yearLevel", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save curriculum: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addSubject() {
        val code = etCode.text.toString().trim().uppercase()
        val title = etTitle.text.toString().trim()

        if (spnCourse.selectedItem == null || spnYear.selectedItem == null) {
            Toast.makeText(this, "Please select Course and Year first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (code.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Fill in subject code and title.", Toast.LENGTH_SHORT).show()
            return
        }

        val newSubject = SubjectEntry(code, title, units = 3)

        if(currentSubjectList.any { it.subjectCode == code }) {
            Toast.makeText(this, "Subject code $code already exists in this curriculum.", Toast.LENGTH_SHORT).show()
            return
        }

        currentSubjectList.add(newSubject)
        updateSubjectListView()
        saveCurriculum()

        etCode.text.clear()
        etTitle.text.clear()
    }

    private fun removeSubject() {
        if (selectedSubjectIndex != -1 && selectedSubjectIndex < currentSubjectList.size) {
            currentSubjectList.removeAt(selectedSubjectIndex)
            clearSelection()
            updateSubjectListView()
            saveCurriculum()
            Toast.makeText(this, "Subject removed.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Select a subject to remove.", Toast.LENGTH_SHORT).show()
        }
    }
}
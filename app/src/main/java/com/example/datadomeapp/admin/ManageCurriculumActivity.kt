package com.example.datadomeapp.admin

import android.os.Bundle
import android.view.View // ⬅️ Add this import
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Curriculum // Assuming your models are here
import com.example.datadomeapp.models.SubjectEntry // Assuming your models are here
import com.google.firebase.firestore.FirebaseFirestore

class ManageCurriculumActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val curriculumCollection = firestore.collection("curriculums")

    private lateinit var spnCourse: Spinner
    private lateinit var spnYear: Spinner
    private lateinit var lvSubjects: ListView
    private lateinit var etCode: EditText
    private lateinit var etTitle: EditText
    private lateinit var btnAdd: Button
    private lateinit var btnDelete: Button

    private val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year")
    private val courseList = mutableListOf<String>() // Listahan ng Course Codes
    private val currentSubjectList = mutableListOf<SubjectEntry>() // Subjects ng napiling curriculum
    private lateinit var subjectAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_curriculum)

        spnCourse = findViewById(R.id.spnCurriculumCourse)
        spnYear = findViewById(R.id.spnCurriculumYear)
        lvSubjects = findViewById(R.id.lvRequiredSubjects)
        etCode = findViewById(R.id.etSubjectCode)
        etTitle = findViewById(R.id.etSubjectTitle)
        btnAdd = findViewById(R.id.btnAddSubjectToCurriculum)
        btnDelete = findViewById(R.id.btnDeleteCurriculumSubject)

        spnYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearLevels)

        subjectAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf())
        lvSubjects.adapter = subjectAdapter
        lvSubjects.choiceMode = ListView.CHOICE_MODE_SINGLE

        loadCourses()
        setupListeners()
    }

    private fun loadCourses() {
        // Assume courses are stored in a 'courses' collection (from ManageCoursesActivity)
        firestore.collection("courses").get()
            .addOnSuccessListener { snapshot ->
                courseList.clear()
                snapshot.documents.forEach { doc ->
                    doc.getString("code")?.let { courseList.add(it) }
                }
                spnCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseList)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load courses.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupListeners() {
        // Whenever Course or Year changes, load the corresponding curriculum
        val listener = object : AdapterView.OnItemSelectedListener {

            // ➡️ INAYOS: Tamang parameter signature para sa onItemSelected
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                loadCurriculum()
            }

            // ➡️ INAYOS: Idinagdag ang kailangang onNothingSelected
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
        spnCourse.onItemSelectedListener = listener
        spnYear.onItemSelectedListener = listener

        btnAdd.setOnClickListener { addSubject() }
        btnDelete.setOnClickListener { removeSubject() }
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
                // Kung walang Curriculum pa, clear lang ang listahan at mag-umpisa
                currentSubjectList.clear()
                updateSubjectListView()
            }
    }

    private fun updateSubjectListView() {
        val displayList = currentSubjectList.map { "${it.subjectCode} - ${it.subjectTitle}" }
        (subjectAdapter as ArrayAdapter<String>).clear()
        (subjectAdapter as ArrayAdapter<String>).addAll(displayList)
        subjectAdapter.notifyDataSetChanged()
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
                Toast.makeText(this, "Failed to save curriculum.", Toast.LENGTH_SHORT).show()
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

        val newSubject = SubjectEntry(code, title, units = 3) // Default 3 units

        // Simple check para maiwasan ang duplicate subject code
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
        val pos = lvSubjects.checkedItemPosition
        if (pos != ListView.INVALID_POSITION && pos < currentSubjectList.size) {
            currentSubjectList.removeAt(pos)
            lvSubjects.setItemChecked(pos, false) // Uncheck
            updateSubjectListView()
            saveCurriculum()
            Toast.makeText(this, "Subject removed.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Select a subject to remove.", Toast.LENGTH_SHORT).show()
        }
    }
}
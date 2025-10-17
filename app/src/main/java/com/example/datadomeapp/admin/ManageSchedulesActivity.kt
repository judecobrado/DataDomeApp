package com.example.datadomeapp.admin

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
// IMPORTANT: Make sure these models exist in your 'com.example.datadomeapp.models' package
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.SubjectEntry
import com.example.datadomeapp.models.Teacher
import com.example.datadomeapp.models.Curriculum
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class ManageSchedulesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val assignmentsCollection = firestore.collection("classAssignments")
    private val usersCollection = firestore.collection("users")
    private val curriculumCollection = firestore.collection("curriculums")
    private val sectionsCollection = firestore.collection("sections")

    // UI Elements
    private lateinit var spnCourse: Spinner
    private lateinit var spnYear: Spinner
    private lateinit var spnSubject: Spinner
    private lateinit var spnTeacher: Spinner
    private lateinit var spnSectionBlock: Spinner
    private lateinit var etCapacity: EditText
    private lateinit var btnSave: Button
    private lateinit var tlScheduleMatrix: TableLayout
    private lateinit var tvConflictWarning: TextView
    private lateinit var spnDay: Spinner
    private lateinit var etStartTime: EditText
    private lateinit var etEndTime: EditText

    // Data Holders
    private val courseList = mutableListOf<String>()
    private val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year")
    private val weekDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val teacherList = mutableListOf<Teacher>()
    private val subjectEntryList = mutableListOf<SubjectEntry>()

    // Assignments for the current Course/Year (Used for Teacher Conflict Check)
    private val allAssignmentsForCourseYear = mutableListOf<ClassAssignment>()
    // Assignments filtered by the currently selected Section Block (Used for Matrix/Section Overlap Check)
    private val currentAssignmentsForMatrix = mutableListOf<ClassAssignment>()

    private val sectionBlockNames = mutableListOf<String>()
    private lateinit var sectionBlockAdapter: ArrayAdapter<String>

    // Gumagamit ng 24-hour format (HH:mm) para sa schedule data
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Time slots (8 AM to 5 PM, 24-hour format)
    private val timeSlots = 8..17

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_schedules)

        // Initialize UI components
        spnCourse = findViewById(R.id.spnCourse)
        spnYear = findViewById(R.id.spnYear)
        spnSubject = findViewById(R.id.spnSubject)
        spnTeacher = findViewById(R.id.spnTeacher)
        spnSectionBlock = findViewById(R.id.spnSectionBlock)
        etCapacity = findViewById(R.id.etMaxCapacity)
        btnSave = findViewById(R.id.btnSaveAssignment)
        tlScheduleMatrix = findViewById(R.id.tlScheduleMatrix)
        tvConflictWarning = findViewById(R.id.tvConflictWarning)
        spnDay = findViewById(R.id.spnDay)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)

        // Setup Adapters
        spnYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearLevels)
        spnDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekDays)

        sectionBlockAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionBlockNames)
        spnSectionBlock.adapter = sectionBlockAdapter

        loadCoursesAndTeachers()
        setupListeners()

        etCapacity.setText("50")
        btnSave.isEnabled = false // Default disabled
    }

    // Helper function para kunin ang Course Code (ALL CAPS)
    private fun getCourseCodeFromSpinner(): String? {
        return spnCourse.selectedItem?.toString()?.trim()?.uppercase(Locale.getDefault())
    }

    private fun loadCoursesAndTeachers() {
        // Load Courses
        firestore.collection("courses").get().addOnSuccessListener { snapshot ->
            courseList.clear()
            snapshot.documents.forEach { doc ->
                doc.getString("code")?.let { courseList.add(it) }
            }
            spnCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseList)
        }

        // Load Teachers
        usersCollection.whereEqualTo("role", "teacher").get().addOnSuccessListener { snapshot ->
            teacherList.clear()
            snapshot.documents.forEach { doc ->
                val email = doc.getString("email") ?: "N/A"
                val name = doc.getString("name") ?: email.substringBefore("@")
                teacherList.add(Teacher(uid = doc.id, teacherId = "", name = name))
            }
            val teacherNames = teacherList.map { it.name + " (UID: " + it.uid.takeLast(4) + ")" }
            spnTeacher.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teacherNames)
        }
    }

    private fun setupListeners() {
        // Curriculum Listener (Triggers when Course or Year changes)
        val courseYearListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadSubjectsSectionsAndAllAssignments()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spnCourse.onItemSelectedListener = courseYearListener
        spnYear.onItemSelectedListener = courseYearListener

        // Section Listener (Triggers when Section Block changes)
        spnSectionBlock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterAssignmentsAndRenderMatrix()
                checkConflicts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Time Picker Setup
        etStartTime.setOnClickListener { showTimePicker(it as EditText) }
        etEndTime.setOnClickListener { showTimePicker(it as EditText) }

        // Conflict checking listeners
        spnDay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { checkConflicts() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        etStartTime.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkConflicts() }
        etEndTime.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkConflicts() }
        spnTeacher.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { checkConflicts() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { saveAssignment() }
    }

    private fun showTimePicker(editText: EditText) {
        val calendar = Calendar.getInstance()

        val initialTime: Date? = try { timeFormat.parse(editText.text.toString()) } catch (e: Exception) { null }

        if (initialTime != null) {
            calendar.time = initialTime
        }

        val initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            editText.setText(timeFormat.format(calendar.time))
            checkConflicts()

        }, initialHour, initialMinute, true).show() // 'true' means 24-hour mode
    }

    /**
     * 🛑 INAYOS: Load Section Blocks mula sa Nested Map Structure (Map<YearKey, List<Blocks>>).
     * Gumagamit ng No-Space Key (e.g., "1stYear") para sa field key.
     */
    /**
     * ✅ PINAKAMATATAG NA PAGKUHA: Load Section Blocks gamit ang Firestore `get()` para sa nested Array.
     */
    /**
     * ✅ Load Subjects (Curriculum), Section Blocks, at Assignments.
     * Gumagamit ng ROBUST Nested Array Retrieval at idinagdag na LOGGING para sa Section.
     */
    private fun loadSubjectsSectionsAndAllAssignments() {
        val courseCode = getCourseCodeFromSpinner() ?: return // Ito ay ALL CAPS
        val yearLevel = spnYear.selectedItem?.toString() ?: return // E.g., "1st Year"

        // 🎯 GUMAGAWA NG NO-SPACE KEY (e.g., "1stYear")
        val yearLevelKey = yearLevel.trim().replace(" ", "")

        // Curriculum Document ID: COURSECODE_YEARSTRING (e.g., BSIT_1stYear)
        val docId = "${courseCode}_${yearLevelKey}"
        Log.d("ScheduleLoad", "--- Starting Load ---")
        Log.d("ScheduleLoad", "Target Course: $courseCode, Target Year Key: $yearLevelKey")
        // ---------------------------------------------------------------------

        // --- Load 1. Subjects (Curriculum) ---
        curriculumCollection.document(docId).get()
            .addOnSuccessListener { doc ->
                subjectEntryList.clear()
                if (doc.exists()) {
                    val curriculum = doc.toObject(Curriculum::class.java)
                    curriculum?.requiredSubjects?.let { subjectEntryList.addAll(it) }

                    val subjectNames = subjectEntryList.map { it.subjectCode + " - " + it.subjectTitle }
                    spnSubject.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, subjectNames)
                    Log.d("ScheduleLoad", "Curriculum Loaded: Found ${subjectEntryList.size} subject(s).")
                } else {
                    spnSubject.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mutableListOf("No Curriculum Found"))
                    Toast.makeText(this, "Curriculum for $docId not found.", Toast.LENGTH_SHORT).show()
                    Log.w("ScheduleLoad", "Curriculum document for $docId not found.")
                }

                // --- Load 2. Section Blocks (ROBUST Nested Array Retrieval) ---
                loadSectionBlocks(courseCode, yearLevelKey)

            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading curriculum: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ScheduleLoad", "Curriculum fetch failed: ${e.message}")
                // Kahit failed ang curriculum, subukan pa ring i-load ang sections
                loadSectionBlocks(courseCode, yearLevelKey)
            }
    }

    /**
     * Helper function para mag-load ng Section Blocks, kasama ang detalyadong logging.
     */
    private fun loadSectionBlocks(courseCode: String, yearLevelKey: String) {
        sectionsCollection.document(courseCode).get()
            .addOnSuccessListener { doc ->
                sectionBlockNames.clear() // Clear previous entries

                if (!doc.exists()) {
                    Log.w("SectionLoad", "Document sections/$courseCode DOES NOT EXIST.")
                    Toast.makeText(this, "Section data not found. Please setup sections.", Toast.LENGTH_LONG).show()
                }

                val sectionsRaw = doc.get("sections")
                if (sectionsRaw is Map<*, *>) {
                    val blocksRaw = sectionsRaw[yearLevelKey]
                    if (blocksRaw is List<*>) {
                        val blocksListForYear = blocksRaw.filterIsInstance<String>()
                        if (blocksListForYear.isEmpty()) {
                            Log.w("SectionLoad", "No blocks found for $yearLevelKey.")
                        } else {
                            Log.d("SectionLoad", "Found ${blocksListForYear.size} block(s) for $yearLevelKey: $blocksListForYear")
                        }
                        sectionBlockNames.addAll(blocksListForYear)
                    } else {
                        Log.e("SectionLoad", "Value for '$yearLevelKey' is not a List.")
                    }
                } else {
                    Log.e("SectionLoad", "Field 'sections' missing or not a Map.")
                }

                // ✅ Add default prompt at top
                sectionBlockNames.add(0, "Select Section")

                // Notify adapter
                sectionBlockAdapter.notifyDataSetChanged()

                // Optional: set initial selection to default
                spnSectionBlock.setSelection(0)

                // Reload assignments after sections are loaded
                loadAllAssignments()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading section blocks: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("SectionLoad", "Firestore fetch failed: ${e.message}")
                loadAllAssignments() // Proceed even if failed
            }
    }


    // Load Lahat ng Assignments para sa Course/Year
    private fun loadAllAssignments() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return

        assignmentsCollection
            .whereEqualTo("courseCode", courseCode)
            .whereEqualTo("yearLevel", yearLevel)
            .get()
            .addOnSuccessListener { snapshot ->
                allAssignmentsForCourseYear.clear()
                snapshot.documents.forEach { doc ->
                    doc.toObject(ClassAssignment::class.java)?.let { allAssignmentsForCourseYear.add(it) }
                }
                // I-filter at i-render ang matrix
                filterAssignmentsAndRenderMatrix()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching assignments: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // I-filter ang assignments at I-render ang Matrix (Walang pagbabago sa logic)
    private fun filterAssignmentsAndRenderMatrix() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return
        val yearNumber = yearLevel.substring(0, 1)

        val selectedSectionBlock = spnSectionBlock.selectedItem?.toString()?.trim()?.uppercase(Locale.getDefault()) ?: ""

        if (selectedSectionBlock.isEmpty()) {
            currentAssignmentsForMatrix.clear()
            renderScheduleMatrix()
            return
        }

        // SectionName Prefix: e.g., "BSIT-1-A" (Ito ang gagamitin natin sa assignment document ID)
        val sectionPrefix = "${courseCode}-${yearNumber}-${selectedSectionBlock}"

        // Filter ang lahat ng assignments na na-load na
        currentAssignmentsForMatrix.clear()
        currentAssignmentsForMatrix.addAll(
            allAssignmentsForCourseYear.filter {
                it.sectionName.startsWith(sectionPrefix, ignoreCase = true)
            }
        )

        renderScheduleMatrix()
        checkConflicts()
    }

    private fun renderScheduleMatrix() {
        tlScheduleMatrix.removeAllViews() // Clear previous table data

        // --- 1. Create Table Headers (Days) ---
        val headerRow = TableRow(this).apply {
            addView(TextView(this@ManageSchedulesActivity).apply {
                text = "Time"
                setPadding(12, 12, 12, 12)
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(Color.LTGRAY)
                textSize = 14f
            })
        }
        weekDays.forEach { day ->
            headerRow.addView(TextView(this).apply {
                text = day
                setPadding(12, 12, 12, 12)
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                minWidth = 150
                textSize = 14f
            })
        }
        tlScheduleMatrix.addView(headerRow)


        // --- 2. Create Data Rows (Time Slots) ---
        timeSlots.forEach { hour ->
            val row = TableRow(this).apply {
                // Time Label (e.g., 08:00 AM)
                addView(TextView(this@ManageSchedulesActivity).apply {
                    val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0) }
                    val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val timeDisplay = displayTimeFormat.format(calendar.time)

                    text = timeDisplay
                    setPadding(12, 12, 12, 12)
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundColor(Color.parseColor("#EEEEEE"))
                    textSize = 12f
                })
            }

            // Create a cell for each day
            weekDays.forEach { day ->
                val assignmentsInSlot = getAssignmentsForSlot(day, hour)

                // Content of the cell (Assignment details)
                val cellText = if (assignmentsInSlot.isEmpty()) {
                    "" // Empty slot
                } else {
                    assignmentsInSlot.joinToString("\n") {
                        val teacherInitial = it.assignedTeacherName.split(" ").firstOrNull() ?: ""
                        "${it.subjectCode}\n@ ${it.schedule.substringAfter(" ").substringBefore("-")}\n- ${teacherInitial}"
                    }
                }

                val isConflict = assignmentsInSlot.size > 1

                val cellBackground = if (isConflict) {
                    Color.parseColor("#FFD0D0") // Red for Conflict (Shouldn't happen with strict check)
                } else if (assignmentsInSlot.isNotEmpty()) {
                    Color.parseColor("#D0FFD0") // Light Green for Assigned
                } else {
                    Color.WHITE // White for Empty
                }

                row.addView(TextView(this@ManageSchedulesActivity).apply {
                    text = cellText
                    setPadding(10, 10, 10, 10)
                    setBackgroundColor(cellBackground)
                    gravity = Gravity.CENTER
                    minWidth = 150
                    minHeight = 80
                    minLines = 4
                    textSize = 12f
                    if (isConflict) {
                        setTextColor(Color.RED)
                        setTypeface(null, Typeface.BOLD)
                    } else {
                        setTextColor(Color.BLACK)
                    }
                })
            }
            tlScheduleMatrix.addView(row)
        }
    }

    private fun getAssignmentsForSlot(day: String, startHour: Int): List<ClassAssignment> {
        return currentAssignmentsForMatrix.filter { assignment -> // Gumagamit ng filtered list
            val assignmentDay = assignment.schedule.split(" ").firstOrNull() ?: "UNKNOWN"
            if (assignmentDay != day) return@filter false

            val timeRange = assignment.schedule.substringAfter(" ").split("-")
            val startTimeStr = timeRange.firstOrNull() ?: return@filter false

            // Parses the HH:mm string and gets the hour in 24-hour format
            val scheduleHour = try {
                val date = timeFormat.parse(startTimeStr)
                val cal = Calendar.getInstance()
                if (date != null) {
                    cal.time = date
                    cal.get(Calendar.HOUR_OF_DAY)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Schedule", "Error parsing time: $startTimeStr", e)
                null
            }

            return@filter scheduleHour == startHour
        }
    }

    private fun checkConflicts() {
        tvConflictWarning.text = ""
        tvConflictWarning.visibility = View.GONE
        btnSave.isEnabled = false // Default disabled

        val day = spnDay.selectedItem?.toString() ?: return
        val startTime = etStartTime.text.toString().trim()
        val endTime = etEndTime.text.toString().trim()
        val sectionBlock = spnSectionBlock.selectedItem?.toString() ?: return

        if (spnTeacher.selectedItem == null || startTime.isEmpty() || endTime.isEmpty()) {
            tvConflictWarning.visibility = View.GONE
            return
        }

        val newSchedule = "$day $startTime-$endTime"

        val selectedTeacher = teacherList[spnTeacher.selectedItemPosition]

        // --- 1. SECTION OVERLAP CHECK (Fatal Conflict) ---
        val sectionConflict = currentAssignmentsForMatrix.find {
            it.schedule.equals(newSchedule, ignoreCase = true)
        }

        if (sectionConflict != null) {
            tvConflictWarning.text = "❌ FATAL CONFLICT: Section Overlap!\n" +
                    "Section $sectionBlock is already scheduled for:\n" +
                    "${sectionConflict.subjectCode} (${sectionConflict.assignedTeacherName}) at this exact time."
            tvConflictWarning.visibility = View.VISIBLE
            tvConflictWarning.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            return
        }

        // --- 2. TEACHER CONFLICT CHECK (Fatal Conflict) ---
        val teacherConflict = allAssignmentsForCourseYear.find {
            it.assignedTeacherId == selectedTeacher.uid && it.schedule.equals(newSchedule, ignoreCase = true)
        }

        if (teacherConflict != null) {
            tvConflictWarning.text = "❌ FATAL CONFLICT: Teacher Conflict!\n" +
                    "${selectedTeacher.name} is already assigned to:\n" +
                    "${teacherConflict.subjectCode} in Section ${teacherConflict.sectionName} at this time."
            tvConflictWarning.visibility = View.VISIBLE
            tvConflictWarning.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            return
        }

        // --- 3. PASSED CHECK ---
        tvConflictWarning.text = "✅ No Conflicts detected. Ready to save."
        tvConflictWarning.visibility = View.VISIBLE
        tvConflictWarning.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
        btnSave.isEnabled = true // Payagan mag-save
    }


    private fun saveAssignment() {
        // Input validation (Unchanged)
        if (spnSubject.selectedItem == null || spnTeacher.selectedItem == null || subjectEntryList.isEmpty()) {
            Toast.makeText(this, "Select a valid subject and teacher.", Toast.LENGTH_SHORT).show()
            return
        }
        val day = spnDay.selectedItem?.toString() ?: return
        val startTime = etStartTime.text.toString().trim()
        val endTime = etEndTime.text.toString().trim()
        val sectionBlock = spnSectionBlock.selectedItem?.toString()?.trim()?.uppercase(Locale.getDefault()) ?: return
        if (startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, "Select Start and End Time.", Toast.LENGTH_SHORT).show()
            return
        }

        val finalScheduleString = "$day $startTime-$endTime"
        val selectedTeacher = teacherList[spnTeacher.selectedItemPosition]

        // --- RE-CHECK CONFLICTS BEFORE SAVE (Crucial Security Check) ---

        // 1. Check for Section Overlap (sa kasalukuyang block)
        val sectionConflict = currentAssignmentsForMatrix.find {
            it.schedule.equals(finalScheduleString, ignoreCase = true)
        }

        // 2. Check for Teacher Conflict (sa lahat ng assignments)
        val teacherConflict = allAssignmentsForCourseYear.find {
            it.assignedTeacherId == selectedTeacher.uid && it.schedule.equals(finalScheduleString, ignoreCase = true)
        }

        if (sectionConflict != null) {
            Toast.makeText(this, "SAVE BLOCKED: Section Overlap detected for Block $sectionBlock. Cannot save.", Toast.LENGTH_LONG).show()
            checkConflicts()
            return
        }

        if (teacherConflict != null) {
            Toast.makeText(this, "SAVE BLOCKED: Teacher Conflict detected for ${selectedTeacher.name}. Cannot save.", Toast.LENGTH_LONG).show()
            checkConflicts()
            return
        }

        // --- IF NO CONFLICTS, PROCEED TO SAVE ---

        val courseCode = getCourseCodeFromSpinner() ?: return // Ito ay ALL CAPS na
        val yearLevel = spnYear.selectedItem.toString()
        val subjectEntry = subjectEntryList[spnSubject.selectedItemPosition]
        val teacher = teacherList[spnTeacher.selectedItemPosition]
        val capacity = etCapacity.text.toString().toIntOrNull() ?: 50

        // Gagamitin ang year number (e.g., "1st Year" -> "1") para sa Section Name Convention
        val yearNumber = yearLevel.substring(0,1)

        // Section Name Convention: COURSE-YEARNUMBER-BLOCK-SUBJECTCODE
        // (E.g., BSIT-1-A-CS101) - Ito ay ginagamit na Assignment Document ID
        val finalSectionName = "${courseCode}-${yearNumber}-${sectionBlock}-${subjectEntry.subjectCode}"

        val assignment = ClassAssignment(
            subjectCode = subjectEntry.subjectCode,
            subjectTitle = subjectEntry.subjectTitle,
            courseCode = courseCode,
            yearLevel = yearLevel,
            sectionName = finalSectionName,
            assignedTeacherId = teacher.uid,
            assignedTeacherName = teacher.name,
            schedule = finalScheduleString,
            maxCapacity = capacity,
            currentlyEnrolled = 0
        )

        assignmentsCollection.document(finalSectionName).set(assignment)
            .addOnSuccessListener {
                Toast.makeText(this, "Class Assignment Saved: $finalSectionName", Toast.LENGTH_LONG).show()
                // I-reload ang lahat ng data para mag-update ang matrix
                loadSubjectsSectionsAndAllAssignments()
                tvConflictWarning.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
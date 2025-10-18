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
import com.example.datadomeapp.models.ScheduleItem
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
        spnYear.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearLevels)
        spnDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekDays)

        sectionBlockAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionBlockNames)
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
            spnCourse.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseList)
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
            spnTeacher.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teacherNames)
        }
    }

    private fun setupListeners() {
        // Curriculum Listener (Triggers when Course or Year changes)
        val courseYearListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                loadSubjectsSectionsAndAllAssignments()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spnCourse.onItemSelectedListener = courseYearListener
        spnYear.onItemSelectedListener = courseYearListener

        // Section Listener (Triggers when Section Block changes)
        spnSectionBlock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
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
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                checkConflicts()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        etStartTime.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkConflicts() }
        etEndTime.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkConflicts() }
        spnTeacher.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                checkConflicts()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { saveAssignment() }
    }

    private fun showTimePicker(editText: EditText) {
        val calendar = Calendar.getInstance()

        val initialTime: Date? = try {
            timeFormat.parse(editText.text.toString())
        } catch (e: Exception) {
            null
        }

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

                    val subjectNames =
                        subjectEntryList.map { it.subjectCode + " - " + it.subjectTitle }
                    spnSubject.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        subjectNames
                    )
                    Log.d(
                        "ScheduleLoad",
                        "Curriculum Loaded: Found ${subjectEntryList.size} subject(s)."
                    )
                } else {
                    spnSubject.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        mutableListOf("No Curriculum Found")
                    )
                    Toast.makeText(this, "Curriculum for $docId not found.", Toast.LENGTH_SHORT)
                        .show()
                    Log.w("ScheduleLoad", "Curriculum document for $docId not found.")
                }

                // --- Load 2. Section Blocks (ROBUST Nested Array Retrieval) ---
                loadSectionBlocks(courseCode, yearLevelKey)

            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading curriculum: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
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
                    Toast.makeText(
                        this,
                        "Section data not found. Please setup sections.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                val sectionsRaw = doc.get("sections")
                if (sectionsRaw is Map<*, *>) {
                    val blocksRaw = sectionsRaw[yearLevelKey]
                    if (blocksRaw is List<*>) {
                        val blocksListForYear = blocksRaw.filterIsInstance<String>()
                        if (blocksListForYear.isEmpty()) {
                            Log.w("SectionLoad", "No blocks found for $yearLevelKey.")
                        } else {
                            Log.d(
                                "SectionLoad",
                                "Found ${blocksListForYear.size} block(s) for $yearLevelKey: $blocksListForYear"
                            )
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
                Toast.makeText(
                    this,
                    "Error loading section blocks: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
                    doc.toObject(ClassAssignment::class.java)
                        ?.let { allAssignmentsForCourseYear.add(it) }
                }
                // I-filter at i-render ang matrix
                filterAssignmentsAndRenderMatrix()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching assignments: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
    }

    // I-filter ang assignments at I-render ang Matrix (Walang pagbabago sa logic)
    private fun filterAssignmentsAndRenderMatrix() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return
        val yearNumber = yearLevel.substring(0, 1)

        val selectedSectionBlock =
            spnSectionBlock.selectedItem?.toString()?.trim()?.uppercase(Locale.getDefault()) ?: ""

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
                setTextSize(14f)
                setTypeface(null, Typeface.BOLD)
                setPadding(10, 10, 10, 10)
                setBackgroundColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            })
            weekDays.forEach { day ->
                addView(TextView(this@ManageSchedulesActivity).apply {
                    text = day
                    setTextSize(14f)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(10, 10, 10, 10)
                    setBackgroundColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                    layoutParams = TableRow.LayoutParams(
                        0,
                        TableRow.LayoutParams.WRAP_CONTENT,
                        1f
                    ) // Equal weight
                })
            }
        }
        tlScheduleMatrix.addView(headerRow)

        // --- 2. Create Time Rows and Class Blocks ---
        val assignmentsMatrix =
            Array(weekDays.size) { arrayOfNulls<MutableList<ClassAssignment>>(timeSlots.count()) }

        // Pre-populate the matrix with assignments
        currentAssignmentsForMatrix.forEach { assignment ->
            val dayIndex = weekDays.indexOf(assignment.day)
            if (dayIndex >= 0) {

                // --- FIX: Gamitin ang Calendar para kunin ang oras (24-hour format) ---
                val startCal = Calendar.getInstance().apply {
                    time = timeFormat.parse(assignment.startTime) ?: return@forEach
                }
                val endCal = Calendar.getInstance().apply {
                    time = timeFormat.parse(assignment.endTime) ?: return@forEach
                }

                // Walang deprecated warning
                val startHour = startCal.get(Calendar.HOUR_OF_DAY)
                val endHour = endCal.get(Calendar.HOUR_OF_DAY)
                // ---------------------------------------------------------------------

                val startSlot = startHour - timeSlots.first
                val endSlot = endHour - timeSlots.first

                // Mark all slots covered by the assignment
                for (i in startSlot until endSlot) {
                    if (i >= 0 && i < timeSlots.count()) {
                        if (assignmentsMatrix[dayIndex][i] == null) {
                            assignmentsMatrix[dayIndex][i] = mutableListOf()
                        }
                        assignmentsMatrix[dayIndex][i]?.add(assignment)
                    }
                }
            }
        }

        // Render the table row by row (by time slot)
        var currentHour = timeSlots.first
        while (currentHour <= timeSlots.last) {
            val timeLabel =
                String.format(Locale.getDefault(), "%02d:00-%02d:00", currentHour, currentHour + 1)
            val row = TableRow(this)

            // Time Label Cell
            row.addView(TextView(this).apply {
                text = timeLabel
                setPadding(10, 10, 10, 10)
                gravity = Gravity.END
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            })

            // Day Cells
            for (dayIndex in weekDays.indices) {
                val slotIndex = currentHour - timeSlots.first
                val assignmentsInSlot = assignmentsMatrix[dayIndex][slotIndex]

                val cellText = if (assignmentsInSlot.isNullOrEmpty()) {
                    ""
                } else {
                    assignmentsInSlot.joinToString("\n") { it.subjectCode }
                }

                row.addView(TextView(this).apply {
                    text = cellText
                    setTextSize(12f)
                    setPadding(10, 10, 10, 10)
                    gravity = Gravity.CENTER
                    // Highlight the cell if a class is scheduled
                    setBackgroundColor(if (cellText.isNotEmpty()) Color.parseColor("#C8E6C9") else Color.WHITE)
                    layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            tlScheduleMatrix.addView(row)
            currentHour++
        }
    }

    private fun getAssignmentsForSlot(day: String, startHour: Int): List<ClassAssignment> {
        return currentAssignmentsForMatrix.filter { assignment -> // 'assignment' is the ClassAssignment object

            // 1. Check if the day matches
            // Instead of assignment.schedule, use assignment.day directly
            val assignmentDay = assignment.day
            if (assignmentDay != day) return@filter false

            // 2. Get the start time string (e.g., "08:00")
            val startTimeStr = assignment.startTime

            // 3. Parses the HH:mm string and gets the hour in 24-hour format
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

            // 4. Check if the class starts exactly on the boundary of the slot (e.g., 08:00 for the 8-9 slot)
            // OR if the class time falls within the slot's range.
            val classStartHour = scheduleHour ?: -1
            val classEndHour = try {
                val date = timeFormat.parse(assignment.endTime)
                val cal = Calendar.getInstance()
                if (date != null) {
                    cal.time = date
                    cal.get(Calendar.HOUR_OF_DAY)
                } else {
                    -1
                }
            } catch (e: Exception) {
                -1
            }

            // A class should be displayed in a slot if its start hour is <= slot start and its end hour is > slot start.
            // Or more simply: if the class covers the 'startHour' slot.
            return@filter classStartHour <= startHour && classEndHour > startHour
        }
    }

    private fun checkConflicts() {
        val selectedTeacher = teacherList.getOrNull(spnTeacher.selectedItemPosition)
        val day = spnDay.selectedItem?.toString()
        val startTimeStr = etStartTime.text.toString()
        val endTimeStr = etEndTime.text.toString()
        val sectionBlock = spnSectionBlock.selectedItem?.toString()

        if (day == null || startTimeStr.isEmpty() || endTimeStr.isEmpty() || sectionBlock == "Select Section") {
            tvConflictWarning.text = ""
            btnSave.isEnabled = false
            return
        }

        try {
            val newStart =
                timeFormat.parse(startTimeStr)?.time ?: throw Exception("Invalid start time")
            val newEnd = timeFormat.parse(endTimeStr)?.time ?: throw Exception("Invalid end time")

            if (newStart >= newEnd) {
                tvConflictWarning.text = "❌ Error: Start time must be before end time."
                btnSave.isEnabled = false
                return
            }

            val allConflicts = mutableListOf<String>()

            // 1. Teacher Conflict Check
            if (selectedTeacher != null) {
                val teacherConflict = allAssignmentsForCourseYear.filter { assignment ->
                    // Check all assignments for the *entire* course/year, regardless of section
                    assignment.teacherUid == selectedTeacher.uid && assignment.day == day
                }.any { existing ->
                    // Check for time overlap
                    val existingStart =
                        timeFormat.parse(existing.startTime)?.time ?: return@any false
                    val existingEnd = timeFormat.parse(existing.endTime)?.time ?: return@any false
                    (newStart < existingEnd && newEnd > existingStart)
                }
                if (teacherConflict) {
                    allConflicts.add("Teacher Conflict: ${selectedTeacher.name} is already scheduled at this time.")
                }
            }

            // 2. Section Overlap Check (Only for the selected section block)
            // Linya ~580
            // 2. Section Overlap Check (Only for the selected section block)
            val sectionConflict = currentAssignmentsForMatrix.any { existing ->
                // 2a. Check if the day and block match the existing item.
                val dayAndBlockMatch = (existing.day == day && existing.sectionBlock == sectionBlock)

                if (dayAndBlockMatch) {
                    // 2b. Check for time overlap using the existing item's times.
                    val existingStart = timeFormat.parse(existing.startTime)?.time ?: return@any false
                    val existingEnd = timeFormat.parse(existing.endTime)?.time ?: return@any false
                    (newStart < existingEnd && newEnd > existingStart)
                } else {
                    false
                }
            }
            if (sectionConflict) {
                allConflicts.add("Section Conflict: ${sectionBlock} already has a class scheduled at this time.")
            }

            // --- Final Output ---
            if (allConflicts.isNotEmpty()) {
                tvConflictWarning.text = "⚠️ Conflicts Found:\n" + allConflicts.joinToString("\n")
                btnSave.isEnabled = false
            } else {
                tvConflictWarning.text = "✅ No conflicts found. Ready to save."
                btnSave.isEnabled = true
            }

        } catch (e: Exception) {
            tvConflictWarning.text = "❌ Error in time format."
            btnSave.isEnabled = false
        }
    }

    private fun saveAssignment() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return
        val subjectName = spnSubject.selectedItem?.toString() ?: return
        val teacherSelection = spnTeacher.selectedItem?.toString() ?: return
        val sectionBlock = spnSectionBlock.selectedItem?.toString() ?: return
        val day = spnDay.selectedItem?.toString() ?: return
        val startTimeStr = etStartTime.text.toString()
        val endTimeStr = etEndTime.text.toString()
        val capacity = etCapacity.text.toString().toIntOrNull() ?: 50

        if (!btnSave.isEnabled) {
            Toast.makeText(this, "Please resolve conflicts before saving.", Toast.LENGTH_LONG)
                .show()
            return
        }

        val selectedTeacher =
            teacherList.firstOrNull { (it.name + " (UID: " + it.uid.takeLast(4) + ")") == teacherSelection }
                ?: run {
                    Toast.makeText(this, "Invalid teacher selected.", Toast.LENGTH_SHORT).show()
                    return
                }

        val (subjectCode, subjectTitle) = subjectName.split(" - ").map { it.trim() }
            .let { Pair(it.getOrNull(0) ?: "", it.getOrNull(1) ?: "") }
        val yearNumber = yearLevel.substring(0, 1)

        // Unique ID for the assignment document: e.g., BSIT-1-A-MON-0800
        val assignmentId =
            "${courseCode}-${yearNumber}-${sectionBlock}-${day}-${startTimeStr.replace(":", "")}"
        val sectionName = "${courseCode}-${yearNumber}-${sectionBlock}"

        val assignment = ClassAssignment(
            assignmentId = assignmentId,
            courseCode = courseCode,
            yearLevel = yearLevel,
            sectionBlock = sectionBlock,
            sectionName = sectionName,
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            teacherUid = selectedTeacher.uid,
            teacherName = selectedTeacher.name,
            day = day,
            startTime = startTimeStr,
            endTime = endTimeStr,
            maxCapacity = capacity,
            enrolledCount = 0
        )

        // Linya ~657
        // ... (Linya 657)
        assignmentsCollection.document(assignmentId).set(assignment)
            // TAMA: Ang .set() ay nagbabalik lang ng success (Void)
            .addOnSuccessListener {
                Toast.makeText(this, "Schedule successfully saved!", Toast.LENGTH_LONG).show()

                // I-refresh ang data at matrix
                loadAllAssignments()
                checkConflicts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save schedule: ${e.message}", Toast.LENGTH_LONG)
                    .show()
                Log.e("ManageSchedules", "Save failed", e)
            }
    } // <--- Dito nagtatapos ang saveAssignment() function

} // <--- Dito nagtatapos ang ManageSchedulesActivity class
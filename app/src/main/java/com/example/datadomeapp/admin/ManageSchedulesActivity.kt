package com.example.datadomeapp.admin

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.SubjectEntry
import com.example.datadomeapp.models.Teacher
import com.example.datadomeapp.models.Curriculum
import com.example.datadomeapp.models.Room
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class ManageSchedulesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val assignmentsCollection = firestore.collection("classAssignments")
    private val usersCollection = firestore.collection("users")
    private val teachersCollection = firestore.collection("teachers")
    private val curriculumCollection = firestore.collection("curriculums")
    private val sectionsCollection = firestore.collection("sections")
    private val roomsCollection = firestore.collection("rooms")

    // UI Elements
    private lateinit var spnCourse: Spinner
    private lateinit var spnYear: Spinner
    private lateinit var spnSubject: Spinner

    private lateinit var spnDepartment: Spinner
    private lateinit var actvTeacher: AutoCompleteTextView

    private lateinit var spnSectionBlock: Spinner
    private lateinit var spnRoom: Spinner
    private lateinit var etCapacity: EditText
    private lateinit var btnSave: Button
    private lateinit var tlScheduleMatrix: TableLayout
    private lateinit var tvConflictWarning: TextView
    private lateinit var spnDay: Spinner
    private lateinit var spnStartTime: Spinner
    private lateinit var spnEndTime: Spinner

    // Data Holders
    private val courseList = mutableListOf<String>()
    private val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year")
    private val weekDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val teacherList = mutableListOf<Teacher>()
    private val departmentList = mutableListOf<String>()
    private val subjectEntryList = mutableListOf<SubjectEntry>()
    private val roomList = mutableListOf<Room>()

    // Assignments for the current Course/Year (Used for Teacher/Room Conflict Check)
    private val allAssignmentsForCourseYear = mutableListOf<ClassAssignment>()

    // Assignments filtered by the currently selected Section Block (Used for Matrix/Section Overlap Check)
    private val currentAssignmentsForMatrix = mutableListOf<ClassAssignment>()

    private val sectionBlockNames = mutableListOf<String>()
    private lateinit var sectionBlockAdapter: ArrayAdapter<String>

    // Time Formatters: Display (12-hour: h:mm a) at Internal (24-hour: HH:mm)
    private val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val internalTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Time slots para sa Matrix header at Selection (7 AM to 8 PM)
    private val timeSlots = 7..20
    private val availableTimeOptions = generateTimeSlots(7, 21, 30)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_schedule_management)

        // Initialize UI components
        spnCourse = findViewById(R.id.spnCourse)
        spnYear = findViewById(R.id.spnYear)
        spnSubject = findViewById(R.id.spnSubject)

        // INITIALIZE NEW UI
        spnDepartment = findViewById(R.id.spnDepartment)
        actvTeacher = findViewById(R.id.actvTeacher)

        spnSectionBlock = findViewById(R.id.spnSectionBlock)
        spnRoom = findViewById(R.id.spnRoom)
        etCapacity = findViewById(R.id.etMaxCapacity)
        btnSave = findViewById(R.id.btnSaveAssignment)
        tlScheduleMatrix = findViewById(R.id.tlScheduleMatrix)
        tvConflictWarning = findViewById(R.id.tvConflictWarning)
        spnDay = findViewById(R.id.spnDay)
        spnStartTime = findViewById(R.id.spnStartTime)
        spnEndTime = findViewById(R.id.spnEndTime)

        // Setup Adapters
        spnYear.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, yearLevels)
        spnDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekDays)

        sectionBlockAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionBlockNames)
        spnSectionBlock.adapter = sectionBlockAdapter

        val timeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            availableTimeOptions
        )
        spnStartTime.adapter = timeAdapter
        spnEndTime.adapter = timeAdapter

        // DEPARTMENT ADAPTER
        spnDepartment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departmentList)

        // AUTOCOMPLETE TEACHER ADAPTER (Initial Empty)
        val teacherAutoCompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        actvTeacher.setAdapter(teacherAutoCompleteAdapter)


        loadCoursesTeachersAndRooms()
        setupListeners()

        etCapacity.setText("50")
        btnSave.isEnabled = false
    }

    private fun getCourseCodeFromSpinner(): String? {
        return spnCourse.selectedItem?.toString()?.trim()?.uppercase(Locale.getDefault())
    }

    private fun loadCoursesTeachersAndRooms() {
        // Load Courses
        firestore.collection("courses").get().addOnSuccessListener { snapshot ->
            courseList.clear()
            snapshot.documents.forEach { doc ->
                doc.getString("code")?.let { courseList.add(it) }
            }
            spnCourse.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseList)
        }

        // Load Teachers and Departments
        teachersCollection.get().addOnSuccessListener { snapshot ->
            teacherList.clear()
            departmentList.clear()
            val uniqueDepartments = mutableSetOf<String>()

            snapshot.documents.forEach { doc ->
                doc.toObject(Teacher::class.java)?.let { teacher ->
                    teacherList.add(teacher)
                    if (teacher.department.isNotBlank()) {
                        uniqueDepartments.add(teacher.department)
                    }
                }
            }

            // Populate Department Spinner
            departmentList.addAll(uniqueDepartments.sorted())
            departmentList.add(0, "All Departments")
            (spnDepartment.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()

            // Initial setup: filter lahat ng guro
            filterTeachersByDepartment(actvTeacher, spnDepartment.selectedItem?.toString())

        }.addOnFailureListener { e ->
            Log.e("ScheduleLoad", "Error loading teacher profiles: ${e.message}")
            Toast.makeText(this, "Error loading teachers.", Toast.LENGTH_SHORT).show()
        }

        // --- Load Rooms ---
        roomsCollection.get().addOnSuccessListener { snapshot ->
            roomList.clear()
            roomList.add(Room(id = "Select Room"))
            snapshot.documents.forEach { doc ->
                doc.toObject(Room::class.java)?.let {
                    if (it.id != "Select Room") {
                        roomList.add(it)
                    }
                }
            }
            val roomNames = roomList.map { it.id }
            spnRoom.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roomNames)
        }
    }

    // FUNCTION: Filter Teachers based on selected department (Flexible for main and edit dialog)
    private fun filterTeachersByDepartment(autoCompleteTextView: AutoCompleteTextView, selectedDept: String?) {
        val selectedDepartment = selectedDept ?: "All Departments"

        val filteredTeachers = if (selectedDepartment == "All Departments" || selectedDepartment.isBlank()) {
            teacherList
        } else {
            teacherList.filter { it.department == selectedDepartment }
        }

        // Format: Name (Department) (UID: last 4 digits)
        val filteredTeacherNames = filteredTeachers.map {
            "${it.name} (${it.department}) (UID: ${it.uid.takeLast(4)})"
        }

        val adapter = autoCompleteTextView.adapter as? ArrayAdapter<String>
        adapter?.clear()
        adapter?.addAll(filteredTeacherNames)
        adapter?.notifyDataSetChanged()

        // Clear previous selection if the department filter changes
        autoCompleteTextView.setText("", false)
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

        // DEPARTMENT LISTENER (Triggers teacher filtering and conflict check)
        spnDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterTeachersByDepartment(actvTeacher, spnDepartment.selectedItem?.toString())
                checkConflicts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // AUTOCOMPLETE LISTENER (Triggers conflict check kapag may pinili)
        actvTeacher.setOnItemClickListener { _, _, _, _, ->
            checkConflicts()
        }

        // CONFLICT CHECKING LISTENERS FOR OTHER SPINNERS
        spnDay.onItemSelectedListener = createConflictListener { checkConflicts() }
        spnStartTime.onItemSelectedListener = createConflictListener { checkConflicts() }
        spnEndTime.onItemSelectedListener = createConflictListener { checkConflicts() }
        spnRoom.onItemSelectedListener = createConflictListener { checkConflicts() }
        etCapacity.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkConflicts() }

        btnSave.setOnClickListener { saveAssignment() }
    }

    // HELPER FUNCTION FOR LISTENERS
    private fun createConflictListener(onCheck: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onCheck()
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    // HELPER FUNCTION: generateTimeSlots (12-hour format)
    private fun generateTimeSlots(startHour: Int, endHour: Int, intervalMinutes: Int): List<String> {
        val times = mutableListOf<String>()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MINUTE, 0)
        }

        for (h in startHour until endHour) {
            calendar.set(Calendar.HOUR_OF_DAY, h)
            for (m in 0 until 60 step intervalMinutes) {
                calendar.set(Calendar.MINUTE, m)
                times.add(displayTimeFormat.format(calendar.time))
            }
        }

        calendar.set(Calendar.HOUR_OF_DAY, endHour)
        calendar.set(Calendar.MINUTE, 0)
        times.add(displayTimeFormat.format(calendar.time))

        return times.distinct().sortedWith(compareBy {
            try {
                displayTimeFormat.parse(it)
            } catch (e: ParseException) {
                // Should not happen if time format is correct
                Date(0)
            }
        })
    }

    // NEW: Flexible parser para sa oras (Handles both "h:mm a" and "HH:mm")
    private fun parseTimeFlexibly(timeStr: String): Date? {
        return try {
            displayTimeFormat.parse(timeStr)
        } catch (e: ParseException) {
            try {
                internalTimeFormat.parse(timeStr)
            } catch (e2: ParseException) {
                null
            }
        }
    }

    // NEW: Helper to get selected Teacher object from Autocomplete
    private fun getSelectedTeacherFromAutocomplete(autoCompleteTextView: AutoCompleteTextView): Teacher? {
        val selectedTeacherDisplay = autoCompleteTextView.text.toString().trim()
        if (selectedTeacherDisplay.isEmpty()) return null

        // Extract the last 4 characters of the UID from the display string
        val uidMatch = "UID: (....)\\)".toRegex().find(selectedTeacherDisplay)
        val lastFourUid = uidMatch?.groupValues?.get(1) ?: return null

        // Find the full Teacher object using the last 4 digits of the UID
        return teacherList.firstOrNull { it.uid.endsWith(lastFourUid) }
    }


    /**
     * Load Subjects (Curriculum), Section Blocks, at Assignments.
     */
    private fun loadSubjectsSectionsAndAllAssignments() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return
        val yearLevelKey = yearLevel.trim().replace(" ", "")

        val docId = "${courseCode}_${yearLevelKey}"
        Log.d("ScheduleLoad", "Target Course: $courseCode, Target Year Key: $yearLevelKey")

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
                } else {
                    spnSubject.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        mutableListOf("No Curriculum Found")
                    )
                    Toast.makeText(this, "Curriculum for $docId not found.", Toast.LENGTH_SHORT)
                        .show()
                }

                // --- Load 2. Section Blocks ---
                loadSectionBlocks(courseCode, yearLevelKey)

            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading curriculum: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                loadSectionBlocks(courseCode, yearLevelKey)
            }
    }

    /**
     * Helper function para mag-load ng Section Blocks.
     */
    private fun loadSectionBlocks(courseCode: String, yearLevelKey: String) {
        sectionsCollection.document(courseCode).get()
            .addOnSuccessListener { doc ->
                sectionBlockNames.clear()

                if (!doc.exists()) {
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
                        sectionBlockNames.addAll(blocksListForYear)
                    }
                }

                sectionBlockNames.add(0, "Select Section")

                sectionBlockAdapter.notifyDataSetChanged()
                spnSectionBlock.setSelection(0)

                loadAllAssignments()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error loading section blocks: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                loadAllAssignments()
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
                filterAssignmentsAndRenderMatrix()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching assignments: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
    }

    private fun filterAssignmentsAndRenderMatrix() {
        val selectedSectionBlock = spnSectionBlock.selectedItem?.toString()?.trim() ?: ""

        if (selectedSectionBlock.isEmpty() || selectedSectionBlock == "Select Section") {
            currentAssignmentsForMatrix.clear()
            renderScheduleMatrix()
            return
        }

        currentAssignmentsForMatrix.clear()
        currentAssignmentsForMatrix.addAll(
            allAssignmentsForCourseYear.filter {
                it.sectionBlock == selectedSectionBlock
            }
        )

        renderScheduleMatrix()
        checkConflicts()
    }

    private fun renderScheduleMatrix() {
        tlScheduleMatrix.removeAllViews()

        // --- 1. Create Table Headers (Days) ---
        val headerRow = TableRow(this)
        headerRow.layoutParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        )

        // Empty corner cell for time column
        val cornerCell = TextView(this).apply {
            text = "Time"
            setPadding(10, 10, 10, 10)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.LTGRAY)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerRow.addView(cornerCell)

        weekDays.forEach { day ->
            val dayHeader = TextView(this).apply {
                text = day
                setPadding(10, 10, 10, 10)
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(Color.LTGRAY)
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.5f)
            }
            headerRow.addView(dayHeader)
        }
        tlScheduleMatrix.addView(headerRow)

        // --- 2. Create Time Rows and Class Blocks ---
        val cellHeight = 100 // Height in pixels for each time slot
        val internalHalfHourFormat = SimpleDateFormat("HH:mm")

        var currentHour = timeSlots.first
        while (currentHour < timeSlots.last) {

            // 🟢 TIYAKIN NA DITO NAKA-DEKLARA ANG ROW
            val row = TableRow(this)
            row.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )

            // Time cell
            val timeCell = TextView(this).apply {
                text = displayTimeFormat.format(internalTimeFormat.parse("$currentHour:00"))
                setPadding(5, 10, 5, 10)
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#F0F0F0"))
                layoutParams = TableRow.LayoutParams(0, cellHeight, 1.0f)
            }
            row.addView(timeCell)

            // Loop para sa bawat araw (Mon, Tue, Wed, ...)
            weekDays.forEach { day ->
                val startInternal = internalHalfHourFormat.format(internalTimeFormat.parse("$currentHour:00"))
                val endInternal = internalHalfHourFormat.format(internalTimeFormat.parse("${currentHour + 1}:00"))

                // Hanapin ang assignment sa oras na ito
                val assignment = currentAssignmentsForMatrix.firstOrNull { a ->
                    a.day == day &&
                            (parseTimeFlexibly(a.startTime)?.time ?: 0L) <= (internalTimeFormat.parse(startInternal)?.time ?: 0L) &&
                            (parseTimeFlexibly(a.endTime)?.time ?: 0L) > (internalTimeFormat.parse(startInternal)?.time ?: 0L)
                }

                val cell = TextView(this).apply {
                    setPadding(5, 5, 5, 5)
                    gravity = Gravity.CENTER
                    layoutParams = TableRow.LayoutParams(0, cellHeight, 1.5f).apply {
                        setMargins(1, 1, 1, 1)
                    }
                    setBackgroundColor(Color.WHITE)

                    if (assignment != null) {
                        text = "${assignment.subjectCode}\n${assignment.teacherName.split(" ").last()}"
                        setBackgroundColor(Color.parseColor("#BBDEFB")) // Light Blue
                        setOnClickListener {
                            showAssignmentDetailsDialog(assignment)
                        }
                    } else {
                        text = ""
                        // Cell Click Listener for adding assignment at this time
                        setOnClickListener {
                            spnDay.setSelection(weekDays.indexOf(day))
                            spnStartTime.setSelection(availableTimeOptions.indexOf(displayTimeFormat.format(internalTimeFormat.parse(startInternal))))
                            spnEndTime.setSelection(availableTimeOptions.indexOf(displayTimeFormat.format(internalTimeFormat.parse(endInternal))))
                            checkConflicts()
                        }
                    }
                }
                row.addView(cell)
            }

            // 🟢 ITO ANG DAPAT ILAGAY SA LOOB NG WHILE LOOP
            tlScheduleMatrix.addView(row)

            currentHour++
        }
    }

    /**
     * Nagpapakita ng dialog para i-delete o i-edit ang detalye ng Assignment.
     */
    private fun showAssignmentDetailsDialog(assignment: ClassAssignment) {
        val teacherName = teacherList.firstOrNull { it.uid == assignment.teacherUid }?.name ?: "N/A"

        val details = """
            Subject: ${assignment.subjectCode} - ${assignment.subjectTitle}
            Section: ${assignment.sectionBlock}
            Time: ${assignment.day} ${assignment.startTime} - ${assignment.endTime}
            Room: ${assignment.roomNumber}
            Instructor: ${teacherName}
            Capacity: ${assignment.enrolledCount} / ${assignment.maxCapacity}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Assignment Details")
            .setMessage(details)
            .setPositiveButton("Edit Assignment") { _, _ ->
                showEditAssignmentDialog(assignment)
            }
            .setNegativeButton("Delete Assignment") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Confirm Deletion")
                    .setMessage("Are you sure you want to permanently delete this assignment?\n${assignment.subjectCode} (${assignment.sectionBlock})")
                    .setPositiveButton("YES, Delete") { _, _ ->
                        deleteAssignment(assignment.assignmentId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("Close", null)
            .show()
    }

    // BAGONG FUNCTION: showEditAssignmentDialog
    private fun showEditAssignmentDialog(assignment: ClassAssignment) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_schedule_dialog_edit, null)
        val spnDayEdit = dialogView.findViewById<Spinner>(R.id.spnDayEdit)
        val spnStartTimeEdit = dialogView.findViewById<Spinner>(R.id.spnStartTimeEdit)
        val spnEndTimeEdit = dialogView.findViewById<Spinner>(R.id.spnEndTimeEdit)
        val spnRoomEdit = dialogView.findViewById<Spinner>(R.id.spnRoomEdit)

        // NEW: Edit Dialog UI components
        val spnDepartmentEdit = dialogView.findViewById<Spinner>(R.id.spnDepartmentEdit)
        val actvTeacherEdit = dialogView.findViewById<AutoCompleteTextView>(R.id.actvTeacherEdit)

        val etCapacityEdit = dialogView.findViewById<EditText>(R.id.etCapacityEdit)
        val tvConflictEdit = dialogView.findViewById<TextView>(R.id.tvConflictWarningEdit)

        // Find current teacher profile
        val currentTeacher = teacherList.firstOrNull { it.uid == assignment.teacherUid }
        val currentTeacherDisplay = currentTeacher?.let {
            "${it.name} (${it.department}) (UID: ${it.uid.takeLast(4)})"
        } ?: ""

        // Setup Adapters
        spnDayEdit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekDays)
        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, availableTimeOptions)
        spnStartTimeEdit.adapter = timeAdapter
        spnEndTimeEdit.adapter = timeAdapter
        val roomNames = roomList.map { it.id }
        spnRoomEdit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roomNames)

        // Department Adapter for Edit
        spnDepartmentEdit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departmentList)
        // Autocomplete Adapter for Edit
        val teacherAutoCompleteAdapterEdit = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        actvTeacherEdit.setAdapter(teacherAutoCompleteAdapterEdit)

        // Set current values
        spnDayEdit.setSelection(weekDays.indexOf(assignment.day))
        spnStartTimeEdit.setSelection(availableTimeOptions.indexOf(assignment.startTime))
        spnEndTimeEdit.setSelection(availableTimeOptions.indexOf(assignment.endTime))
        spnRoomEdit.setSelection(roomNames.indexOf(assignment.roomNumber))
        etCapacityEdit.setText(assignment.maxCapacity.toString())

        // Set Department and Teacher
        val currentDepartment = currentTeacher?.department ?: "All Departments"
        spnDepartmentEdit.setSelection(departmentList.indexOf(currentDepartment))

        // 1. Initial filter must run to populate actvTeacherEdit
        filterTeachersByDepartment(actvTeacherEdit, currentDepartment)

        // 2. Set the current teacher value in the AutoCompleteTextView
        actvTeacherEdit.setText(currentTeacherDisplay, false)


        // Create the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Assignment: ${assignment.subjectCode} (${assignment.sectionBlock})")
            .setView(dialogView)
            .setPositiveButton("Save Changes", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // Conflict check function for Edit Dialog
            val checkEditConflicts = {
                val newDay = spnDayEdit.selectedItem?.toString() ?: ""
                val newStartTimeStr = spnStartTimeEdit.selectedItem?.toString() ?: ""
                val newEndTimeStr = spnEndTimeEdit.selectedItem?.toString() ?: ""
                val newRoom = spnRoomEdit.selectedItem?.toString() ?: ""

                // Get selected teacher from Autocomplete
                val newTeacher = getSelectedTeacherFromAutocomplete(actvTeacherEdit)

                var conflictMessage = ""
                var hasConflict = false

                if (newDay.isEmpty() || newStartTimeStr.isEmpty() || newEndTimeStr.isEmpty() || newRoom.isEmpty() || newTeacher == null) {
                    conflictMessage = "⚠️ Please complete all fields."
                    hasConflict = true
                } else {
                    val getTimeInMs: (String) -> Long = { timeStr ->
                        val parsedTime = parseTimeFlexibly(timeStr)
                        if (parsedTime != null) {
                            internalTimeFormat.parse(internalTimeFormat.format(parsedTime))?.time ?: 0L
                        } else {
                            0L
                        }
                    }

                    try {
                        val newStartInternal = getTimeInMs(newStartTimeStr)
                        val newEndInternal = getTimeInMs(newEndTimeStr)

                        if (newStartInternal >= newEndInternal) {
                            conflictMessage = "❌ Error: Start time must be before end time."
                            hasConflict = true
                        } else {
                            val otherAssignments = allAssignmentsForCourseYear.filter { it.assignmentId != assignment.assignmentId }

                            // 1. Teacher Conflict
                            val teacherConflict = otherAssignments.filter { a ->
                                a.teacherUid == newTeacher.uid && a.day == newDay
                            }.any { existing ->
                                val existingStart = getTimeInMs(existing.startTime)
                                val existingEnd = getTimeInMs(existing.endTime)
                                (newStartInternal < existingEnd && newEndInternal > existingStart)
                            }
                            if (teacherConflict) {
                                conflictMessage += "Teacher Conflict: ${newTeacher.name} is scheduled.\n"
                                hasConflict = true
                            }

                            // 2. Room Conflict
                            val roomConflict = otherAssignments.filter { a ->
                                a.roomNumber == newRoom && a.day == newDay
                            }.any { existing ->
                                val existingStart = getTimeInMs(existing.startTime)
                                val existingEnd = getTimeInMs(existing.endTime)
                                (newStartInternal < existingEnd && newEndInternal > existingStart)
                            }
                            if (roomConflict) {
                                conflictMessage += "Room Conflict: Room $newRoom is occupied.\n"
                                hasConflict = true
                            }

                            // 3. Section Conflict
                            if (assignment.sectionBlock != "Select Section") {
                                val sectionConflict = currentAssignmentsForMatrix.filter { it.assignmentId != assignment.assignmentId }.any { existing ->
                                    val dayMatch = existing.day == newDay
                                    if (dayMatch) {
                                        val existingStart = getTimeInMs(existing.startTime)
                                        val existingEnd = getTimeInMs(existing.endTime)
                                        (newStartInternal < existingEnd && newEndInternal > existingStart)
                                    } else {
                                        false
                                    }
                                }
                                if (sectionConflict) {
                                    conflictMessage += "Section Conflict: ${assignment.sectionBlock} already has a class scheduled at this time.\n"
                                    hasConflict = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        conflictMessage = "❌ Error in time processing: ${e.message}"
                        hasConflict = true
                    }
                }

                tvConflictEdit.text = if (hasConflict) conflictMessage.trim() else "✅ No conflicts found."
                saveButton.isEnabled = !hasConflict
            }

            // Department Listener for Edit Dialog (filters the list)
            spnDepartmentEdit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedDept = spnDepartmentEdit.selectedItem.toString()
                    filterTeachersByDepartment(actvTeacherEdit, selectedDept)
                    checkEditConflicts()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Autocomplete Listener for Edit Dialog (selects teacher and checks conflict)
            actvTeacherEdit.setOnItemClickListener { _, _, _, _, ->
                checkEditConflicts()
            }

            // Other listeners to check for conflicts on change
            spnDayEdit.onItemSelectedListener = createConflictListener(checkEditConflicts)
            spnStartTimeEdit.onItemSelectedListener = createConflictListener(checkEditConflicts)
            spnEndTimeEdit.onItemSelectedListener = createConflictListener(checkEditConflicts)
            spnRoomEdit.onItemSelectedListener = createConflictListener(checkEditConflicts)

            // Initial check
            checkEditConflicts()

            saveButton.setOnClickListener {
                if (saveButton.isEnabled) {
                    val newDay = spnDayEdit.selectedItem.toString()
                    val newStartTimeStrDisplay = spnStartTimeEdit.selectedItem.toString()
                    val newEndTimeStrDisplay = spnEndTimeEdit.selectedItem.toString()
                    val newRoom = spnRoomEdit.selectedItem.toString()
                    val newCapacity = etCapacityEdit.text.toString().toIntOrNull() ?: 50

                    // Get selected teacher
                    val newTeacher = getSelectedTeacherFromAutocomplete(actvTeacherEdit) ?: return@setOnClickListener

                    val parsedTime = parseTimeFlexibly(newStartTimeStrDisplay) ?: return@setOnClickListener
                    val internalStartTime = internalTimeFormat.format(parsedTime)

                    // Gumawa ng bagong Assignment ID
                    val newAssignmentId = "${assignment.courseCode}-${assignment.sectionBlock}-${newDay}-${internalStartTime.replace(":", "")}"

                    val updatedAssignment = assignment.copy(
                        assignmentId = newAssignmentId,
                        teacherUid = newTeacher.uid,
                        teacherName = newTeacher.name,
                        day = newDay,
                        startTime = newStartTimeStrDisplay,
                        endTime = newEndTimeStrDisplay,
                        roomNumber = newRoom,
                        maxCapacity = newCapacity
                    )

                    // 1. Delete the old document if the ID has changed
                    if (assignment.assignmentId != newAssignmentId) {
                        assignmentsCollection.document(assignment.assignmentId).delete()
                            .addOnFailureListener { e -> Log.e("Edit", "Failed to delete old ID: ${assignment.assignmentId}", e) }
                    }

                    // 2. Save the new/updated document
                    assignmentsCollection.document(newAssignmentId).set(updatedAssignment)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Assignment updated successfully!", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            loadAllAssignments()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update assignment: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
        dialog.show()
    }

    /**
     * Nagde-delete ng schedule mula sa Firestore gamit ang Assignment ID.
     */
    private fun deleteAssignment(assignmentId: String) {
        assignmentsCollection.document(assignmentId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Assignment '$assignmentId' successfully deleted.", Toast.LENGTH_LONG).show()
                loadAllAssignments()
                checkConflicts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete assignment: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ManageSchedules", "Delete failed", e)
            }
    }


    private fun checkConflicts() {
        // NEW: Get teacher from Autocomplete
        val selectedTeacher = getSelectedTeacherFromAutocomplete(actvTeacher)

        val day = spnDay.selectedItem?.toString()
        val startTimeStrDisplay = spnStartTime.selectedItem?.toString() ?: ""
        val endTimeStrDisplay = spnEndTime.selectedItem?.toString() ?: ""
        val sectionBlock = spnSectionBlock.selectedItem?.toString()?.trim()
        val roomNumber = spnRoom.selectedItem?.toString()?.trim()

        if (day == null || startTimeStrDisplay.isEmpty() || endTimeStrDisplay.isEmpty() ||
            selectedTeacher == null || sectionBlock == null || sectionBlock == "Select Section" ||
            roomNumber == null || roomNumber == "Select Room"
        ) {
            tvConflictWarning.text = "⚠️ Please complete all schedule details."
            btnSave.isEnabled = false
            return
        }

        val getTimeInMs: (String) -> Long = { timeStr ->
            val parsedTime = parseTimeFlexibly(timeStr)
            if (parsedTime != null) {
                internalTimeFormat.parse(internalTimeFormat.format(parsedTime))?.time ?: 0L
            } else {
                0L
            }
        }


        try {
            val newStartInternal = getTimeInMs(startTimeStrDisplay)
            val newEndInternal = getTimeInMs(endTimeStrDisplay)


            if (newStartInternal == 0L || newEndInternal == 0L) {
                tvConflictWarning.text = "❌ Error: Invalid time selected."
                btnSave.isEnabled = false
                return
            }

            if (newStartInternal >= newEndInternal) {
                tvConflictWarning.text = "❌ Error: Start time must be before end time."
                btnSave.isEnabled = false
                return
            }

            val allConflicts = mutableListOf<String>()

            // 1. Teacher Conflict Check
            val teacherConflict = allAssignmentsForCourseYear.filter { assignment ->
                assignment.teacherUid == selectedTeacher.uid && assignment.day == day
            }.any { existing ->
                val existingStart = getTimeInMs(existing.startTime)
                val existingEnd = getTimeInMs(existing.endTime)
                (newStartInternal < existingEnd && newEndInternal > existingStart)
            }
            if (teacherConflict) {
                allConflicts.add("Teacher Conflict: ${selectedTeacher.name} is already scheduled at this time.")
            }


            // 2. Section Overlap Check
            val sectionConflict = currentAssignmentsForMatrix.any { existing ->
                val dayMatch = existing.day == day
                if (dayMatch) {
                    val existingStart = getTimeInMs(existing.startTime)
                    val existingEnd = getTimeInMs(existing.endTime)
                    (newStartInternal < existingEnd && newEndInternal > existingStart)
                } else {
                    false
                }
            }
            if (sectionConflict) {
                allConflicts.add("Section Conflict: ${sectionBlock} already has a class scheduled at this time.")
            }

            // 3. Room Conflict Check
            val roomConflict = allAssignmentsForCourseYear.filter { assignment ->
                assignment.roomNumber == roomNumber && assignment.day == day
            }.any { existing ->
                val existingStart = getTimeInMs(existing.startTime)
                val existingEnd = getTimeInMs(existing.endTime)
                (newStartInternal < existingEnd && newEndInternal > existingStart)
            }
            if (roomConflict) {
                allConflicts.add("Room Conflict: Room $roomNumber is already occupied at this time.")
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
            tvConflictWarning.text = "❌ Error in time format/parsing: ${e.message}"
            btnSave.isEnabled = false
        }
    }

    private fun saveAssignment() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return
        val subjectName = spnSubject.selectedItem?.toString() ?: return
        // NEW: Get selected teacher
        val selectedTeacher = getSelectedTeacherFromAutocomplete(actvTeacher) ?: run {
            Toast.makeText(this, "Please select a valid teacher.", Toast.LENGTH_SHORT).show()
            return
        }

        val sectionBlock = spnSectionBlock.selectedItem?.toString()?.trim() ?: return
        val roomNumber = spnRoom.selectedItem?.toString()?.trim() ?: return
        val day = spnDay.selectedItem?.toString() ?: return
        val startTimeStrDisplay = spnStartTime.selectedItem?.toString() ?: return
        val endTimeStrDisplay = spnEndTime.selectedItem?.toString() ?: return
        val capacity = etCapacity.text.toString().toIntOrNull() ?: 50

        if (!btnSave.isEnabled) {
            Toast.makeText(this, "Please resolve conflicts before saving.", Toast.LENGTH_LONG)
                .show()
            return
        }

        val (subjectCode, subjectTitle) = subjectName.split(" - ").map { it.trim() }
            .let { Pair(it.getOrNull(0) ?: "", it.getOrNull(1) ?: "") }
        val yearNumber = yearLevel.substring(0, 1)

        val parsedTime = parseTimeFlexibly(startTimeStrDisplay) ?: return
        val internalStartTime = internalTimeFormat.format(parsedTime)


        val assignmentId =
            "${courseCode}-${yearNumber}-${sectionBlock}-${day}-${internalStartTime.replace(":", "")}"
        val sectionName = "${courseCode}-${yearNumber}-${sectionBlock}"

        val assignment = ClassAssignment(
            assignmentId = assignmentId,
            courseCode = courseCode,
            yearLevel = yearLevel,
            sectionBlock = sectionBlock,
            sectionName = sectionName,
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            teacherUid = selectedTeacher.uid, // Use UID from the object
            teacherName = selectedTeacher.name, // Use name from the object
            day = day,
            startTime = startTimeStrDisplay,
            endTime = endTimeStrDisplay,
            roomNumber = roomNumber,
            maxCapacity = capacity,
            enrolledCount = 0
        )

        assignmentsCollection.document(assignmentId).set(assignment)
            .addOnSuccessListener {
                Toast.makeText(this, "Schedule successfully saved!", Toast.LENGTH_LONG).show()

                clearScheduleInputs()

                loadAllAssignments()
                checkConflicts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save schedule: ${e.message}", Toast.LENGTH_LONG)
                    .show()
                Log.e("ManageSchedules", "Save failed", e)
            }
    }

    private fun clearScheduleInputs() {
        spnSubject.setSelection(0)
        spnDepartment.setSelection(0)
        actvTeacher.setText("", false)
        spnDay.setSelection(0)
        spnRoom.setSelection(0)
        spnStartTime.setSelection(0)
        spnEndTime.setSelection(0)
        etCapacity.setText("50")
        tvConflictWarning.text = ""
        btnSave.isEnabled = false
    }

}
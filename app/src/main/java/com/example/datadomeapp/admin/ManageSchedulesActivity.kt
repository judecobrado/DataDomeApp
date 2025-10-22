package com.example.datadomeapp.admin

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.* // Import all models including TimeSlot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import com.google.firebase.firestore.SetOptions

class ManageSchedulesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val assignmentsCollection = firestore.collection("classAssignments")
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
    private val weekDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val teacherList = mutableListOf<Teacher>()
    private val departmentList = mutableListOf<String>()
    private val subjectEntryList = mutableListOf<SubjectEntry>()
    private val roomList = mutableListOf<Room>()

    // Assignments for the current Course/Year (Subject-Centric)
    private val allAssignmentsForCourseYear = mutableListOf<ClassAssignment>()

    // Assignments filtered by the currently selected Section Block (Subject-Centric)
    private val currentAssignmentsForMatrix = mutableListOf<ClassAssignment>()

    private val sectionBlockNames = mutableListOf<String>()
    private lateinit var sectionBlockAdapter: ArrayAdapter<String>

    // Time Formatters: Display (12-hour: h:mm a) at Internal (24-hour: HH:mm)
    private val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val internalTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Time slots para sa Matrix header at Selection (7 AM to 8 PM)
    private val timeSlots = 7..20
    private val availableTimeOptions = generateTimeSlots(7, 21, 30)


    private var sectionBlockList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_schedule_management)

        // Initialize UI components
        spnCourse = findViewById(R.id.spnCourse)
        spnYear = findViewById(R.id.spnYear)
        spnSubject = findViewById(R.id.spnSubject)

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

            // 🟢 FIX: Unchecked cast warning
            (spnDepartment.adapter as? ArrayAdapter<String>)?.notifyDataSetChanged()

            // Initial setup: filter lahat ng guro
            filterTeachersByDepartment(actvTeacher, spnDepartment.selectedItem?.toString())

        }.addOnFailureListener { e ->
            Log.e("ScheduleLoad", "Error loading teacher profiles: ${e.message}")
            Toast.makeText(this, "Error loading teachers.", Toast.LENGTH_SHORT).show()
        }

        // --- Load Rooms ---
        roomsCollection.get().addOnSuccessListener { snapshot ->
            roomList.clear()
            snapshot.documents.forEach { doc ->
                doc.toObject(Room::class.java)?.let {
                    roomList.add(it)
                }
            }
            val roomNames = roomList.map { it.id }.toMutableList()
            roomNames.add(0, "Select Room")
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
                // Parse the display string back to a Date for sorting
                displayTimeFormat.parse(it) ?: Date(0)
            } catch (e: ParseException) {
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


    // Load Lahat ng Assignments para sa Course/Year (Subject-Centric)
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
        // Kumuha ng ClassAssignments na may kahit isang TimeSlot na tumutugma sa Section Block
        currentAssignmentsForMatrix.addAll(
            allAssignmentsForCourseYear.filter { assignment ->
                // Tingnan kung may kahit isang TimeSlot sa Map na tumutugma sa selectedSectionBlock
                assignment.scheduleSlots.values.any { slot ->
                    slot.sectionBlock == selectedSectionBlock
                }
            }
        )

        renderScheduleMatrix()
        checkConflicts()
    }

    // 🟢 UPDATED: renderScheduleMatrix para gamitin ang scheduleSlots
    private fun renderScheduleMatrix() {
        tlScheduleMatrix.removeAllViews()

        // --- 1. Header creation ---
        val headerRow = TableRow(this)
        headerRow.layoutParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        )
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
        // --- End of Header creation ---


        // --- 2. Create Time Rows and Class Blocks ---
        val cellHeight = 100 // Height in pixels for each time slot
        val internalHalfHourFormat = SimpleDateFormat("HH:mm")

        // CRITICAL: Kumuha ng kasalukuyang Section Block para sa Matrix
        val selectedSectionBlock = spnSectionBlock.selectedItem?.toString()?.trim() ?: ""

        var currentHour = timeSlots.first
        while (currentHour < timeSlots.last) {

            val row = TableRow(this)
            row.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )

            // Time cell (Display format)
            val timeDisplay = internalTimeFormat.parse("$currentHour:00")
            val timeCell = TextView(this).apply {
                text = if (timeDisplay != null) displayTimeFormat.format(timeDisplay) else "$currentHour:00"
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
                val startInternalTimeMs = internalTimeFormat.parse(startInternal)?.time ?: 0L

                // 🟢 NEW LOGIC: Hanapin ang TimeSlot na pasok sa oras na ito
                val assignedPair = currentAssignmentsForMatrix.firstNotNullOfOrNull { assignment ->
                    // Gamitin ang .values para i-iterate ang TimeSlot objects sa Map
                    assignment.scheduleSlots.values.firstOrNull { slot ->
                        // ⚠️ CHECK: Siguraduhin na ang slot ay para sa kasalukuyang Section Block
                        slot.sectionBlock == selectedSectionBlock &&
                                slot.day == day &&
                                // CRITICAL: Time Slot Overlap Check
                                (parseTimeFlexibly(slot.startTime)?.time ?: 0L) <= startInternalTimeMs &&
                                (parseTimeFlexibly(slot.endTime)?.time ?: 0L) > startInternalTimeMs
                    }?.let { slot ->
                        // CRITICAL: I-retrieve ang slot key para sa deletion/editing
                        val slotKey = assignment.scheduleSlots.entries.first { it.value == slot }.key
                        Triple(assignment, slot, slotKey)
                    }
                }

                val cell = TextView(this).apply {
                    setPadding(5, 5, 5, 5)
                    gravity = Gravity.CENTER
                    layoutParams = TableRow.LayoutParams(0, cellHeight, 1.5f).apply {
                        setMargins(1, 1, 1, 1)
                    }
                    setBackgroundColor(Color.WHITE)

                    if (assignedPair != null) {
                        val (assignment, slot, slotKey) = assignedPair
                        // ⚠️ Gamitin ang slot.roomLocation
                        text = "${assignment.subjectCode}\n${assignment.teacherName.split(" ").last()}\n@${slot.roomLocation}"
                        setBackgroundColor(Color.parseColor("#BBDEFB")) // Light Blue
                        setOnClickListener {
                            // CRITICAL: Ipinasa ang assignment, ang TimeSlot, at ang slotKey (para sa pag-update)
                            showAssignmentDetailsDialog(assignment, slot, slotKey)
                        }
                    } else {
                        text = ""
                        // Cell Click Listener for adding assignment at this time
                        setOnClickListener {
                            // Logic para i-set ang spinner sa oras na kinlik
                            val nextHourInternalTime = internalTimeFormat.parse("${currentHour + 1}:00")
                            val nextHourDisplay = if (nextHourInternalTime != null) displayTimeFormat.format(nextHourInternalTime) else ""
                            // ⚠️ Siguraduhin na ang parse ng startInternal ay tama
                            val currentHourDisplay = displayTimeFormat.format(internalTimeFormat.parse(startInternal))

                            spnDay.setSelection(weekDays.indexOf(day))
                            spnStartTime.setSelection(availableTimeOptions.indexOf(currentHourDisplay))
                            spnEndTime.setSelection(availableTimeOptions.indexOf(nextHourDisplay))
                            checkConflicts()
                        }
                    }
                }
                row.addView(cell)
            }

            tlScheduleMatrix.addView(row)

            currentHour++
        }
    }

    /**
     * Nagpapakita ng dialog para i-delete o i-edit ang detalye ng Time Slot.
     */
    private fun showAssignmentDetailsDialog(assignment: ClassAssignment, slot: TimeSlot, slotKey: String) {
        val teacherName = assignment.teacherName

        val details = """
        Subject: ${assignment.subjectCode} - ${assignment.subjectTitle}
        Section: ${slot.sectionBlock} 
        Time Slot: ${slot.day} ${slot.startTime} - ${slot.endTime}
        Room: ${slot.roomLocation} 
        Instructor: ${teacherName}
        Capacity: ${assignment.enrolledCount} / ${assignment.maxCapacity}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Assignment Details")
            .setMessage(details)
            .setPositiveButton("Edit Time Slot") { _, _ ->
                showEditSlotDialog(assignment, slot, slotKey) // ⚠️ Ipinasa ang slotKey
            }
            .setNegativeButton("Delete Time Slot") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Confirm Deletion")
                    .setMessage("Are you sure you want to delete this time slot?\n${assignment.subjectCode} (${slot.sectionBlock}) - ${slot.day} ${slot.startTime}")
                    .setPositiveButton("YES, Delete") { _, _ ->
                        deleteTimeSlot(assignment, slotKey) // ⚠️ Ipinasa ang slotKey
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("Close", null)
            .show()
    }

    // 🟢 UPDATED: Function para i-delete ang isang Time Slot (Gamit ang Slot Key)
    // 🟢 FIXED: Function para i-delete ang isang Time Slot (May check na sa enrolledCount)
    private fun deleteTimeSlot(assignment: ClassAssignment, slotKeyToDelete: String) {

        // 🛑 1. CRITICAL CHECK: Tiyakin na walang enrolled na estudyante.
        if (assignment.enrolledCount > 0) {
            AlertDialog.Builder(this)
                .setTitle("Deletion Blocked")
                .setMessage("Cannot delete this schedule slot. There are currently ${assignment.enrolledCount} enrolled students. Please unenroll them first.")
                .setPositiveButton("OK", null)
                .show()
            return // ⬅️ I-block ang deletion process
        }

        // 2. Gumawa ng bagong mutable map ng slots
        val updatedSlots = assignment.scheduleSlots.toMutableMap()

        // Tiyakin na ang slot na ide-delete ay talagang nandoon
        if (!updatedSlots.containsKey(slotKeyToDelete)) {
            Toast.makeText(this, "Error: Slot to delete not found.", Toast.LENGTH_SHORT).show()
            return
        }

        // Tanggalin ang slot gamit ang key
        updatedSlots.remove(slotKeyToDelete)

        // 3. I-execute ang Deletion/Update
        if (updatedSlots.isEmpty()) {
            // A. Kung wala nang slots, burahin ang buong ClassAssignment document (gamit ang assignmentNo)
            assignmentsCollection.document(assignment.assignmentNo).delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "${assignment.subjectCode} assignment deleted completely.", Toast.LENGTH_LONG).show()
                    loadAllAssignments()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete assignment: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            // B. Kung may natira pang slots, i-update ang ClassAssignment document
            // Tandaan: Maaari mong i-renumber ang keys (slot1, slot2) dito,
            // pero mas simple kung hahayaan mo lang ang keys (slot1, slot3) at magdaragdag ka lang ng slotX
            // kung sakaling mag-a-add ulit. Para sa simplehan, hayaan na lang ang keys.

            val updatedAssignment = assignment.copy(scheduleSlots = updatedSlots)
            assignmentsCollection.document(assignment.assignmentNo).set(updatedAssignment)
                .addOnSuccessListener {
                    Toast.makeText(this, "Time slot deleted successfully!", Toast.LENGTH_LONG).show()
                    loadAllAssignments()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete time slot: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    // 🟢 NEW: Function para i-edit ang isang Time Slot
    private fun showEditSlotDialog(assignment: ClassAssignment, slotToEdit: TimeSlot, slotKeyToEdit: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_schedule_dialog_edit, null) // Assuming ito ang tamang layout file name
        // ⚠️ CRITICAL: NEW SPINNER
        //val spnSectionBlockEdit = dialogView.findViewById<TextView>(R.id.spnSectionBlockEdit) // Assume ito ang ID na ginamit mo

        val spnDayEdit = dialogView.findViewById<Spinner>(R.id.spnDayEdit)
        val spnStartTimeEdit = dialogView.findViewById<Spinner>(R.id.spnStartTimeEdit)
        val spnEndTimeEdit = dialogView.findViewById<Spinner>(R.id.spnEndTimeEdit)
        val spnRoomEdit = dialogView.findViewById<Spinner>(R.id.spnRoomEdit)

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
        val roomNames = roomList.map { it.id }.toMutableList().apply { add(0, "Select Room") }
        spnRoomEdit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roomNames)

        // Department Adapter for Edit
        spnDepartmentEdit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departmentList)
        // Autocomplete Adapter for Edit
        val teacherAutoCompleteAdapterEdit = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        actvTeacherEdit.setAdapter(teacherAutoCompleteAdapterEdit)

        // Set current values
        spnDayEdit.setSelection(weekDays.indexOf(slotToEdit.day))
        spnStartTimeEdit.setSelection(availableTimeOptions.indexOf(slotToEdit.startTime))
        spnEndTimeEdit.setSelection(availableTimeOptions.indexOf(slotToEdit.endTime))
        spnRoomEdit.setSelection(roomNames.indexOf(slotToEdit.roomLocation))
        etCapacityEdit.setText(assignment.maxCapacity.toString())

        // ⚠️ CRITICAL: Set initial Section Block (Gamit ang slotToEdit)
        val initialSection = slotToEdit.sectionBlock // ⬅️ FIX: Line 746/747 - Kinukuha na sa slotToEdit

        // Set Department and Teacher
        val currentDepartment = currentTeacher?.department ?: "All Departments"
        spnDepartmentEdit.setSelection(departmentList.indexOf(currentDepartment))
        filterTeachersByDepartment(actvTeacherEdit, currentDepartment)
        actvTeacherEdit.setText(currentTeacherDisplay, false)


        // Create the AlertDialog
        // ⚠️ CRITICAL: UPDATED TITLE (gamit ang slotToEdit.sectionBlock)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Slot for: ${assignment.subjectCode} (${slotToEdit.sectionBlock})")
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
                val newSection = slotToEdit.sectionBlock // Gagamitin ang original section
                val newCapacity = etCapacityEdit.text.toString().toIntOrNull() ?: 50

                // Get selected teacher from Autocomplete (for checking)
                val newTeacher = getSelectedTeacherFromAutocomplete(actvTeacherEdit)

                var conflictMessage = ""
                var hasConflict = false

                if (newDay.isEmpty() || newStartTimeStr.isEmpty() || newEndTimeStr.isEmpty() || newRoom == "Select Room" || newTeacher == null || newSection == "Select Section" || newSection.isEmpty()) {
                    conflictMessage = "⚠️ Please complete all fields."
                    hasConflict = true
                } else {
                    // ... (Time comparison logic)
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
                            // 🟢 NEW CONFLICT CHECK LOGIC (May sectionBlock na)
                            val newSlot = TimeSlot(newDay, newStartTimeStr, newEndTimeStr, newRoom, newSection) // ⚠️ May sectionBlock na

                            val allConflicts = checkSlotConflicts(
                                newSlot = newSlot,
                                targetTeacherUid = newTeacher.uid,
                                assignmentToExcludeId = assignment.assignmentNo // Huwag i-check ang sarili niyang document
                            )

                            if (allConflicts.isNotEmpty()) {
                                conflictMessage = allConflicts.joinToString("\n")
                                hasConflict = true
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

            // Listeners to check for conflicts on change
            spnDepartmentEdit.onItemSelectedListener = createConflictListener { filterTeachersByDepartment(actvTeacherEdit, spnDepartmentEdit.selectedItem?.toString()); checkEditConflicts() }
            actvTeacherEdit.setOnItemClickListener { _, _, _, _, -> checkEditConflicts() }
            spnDayEdit.onItemSelectedListener = createConflictListener { checkEditConflicts() }
            spnStartTimeEdit.onItemSelectedListener = createConflictListener { checkEditConflicts() }
            spnEndTimeEdit.onItemSelectedListener = createConflictListener { checkEditConflicts() }
            spnRoomEdit.onItemSelectedListener = createConflictListener { checkEditConflicts() }
            etCapacityEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkEditConflicts() }
            etCapacityEdit.setOnClickListener { checkEditConflicts() } // Add click listener too

            // Initial check
            checkEditConflicts()

            saveButton.setOnClickListener {
                if (saveButton.isEnabled) {
                    val newDay = spnDayEdit.selectedItem.toString()
                    val newStartTimeStrDisplay = spnStartTimeEdit.selectedItem.toString()
                    val newEndTimeStrDisplay = spnEndTimeEdit.selectedItem.toString()
                    val newRoom = spnRoomEdit.selectedItem.toString()
                    val newSection = slotToEdit.sectionBlock
                    val newCapacity = etCapacityEdit.text.toString().toIntOrNull() ?: 50
                    val newTeacher = getSelectedTeacherFromAutocomplete(actvTeacherEdit) ?: return@setOnClickListener

                    // 1. Gumawa ng updated ClassAssignment document
                    // ⚠️ CRITICAL FIX: Paggamit ng Map at slotKeyToEdit
                    val newSlot = TimeSlot(newDay, newStartTimeStrDisplay, newEndTimeStrDisplay, newRoom, newSection) // ⚠️ May section block na

                    // ⚠️ CRITICAL FIX: Map logic (Line 839 errors addressed)
                    val updatedSlots = assignment.scheduleSlots.toMutableMap()
                    updatedSlots[slotKeyToEdit] = newSlot // ⬅️ I-update ang slot gamit ang KEY

                    val updatedAssignment = assignment.copy(
                        teacherUid = newTeacher.uid,
                        teacherName = newTeacher.name,
                        maxCapacity = newCapacity,
                        scheduleSlots = updatedSlots // ⬅️ Ipinasa ang updated Map
                    )

                    // 2. I-save ang in-update na document
                    assignmentsCollection.document(assignment.assignmentNo).set(updatedAssignment) // ⬅️ FIX: assignmentId -> assignmentNo
                        .addOnSuccessListener {
                            Toast.makeText(this, "Time Slot updated successfully!", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            loadAllAssignments() // Refresh Matrix
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update time slot: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
        dialog.show()
    }


    /**
     * Helper function para mag-check ng conflict sa isang TimeSlot
     */
    private fun checkSlotConflicts(newSlot: TimeSlot, targetTeacherUid: String?, assignmentToExcludeId: String? = null): List<String> {
        // ⚠️ Palitan ang roomNumber ng roomLocation
        if (targetTeacherUid == null || newSlot.roomLocation == "Select Room") return listOf("⚠️ Missing teacher or room details.")

        val allConflicts = mutableListOf<String>()

        val getTimeInMs: (String) -> Long = { timeStr ->
            val parsedTime = parseTimeFlexibly(timeStr)
            if (parsedTime != null) {
                internalTimeFormat.parse(internalTimeFormat.format(parsedTime))?.time ?: 0L
            } else {
                0L
            }
        }

        val newStartInternal = internalTimeFormat.parse(internalTimeFormat.format(parseTimeFlexibly(newSlot.startTime) ?: Date(0)))?.time ?: 0L
        val newEndInternal = internalTimeFormat.parse(internalTimeFormat.format(parseTimeFlexibly(newSlot.endTime) ?: Date(0)))?.time ?: 0L

        if (newStartInternal >= newEndInternal) {
            return listOf("❌ Error: Start time must be before end time.")
        }

        // Iterahin ang lahat ng assignments na na-load (maliban sa sarili kung nag-e-edit)
        // ⚠️ Gumamit ng assignmentNo para i-exclude
        allAssignmentsForCourseYear.filter { it.assignmentNo != assignmentToExcludeId }.forEach { existingAssignment ->

            // ⚠️ Gamitin ang .values para i-iterate ang TimeSlot objects sa Map
            existingAssignment.scheduleSlots.values.forEach { existingSlot ->

                if (existingSlot.day == newSlot.day) {

                    val existingStart = internalTimeFormat.parse(internalTimeFormat.format(parseTimeFlexibly(existingSlot.startTime) ?: Date(0)))?.time ?: 0L
                    val existingEnd = internalTimeFormat.parse(internalTimeFormat.format(parseTimeFlexibly(existingSlot.endTime) ?: Date(0)))?.time ?: 0L

                    // Check Overlap: (A < D && C > B)
                    val isOverlapping = (newStartInternal < existingEnd && newEndInternal > existingStart)

                    if (isOverlapping) {
                        // 1. Teacher Conflict Check (Walang pagbabago)
                        if (existingAssignment.teacherUid == targetTeacherUid) {
                            allConflicts.add("Teacher Conflict: ${existingAssignment.teacherName} is scheduled in ${existingSlot.sectionBlock} at ${existingSlot.startTime} in ${existingSlot.roomLocation}.")
                        }

                        // 2. Room Conflict Check (⚠️ Palitan ang field name)
                        if (existingSlot.roomLocation == newSlot.roomLocation) {
                            allConflicts.add("Room Conflict: Room ${newSlot.roomLocation} is occupied by ${existingAssignment.subjectCode} (${existingSlot.sectionBlock}) at ${existingSlot.startTime}.")
                        }

                        // 3. Section Conflict Check (⚠️ Palitan: Gamitin ang sectionBlock sa TimeSlot)
                        if (existingSlot.sectionBlock == newSlot.sectionBlock) {
                            allConflicts.add("Section Conflict: ${newSlot.sectionBlock} has ${existingAssignment.subjectCode} at ${existingSlot.startTime}.")
                        }
                    }
                }
            }
        }

        return allConflicts.distinct()
    }


    // 🟢 UPDATED: checkConflicts para gamitin ang checkSlotConflicts
    private fun checkConflicts() {
        val selectedTeacher = getSelectedTeacherFromAutocomplete(actvTeacher)
        val day = spnDay.selectedItem?.toString()
        val startTimeStrDisplay = spnStartTime.selectedItem?.toString() ?: ""
        val endTimeStrDisplay = spnEndTime.selectedItem?.toString() ?: ""
        val sectionBlock = spnSectionBlock.selectedItem?.toString()?.trim() // Kukunin ang Section Block
        val roomLocation = spnRoom.selectedItem?.toString()?.trim() // ⚠️ roomLocation na
        val subjectName = spnSubject.selectedItem?.toString() ?: ""

        if (day == null || startTimeStrDisplay.isEmpty() || endTimeStrDisplay.isEmpty() ||
            selectedTeacher == null || sectionBlock == null || sectionBlock == "Select Section" ||
            roomLocation == "Select Room" || roomLocation == null || subjectName == "No Curriculum Found" || subjectName.isEmpty()
        ) {
            tvConflictWarning.text = "⚠️ Please complete all schedule details."
            btnSave.isEnabled = false
            return
        }

        // ⚠️ Gumawa ng TimeSlot object (may sectionBlock na)
        val newSlot = TimeSlot(day, startTimeStrDisplay, endTimeStrDisplay, roomLocation, sectionBlock)

        val allConflicts = checkSlotConflicts(
            newSlot = newSlot,
            targetTeacherUid = selectedTeacher.uid,
            assignmentToExcludeId = null // Walang i-e-exclude dahil INSERT ang ginagawa
        )

        // --- Final Output ---
        if (allConflicts.isNotEmpty()) {
            tvConflictWarning.text = "⚠️ Conflicts Found:\n" + allConflicts.joinToString("\n")
            btnSave.isEnabled = false
        } else {
            tvConflictWarning.text = "✅ No conflicts found. Ready to save."
            btnSave.isEnabled = true
        }
    }

    // 🟢 UPDATED: saveAssignment para gumawa ng Upsert (Subject-Centric)
    // 🟢 FINAL & CORRECTED: saveAssignment para gumawa ng Upsert (Subject/Teacher/Section-Centric)
    private fun saveAssignment() {
        val courseCode = getCourseCodeFromSpinner() ?: return
        val yearLevel = spnYear.selectedItem?.toString() ?: return
        val subjectName = spnSubject.selectedItem?.toString() ?: return
        val selectedTeacher = getSelectedTeacherFromAutocomplete(actvTeacher) ?: return

        val sectionBlock = spnSectionBlock.selectedItem?.toString()?.trim() ?: return
        val roomLocation = spnRoom.selectedItem?.toString()?.trim() ?: return
        val day = spnDay.selectedItem?.toString() ?: return
        val startTimeStrDisplay = spnStartTime.selectedItem?.toString() ?: return
        val endTimeStrDisplay = spnEndTime.selectedItem?.toString() ?: return
        val capacity = etCapacity.text.toString().toIntOrNull() ?: 50

        if (!btnSave.isEnabled) {
            Toast.makeText(this, "Please resolve conflicts before saving.", Toast.LENGTH_LONG).show()
            return
        }

        val (subjectCode, subjectTitle) = subjectName.split(" - ").map { it.trim() }
            .let { Pair(it.getOrNull(0) ?: "", it.getOrNull(1) ?: "") }

        // 2. Gumawa ng bagong TimeSlot object
        val newSlot = TimeSlot(day, startTimeStrDisplay, endTimeStrDisplay, roomLocation, sectionBlock)

        // 3. Simulan ang Firestore Query: Hanapin kung may existing document na tumutugma sa key:
        // [Course, Year, Subject, Teacher, SectionBlock]
        assignmentsCollection
            .whereEqualTo("courseCode", courseCode)
            .whereEqualTo("yearLevel", yearLevel)
            .whereEqualTo("subjectCode", subjectCode)
            .whereEqualTo("teacherUid", selectedTeacher.uid)
            // ⚠️ CRITICAL: I-check kung may existing slot sa sectionBlock na ito
            .whereEqualTo("scheduleSlots.slot1.sectionBlock", sectionBlock)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val existingDocId = querySnapshot.documents.firstOrNull()?.id // Kunin ang Firestore ID

                // Simulan ang Transaction (Para sa Atomic Update/Insert)
                firestore.runTransaction { transaction ->

                    val assignmentRef = if (existingDocId != null) {
                        // UPDATE path: Gamitin ang existing ID
                        assignmentsCollection.document(existingDocId)
                    } else {
                        // INSERT path: Mag-generate lang ng temporary reference
                        assignmentsCollection.document()
                    }

                    val snapshot = transaction.get(assignmentRef)

                    if (snapshot.exists() && existingDocId != null) {
                        // ============== UPDATE LOGIC (May existing document) ==============
                        val existingAssignment = snapshot.toObject(ClassAssignment::class.java)!!

                        // Check for duplicate slot (oras, araw, at room ay pareho)
                        if (existingAssignment.scheduleSlots.values.any { it == newSlot }) {
                            throw Exception("Duplicate time slot detected: Slot already exists for this subject.")
                        }

                        val updatedSlots = existingAssignment.scheduleSlots.toMutableMap()
                        // Idagdag ang new slot gamit ang bagong key (e.g., "slotX")
                        val newSlotKey = "slot${updatedSlots.size + 1}"
                        updatedSlots[newSlotKey] = newSlot

                        val updatedAssignment = existingAssignment.copy(
                            maxCapacity = capacity, // I-update ang capacity
                            scheduleSlots = updatedSlots
                        )
                        transaction.set(assignmentRef, updatedAssignment)

                    } else {
                        // ============== INSERT LOGIC (Bagong document) ==============

                        // 1. Basahin at i-increment ang Counter Document (Atomic Operation)
                        val counterRef = firestore.collection("systemCounters").document("assignmentNoCounter")
                        val counterSnapshot = transaction.get(counterRef)

                        val currentValue = (counterSnapshot.get("currentValue") as? Number)?.toLong() ?: 0L
                        val nextValue = currentValue + 1

                        // ✅ FIX: Gumamit ng SET at MERGE (Para sa Upsert ng Counter)
                        transaction.set(counterRef, mapOf("currentValue" to nextValue), SetOptions.merge())

                        // 2. Gumawa ng Unique Sequential ID (e.g., "0001")
                        val shortReadableNo = String.format("%04d", nextValue)

                        // ⚠️ KRITIKAL: Gumamit ng BAGONG REFERENCE na may sequential ID bilang Document ID
                        val newAssignmentRef = assignmentsCollection.document(shortReadableNo)

                        val newAssignment = ClassAssignment(
                            assignmentNo = shortReadableNo, // Ang arbitrary ID
                            academicYear = "2025-2026", // ⚠️ Palitan ito ng actual input
                            semester = "1st Semester",   // ⚠️ Palitan ito ng actual input
                            courseCode = courseCode,
                            yearLevel = yearLevel,
                            subjectCode = subjectCode,
                            subjectTitle = subjectTitle,
                            teacherUid = selectedTeacher.uid,
                            teacherName = selectedTeacher.name,
                            maxCapacity = capacity,
                            enrolledCount = 0,
                            scheduleSlots = mapOf("slot1" to newSlot) // Bagong Map structure
                        )
                        transaction.set(newAssignmentRef, newAssignment)
                    }
                    null // Return null to commit the transaction
                }
                    .addOnSuccessListener {
                        // ... (Success Handler)
                        Toast.makeText(this, "Time slot successfully saved/updated!", Toast.LENGTH_LONG).show()
                        clearScheduleInputs()
                        loadAllAssignments()
                        checkConflicts()
                    }.addOnFailureListener { e ->
                        // ... (Failure Handler)
                        if (e.message?.contains("Duplicate time slot") == true) {
                            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Failed to save schedule: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        Log.e("ManageSchedules", "Save failed", e)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error querying existing assignments: ${e.message}", Toast.LENGTH_LONG).show()
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
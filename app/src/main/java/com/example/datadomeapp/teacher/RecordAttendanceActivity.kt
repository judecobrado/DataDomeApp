package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldPath
import androidx.appcompat.app.AlertDialog
import com.example.datadomeapp.models.Student
import com.example.datadomeapp.models.TimeSlot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat
import com.example.datadomeapp.R

// DATA MODEL: Aggregated Attendance Record (kasama ang Recitation)
data class DailyAttendanceRecord(
    val assignmentId: String = "",
    val subjectCode: String = "",
    val date: String = "",
    val timeSlotKey: String = "",
    val displayTimeSlot: String = "",
    val statuses: Map<String, String> = emptyMap(), // studentId -> Status
    val recitationPoints: Map<String, Int> = emptyMap() // studentId -> 0 or 1
)


class RecordAttendanceActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val ATTENDANCE_COLLECTION = "dailyAttendanceRecords"

    private lateinit var tvAttendanceHeader: TextView
    private lateinit var etAttendanceDate: EditText
    private lateinit var etAttendanceTimeSlot: EditText

    private lateinit var rgModeSelection: RadioGroup
    private lateinit var rbAttendanceMode: RadioButton
    private lateinit var rbRecitationMode: RadioButton

    private var scheduleSlots: Map<String, TimeSlot>? = null
    private var selectedTimeSlotKey: String? = null
    private var selectedDisplayTime: String? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView
    private lateinit var btnSaveAttendance: Button

    private var assignmentId: String? = null
    private var className: String? = null
    private var subjectCode: String? = null
    private var currentStudentList = mutableListOf<Student>()
    private lateinit var attendanceAdapter: AttendanceAdapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var isPreviousDay: Boolean = false

    private var isDataModified: Boolean = false
    private var isExistingRecordLoaded: Boolean = false

    // Gamitin ang standard Android colors para maiwasan ang R.color error.
    private val colorUnsaved: Int by lazy { ContextCompat.getColor(this, android.R.color.holo_red_dark) }
    private val colorSaved: Int by lazy { ContextCompat.getColor(this, android.R.color.holo_green_dark) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_record_attendance)

        // --- View Binding ---
        tvAttendanceHeader = findViewById(R.id.tvAttendanceHeader)
        etAttendanceDate = findViewById(R.id.etAttendanceDate)
        etAttendanceTimeSlot = findViewById(R.id.etAttendanceTimeSlot)
        recyclerView = findViewById(R.id.recyclerViewAttendance)
        btnSaveAttendance = findViewById(R.id.btnSaveAttendance)
        tvNoRecords = findViewById(R.id.tvNoRecords)
        rgModeSelection = findViewById(R.id.rgModeSelection)
        rbAttendanceMode = findViewById(R.id.rbAttendanceMode)
        rbRecitationMode = findViewById(R.id.rbRecitationMode)

        // --- Setup ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")
        tvAttendanceHeader.text = "Record Attendance for $className"
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing class assignment ID.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Set default date to today
        val today = dateFormat.format(Date())
        etAttendanceDate.setText(today)
        updateUIForDate(today)

        etAttendanceDate.setOnClickListener { showOptionsForDateSelection() }
        etAttendanceTimeSlot.setOnClickListener { showTimeSlotSelection() }

        rgModeSelection.setOnCheckedChangeListener { _, checkedId ->
            if (::attendanceAdapter.isInitialized) {
                val mode = if (checkedId == R.id.rbAttendanceMode) { AttendanceAdapter.Mode.ATTENDANCE } else { AttendanceAdapter.Mode.RECITATION }
                attendanceAdapter.setMode(mode)
            }
        }
        rbAttendanceMode.isChecked = true

        // 1. Load Student List (Ito ang naglo-load ng scheduleSlots at students)
        loadStudentList(assignmentId!!)

        // 2. Save Button Logic
        btnSaveAttendance.setOnClickListener {
            if (!isPreviousDay) {
                saveAttendance()
            } else {
                Toast.makeText(this, "Attendance cannot be modified for a previous date.", Toast.LENGTH_SHORT).show()
            }
        }
        updateSaveButtonState()
    }

    override fun onPause() {
        super.onPause()

        if (::attendanceAdapter.isInitialized && isDataModified && !isPreviousDay && isExistingRecordLoaded) {
            Log.d("RecordAttendance", "Autosaving *updates* to existing record in onPause()...")
            saveAttendance()
        } else if (isDataModified && !isExistingRecordLoaded) {
            Log.d("RecordAttendance", "Unsaved *new* record detected. Skipping auto-save. Manual save required.")
        }
    }

    private fun markDataModified() {
        if (!isPreviousDay && !isDataModified) {
            isDataModified = true
            updateSaveButtonState()
            Log.d("RecordAttendance", "Data modified. Unsaved changes detected.")
        }
    }

    private fun updateSaveButtonState() {
        if (isDataModified && !isPreviousDay) {
            btnSaveAttendance.text = "SAVE CHANGES 💾"
            btnSaveAttendance.setBackgroundColor(colorUnsaved)
        } else {
            btnSaveAttendance.text = "Save Attendance"
            btnSaveAttendance.setBackgroundColor(colorSaved)
        }
    }


    // --- Date Picker ---
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val newDate = dateFormat.format(selectedDate.time)

                etAttendanceDate.setText(newDate)
                updateUIForDate(newDate)

                // 🟢 I-reset ang Time Slot para mapilitan ang user na pumili
                selectedTimeSlotKey = null
                selectedDisplayTime = null
                etAttendanceTimeSlot.setText("")

                isDataModified = false
                isExistingRecordLoaded = false
                updateSaveButtonState()

                // Tatawagin ang loadExistingAttendance, pero magre-return dahil null ang selectedTimeSlotKey
                loadExistingAttendance(assignmentId!!, newDate)

            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    // 🔴 INALIS ANG DIRECT ADAPTER SET EDITABLE DITO
    private fun updateUIForDate(dateString: String) {
        try {
            val selectedDate = dateFormat.parse(dateString)!!
            val today = dateFormat.parse(dateFormat.format(Date()))!!

            // Tiyakin na ang editability ay batay sa petsa
            isPreviousDay = selectedDate.before(today)

            // Pansamantalang i-hide ang button kung previous day.
            // Ang visibility ng button ay babaguhin ulit sa loadExistingAttendance para i-check ang Time Slot.
            btnSaveAttendance.visibility = if (isPreviousDay) View.GONE else View.VISIBLE

            if (::attendanceAdapter.isInitialized) {
            }

            updateSaveButtonState()

        } catch (e: Exception) {
            Log.e("AttendanceDate", "Date parsing error for $dateString: $e")
            isPreviousDay = false
        }
    }


    private fun loadStudentList(assignmentId: String) {
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val fetchedYearLevel = doc.getString("yearLevel")
                val fetchedSemester = doc.getString("semester")
                val slotsMap = doc.get("scheduleSlots") as? Map<String, Map<String, String>>

                this.scheduleSlots = slotsMap?.mapValues { (_, value) ->
                    TimeSlot(
                        day = value["day"] ?: "",
                        startTime = value["startTime"] ?: "",
                        endTime = value["endTime"] ?: "",
                        roomLocation = value["roomLocation"] ?: "",
                        sectionBlock = value["sectionBlock"] ?: ""
                    )
                }

                if (fetchedSubjectCode == null || fetchedYearLevel == null || fetchedSemester == null || scheduleSlots.isNullOrEmpty()) {
                    Toast.makeText(this, "Error: Class details missing.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                this.subjectCode = fetchedSubjectCode

                // 1. Kukunin ang listahan ng students base sa Year Level
                firestore.collection("students")
                    .whereEqualTo("yearLevel", fetchedYearLevel)
                    .whereEqualTo("status", "Admitted")
                    .get()
                    .addOnSuccessListener { studentsSnapshot ->
                        val allStudentIds = studentsSnapshot.documents.map { it.id }

                        if (allStudentIds.isEmpty()) {
                            Toast.makeText(this, "No admitted students found for this year level.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        checkStudentEnrollmentBatch(allStudentIds, fetchedSubjectCode, fetchedSemester, fetchedYearLevel)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to load student profiles by year level.", Toast.LENGTH_SHORT).show()
                        Log.e("AttendanceLoader", "Error loading students by year level: $e")
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load class assignment details.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun showTimeSlotSelection() {
        val slots = scheduleSlots ?: return

        val slotDisplayItems = slots.map { (key, slot) ->
            "${key}|${slot.day} ${slot.startTime} - ${slot.endTime}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pumili ng Time Slot")
            .setItems(slotDisplayItems.map { it.split("|")[1] }.toTypedArray()) { dialog, which ->
                val selectedItem = slotDisplayItems[which]
                val parts = selectedItem.split("|")

                selectedTimeSlotKey = parts[0]
                selectedDisplayTime = parts[1]
                etAttendanceTimeSlot.setText(selectedDisplayTime)

                isDataModified = false
                isExistingRecordLoaded = false
                updateSaveButtonState()

                loadExistingAttendance(assignmentId!!, etAttendanceDate.text.toString())
                dialog.dismiss()
            }
            .show()
    }

    private fun checkStudentEnrollmentBatch(
        allStudentIds: List<String>,
        subjectCode: String,
        semester: String,
        yearLevel: String
    ) {

        val finalEnrolledStudents = mutableListOf<Student>()
        tvNoRecords.text = "Checking enrollment status for ${allStudentIds.size} students... (Optimized Check)"
        tvNoRecords.visibility = View.VISIBLE

        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"
        Log.d("AttendanceLoader", "Using Enrollment Doc ID: $enrollmentDocId")

        lifecycleScope.launch {
            try {
                // ... (Student Enrollment Batch Logic - Tiyakin lang na tama ang listahan)
                val studentProfilesQuery = firestore.collection("students")
                    .whereIn(FieldPath.documentId(), allStudentIds)
                    .get().await()

                val studentMap = studentProfilesQuery.documents
                    .mapNotNull { doc -> doc.toObject(Student::class.java)?.copy(id = doc.id) }
                    .associateBy { it.id }

                val enrollmentChecks = allStudentIds.chunked(10).flatMap { idChunk ->
                    idChunk.map { studentId ->
                        async {
                            val subjectRef = firestore.collection("students").document(studentId)
                                .collection("subjects").document(enrollmentDocId)
                            val subjectSnapshot = subjectRef.get().await()
                            if (subjectSnapshot.exists()) studentId else null
                        }
                    }
                }

                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()

                enrolledStudentIds.forEach { id ->
                    studentMap[id]?.let { finalEnrolledStudents.add(it) }
                }

                // --- UI Update Logic ---
                currentStudentList.clear()
                currentStudentList.addAll(finalEnrolledStudents.sortedBy { it.lastName })

                if (currentStudentList.isEmpty()) {
                    tvNoRecords.text = "No students are officially enrolled in $subjectCode for $yearLevel $semester."
                    tvNoRecords.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    attendanceAdapter = AttendanceAdapter(
                        studentList = currentStudentList,
                        assignmentId = assignmentId!!,
                        // 🟢 Initial state ay editable, pero loadExistingAttendance ang magde-decide
                        isEditable = !isPreviousDay,
                        currentMode = AttendanceAdapter.Mode.ATTENDANCE,
                        onDataChanged = { markDataModified() }
                    )
                    recyclerView.adapter = attendanceAdapter

                    // Tatawagin ang loadExistingAttendance para i-check ang Time Slot at Petsa
                    loadExistingAttendance(assignmentId!!, etAttendanceDate.text.toString())
                }

            } catch (e: Exception) {
                Log.e("AttendanceLoader", "Error validating student enrollment: ${e.message}", e)
                tvNoRecords.text = "Error loading student list. Please check dependencies/data."
                tvNoRecords.visibility = View.VISIBLE
            }
        }
    }

    // --- 🟢 Load Existing Attendance (With Recitation) ---
    private fun loadExistingAttendance(assignmentId: String, date: String) {
        val timeSlotKey = selectedTimeSlotKey

        // 🔴 GUARDRAIL: Pwede lang mag-record kung may Time Slot
        if (timeSlotKey.isNullOrEmpty()) {
            Log.w("ATTENDANCE_DEBUG", "Time Slot not selected/available, preventing data load and editing.")

            tvNoRecords.text = "🚨 Pumili muna ng Time Slot para makapag-record ng attendance."
            tvNoRecords.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnSaveAttendance.visibility = View.GONE // ITATAGO

            if (::attendanceAdapter.isInitialized) {
                attendanceAdapter.updateStatuses(emptyMap(), emptyMap())
                attendanceAdapter.setEditable(false)
            }
            isExistingRecordLoaded = false
            isDataModified = false
            return
        }

        // 🟢 Kung may Time Slot, i-set ang editability batay sa petsa.
        updateUIForDate(date) // Dito nag-se-set ng isPreviousDay
        // I-restore ang visibility ng Save button (tatanggalin lang ulit kung isPreviousDay)
        btnSaveAttendance.visibility = if (isPreviousDay) View.GONE else View.VISIBLE


        // Dito magsisimula ang ID check!
        val recordId = "${assignmentId}_${date}_$timeSlotKey"
        Log.d("ATTENDANCE_DEBUG", "Attempting to load record ID: $recordId")


        firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
            .get()
            .addOnSuccessListener { documentSnapshot ->

                isExistingRecordLoaded = documentSnapshot.exists()
                Log.d("ATTENDANCE_DEBUG", "Document Exists in Firestore: ${documentSnapshot.exists()}")

                if (documentSnapshot.exists()) {
                    val existingAttendance =
                        documentSnapshot.get("statuses") as? Map<String, String> ?: emptyMap()

                    // FIX: Tiyakin na tama ang pagbasa ng recitationPoints (Long to Int conversion)
                    val existingRecitationLong =
                        documentSnapshot.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
                    val existingRecitation = existingRecitationLong.mapValues { it.value.toInt() }

                    Log.d("ATTENDANCE_DEBUG", "Fetched Attendance count: ${existingAttendance.size}")
                    Log.d("ATTENDANCE_DEBUG", "Fetched Recitation count: ${existingRecitation.size}")


                    if (::attendanceAdapter.isInitialized) {
                        attendanceAdapter.updateStatuses(existingAttendance, existingRecitation)
                        attendanceAdapter.setEditable(!isPreviousDay)
                    }

                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                } else {
                    // Kung wala, I-CLEAR ang adapter
                    if (::attendanceAdapter.isInitialized) {
                        attendanceAdapter.updateStatuses(emptyMap(), emptyMap())
                        attendanceAdapter.setEditable(!isPreviousDay)
                    }
                    if (isPreviousDay) {
                        tvNoRecords.text = "No attendance records found for this date and time slot."
                        tvNoRecords.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvNoRecords.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
                isDataModified = false
                updateSaveButtonState()
            }
            .addOnFailureListener { e ->
                Log.e("ATTENDANCE_DEBUG", "Failed to get existing attendance record.", e)
                tvNoRecords.text = "Error loading records. Please check connection."
                tvNoRecords.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    // --- 🟢 Save Attendance (With Recitation) ---
    private fun saveAttendance() {
        val dateToSave = etAttendanceDate.text.toString()
        val timeSlotKey = selectedTimeSlotKey
        val displayTimeSlot = selectedDisplayTime

        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty() || timeSlotKey.isNullOrEmpty() || displayTimeSlot.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Petsa, Subject Code, o Time Slot ay kulang. Hindi makakapag-save.", Toast.LENGTH_LONG).show()
            return
        }

        val (attendanceMap, recitationMap) = attendanceAdapter.getAttendanceAndRecitationMaps()

        val unmarkedStudents = currentStudentList.filter { student ->
            student.id?.let { studentId -> attendanceMap[studentId].isNullOrEmpty() } ?: true
        }

        if (unmarkedStudents.isNotEmpty()) {
            val count = unmarkedStudents.size
            Toast.makeText(this, "🚨 REQUIRED: May $count estudyante na walang attendance status. Pakitiyak na lahat ay naka-check.", Toast.LENGTH_LONG).show()
            return
        }

        val recordId = "${assignmentId}_${dateToSave}_$timeSlotKey"

        firestore.document("systemSettings/currentTerm")
            .get()
            .addOnSuccessListener { termDoc ->
                val academicTerm = termDoc.getString("academicTerm") ?: ""
                val academicYear = termDoc.getString("academicYear") ?: ""
                val semester = termDoc.getString("semester") ?: ""

                // 🔹 Step 2: Include them in the saved record
                val dailyRecord = hashMapOf(
                    "assignmentId" to assignmentId!!,
                    "subjectCode" to subjectCode!!,
                    "date" to dateToSave,
                    "timeSlotKey" to timeSlotKey,
                    "displayTimeSlot" to displayTimeSlot,
                    "statuses" to attendanceMap,
                    "recitationPoints" to recitationMap,
                    "academicTerm" to academicTerm,
                    "academicYear" to academicYear,
                    "semester" to semester
                )

                firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
                    .set(dailyRecord)
                    .addOnSuccessListener {
                        Log.i("AttendanceSaver", "Attendance saved successfully. Document ID: $recordId")

                        isDataModified = false
                        isExistingRecordLoaded = true
                        updateSaveButtonState()

                        Toast.makeText(this, "Attendance successfully updated! ✅", Toast.LENGTH_LONG).show()
                        updateUIForDate(dateToSave)
                    }
                    .addOnFailureListener { e ->
                        Log.e("AttendanceSaver", "Save FAILED: ${e.message}", e)
                        Toast.makeText(this, "Failed to save attendance: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceSaver", "Failed to fetch current term: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch current term info.", Toast.LENGTH_LONG).show()
            }
    }

    private fun showOptionsForDateSelection() {
        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa")
            .setItems(
                arrayOf(
                    "Pumili sa Kalendaryo...",
                    "Tingnan ang mga Nakaraang Attendance"
                )
            ) { _, which ->
                if (which == 0) {
                    showDatePickerDialog()
                } else {
                    fetchExistingAttendanceDates(assignmentId!!)
                }
            }
            .show()
    }

    // --- 🟢 Fetch Existing Attendance Dates (Aggregated) ---
    private fun fetchExistingAttendanceDates(assignmentId: String) {
        if (assignmentId.isNullOrEmpty()) return

        firestore.collection(ATTENDANCE_COLLECTION)
            .whereEqualTo("assignmentId", assignmentId)
            .orderBy("date", Query.Direction.DESCENDING)
            .orderBy("displayTimeSlot", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "Wala pang naitalang attendance records para sa klase na ito.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val recordedItems = snapshot.documents.mapNotNull { doc ->
                    val date = doc.getString("date")
                    val displayTime = doc.getString("displayTimeSlot")
                    val timeKey = doc.getString("timeSlotKey")

                    if (date != null && displayTime != null && timeKey != null) {
                        // Format: DATE|DISPLAY_TIME|TIME_KEY
                        "${date}|${displayTime}|${timeKey}"
                    } else {
                        null
                    }
                }

                showExistingDatesDialog(recordedItems.toSet().toList())
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceDate", "Error fetching existing records: $e")
                Toast.makeText(this, "Failed to load existing attendance records.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showExistingDatesDialog(recordedItems: List<String>) {

        val displayArray = recordedItems.map {
            it.split("|").let { parts ->
                "${parts[0]} (${parts[1]})" // DATE (DISPLAY_TIME)
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa at Oras ng Attendance")
            .setItems(displayArray) { dialog, which ->
                val selectedItem = recordedItems[which]
                val parts = selectedItem.split("|")

                val selectedDate = parts[0]
                val selectedTimeDisplay = parts[1]
                val selectedTimeKey = parts[2]

                etAttendanceDate.setText(selectedDate)
                updateUIForDate(selectedDate) // 🟢 Ise-set ang isPreviousDay

                selectedTimeSlotKey = selectedTimeKey
                selectedDisplayTime = selectedTimeDisplay
                etAttendanceTimeSlot.setText(selectedTimeDisplay) // 🟢 Ise-set ang Time Slot display

                isDataModified = false
                isExistingRecordLoaded = true
                updateSaveButtonState()

                loadExistingAttendance(assignmentId!!, selectedDate) // 🟢 Maglo-load ng data

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
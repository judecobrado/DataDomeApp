package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldPath // <--- Kailangan para sa whereIn query
import androidx.appcompat.app.AlertDialog
import com.example.datadomeapp.models.Student
import com.example.datadomeapp.models.TimeSlot
import com.google.firebase.firestore.Query
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.toObject
import java.text.SimpleDateFormat
import java.util.*

// 🟢 FIX 1: NEW Data Model para sa ISANG document lang kada araw
// Ilagay ito sa labas ng Activity class, ideal sa isang 'models' package.
// Hindi ito ang iyong lumang 'AttendanceRecord' na para sa Batch Write.
data class DailyAttendanceRecord(
    val assignmentId: String = "",
    val subjectCode: String = "",
    val date: String = "",
    val timeSlotKey: String = "", // 🛑 NEW: Slot key (e.g., "slot1", "slot2")
    val displayTimeSlot: String = "", // 🛑 NEW: Display format (e.g., "Mon 1:00 PM - 2:00 PM")
    // Ang lahat ng statuses ay nasa isang Map: studentId -> status
    val statuses: Map<String, String> = emptyMap()
)


class RecordAttendanceActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    // 🟢 Bagong Collection Name
    private val ATTENDANCE_COLLECTION = "dailyAttendanceRecords"

    private lateinit var tvAttendanceHeader: TextView
    private lateinit var etAttendanceDate: EditText
    private lateinit var etAttendanceTimeSlot: EditText
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

    private var isPreviousDay: Boolean = false // Track the edit status

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_record_attendance)

        // ... (View Binding at Intent Data retrieval, walang pagbabago dito) ...

        // --- Get Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")

        // --- View Binding ---
        tvAttendanceHeader = findViewById(R.id.tvAttendanceHeader)
        etAttendanceDate = findViewById(R.id.etAttendanceDate)
        etAttendanceTimeSlot = findViewById(R.id.etAttendanceTimeSlot)
        recyclerView = findViewById(R.id.recyclerViewAttendance)
        btnSaveAttendance = findViewById(R.id.btnSaveAttendance)
        tvNoRecords = findViewById(R.id.tvNoRecords)

        tvAttendanceHeader.text = "Record Attendance for $className"
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Set default date to today and check its status
        val today = dateFormat.format(Date())
        etAttendanceDate.setText(today)
        updateUIForDate(today)

        etAttendanceDate.setOnClickListener { showOptionsForDateSelection() }
        etAttendanceTimeSlot.setOnClickListener { showTimeSlotSelection() }

        if (assignmentId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing class assignment ID.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 1. Load Student List
        loadStudentList(assignmentId!!)

        // 2. Save Button Logic
        btnSaveAttendance.setOnClickListener {
            if (!isPreviousDay) {
                saveAttendance()
            } else {
                Toast.makeText(
                    this,
                    "Attendance cannot be modified for a previous date.",
                    Toast.LENGTH_SHORT
                ).show()
            }
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

                if (::attendanceAdapter.isInitialized) {
                    loadExistingAttendance(assignmentId!!, newDate)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    // 🌟 FUNCTION: Tiyakin kung editable ang attendance
    private fun updateUIForDate(dateString: String) {
        try {
            val selectedDate = dateFormat.parse(dateString)!!
            val today = dateFormat.parse(dateFormat.format(Date()))!!

            isPreviousDay = selectedDate.before(today)
            btnSaveAttendance.visibility = if (isPreviousDay) View.GONE else View.VISIBLE

            if (::attendanceAdapter.isInitialized) {
                // ✅ Tiyakin na ang adapter ay na-update ang editable state
                attendanceAdapter.setEditable(!isPreviousDay)
            }

        } catch (e: Exception) {
            Log.e("AttendanceDate", "Date parsing error for $dateString: $e")
            isPreviousDay = false
        }
    }

    // ... (loadStudentList at fetchStudentProfiles, walang major change maliban sa pagtawag ng loadExistingAttendance) ...

    // SA CLASS DETAILSACTIVITY.KT
// Ang bagong Student Model ay kailangan, kaya kailangan nating i-assume na ito ay may 'sectionId' field
// gaya ng nakita sa enrollment screenshot.

// ⚠️ Palitan ang Luma/Current loadStudentList function ng bago:

    private fun loadStudentList(assignmentId: String) {
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val fetchedYearLevel = doc.getString("yearLevel")
                // 🟢 CRITICAL: Kunin ang Semester
                val fetchedSemester = doc.getString("semester")

                // 🛑 Kumuha ng schedule slots
                val slotsMap = doc.get("scheduleSlots") as? Map<String, Map<String, String>>

                // Convert Map<String, Map<String, String>> to Map<String, TimeSlot>
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
                    Toast.makeText(
                        this,
                        "Error: Class details (Subject/Year/Semester) or schedule slots missing.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                // I-set ang unang slot bilang default at i-update ang UI
                val defaultSlotEntry = scheduleSlots!!.entries.first()
                val slot = defaultSlotEntry.value
                selectedTimeSlotKey = defaultSlotEntry.key
                selectedDisplayTime = "${slot.day} ${slot.startTime} - ${slot.endTime}"
                etAttendanceTimeSlot.setText(selectedDisplayTime)

                this.subjectCode = fetchedSubjectCode

                // 1. Kukunin ang listahan ng students base sa Year Level
                firestore.collection("students")
                    .whereEqualTo("yearLevel", fetchedYearLevel)
                    .whereEqualTo("status", "Admitted") // I-filter lang ang Admitted
                    .get()
                    .addOnSuccessListener { studentsSnapshot ->
                        val allStudentIds = studentsSnapshot.documents.map { it.id }

                        if (allStudentIds.isEmpty()) {
                            Toast.makeText(
                                this,
                                "No admitted students found for this year level.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        // 2. I-check ang enrollment
                        // 🟢 IPASA ang Semester at Year Level
                        checkStudentEnrollmentBatch(allStudentIds, fetchedSubjectCode, fetchedSemester, fetchedYearLevel)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to load student profiles by year level.",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("AttendanceLoader", "Error loading students by year level: $e")
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load class assignment details.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    // ⚠️ IDAGDAG ITO SA LOOB NG CLASS

    private fun showTimeSlotSelection() {
        val slots = scheduleSlots ?: return

        val slotDisplayItems = slots.map { (key, slot) ->
            // Format: "slot1|Mon 1:00 PM - 2:00 PM"
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

                // Pagkatapos pumili, i-load ang attendance para sa petsa at oras na iyon
                loadExistingAttendance(assignmentId!!, etAttendanceDate.text.toString())

                dialog.dismiss()
            }
            .show()
    }

    private fun checkStudentEnrollmentBatch(
        allStudentIds: List<String>,
        subjectCode: String,
        semester: String,    // 🟢 NEW PARAMETER
        yearLevel: String    // 🟢 NEW PARAMETER
    ) {

        val finalEnrolledStudents = mutableListOf<Student>()

        tvNoRecords.text = "Checking enrollment status for ${allStudentIds.size} students... (Optimized Check)"
        tvNoRecords.visibility = View.VISIBLE

        // 🚨 CRITICAL: I-clean ang strings at I-construct ang Enrollment Doc ID
        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"
        Log.d("AttendanceLoader", "Using Enrollment Doc ID: $enrollmentDocId")

        // Simulan ang Coroutine
        lifecycleScope.launch {
            try {
                // 1. I-pre-fetch ang lahat ng Student Profiles (Mas mabilis na 1 read)
                val studentProfilesQuery = firestore.collection("students")
                    .whereIn(FieldPath.documentId(), allStudentIds)
                    .get().await()

                // Map: Student ID -> Student Object
                val studentMap = studentProfilesQuery.documents
                    .mapNotNull { doc -> doc.toObject(Student::class.java)?.copy(id = doc.id) }
                    .associateBy { it.id }

                // 2. Gawin ang LAHAT ng Enrollment Checks nang magkasabay (in parallel)
                val enrollmentChecks = allStudentIds.map { studentId ->
                    async {
                        val subjectRef = firestore.collection("students").document(studentId)
                            .collection("subjects").document(enrollmentDocId)

                        val subjectSnapshot = subjectRef.get().await()

                        // Kung may enrollment record, ibalik ang Student ID
                        if (subjectSnapshot.exists()) studentId else null
                    }
                }

                // 3. Hintayin ang resulta ng lahat ng parallel checks
                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()

                // 4. Buuin ang final list gamit ang pre-fetched map
                enrolledStudentIds.forEach { id ->
                    studentMap[id]?.let { finalEnrolledStudents.add(it) }
                }

                // --- UI Update Logic ---
                currentStudentList.clear()
                currentStudentList.addAll(finalEnrolledStudents)

                if (currentStudentList.isEmpty()) {
                    tvNoRecords.text =
                        "No students are officially enrolled in $subjectCode for $yearLevel $semester."
                    tvNoRecords.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    // CRITICAL: Initialize and set the adapter here!
                    attendanceAdapter = AttendanceAdapter(
                        studentList = currentStudentList,
                        assignmentId = assignmentId!!,
                        isEditable = !isPreviousDay
                    )
                    recyclerView.adapter = attendanceAdapter

                    // I-load ang existing attendance pagkatapos ng adapter setup
                    loadExistingAttendance(assignmentId!!, etAttendanceDate.text.toString())
                }

            } catch (e: Exception) {
                Log.e("AttendanceLoader", "Error validating student enrollment: ${e.message}", e)
                tvNoRecords.text = "Error loading student list. Please check dependencies/data."
                tvNoRecords.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchStudentProfiles(studentIds: List<String>, assignmentId: String) {
        firestore.collection("students")
            .whereIn("id", studentIds)
            .orderBy("lastName")
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                currentStudentList.clear()
                val students =
                    studentsSnapshot.documents.mapNotNull { it.toObject(Student::class.java) }
                currentStudentList.addAll(students)

                attendanceAdapter = AttendanceAdapter(
                    studentList = currentStudentList,
                    assignmentId = assignmentId,
                    isEditable = !isPreviousDay
                )
                recyclerView.adapter = attendanceAdapter

                loadExistingAttendance(assignmentId, etAttendanceDate.text.toString())
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceLoader", "Error fetching student profiles.", e)
            }
    }


    // --- 🟢 FIX 2: Load Existing Attendance (Aggregated) ---
    // ⚠️ PALITAN ang buong loadExistingAttendance function:

    private fun loadExistingAttendance(assignmentId: String, date: String) {
        Log.d("AttendanceLoader", "Checking for existing attendance on $date (Aggregated Method).")

        val timeSlotKey = selectedTimeSlotKey

        if (timeSlotKey.isNullOrEmpty()) {
            Log.w("AttendanceLoader", "Time Slot not selected, skipping load.")
            // I-skip ang load, pero huwag i-display ang "No Records" para hindi nakakalito
            return
        }

        tvNoRecords.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        // 🟢 NEW Document ID format: assignmentId_date_timeSlotKey
        val recordId = "${assignmentId}_${date}_$timeSlotKey"

        firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                // ... (The rest of the logic remains the same) ...
                if (documentSnapshot.exists()) {
                    Log.i("AttendanceLoader", "Found existing aggregated record.")
                    val existingAttendance =
                        documentSnapshot.get("statuses") as? Map<String, String> ?: emptyMap()

                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    attendanceAdapter.updateStatuses(existingAttendance)

                } else {
                    // ... (Logic for no record found) ...
                    if (isPreviousDay) {
                        tvNoRecords.text =
                            "No attendance records found for this date and time slot." // 🛑 Updated message
                        tvNoRecords.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        attendanceAdapter.updateStatuses(emptyMap())
                    } else {
                        tvNoRecords.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        attendanceAdapter.updateStatuses(emptyMap())
                    }
                }
            }
            .addOnFailureListener {
                Log.e("AttendanceLoader", "Failed to get existing attendance record.", it)
                tvNoRecords.text = "Error loading records. Please check connection."
                tvNoRecords.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    // --- 🟢 FIX 3: Save Attendance (Aggregated) ---
    // ⚠️ PALITAN ang buong saveAttendance function:

    private fun saveAttendance() {
        val dateToSave = etAttendanceDate.text.toString()
        val timeSlotKey = selectedTimeSlotKey
        val displayTimeSlot = selectedDisplayTime

        // --- 1. Basic Field Validation ---
        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty() || timeSlotKey.isNullOrEmpty() || displayTimeSlot.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Petsa, Subject Code, o Time Slot ay kulang. Hindi makakapag-save.", Toast.LENGTH_LONG).show()
            return
        }

        // 1. Kukunin ang attendance map mula sa adapter
        val attendanceMap = attendanceAdapter.getAttendanceMap()

        // --- 2. CRITICAL: Attendance Status Validation (Check if all students are marked) ---
        val unmarkedStudents = currentStudentList.filter { student ->
            student.id?.let { studentId ->
                // Ang status ay 'Absent', 'Present', 'Late', 'Excused'.
                // Kung ang studentId ay wala sa attendanceMap, ibig sabihin walang check.
                attendanceMap[studentId].isNullOrEmpty()
            } ?: true // True kung null ang student.id (shouldn't happen, but safe check)
        }

        if (unmarkedStudents.isNotEmpty()) {
            val count = unmarkedStudents.size
            // Mag-display ng babala at itigil ang save operation
            Toast.makeText(this, "🚨 REQUIRED: May $count estudyante na walang status. Pakitiyak na lahat ay naka-check.", Toast.LENGTH_LONG).show()
            return
        }
        // --- END Validation ---

        // 2. Gumawa ng composite document ID
        val recordId = "${assignmentId}_${dateToSave}_$timeSlotKey"

        // 3. Gumawa ng isang DailyAttendanceRecord object
        val dailyRecord = DailyAttendanceRecord(
            assignmentId = assignmentId!!,
            subjectCode = subjectCode!!,
            date = dateToSave,
            timeSlotKey = timeSlotKey,
            displayTimeSlot = displayTimeSlot,
            statuses = attendanceMap
        )

        // 4. I-save bilang ISANG document
        firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
            .set(dailyRecord)
            .addOnSuccessListener {
                Log.i("AttendanceSaver", "Attendance saved as single aggregated record for $dateToSave/$timeSlotKey. Document ID: $recordId")

                // ✅ SUCCESS LOGIC: Manatili sa screen at i-update ang UI
                Toast.makeText(this, "Attendance successfully updated! ✅", Toast.LENGTH_LONG).show()

                // I-disable ang editing kung ang sinave ay previous day (kahit na dapat GONE na ang button)
                updateUIForDate(dateToSave)

                // ❌ TINANGGAL: finish()
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceSaver", "Save FAILED: ${e.message}", e)
                Toast.makeText(this, "Failed to save attendance: ${e.message}", Toast.LENGTH_LONG).show()
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

    // --- 🟢 FIX 4: Fetch Existing Attendance Dates (Aggregated) ---
    // SA RECORDATTENDANCEACTIVITY.KT

    private fun fetchExistingAttendanceDates(assignmentId: String) {
        if (assignmentId.isNullOrEmpty()) return

        firestore.collection(ATTENDANCE_COLLECTION)
            .whereEqualTo("assignmentId", assignmentId)
            .orderBy("date", Query.Direction.DESCENDING)
            .orderBy("displayTimeSlot", Query.Direction.DESCENDING) // I-order din sa oras
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(
                        this,
                        "Wala pang naitalang attendance records para sa klase na ito.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                // Kuhanin ang listahan ng (Document ID - para makuha ang key) at (Display Value)
                val recordedItems = snapshot.documents.mapNotNull { doc ->
                    val date = doc.getString("date")
                    val displayTime = doc.getString("displayTimeSlot")
                    val timeKey = doc.getString("timeSlotKey") // Kukunin ang key

                    if (date != null && displayTime != null && timeKey != null) {
                        // Mag-store ng string na may delimiter para madaling i-parse
                        "${date}|${displayTime}|${timeKey}"
                    } else {
                        null
                    }
                }

                // I-display ang listahan ng unique date-time combinations
                showExistingDatesDialog(recordedItems.toSet().toList())
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceDate", "Error fetching existing records: $e")
                Toast.makeText(
                    this,
                    "Failed to load existing attendance records.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showExistingDatesDialog(recordedItems: List<String>) {

        // I-format ang items para sa dialog (Petsa at Oras lang)
        val displayArray = recordedItems.map {
            it.split("|").let { parts ->
                "${parts[0]} (${parts[1]})" // Format: 2024-10-25 (Mon 1:00 PM - 2:00 PM)
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa at Oras ng Attendance")
            .setItems(displayArray) { dialog, which ->
                val selectedItem = recordedItems[which]
                val parts = selectedItem.split("|")

                val selectedDate = parts[0]
                val selectedTimeDisplay = parts[1]
                val selectedTimeKey = parts[2] // Kukunin ang time slot key

                // 1. I-set ang Petsa
                etAttendanceDate.setText(selectedDate)
                updateUIForDate(selectedDate) // I-update ang editable state

                // 2. I-set ang Oras at Key
                selectedTimeSlotKey = selectedTimeKey
                selectedDisplayTime = selectedTimeDisplay
                etAttendanceTimeSlot.setText(selectedTimeDisplay)

                // 3. I-load ang attendance
                loadExistingAttendance(assignmentId!!, selectedDate)

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
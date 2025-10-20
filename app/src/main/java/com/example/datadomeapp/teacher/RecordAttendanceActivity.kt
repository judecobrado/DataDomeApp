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
import androidx.appcompat.app.AlertDialog
import com.example.datadomeapp.models.Student
import java.text.SimpleDateFormat
import java.util.*

class RecordAttendanceActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tvAttendanceHeader: TextView
    private lateinit var etAttendanceDate: EditText
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

        Log.d("AttendanceActivity", "Activity created.")

        // --- Get Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")
        Log.d("AttendanceActivity", "Intent received: Assignment ID=$assignmentId, Class Name=$className")

        // --- View Binding ---
        tvAttendanceHeader = findViewById(R.id.tvAttendanceHeader)
        etAttendanceDate = findViewById(R.id.etAttendanceDate)
        recyclerView = findViewById(R.id.recyclerViewAttendance)
        btnSaveAttendance = findViewById(R.id.btnSaveAttendance)
        tvNoRecords = findViewById(R.id.tvNoRecords)

        tvAttendanceHeader.text = "Record Attendance for $className"
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Set default date to today and check its status
        val today = dateFormat.format(Date())
        etAttendanceDate.setText(today)
        Log.d("AttendanceActivity", "Setting default date to $today")
        updateUIForDate(today)

        etAttendanceDate.setOnClickListener { showOptionsForDateSelection() }

        if (assignmentId.isNullOrEmpty()) {
            Log.e("AttendanceActivity", "FATAL: Assignment ID is null or empty.")
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
                Toast.makeText(this, "Attendance cannot be modified for a previous date.", Toast.LENGTH_SHORT).show()
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

                // 🌟 FIX 1: Tiyakin na ang UI/Editable state ay na-a-update muna
                updateUIForDate(newDate)

                // 🌟 FIX 2: Tiyakin na na-initialize na ang adapter BAGO mag-load
                if (::attendanceAdapter.isInitialized) {
                    // Ito na ang magre-refresh ng status batay sa bagong petsa at editable state
                    loadExistingAttendance(assignmentId!!, newDate)
                } else {
                    // Kung hindi pa initialized, tatawagin ito sa loob ng fetchStudentProfiles.
                    // Para sa kasiguraduhan, i-log natin ito.
                    Log.w("AttendanceDate", "Adapter not yet initialized. Data will load after students are fetched.")
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

            // 🛑 FIX: I-parse ulit ang "today" para alisin ang time component.
            // (Kung ginawa mo na ito, okay lang, pero tiyakin na walang isyu sa time)
            val today = dateFormat.parse(dateFormat.format(Date()))!!

            // Check if selected date is strictly before today
            isPreviousDay = selectedDate.before(today)

            Log.d("AttendanceDate", "Selected date: $dateString. Is previous day: $isPreviousDay. Is editable: ${!isPreviousDay}")

            // I-adjust ang UI
            btnSaveAttendance.visibility = if (isPreviousDay) View.GONE else View.VISIBLE

            // 🌟 CRITICAL FIX: I-update ang adapter **kung initialized**
            if (::attendanceAdapter.isInitialized) {
                Log.d("AdapterState", "Setting adapter editable state to: ${!isPreviousDay}")
                attendanceAdapter.setEditable(!isPreviousDay)
            } else {
                Log.d("AdapterState", "Adapter is not initialized yet. Skipping setEditable.")
            }

        } catch (e: Exception) {
            Log.e("AttendanceDate", "Date parsing error for $dateString: $e")
            isPreviousDay = false
        }
    }

    // --- Load Students and Get SubjectCode ---
    private fun loadStudentList(assignmentId: String) {
        Log.d("AttendanceLoader", "Starting load for assignment ID: $assignmentId")

        // Step A: Get Class Assignment Details
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val sectionName = doc.getString("sectionName")

                if (fetchedSubjectCode == null || sectionName == null) {
                    Log.e("AttendanceLoader", "Error: Class details incomplete for $assignmentId.")
                    Toast.makeText(this, "Error: Class details incomplete.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                this.subjectCode = fetchedSubjectCode
                Log.d("AttendanceLoader", "Fetched SubjectCode: $fetchedSubjectCode, Section: $sectionName")

                // Step B: Get Student IDs via Collection Group Query
                firestore.collectionGroup("subjects")
                    .whereEqualTo("subjectCode", fetchedSubjectCode)
                    .whereEqualTo("sectionName", sectionName)
                    .get()
                    .addOnSuccessListener { subjectsSnapshot ->
                        val studentIds = subjectsSnapshot.documents.map { it.reference.parent.parent!!.id }

                        if (studentIds.isEmpty()) {
                            Log.w("AttendanceLoader", "No students found in this class.")
                            Toast.makeText(this, "No students found in this class.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // Step C: Fetch Student Profiles
                        fetchStudentProfiles(studentIds.take(10), assignmentId)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceLoader", "Error loading assignment details: $e")
                Toast.makeText(this, "Failed to load class list.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchStudentProfiles(studentIds: List<String>, assignmentId: String) {
        Log.d("AttendanceLoader", "Fetching profiles for ${studentIds.size} students.")

        firestore.collection("students")
            .whereIn("id", studentIds)
            .orderBy("lastName")
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                currentStudentList.clear()
                val students = studentsSnapshot.documents.mapNotNull { it.toObject(Student::class.java) }
                currentStudentList.addAll(students)

                Log.i("AttendanceLoader", "Successfully fetched ${currentStudentList.size} student profiles.")

                // Initialize Adapter
                attendanceAdapter = AttendanceAdapter(
                    studentList = currentStudentList,
                    assignmentId = assignmentId,
                    isEditable = true // 🛑 CRITICAL FIX: Ipinasa ang editable status
                )
                recyclerView.adapter = attendanceAdapter

                // I-load ang existing attendance record
                loadExistingAttendance(assignmentId, etAttendanceDate.text.toString())
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceLoader", "Error fetching student profiles.", e)
            }
    }

    // --- Load Existing Attendance ---
    private fun loadExistingAttendance(assignmentId: String, date: String) {
        Log.d("AttendanceLoader", "Checking for existing attendance on $date.")

        // 1. I-reset ang visibility state
        tvNoRecords.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE // Default: Ipakita ang listahan

        firestore.collection("attendance")
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.i("AttendanceLoader", "Found ${snapshot.size()} existing records.")

                val existingAttendance = snapshot.documents.associate {
                    it.getString("studentId")!! to it.getString("status")!!
                }

                // 2. CHECK LOGIC: Kung kailan itatago ang RecyclerView
                if (snapshot.isEmpty) {
                    // Kung walang nakitang records:
                    if (isPreviousDay) {
                        // Kaso A: Nakaraan ang petsa AT walang records (Display message only)
                        tvNoRecords.text = "No attendance records found for this date."
                        tvNoRecords.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE // 🛑 ITAGO ANG STUDENT LIST

                        // Kailangan pa ring i-reset ang adapter statuses para walang lumang data
                        attendanceAdapter.updateStatuses(emptyMap())
                        return@addOnSuccessListener // Tapusin na ang function dito
                    }
                    // Kaso B: Today AT walang records (Normal, ipapakita ang listahan na may default 'Absent')
                }

                // 3. Kung may records o Today ang petsa:
                tvNoRecords.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE // Ipakita ang student list

                attendanceAdapter.updateStatuses(existingAttendance)
            }
            .addOnFailureListener {
                Log.e("AttendanceLoader", "Failed to query existing attendance.", it)
                // Error handling: Itago ang listahan at magpakita ng error
                tvNoRecords.text = "Error loading records. Please check connection."
                tvNoRecords.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE // 🛑 ITAGO ANG STUDENT LIST
            }
    }

    // --- Save Attendance ---
    private fun saveAttendance() {
        val dateToSave = etAttendanceDate.text.toString()

        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty()) {
            Log.e("AttendanceSaver", "Validation failed. Date: $dateToSave, SubjectCode: $subjectCode")
            Toast.makeText(this, "Error: Date or Subject Code is missing. Cannot save.", Toast.LENGTH_LONG).show()
            return
        }

        val attendanceMap = attendanceAdapter.getAttendanceMap()
        Log.d("AttendanceSaver", "Starting save process for date: $dateToSave. Saving ${attendanceMap.size} records.")

        val batch = firestore.batch()

        for ((studentId, status) in attendanceMap) {
            val recordId = "${assignmentId}_${dateToSave}_${studentId}"
            val recordRef = firestore.collection("attendance").document(recordId)

            val record = AttendanceRecord(
                studentId = studentId,
                assignmentId = assignmentId!!,
                subjectCode = subjectCode!!,
                date = dateToSave,
                status = status
            )

            batch.set(recordRef, record)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.i("AttendanceSaver", "Batch commit SUCCESSFUL for $dateToSave.")
                Toast.makeText(this, "Attendance saved successfully for $dateToSave.", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceSaver", "Batch commit FAILED: ${e.message}", e)
                Toast.makeText(this, "Failed to save attendance: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showOptionsForDateSelection() {
        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa")
            // ✅ FIX: Alisin ang explicit type declarations (dialog: DialogInterface, which: Int)
            .setItems(arrayOf("Pumili sa Kalendaryo...", "Tingnan ang mga Nakaraang Attendance")) { dialog, which ->
                if (which == 0) {
                    showDatePickerDialog()
                } else {
                    fetchExistingAttendanceDates(assignmentId!!)
                }
            }
            .show()
    }

    private fun fetchExistingAttendanceDates(assignmentId: String) {
        if (assignmentId.isNullOrEmpty()) return

        // I-query ang lahat ng records para sa Assignment ID at i-order para sa mas magandang user view
        firestore.collection("attendance")
            .whereEqualTo("assignmentId", assignmentId)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "Wala pang naitalang attendance records para sa klase na ito.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Kuhanin ang lahat ng 'date' at alisin ang duplicates (DISTINCT)
                val uniqueDates = snapshot.documents
                    .mapNotNull { it.getString("date") }
                    .distinct()
                    .toList()

                showExistingDatesDialog(uniqueDates)
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceDate", "Error fetching unique dates: $e")
                Toast.makeText(this, "Failed to load existing attendance dates.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showExistingDatesDialog(dates: List<String>) {
        val datesArray = dates.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa ng Attendance")
            // ✅ FIX 1: Alisin ang explicit type declarations (dialog: DialogInterface, which: Int)
            .setItems(datesArray) { dialog, which ->
                val selectedDate = datesArray[which]

                // CRITICAL: Ito ang nawawalang logic para gumana ang selection
                etAttendanceDate.setText(selectedDate)
                updateUIForDate(selectedDate)
                loadExistingAttendance(assignmentId!!, selectedDate)

                dialog.dismiss()
            }
            // ✅ FIX 2: Alisin ang explicit type declarations (dialog: DialogInterface, _)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
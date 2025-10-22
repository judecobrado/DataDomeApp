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
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// 🟢 FIX 1: NEW Data Model para sa ISANG document lang kada araw
// Ilagay ito sa labas ng Activity class, ideal sa isang 'models' package.
// Hindi ito ang iyong lumang 'AttendanceRecord' na para sa Batch Write.
data class DailyAttendanceRecord(
    val assignmentId: String = "",
    val subjectCode: String = "",
    val date: String = "",
    // Ang lahat ng statuses ay nasa isang Map: studentId -> status
    val statuses: Map<String, String> = emptyMap()
)


class RecordAttendanceActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    // 🟢 Bagong Collection Name
    private val ATTENDANCE_COLLECTION = "dailyAttendanceRecords"

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

        // ... (View Binding at Intent Data retrieval, walang pagbabago dito) ...

        // --- Get Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")

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
        updateUIForDate(today)

        etAttendanceDate.setOnClickListener { showOptionsForDateSelection() }

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
                attendanceAdapter.setEditable(!isPreviousDay)
            }

        } catch (e: Exception) {
            Log.e("AttendanceDate", "Date parsing error for $dateString: $e")
            isPreviousDay = false
        }
    }

    // ... (loadStudentList at fetchStudentProfiles, walang major change maliban sa pagtawag ng loadExistingAttendance) ...

    private fun loadStudentList(assignmentId: String) {
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val sectionName = doc.getString("sectionName")

                if (fetchedSubjectCode == null || sectionName == null) {
                    Toast.makeText(this, "Error: Class details incomplete.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                this.subjectCode = fetchedSubjectCode

                firestore.collectionGroup("subjects")
                    .whereEqualTo("subjectCode", fetchedSubjectCode)
                    .whereEqualTo("sectionName", sectionName)
                    .get()
                    .addOnSuccessListener { subjectsSnapshot ->
                        val studentIds = subjectsSnapshot.documents.mapNotNull {
                            it.reference.parent.parent?.id
                        }

                        if (studentIds.isEmpty()) {
                            Toast.makeText(this, "No students found in this class.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // Limitan ang pagkuha ng student IDs para maiwasan ang malaking array sa 'whereIn'
                        fetchStudentProfiles(studentIds.take(30), assignmentId)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load class list.", Toast.LENGTH_SHORT).show()
            }
    }

    // ... (fetchStudentProfiles, walang pagbabago) ...

    private fun fetchStudentProfiles(studentIds: List<String>, assignmentId: String) {
        firestore.collection("students")
            .whereIn("id", studentIds)
            .orderBy("lastName")
            .get()
            .addOnSuccessListener { studentsSnapshot ->
                currentStudentList.clear()
                val students = studentsSnapshot.documents.mapNotNull { it.toObject(Student::class.java) }
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
    private fun loadExistingAttendance(assignmentId: String, date: String) {
        Log.d("AttendanceLoader", "Checking for existing attendance on $date (Aggregated Method).")

        tvNoRecords.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        // 🟢 Gumamit ng Direct Document ID
        val recordId = "${assignmentId}_$date"

        firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                // Check kung may document
                if (documentSnapshot.exists()) {
                    Log.i("AttendanceLoader", "Found existing aggregated record.")

                    // Kukunin ang statuses Map mula sa document
                    val existingAttendance = documentSnapshot.get("statuses") as? Map<String, String> ?: emptyMap()

                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    attendanceAdapter.updateStatuses(existingAttendance)

                } else {
                    Log.w("AttendanceLoader", "No aggregated record found for $recordId.")

                    if (isPreviousDay) {
                        tvNoRecords.text = "No attendance records found for this date."
                        tvNoRecords.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE

                        // Kailangan pa ring i-reset ang adapter statuses
                        attendanceAdapter.updateStatuses(emptyMap())
                    } else {
                        // Kung Today at walang record, ipapakita ang listahan na may default 'Absent'
                        tvNoRecords.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        attendanceAdapter.updateStatuses(emptyMap()) // I-reset sa default status
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
    private fun saveAttendance() {
        val dateToSave = etAttendanceDate.text.toString()

        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Date or Subject Code is missing. Cannot save.", Toast.LENGTH_LONG).show()
            return
        }

        // 1. Kukunin ang attendance map mula sa adapter
        val attendanceMap = attendanceAdapter.getAttendanceMap()

        // 2. Gumawa ng composite document ID (Assignment ID + Date)
        val recordId = "${assignmentId}_$dateToSave"

        // 3. Gumawa ng isang DailyAttendanceRecord object
        val dailyRecord = DailyAttendanceRecord(
            assignmentId = assignmentId!!,
            subjectCode = subjectCode!!,
            date = dateToSave,
            statuses = attendanceMap // I-assign ang buong Map!
        )

        // 4. I-save bilang ISANG document
        firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
            .set(dailyRecord)
            .addOnSuccessListener {
                Log.i("AttendanceSaver", "Attendance saved as single aggregated record for $dateToSave. Document ID: $recordId")
                Toast.makeText(this, "Attendance saved successfully for $dateToSave! 💾", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceSaver", "Save FAILED: ${e.message}", e)
                Toast.makeText(this, "Failed to save attendance: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ... (showOptionsForDateSelection, walang pagbabago) ...

    private fun showOptionsForDateSelection() {
        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa")
            .setItems(arrayOf("Pumili sa Kalendaryo...", "Tingnan ang mga Nakaraang Attendance")) { _, which ->
                if (which == 0) {
                    showDatePickerDialog()
                } else {
                    fetchExistingAttendanceDates(assignmentId!!)
                }
            }
            .show()
    }

    // --- 🟢 FIX 4: Fetch Existing Attendance Dates (Aggregated) ---
    private fun fetchExistingAttendanceDates(assignmentId: String) {
        if (assignmentId.isNullOrEmpty()) return

        // 🟢 I-query ang bagong Collection. Kailangan ng Query dahil walang 'DISTINCT' sa Firestore.
        // Tanging 'assignmentId' lang ang kailangan i-filter.
        firestore.collection(ATTENDANCE_COLLECTION)
            .whereEqualTo("assignmentId", assignmentId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "Wala pang naitalang attendance records para sa klase na ito.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // 🟢 Kuhanin ang lahat ng 'date' mula sa mga aggregated document
                val uniqueDates = snapshot.documents
                    .mapNotNull { it.getString("date") }
                    .toList() // Hindi na kailangan ng .distinct() dahil ang result ay 1 document per date na.

                showExistingDatesDialog(uniqueDates)
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceDate", "Error fetching unique dates: $e")
                Toast.makeText(this, "Failed to load existing attendance dates.", Toast.LENGTH_SHORT).show()
            }
    }

    // ... (showExistingDatesDialog, walang pagbabago sa logic, pero ngayon tumatawag na ng aggregated load) ...

    private fun showExistingDatesDialog(dates: List<String>) {
        val datesArray = dates.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pumili ng Petsa ng Attendance")
            .setItems(datesArray) { dialog, which ->
                val selectedDate = datesArray[which]

                etAttendanceDate.setText(selectedDate)
                updateUIForDate(selectedDate)
                // 🟢 Ito ay tumatawag na sa bagong aggregated load function
                loadExistingAttendance(assignmentId!!, selectedDate)

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
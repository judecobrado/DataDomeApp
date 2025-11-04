package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.BaseActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.teacher.adapters.QuizMonitoringAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.widget.RadioButton

// Ang QuizMonitoringActivity ay gumagamit na ngayon ng QuizMonitoringViewModelFactory na tumatanggap ng quizEndTime
class QuizMonitoringActivity : BaseActivity() {

    private val quizId by lazy { intent.getStringExtra("QUIZ_ID") ?: "" }
    private val assignmentId by lazy { intent.getStringExtra("ASSIGNMENT_ID") ?: "" }
    private val quizTitle by lazy { intent.getStringExtra("QUIZ_TITLE") ?: "Quiz Monitoring" }
    private val quizEndTime by lazy { intent.getLongExtra("SCHEDULED_END_DATE_TIME", 0L) }

    private val viewModel: QuizMonitoringViewModel by viewModels {
        QuizMonitoringViewModelFactory(
            quizId,
            assignmentId,
            quizEndTime
        )
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: QuizMonitoringAdapter
    private lateinit var tvQuizTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var fabManageAccess: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_monitoring)


        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = quizTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvQuizTitle = findViewById(R.id.tvMonitoringQuizTitle)
        tvStatus = findViewById(R.id.tvMonitoringStatus)
        recyclerView = findViewById(R.id.rvStudentMonitoring)
        fabManageAccess = findViewById(R.id.fabManageAccess)

        tvQuizTitle.text = quizTitle
        tvStatus.text = "Loading Live Status..."

        setupRecyclerView()
        observeViewModel()

        fabManageAccess.setOnClickListener {
            viewModel.monitoringData.value?.let { currentList ->
                showManageAccessDialog(currentList)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        adapter = QuizMonitoringAdapter(
            dataList = emptyList(),
            onIntegrityClick = { studentData -> onIntegrityClicked(studentData) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.monitoringData.observe(this, Observer { dataList ->
            adapter.updateList(dataList)
            updateHeaderStatus(dataList)
        })
    }

    private fun updateHeaderStatus(dataList: List<StudentMonitoringData>) {
        val totalStudents = dataList.size

        val completedCount = dataList.count { it.status == "COMPLETED" || it.status == "TIME_EXPIRED" || it.status == "UNATTEMPTED_TIME_EXPIRED" || it.status == "ACCESS_REVOKED" }
        val inProgressCount = dataList.count { it.status == "IN_PROGRESS" || it.status == "CHEATING" }

        val statusText = when {
            totalStudents == 0 -> "Status: No Enrolled Students"
            inProgressCount > 0 -> "Status: $inProgressCount/$totalStudents Students In Progress"
            completedCount == totalStudents -> "Status: All Students Have Finished ✅"
            completedCount > 0 -> "Status: $completedCount/$totalStudents Students Finished"
            else -> "Status: Quiz Assigned, Not Yet Started"
        }

        tvStatus.text = statusText
    }

    fun onIntegrityClicked(studentData: StudentMonitoringData) {
        showCheatLogDialog(studentData)
    }

    private fun showCheatLogDialog(studentData: StudentMonitoringData) {

        viewModel.getDetailedCheatLog(studentData.studentUid) { logList ->

            val logMessage = if (logList.isEmpty()) {
                "No detailed integrity entries found. Student may have not started or completed without incidents."
            } else if (logList.size == 1 && logList[0].startsWith("Error")) {
                logList[0]
            } else {
                logList.joinToString("\n")
            }

            AlertDialog.Builder(this)
                .setTitle("Integrity Log for ${studentData.studentName}")
                .setMessage(
                    "Current Status: ${studentData.status}\n" +
                            "Score: ${studentData.score}\n" +
                            "Total Detected Cheats: ${studentData.cheatCount}\n\n" +
                            "--- Detailed Log ---\n$logMessage"
                )
                .setPositiveButton("CLOSE", null)
                .show()
        }
    }


    // ----------------------------------------------------------------------
    // NEW IMPLEMENTATION: Manage Access Dialog (Central Control)
    // ----------------------------------------------------------------------

    fun showManageAccessDialog(currentList: List<StudentMonitoringData>) {

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_access, null)
        val rgAction = view.findViewById<RadioGroup>(R.id.rgAction)
        val rgStudents = view.findViewById<RadioGroup>(R.id.rgStudents)
        val btnSelectSpecific = view.findViewById<Button>(R.id.btnSelectSpecificStudents)
        val tvStartTime = view.findViewById<TextView>(R.id.tvStartTime)
        val tvEndTime = view.findViewById<TextView>(R.id.tvEndTime)
        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)

        val timeInputContainer = view.findViewById<View>(R.id.timeInputContainer)

        var selectedAction: String? = null // RETAKE, REOPEN, REVOKE
        var selectedStudentsType: String? = null // ALL, SPECIFIC, MISSED
        var specificUids: List<String> = emptyList() // Para sa SPECIFIC selection

        var startTime: Long = System.currentTimeMillis()
        var endTime: Long = 0L

        // Helper function for Date/Time picker
        fun showDateTimePicker(tv: TextView, callback: (Long) -> Unit) {
            val calendar = Calendar.getInstance()

            val datePickerDialog = DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)

                val timePickerDialog = TimePickerDialog(this, { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)

                    val selectedTime = calendar.timeInMillis
                    val currentTime = System.currentTimeMillis()

                    if (selectedTime <= currentTime && tv.id == R.id.tvEndTime) {
                        Toast.makeText(this, "End time must be in the future.", Toast.LENGTH_LONG).show()
                        return@TimePickerDialog
                    }

                    if (tv.id == R.id.tvStartTime && endTime > 0L && selectedTime >= endTime) {
                        Toast.makeText(this, "Start time must be before the set End time.", Toast.LENGTH_LONG).show()
                        return@TimePickerDialog
                    }

                    val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                    tv.text = sdf.format(Date(selectedTime))
                    callback(selectedTime)

                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
                timePickerDialog.setTitle("Set Time")
                timePickerDialog.show()

            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

            if (tv.id == R.id.tvEndTime) {
                datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
            }
            datePickerDialog.show()
        }

        // Helper function for validation
        fun getAffectedStudentsAndValidate(
            action: String?,
            studentsType: String?,
            specificUids: List<String>,
            currentList: List<StudentMonitoringData>,
            startTime: Long,
            endTime: Long
        ): Pair<List<StudentMonitoringData>, Boolean> {
            val affected = when (studentsType) {
                "ALL" -> currentList
                "SPECIFIC" -> currentList.filter { it.studentUid in specificUids }
                "MISSED" -> currentList.filter { it.status == "UNATTEMPTED_TIME_EXPIRED" || it.status == "NOT_STARTED" }
                else -> emptyList()
            }

            val hasValidAction = action != null
            val hasStudents = affected.isNotEmpty()

            val isTimeRequiredAndValid = if (action == "RETAKE" || action == "REOPEN") {
                startTime > 0L && endTime > startTime
            } else {
                true
            }

            val isValid = hasValidAction && hasStudents && isTimeRequiredAndValid

            return Pair(affected, isValid)
        }

        // Helper function to update summary
        fun updateSummary() {
            val (affectedStudents, isValid) = getAffectedStudentsAndValidate(
                selectedAction,
                selectedStudentsType,
                specificUids,
                currentList,
                startTime,
                endTime
            )

            val actionText = selectedAction ?: "N/A"
            val timeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())

            val validUntilText = if (selectedAction == "REVOKE") {
                "Not Applicable (Access Blocked)"
            } else if (endTime > 0L) {
                timeFormat.format(Date(endTime))
            } else {
                "Not set (REQUIRED for RETAKE/REOPEN)"
            }

            val validFromText = if (selectedAction == "REVOKE") {
                "N/A"
            } else if (startTime > 0L) {
                timeFormat.format(Date(startTime))
            } else {
                "N/A"
            }

            val summary = "Action: $actionText\n" +
                    "Students affected: ${affectedStudents.size} students\n" +
                    "Valid from: $validFromText\n" +
                    "Valid until: $validUntilText\n" +
                    "Status: ${if(isValid) "Ready to CONFIRM" else "Validation FAILED: Check Action, Students, and Time."}"

            tvSummary.text = summary
        }

        // ⭐ INITIAL SETUP: Set Start Time to Now (FIXED FORMAT)
        val timeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        tvStartTime.text = "${timeFormat.format(Date(startTime))} (NOW)"


        // --- Student Selection Logic ---
        btnSelectSpecific.setOnClickListener {
            val allUids = currentList.map { it.studentUid }
            val allNames = currentList.map { it.studentName }
            val selectedItems = BooleanArray(allNames.size) { allUids[it] in specificUids }

            AlertDialog.Builder(this)
                .setTitle("Select Specific Students")
                .setMultiChoiceItems(allNames.toTypedArray(), selectedItems) { _, _, _ ->
                    // Selection handled by the boolean array
                }
                .setPositiveButton("OK") { _, _ ->
                    specificUids = allUids.filterIndexed { index, _ -> selectedItems[index] }

                    (rgStudents.getChildAt(1) as RadioButton).isChecked = true
                    Toast.makeText(this, "Selected ${specificUids.size} student(s).", Toast.LENGTH_SHORT).show()
                    updateSummary()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        rgStudents.setOnCheckedChangeListener { _, checkedId ->
            selectedStudentsType = when (checkedId) {
                R.id.rbAllStudents -> "ALL"
                R.id.rbSpecificStudents -> "SPECIFIC"
                R.id.rbMissedStudents -> "MISSED"
                else -> null
            }
            btnSelectSpecific.visibility = if (selectedStudentsType == "SPECIFIC") View.VISIBLE else View.GONE
            updateSummary()
        }

        // --- Action Selection ---
        rgAction.setOnCheckedChangeListener { _, checkedId ->
            selectedAction = when (checkedId) {
                R.id.rbAllowRetake -> "RETAKE"
                R.id.rbReopenAccess -> "REOPEN"
                R.id.rbRevokeAccess -> "REVOKE"
                else -> null
            }

            val showTimePickers = selectedAction == "RETAKE" || selectedAction == "REOPEN"
            timeInputContainer?.visibility = if (showTimePickers) View.VISIBLE else View.GONE

            if (!showTimePickers) {
                startTime = 0L
                endTime = 0L
            } else {
                startTime = System.currentTimeMillis()
                // FIXED: Gamitin ang format na walang (NOW) at ikabit na lang ito.
                val nowSdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                tvStartTime.text = "${nowSdf.format(Date(startTime))} (NOW)"
            }

            updateSummary()
        }

        // --- Time Window Selection ---
        tvStartTime.setOnClickListener { showDateTimePicker(tvStartTime) { time -> startTime = time; updateSummary() } }
        tvEndTime.setOnClickListener { showDateTimePicker(tvEndTime) { time -> endTime = time; updateSummary() } }

        // --- Dialog Setup and Final Confirmation ---
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_manage_access)
            .setView(view)
            .setPositiveButton("Confirm Action", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            updateSummary()
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val (affectedStudents, isValid) = getAffectedStudentsAndValidate(
                    selectedAction,
                    selectedStudentsType,
                    specificUids,
                    currentList,
                    startTime,
                    endTime
                )

                if (!isValid) {
                    Toast.makeText(this, "Validation Failed. Check action, student selection, and time window.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                viewModel.performBulkAction(
                    selectedAction!!,
                    affectedStudents.map { it.studentUid },
                    startTime,
                    endTime
                )

                Toast.makeText(this, "${selectedAction!!} command sent to ${affectedStudents.size} students.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
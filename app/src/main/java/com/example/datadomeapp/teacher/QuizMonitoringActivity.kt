package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import java.util.concurrent.TimeUnit
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
    private val quizType by lazy { intent.getStringExtra("QUIZ_TYPE")?.trim() ?: "" }
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

        val isQuizGloballyFinished = quizEndTime > 0L && System.currentTimeMillis() < quizEndTime

        if (isQuizGloballyFinished) {
            fabManageAccess.visibility = View.GONE
        } else {
            fabManageAccess.visibility = View.VISIBLE
        }

        setupRecyclerView()
        observeViewModel()

        if (!isQuizGloballyFinished) {
            fabManageAccess.setOnClickListener {
                viewModel.monitoringData.value?.let { currentList ->
                    showManageAccessDialog(currentList)
                }
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
            onIntegrityClick = { studentData -> onIntegrityClicked(studentData) },
            onAccessControlClick = { studentData -> showIndividualAccessControlDialog(studentData) },
            quizType = quizType
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

    private fun showManageAccessDialog(currentList: List<StudentMonitoringData>) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_access, null)
        val rgAction = view.findViewById<RadioGroup>(R.id.rgAction)
        val rgStudents = view.findViewById<RadioGroup>(R.id.rgStudents)
        val btnSelectSpecific = view.findViewById<Button>(R.id.btnSelectSpecificStudents)
        val tvStartTime = view.findViewById<TextView>(R.id.tvStartTime)
        val tvEndTime = view.findViewById<TextView>(R.id.tvEndTime)
        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)
        val timeInputContainer = view.findViewById<View>(R.id.timeInputContainer)

        var selectedAction: String? = null
        var selectedStudentsType: String? = null
        var specificUids: List<String> = emptyList()

        // LIVE TIME - Gamitin ang current system time palagi
        var startTime: Long = System.currentTimeMillis()
        var endTime: Long = 0L

        val timeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())

        // ✅ SIMPLIFIED AUTO-UPDATE - WALANG TIMER
        fun updateNowTimeDisplay() {
            val currentNowTime = System.currentTimeMillis()
            tvStartTime.text = "${timeFormat.format(Date(currentNowTime))} (LIVE NOW)"
        }

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

                    tv.text = timeFormat.format(Date(selectedTime))
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
                "MISSED" -> currentList.filter { it.status == "UNATTEMPTED_TIME_EXPIRED" || it.status == "NOT_STARTED" || it.status == "N/A" }
                else -> emptyList()
            }

            val hasValidAction = action != null
            val hasStudents = affected.isNotEmpty()

            // ✅ GAMITIN ANG CURRENT TIME PARA SA VALIDATION
            val currentTime = System.currentTimeMillis()
            val isTimeRequiredAndValid = if (action == "RETAKE" || action == "REOPEN") {
                endTime > currentTime // End time must be in the future
            } else {
                true
            }

            val isValid = hasValidAction && hasStudents && isTimeRequiredAndValid

            return Pair(affected, isValid)
        }

        // Helper function to update summary
        fun updateSummary() {
            // ✅ GAMITIN ANG CURRENT TIME
            val currentNowTime = System.currentTimeMillis()

            val (affectedStudents, isValid) = getAffectedStudentsAndValidate(
                selectedAction,
                selectedStudentsType,
                specificUids,
                currentList,
                currentNowTime, // ✅ CURRENT TIME ANG GINAGAMIT
                endTime
            )

            val actionText = selectedAction ?: "N/A"

            val validUntilText = if (endTime > 0L) {
                timeFormat.format(Date(endTime))
            } else {
                "Not set (REQUIRED for RETAKE/REOPEN)"
            }

            // ✅ IPAKITA ANG LIVE CURRENT TIME
            updateNowTimeDisplay() // ✅ TUMATAWAG PARA I-UPDATE ANG DISPLAY

            val timeRemaining = if (endTime > 0L) {
                val remainingMs = endTime - currentNowTime
                val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
                "\nTime remaining: ${hours}h ${minutes}m"
            } else {
                ""
            }

            val summary = "Action: $actionText\n" +
                    "Students affected: ${affectedStudents.size} students\n" +
                    "Valid from: ${tvStartTime.text}\n" + // ✅ KUNIN NA LANG ANG TEXT MULA SA TV
                    "Valid until: $validUntilText$timeRemaining\n" +
                    "Status: ${if(isValid) "Ready to CONFIRM" else "Validation FAILED: Check Action, Students, and Time."}"

            tvSummary.text = summary
        }

        // ✅ INITIAL SETUP: Live current time
        updateNowTimeDisplay()

        // --- Student Selection Logic ---
        btnSelectSpecific.setOnClickListener {
            val allUids = currentList.map { it.studentUid }
            val allNames = currentList.map { "${it.studentName} - [STUDENT ID: ${it.id}]" }
            val selectedItems = BooleanArray(allNames.size) { allUids[it] in specificUids }

            AlertDialog.Builder(this)
                .setTitle("Select Specific Students")
                .setMultiChoiceItems(allNames.toTypedArray(), selectedItems) { _, _, _ -> }
                .setPositiveButton("OK") { _, _ ->
                    specificUids = allUids.filterIndexed { index, _ -> selectedItems[index] }
                    if (specificUids.isNotEmpty() && selectedStudentsType != "SPECIFIC") {
                        rgStudents.check(R.id.rbSpecificStudents)
                    }
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
                else -> null
            }

            val showTimePickers = selectedAction == "RETAKE"
            timeInputContainer?.visibility = if (showTimePickers) View.VISIBLE else View.GONE

            if (!showTimePickers) {
                endTime = 0L
            } else {
                // ✅ UPDATE ANG DISPLAY AT SUMMARY KAPAG NAG-SELECT NG RETAKE
                updateNowTimeDisplay()
            }

            updateSummary()
        }

        // --- Time Window Selection ---
        // ✅ START TIME AY FIXED NA LIVE - HINDI NA PWDENG I-EDIT
        tvStartTime.setOnClickListener {
            Toast.makeText(this, "Start time is fixed to current time (Live)", Toast.LENGTH_SHORT).show()
        }

        tvEndTime.setOnClickListener {
            showDateTimePicker(tvEndTime) { time ->
                endTime = time
                updateSummary()
            }
        }

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
                val currentTime = System.currentTimeMillis()
                val (initialAffectedStudents, isValid) = getAffectedStudentsAndValidate(
                    selectedAction,
                    selectedStudentsType,
                    specificUids,
                    currentList,
                    currentTime, // ✅ CURRENT TIME ANG GINAMIT
                    endTime
                )

                if (!isValid) {
                    Toast.makeText(this, "Validation Failed. Check action, student selection, and time window.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                var finalUidsToUpdate = initialAffectedStudents.map { it.studentUid }
                val finalAction = selectedAction!!

                if (finalAction == "RETAKE") {
                    val retakeableStatuses = listOf(
                        "COMPLETED", "TIME_EXPIRED", "UNATTEMPTED_TIME_EXPIRED",
                        "ACCESS_REVOKED", "RETAKE_EXPIRED", "N/A"
                    )

                    finalUidsToUpdate = initialAffectedStudents
                        .filter { it.status in retakeableStatuses }
                        .map { it.studentUid }

                    if (finalUidsToUpdate.isEmpty()) {
                        val statusCount = initialAffectedStudents.groupBy { it.status }.mapValues { it.value.size }
                        Toast.makeText(this,
                            "Something went wrong, Try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }
                }

                // ✅ GAMITIN ANG CURRENT TIME PARA SA START TIME
                viewModel.performBulkAction(
                    finalAction,
                    finalUidsToUpdate,
                    currentTime, // ✅ LIVE CURRENT TIME
                    endTime
                )

                Toast.makeText(this, "${finalAction} command sent to ${finalUidsToUpdate.size} students.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showIndividualAccessControlDialog(studentData: StudentMonitoringData) {
        val actions = mutableListOf<String>()

        val isQuizGloballyFinished = quizEndTime > 0L && System.currentTimeMillis() > quizEndTime
        val isExamType = quizType.equals("Exam", ignoreCase = true)

        if (isExamType && !isQuizGloballyFinished &&
            (studentData.status == "EXAM_READY" || studentData.status == "NOT_STARTED" || studentData.status == "UNATTEMPTED_TIME_EXPIRED" ||
                    studentData.status == "RETAKE_EXPIRED")) {
            actions.add("ALLOW ACCESS")
        }

        val restartableStatuses = listOf("COMPLETED")

        if (!isQuizGloballyFinished && studentData.status in restartableStatuses) {
            actions.add("RESTART")
        }

        if (isQuizGloballyFinished &&
            (studentData.status == "COMPLETED" || studentData.status == "TIME_EXPIRED" || studentData.status == "CHEATED_MAX" || studentData.status == "ACCESS_REVOKED" || studentData.status == "N/A")) {
            actions.add("GRANT RETAKE")
        }


        if (actions.isEmpty()) {
            Toast.makeText(this, "${studentData.status}: No access actions available.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Access Control for ${studentData.studentName}")
            .setItems(actions.toTypedArray()) { dialog, which ->
                when (actions[which]) {
                    "ALLOW ACCESS" -> confirmIndividualStart(studentData)
                    "RESTART" -> confirmIndividualRestart(studentData)
                    "GRANT RETAKE" -> showRetakeDeadlinePicker(studentData)
                }
            }
            .show()
    }

    private fun confirmIndividualRestart(studentData: StudentMonitoringData) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Quiz Restart")
            .setMessage(
                "Restarting the quiz for **${studentData.studentName}** will reset their current score and progress to 0. " +
                        "A history log will be saved. Continue?"
            )
            .setPositiveButton("RESTART NOW") { _, _ ->
                // Gumamit ng performBulkAction na may action na "RESTART"
                viewModel.performBulkAction(
                    action = "RESTART",
                    studentUids = listOf(studentData.studentUid),
                    // Ang START at END time ay hindi mahalaga sa RESTART, pero kailangan nating magpasa ng valid value.
                    startTime = System.currentTimeMillis(),
                    endTime = quizEndTime
                )
                // ✅ Pinalitan ang Toast message
                Toast.makeText(this, "Quiz successfully restarted for ${studentData.studentName}.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmIndividualStart(studentData: StudentMonitoringData) {
        val timeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val endTimeText = timeFormat.format(Date(quizEndTime))

        AlertDialog.Builder(this)
            .setTitle("Confirm Allowed Exam")
            .setMessage("Allowed to exam for ${studentData.studentName}.")
            .setPositiveButton("START NOW") { _, _ ->
                // ✅ Pinalitan ng performBulkAction (REOPEN)
                viewModel.performBulkAction(
                    action = "REOPEN",
                    studentUids = listOf(studentData.studentUid),
                    startTime = System.currentTimeMillis(),
                    endTime = quizEndTime
                )
                Toast.makeText(this, "Access granted to ${studentData.studentName}.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRetakeDeadlinePicker(studentData: StudentMonitoringData) {
        val calendar = Calendar.getInstance()
        var selectedTime: Long = 0L

        val datePickerDialog = DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)

            val timePickerDialog = TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                selectedTime = calendar.timeInMillis

                if (selectedTime <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Deadline must be in the future.", Toast.LENGTH_LONG).show()
                    return@TimePickerDialog
                }

                // Call ViewModel to grant retake with the new deadline
                viewModel.performBulkAction(
                    action = "RETAKE",
                    studentUids = listOf(studentData.studentUid),
                    startTime = System.currentTimeMillis(),
                    endTime = selectedTime
                )
                Toast.makeText(this, "Retake granted to ${studentData.studentName} until ${SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(selectedTime))}.", Toast.LENGTH_LONG).show()

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
            timePickerDialog.setTitle("Set Retake Time")
            timePickerDialog.show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

}
package com.example.datadomeapp.teacher

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.BaseActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.teacher.adapters.QuizMonitoringAdapter

// Ang QuizMonitoringActivity ay gumagamit na ngayon ng QuizMonitoringViewModelFactory na tumatanggap ng quizEndTime
class QuizMonitoringActivity : BaseActivity() {

    // ⭐ CRITICAL FIX: I-extract ang quizEndTime mula sa Intent
    private val quizId by lazy { intent.getStringExtra("QUIZ_ID") ?: "" }
    private val assignmentId by lazy { intent.getStringExtra("ASSIGNMENT_ID") ?: "" }
    private val quizTitle by lazy { intent.getStringExtra("QUIZ_TITLE") ?: "Quiz Monitoring" }
    // Ang scheduledEndDateTime ay dapat ipasa mula sa ManageQuizzesActivity
    private val quizEndTime by lazy { intent.getLongExtra("SCHEDULED_END_DATE_TIME", 0L) }

    private val viewModel: QuizMonitoringViewModel by viewModels {
        // ⭐ NEW: I-pass ang quizEndTime sa Factory
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

        tvQuizTitle.text = quizTitle
        tvStatus.text = "Loading Live Status..."

        setupRecyclerView()
        observeViewModel()
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
            onRetakeClick = { studentData -> onRetakeClicked(studentData) },
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

    fun onRetakeClicked(studentData: StudentMonitoringData) {
        // Tawagin ang picker para mag-set ng deadline
        showRetakeDeadlinePicker(studentData)
    }

    private fun showRetakeDeadlinePicker(studentData: StudentMonitoringData) {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)

            val timePickerDialog = TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)

                val deadlineTime = calendar.timeInMillis
                val currentTime = System.currentTimeMillis()

                if (deadlineTime <= currentTime) {
                    Toast.makeText(this, "Deadline must be in the future.", Toast.LENGTH_LONG).show()
                    return@TimePickerDialog
                }

                // 2. I-confirm at I-grant ang retake gamit ang Deadline
                confirmAndGrantRetake(studentData, deadlineTime)

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
            timePickerDialog.setTitle("Set Retake Deadline Time")
            timePickerDialog.show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.setTitle("Set Retake Deadline Date")
        datePickerDialog.show()
    }

    // 3. New: Confirmation Dialog at Final ViewModel Call
    private fun confirmAndGrantRetake(studentData: StudentMonitoringData, deadlineTime: Long) {
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())

        AlertDialog.Builder(this)
            .setTitle("Confirm Retake Grant")
            .setMessage("Are you sure you want to grant ${studentData.studentName} a retake? The deadline to start the retake is: ${sdf.format(Date(deadlineTime))}")
            .setPositiveButton("GRANT") { dialog, _ ->
                // ⭐ ITO ANG TAMANG TAWAG: Ginamit ang viewModel
                viewModel.grantRetake(studentData.studentUid, deadlineTime)
                Toast.makeText(this, "Retake command sent. Deadline set for ${studentData.studentName}.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun updateHeaderStatus(dataList: List<StudentMonitoringData>) {
        val totalStudents = dataList.size

        // I-count ang iba't ibang status, kasama ang bagong UNATTEMPTED_TIME_EXPIRED
        val completedCount = dataList.count { it.status == "COMPLETED" || it.status == "TIME_EXPIRED" || it.status == "UNATTEMPTED_TIME_EXPIRED" }
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
}
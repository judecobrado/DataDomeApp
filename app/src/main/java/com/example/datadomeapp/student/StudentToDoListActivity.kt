package com.example.datadomeapp.student

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.datadomeapp.R
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.toString

// ----------------------------------------------------
// TaskItem Data Class
// ----------------------------------------------------
data class TaskItem(
    val taskId: String = "",
    val title: String = "",
    val details: String = "",
    val date: String = "",
    val time: String = "",
    val done: Boolean = false,
    val dueDateTime: Long = 0,
    val reminderSet: Boolean = false,
    val timestamp: Long = 0
) {
    fun isDueSoon(): Boolean {
        if (done) return false

        val now = System.currentTimeMillis()
        val dueTime = dueDateTime
        val timeDifference = dueTime - now

        // Due within 24 hours
        return timeDifference in 1..(24 * 60 * 60 * 1000)
    }

    fun isOverdue(): Boolean {
        if (done) return false
        return System.currentTimeMillis() > dueDateTime
    }

    fun hoursUntilDue(): Long {
        val now = System.currentTimeMillis()
        return (dueDateTime - now) / (60 * 60 * 1000)
    }
}

class StudentToDoListActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var notificationHelper: NotificationHelper

    private lateinit var taskContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var tvTaskStats: TextView
    private lateinit var toggleLayout: LinearLayout
    private lateinit var tvToggleDone: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var ivToggleArrow: ImageView
    private lateinit var cardToggleDone: MaterialCardView

    // State Variables
    private var isDoneTasksVisible = false
    private var allTasks = listOf<TaskItem>()
    private var taskList = mutableListOf<TaskItem>()
    private val statusUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var dueCheckHandler: Handler

    // Constants - SIMPLIFIED (No SimpleDateFormat for parsing)
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val UPCOMING_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val CHECK_INTERVAL = 30 * 60 * 1000L // Check every 30 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_to_do_list)

        notificationHelper = NotificationHelper(this)
        requestNotificationPermission()

        if (auth.currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        loadTasks()
        startDueSoonChecker() // ADD THIS LINE
    }

    // DUE SOON NOTIFICATION METHODS - ADD THESE
    private fun startDueSoonChecker() {
        dueCheckHandler = Handler(Looper.getMainLooper())
        val dueSoonChecker = object : Runnable {
            override fun run() {
                quickCheckDueSoon()
                dueCheckHandler.postDelayed(this, CHECK_INTERVAL)
            }
        }
        dueCheckHandler.post(dueSoonChecker)
    }

    private fun quickCheckDueSoon() {
        val now = System.currentTimeMillis()
        taskList.forEach { task ->
            if (!task.done && isDueIn24Hours(task) && !task.reminderSet) {
                Log.d("DueSoonCheck", "Due soon task found: ${task.title}")
                showDueSoonNotification(task)
            }
        }
    }

    private fun isDueIn24Hours(task: TaskItem): Boolean {
        try {
            val dueDateTime = convertToTimestamp(task.date, task.time)
            val now = System.currentTimeMillis()
            val diff = dueDateTime - now
            return diff in 1..(24 * 60 * 60 * 1000) // Within 24 hours
        } catch (e: Exception) {
            Log.e("DueSoonCheck", "Error checking due soon: ${e.message}")
            return false
        }
    }

    private fun showDueSoonNotification(task: TaskItem) {
        notificationHelper.showTaskNotification(task, "DUE_SOON")
        updateTaskReminderStatus(task.taskId, true)
    }

    private fun updateTaskReminderStatus(taskId: String, reminderSet: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val taskRef = db.collection("students").document(userId).collection("tasks").document(taskId)

        taskRef.update("reminderSet", reminderSet)
            .addOnSuccessListener {
                Log.d("DueSoonCheck", "Reminder status updated for task: $taskId")
            }
            .addOnFailureListener { e ->
                Log.e("DueSoonCheck", "Failed to update reminder status: ${e.message}")
            }
    }

    private fun convertToTimestamp(date: String, time: String): Long {
        return try {
            val calendar = parseDateTimeToCalendar(date, time)
            calendar?.timeInMillis ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("Timestamp", "Error converting date: ${e.message}")
            System.currentTimeMillis()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleTaskNotifications() {
        val activeTasks = allTasks.filter { !it.done }
        val now = System.currentTimeMillis()

        Log.d("NotificationDebug", "Scheduling notifications for ${activeTasks.size} active tasks")

        activeTasks.forEach { task ->
            scheduleNotificationForTask(task, now)
        }
    }

    private fun scheduleNotificationForTask(task: TaskItem, currentTime: Long) {
        if (task.date != "No Date Set" && task.time != "No Time Set") {
            try {
                val deadlineCalendar = parseDateTimeToCalendar(task.date, task.time)

                if (deadlineCalendar != null) {
                    val deadlineTimeMs = deadlineCalendar.timeInMillis

                    // Cancel any existing notifications for this task
                    AlarmReceiver.cancelScheduledNotification(this, task.taskId)
                    notificationHelper.cancelNotification(task.taskId)

                    // Schedule overdue notification (if already overdue)
                    if (deadlineTimeMs < currentTime) {
                        Log.d("NotificationDebug", "Task is overdue: ${task.title}")
                        // Show immediate overdue notification
                        Handler(mainLooper).postDelayed({
                            notificationHelper.showTaskNotification(task, "OVERDUE")
                        }, 2000)
                    }
                    // Schedule upcoming deadline notification (1 hour before)
                    else {
                        val oneHourBefore = deadlineTimeMs - (60 * 60 * 1000)

                        if (oneHourBefore > currentTime) {
                            Log.d("NotificationDebug", "Scheduling reminder for: ${task.title} at $oneHourBefore")
                            AlarmReceiver.scheduleTaskNotification(
                                this,
                                task,
                                oneHourBefore,
                                "REMINDER"
                            )
                        }

                        // Also schedule overdue notification for exactly at deadline
                        AlarmReceiver.scheduleTaskNotification(
                            this,
                            task,
                            deadlineTimeMs,
                            "OVERDUE"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationDebug", "Failed to parse date/time for notification: ${task.date} ${task.time}", e)
            }
        }
    }

    private fun initializeViews() {
        taskContainer = findViewById(R.id.taskContainer)
        emptyState = findViewById(R.id.emptyState)
        tvTaskStats = findViewById(R.id.tvTaskStats)
        toggleLayout = findViewById(R.id.toggleLayout)
        tvToggleDone = findViewById(R.id.tvToggleDone)
        tvCompletedCount = findViewById(R.id.tvCompletedCount)
        ivToggleArrow = findViewById(R.id.ivToggleArrow)
        cardToggleDone = findViewById(R.id.cardToggleDone)
    }

    private fun setupClickListeners() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddTask).setOnClickListener {
            showAddEditTaskDialog()
        }

        // Make entire toggle layout clickable
        toggleLayout.setOnClickListener {
            toggleCompletedTasksVisibility()
        }
    }

    // ----------------------------------------------------
    // TOGGLE COMPLETED TASKS FUNCTIONALITY
    // ----------------------------------------------------
    private fun toggleCompletedTasksVisibility() {
        isDoneTasksVisible = !isDoneTasksVisible

        // Animate arrow rotation
        ivToggleArrow.animate()
            .rotation(if (isDoneTasksVisible) 180f else 0f)
            .setDuration(200)
            .start()

        // Update toggle text
        tvToggleDone.text = if (isDoneTasksVisible) "Hide Completed Tasks" else "Show Completed Tasks"

        renderTasks()
    }

    // ----------------------------------------------------
    // 1. DATA FETCHING (Firebase Query)
    // ----------------------------------------------------
    private fun loadTasks() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("students").document(userId)
            .collection("tasks")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                allTasks = result.documents.mapNotNull { doc ->
                    try {
                        val task = doc.toObject(TaskItem::class.java)
                        task?.copy(
                            taskId = doc.id,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            dueDateTime = safeConvertToTimestamp(task.date, task.time),
                            reminderSet = doc.getBoolean("reminderSet") ?: false
                        )
                    } catch (e: Exception) {
                        Log.e("ToDoListDebug", "Error parsing task: ${doc.id}", e)
                        // Instead of returning null, create a basic task without dueDateTime
                        val task = doc.toObject(TaskItem::class.java)
                        task?.copy(
                            taskId = doc.id,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            dueDateTime = 0L, // Default value
                            reminderSet = doc.getBoolean("reminderSet") ?: false
                        )
                    }
                }

                taskList.clear()
                taskList.addAll(allTasks)
                updateTaskStats()
                renderTasks()
                quickCheckDueSoon()
                scheduleTaskNotifications()
            }
            .addOnFailureListener { e ->
                Log.e("ToDoListDebug", "Firestore Query Failed!", e)
                Toast.makeText(this, "Failed to load tasks.", Toast.LENGTH_LONG).show()
            }
    }

    private fun safeConvertToTimestamp(date: String, time: String): Long {
        return try {
            convertToTimestamp(date, time)
        } catch (e: Exception) {
            Log.e("ToDoListDebug", "Failed to convert date/time: $date $time", e)
            0L // Return default value instead of crashing
        }
    }

    // ----------------------------------------------------
    // UPDATED TASK STATS AND COMPLETED COUNT
    // ----------------------------------------------------
    private fun updateTaskStats() {
        val totalTasks = allTasks.size
        val completedTasks = allTasks.count { it.done }
        val activeTasks = totalTasks - completedTasks

        // Update main task stats
        tvTaskStats.text = "$activeTasks tasks active • $completedTasks completed"

        // Update completed count badge
        tvCompletedCount.text = completedTasks.toString()

        // Show/hide toggle card based on completed tasks
        cardToggleDone.isVisible = completedTasks > 0

        // Show/hide empty state
        emptyState.isVisible = allTasks.isEmpty()
    }

    // ----------------------------------------------------
    // 2. RENDERING LOGIC (UI Update)
    // ----------------------------------------------------
    // ----------------------------------------------------
// UPDATED RENDER TASKS METHOD - NO DATE/TIME LAST
// ----------------------------------------------------
    private fun renderTasks() {
        taskContainer.removeAllViews()

        // ACTIVE TASKS: May date/time una, walang date/time huli
        val activeTasks = allTasks
            .filter { !it.done }
            .sortedWith(compareBy<TaskItem> {
                // Unang priority: tasks na walang date/time (lalagay sa huli)
                it.date == "No Date Set" || it.time == "No Time Set"
            }.thenBy {
                // Pangalawang priority: due date (soonest first)
                it.dueDateTime
            })

        // DONE TASKS: Most recently completed first
        val doneTasks = allTasks
            .filter { it.done }
            .sortedByDescending { it.timestamp }

        // 1. Render Active Tasks
        if (activeTasks.isNotEmpty()) {
            activeTasks.forEach { task ->
                taskContainer.addView(createTaskView(task))
            }
        } else if (allTasks.isEmpty()) {
            return
        }

        // 2. Render Done Tasks if visible
        if (isDoneTasksVisible && doneTasks.isNotEmpty()) {
            val sectionHeader = createSectionHeader("Completed Tasks (${doneTasks.size})")
            taskContainer.addView(sectionHeader)

            doneTasks.forEach { task ->
                taskContainer.addView(createTaskView(task))
            }
        }
    }
    private fun createSectionHeader(title: String): View {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#7B1113"))
            setPadding(0, 32, 0, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    // SIMPLIFIED PARSING FUNCTION - NO TIMEZONE ISSUES
    private fun parseDateTimeToCalendar(dateStr: String, timeStr: String): Calendar? {
        return try {
            val calendar = Calendar.getInstance()

            // Parse date (MM/dd/yyyy)
            val dateParts = dateStr.split("/")
            if (dateParts.size != 3) return null

            val month = dateParts[0].toInt() - 1  // Calendar months are 0-based
            val day = dateParts[1].toInt()
            val year = dateParts[2].toInt()

            // Parse time (hh:mm aa)
            val timeParts = timeStr.split(":", " ")
            if (timeParts.size < 2) return null

            var hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            // Handle AM/PM
            if (timeParts.size > 2) {
                val amPm = timeParts[2].toLowerCase(Locale.getDefault())
                if (amPm == "pm" && hour < 12) {
                    hour += 12
                } else if (amPm == "am" && hour == 12) {
                    hour = 0
                }
            }

            // Set the calendar with parsed values - USE THE SAME TIMEZONE AS CURRENT TIME
            calendar.set(year, month, day, hour, minute, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            Log.d("OverdueDebug", "Parsed Calendar: ${calendar.time}")
            Log.d("OverdueDebug", "Parsed Millis: ${calendar.timeInMillis}")

            calendar
        } catch (e: Exception) {
            Log.e("OverdueDebug", "Manual parse failed: $dateStr $timeStr", e)
            null
        }
    }

    // ----------------------------------------------------
    // 3. TASK VIEW CREATION (Improved Styling)
    // ----------------------------------------------------
    private fun createTaskView(task: TaskItem): View {
        val userId = auth.currentUser?.uid ?: return LinearLayout(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundResource(if (task.done) R.drawable.task_background_completed else R.drawable.task_background_active)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        }

        // --- View Elements ---
        val tvTitle = TextView(this).apply {
            text = task.title
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (task.done) Color.GRAY else Color.BLACK)
        }

        val tvDetails = TextView(this).apply {
            text = task.details
            textSize = 14f
            setTextColor(if (task.done) Color.GRAY else Color.DKGRAY)
        }

        val tvDateTime = TextView(this).apply {
            text = "${task.date} • ${task.time}"
            textSize = 12f
            setTextColor(if (task.done) Color.GRAY else Color.parseColor("#666666"))
        }

        val cbDone = CheckBox(this).apply {
            text = if (task.done) "Completed" else "Mark as Done"
            isChecked = task.done
            isEnabled = !task.done
            setTextColor(if (task.done) Color.GRAY else Color.BLACK)
        }

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
        }

        val btnEdit = Button(this).apply {
            text = "Edit"
            isEnabled = !task.done
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#7B1113"))
        }

        val btnDelete = Button(this).apply {
            text = "Delete"
            isEnabled = !task.done
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#FF0000"))
        }

        btnLayout.addView(btnEdit)
        btnLayout.addView(btnDelete)

        // --- SIMPLIFIED Deadline Status Logic ---
        var statusMessage = ""
        var statusColor = Color.BLACK
        if (!task.done && task.date != "No Date Set" && task.time != "No Time Set") {
            try {
                Log.d("OverdueDebug", "=== Checking Task: ${task.title} ===")
                Log.d("OverdueDebug", "Date: ${task.date}, Time: ${task.time}")

                val deadlineCalendar = parseDateTimeToCalendar(task.date, task.time)
                val nowCalendar = Calendar.getInstance()

                if (deadlineCalendar != null) {
                    val currentMillis = nowCalendar.timeInMillis
                    val deadlineMillis = deadlineCalendar.timeInMillis
                    val timeDifference = deadlineMillis - currentMillis

                    Log.d("OverdueDebug", "Current: ${nowCalendar.time} ($currentMillis)")
                    Log.d("OverdueDebug", "Deadline: ${deadlineCalendar.time} ($deadlineMillis)")
                    Log.d("OverdueDebug", "Difference: $timeDifference ms (${timeDifference / 1000 / 60} minutes)")

                    // SIMPLE COMPARISON - No buffers, just direct comparison
                    if (deadlineMillis < currentMillis) {
                        statusMessage = "❗️ OVERDUE"
                        statusColor = Color.RED
                        Log.d("OverdueDebug", "STATUS: OVERDUE - Deadline is in past")
                    } else if (timeDifference <= UPCOMING_THRESHOLD_MS) {
                        statusMessage = "⚠️ DEADLINE SOON"
                        statusColor = Color.parseColor("#FFA500")
                        Log.d("OverdueDebug", "STATUS: DEADLINE SOON")
                    } else {
                        Log.d("OverdueDebug", "STATUS: ON TIME - Deadline is in future")
                    }
                }
            } catch (e: Exception) {
                Log.e("OverdueDebug", "Failed to check deadline", e)
            }
        }

        // --- Final Assembly ---
        container.addView(tvTitle)
        container.addView(tvDetails)
        container.addView(tvDateTime)

        if (statusMessage.isNotEmpty() && !task.done) {
            val tvStatus = TextView(this).apply {
                text = statusMessage
                setTextColor(statusColor)
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
            }
            container.addView(tvStatus)
            tvTitle.setTextColor(statusColor)
        }

        container.addView(cbDone)

        if (!task.done) {
            container.addView(btnLayout)
        }

        // Apply Strike-Through if Done
        if (task.done) {
            tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvDetails.paintFlags = tvDetails.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvDateTime.paintFlags = tvDateTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        // --- Action Listeners ---
        if (!task.done) {
            cbDone.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    val doneMap = hashMapOf("done" to true)
                    db.collection("students").document(userId)
                        .collection("tasks").document(task.taskId)
                        .update(doneMap as Map<String, Any>)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Task marked as completed!", Toast.LENGTH_SHORT).show()
                            loadTasks()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update task", Toast.LENGTH_SHORT).show()
                            cbDone.isChecked = false
                        }
                }
            }

            btnEdit.setOnClickListener {
                showAddEditTaskDialog(task.taskId, task.title, task.details, task.date, task.time)
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete \"${task.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        db.collection("students").document(userId)
                            .collection("tasks").document(task.taskId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show()
                                loadTasks()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        return container
    }

    // ----------------------------------------------------
    // 4. ADD/EDIT DIALOG (Helper)
    // ----------------------------------------------------
    private fun showAddEditTaskDialog(
        taskId: String? = null,
        currentTitle: String = "",
        currentDetails: String = "",
        currentDate: String = "No Date Set",
        currentTime: String = "No Time Set"
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.student_addtask_dialog, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDetails = dialogView.findViewById<EditText>(R.id.etDetails)
        val btnDate = dialogView.findViewById<Button>(R.id.btnDate)
        val btnTime = dialogView.findViewById<Button>(R.id.btnTime)
        val tvTitleCount = dialogView.findViewById<TextView>(R.id.tvTitleCount)
        val tvDetailsCount = dialogView.findViewById<TextView>(R.id.tvDetailsCount)

        var selectedDate = currentDate
        var selectedTime = currentTime

        etTitle.setText(currentTitle)
        etDetails.setText(currentDetails)
        btnDate.text = selectedDate
        btnTime.text = selectedTime

        tvTitleCount.text = "${currentTitle.length}/50"
        tvDetailsCount.text = "${currentDetails.length}/500"

        // Create dialog instance first
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (taskId == null) "Add New Task" else "Edit Task")
            .setView(dialogView)
            .setPositiveButton(if (taskId == null) "Add" else "Save", null) // null muna
            .setNegativeButton("Cancel", null)
            .create()

        fun validateDateTime(): Boolean {
            if (selectedDate == "No Date Set" || selectedTime == "No Time Set") {
                return true // Allow saving if no date/time set
            }

            try {
                val deadlineCalendar = parseDateTimeToCalendar(selectedDate, selectedTime)
                val now = Calendar.getInstance()

                return deadlineCalendar != null && !deadlineCalendar.before(now)
            } catch (e: Exception) {
                Log.e("DateTimeValidation", "Error validating date/time", e)
                return false
            }
        }

        fun validateInputs(): Boolean {
            val title = etTitle.text.toString().trim()

            // Check if title is empty
            if (title.isEmpty()) {
                return false
            }

            return true
        }

        fun updateSaveButtonState() {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val isTitleValid = validateInputs() // Check title (required)
            val isDateTimeValid = validateDateTime() // Check date/time (optional)

            // Title must be valid, date/time is optional but must be valid if set
            val isValid = isTitleValid && isDateTimeValid

            if (isValid) {
                positiveButton.isEnabled = true
                positiveButton.setBackgroundColor(Color.parseColor("#7B1113"))
                positiveButton.setTextColor(Color.WHITE)
            } else {
                positiveButton.isEnabled = false
                positiveButton.setBackgroundColor(Color.LTGRAY)
                positiveButton.setTextColor(Color.DKGRAY)
                // Show warning message
            }
        }


        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Prevent double spaces
                if (count == 1 && before == 0 && s?.getOrNull(start) == ' ' &&
                    start > 0 && s.getOrNull(start - 1) == ' ') {
                    etTitle.text?.delete(start, start + 1)
                    return
                }
            }
            override fun afterTextChanged(s: Editable?) {
                // Update character count
                tvTitleCount.text = "${s?.length ?: 0}/50"

                // Prevent input beyond 50 characters
                if (s?.length ?: 0 > 50) {
                    etTitle.setText(s?.subSequence(0, 50))
                    etTitle.setSelection(50)
                    tvTitleCount.text = "50/50"
                }

                // Auto-capitalize first word
                val text = s.toString()
                if (text.isNotEmpty() && text[0].isLowerCase()) {
                    val capitalized = text.replaceFirstChar { it.uppercase() }
                    etTitle.setText(capitalized)
                    etTitle.setSelection(capitalized.length)
                }
                updateSaveButtonState()
            }
        })

        etDetails.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Prevent double spaces
                if (count == 1 && before == 0 && s?.getOrNull(start) == ' ' &&
                    start > 0 && s.getOrNull(start - 1) == ' ') {
                    etDetails.text?.delete(start, start + 1)
                    return
                }
            }
            override fun afterTextChanged(s: Editable?) {
                // Update character count
                tvDetailsCount.text = "${s?.length ?: 0}/500"

                // Prevent input beyond 500 characters
                if (s?.length ?: 0 > 500) {
                    etDetails.setText(s?.subSequence(0, 500))
                    etDetails.setSelection(500)
                    tvDetailsCount.text = "500/500"
                }
            }
        })

        // Set up positive button after dialog is shown
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val title = etTitle.text.toString().trim()
                val details = etDetails.text.toString().trim()

                if (title.isEmpty()) {
                    return@setOnClickListener
                }

                saveTask(taskId, title, details, selectedDate, selectedTime)
                dialog.dismiss()
            }

            // Initial validation
            updateSaveButtonState()
        }

        btnTime.visibility = if (selectedDate != "No Date Set") View.VISIBLE else View.GONE

        // Date Picker Logic
        btnDate.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, y, m, d ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(y, m, d)

                // Validate if selected date is not in the past
                val today = Calendar.getInstance()
                today.set(Calendar.HOUR_OF_DAY, 0)
                today.set(Calendar.MINUTE, 0)
                today.set(Calendar.SECOND, 0)
                today.set(Calendar.MILLISECOND, 0)

                selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                selectedCalendar.set(Calendar.MINUTE, 0)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)

                if (selectedCalendar.before(today)) {
                    Toast.makeText(this, "Cannot select past dates", Toast.LENGTH_SHORT).show()
                } else {
                    selectedDate = "${m + 1}/${d}/${y}" // Manual format instead of dateFormat
                    btnDate.text = selectedDate

                    btnTime.visibility = View.VISIBLE

                    Handler(Looper.getMainLooper()).postDelayed({
                        btnTime.performClick() // Automatically click the time button
                    }, 50)

                    // If time is still default, set to current time
                    if (selectedTime == "No Time Set") {
                        val currentTime = Calendar.getInstance()
                        val hour = currentTime.get(Calendar.HOUR)
                        val minute = currentTime.get(Calendar.MINUTE)
                        val amPm = if (currentTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                        selectedTime = String.format("%02d:%02d %s", hour, minute, amPm)
                        btnTime.text = selectedTime
                    }

                    // Update save button state after date change
                    updateSaveButtonState()
                }
            }, year, month, day)

            dpd.datePicker.minDate = System.currentTimeMillis() - 1000
            dpd.show()
        }

        // Time Picker Logic
        btnTime.setOnClickListener {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val minute = c.get(Calendar.MINUTE)

            val tpd = TimePickerDialog(this, { _, hourOfDay, minute ->
                // Create complete datetime for validation
                val selectedDateTime = Calendar.getInstance()

                if (selectedDate != "No Date Set") {
                    try {
                        val dateParts = selectedDate.split("/")
                        if (dateParts.size == 3) {
                            selectedDateTime.set(
                                dateParts[2].toInt(),
                                dateParts[0].toInt() - 1,
                                dateParts[1].toInt(),
                                hourOfDay,
                                minute,
                                0
                            )
                            selectedDateTime.set(Calendar.MILLISECOND, 0)
                        }
                    } catch (e: Exception) {
                        Log.e("ToDoList", "Date parsing error", e)
                    }
                }

                val now = Calendar.getInstance()

                // If selected datetime is in the past, show error
                if (selectedDateTime.before(now)) {
                    Toast.makeText(this, "Cannot select past date/time", Toast.LENGTH_SHORT).show()
                    // Still update the time display but will disable save button
                    selectedTime = String.format("%02d:%02d %s",
                        if (hourOfDay > 12) hourOfDay - 12 else if (hourOfDay == 0) 12 else hourOfDay,
                        minute,
                        if (hourOfDay < 12) "AM" else "PM"
                    )
                    btnTime.text = selectedTime
                } else {
                    // Format time in 12-hour format with AM/PM
                    selectedTime = String.format("%02d:%02d %s",
                        if (hourOfDay > 12) hourOfDay - 12 else if (hourOfDay == 0) 12 else hourOfDay,
                        minute,
                        if (hourOfDay < 12) "AM" else "PM"
                    )
                    btnTime.text = selectedTime
                }

                // Update save button state after time change
                updateSaveButtonState()
            }, hour, minute, false)

            tpd.show()
        }

        dialog.show()
    }

    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            renderTasks() // Refresh UI to update overdue status
            statusUpdateHandler.postDelayed(this, 60000) // Update every minute
        }
    }

    override fun onResume() {
        super.onResume()
        statusUpdateHandler.postDelayed(statusUpdateRunnable, 60000)
    }

    override fun onPause() {
        super.onPause()
        statusUpdateHandler.removeCallbacks(statusUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        dueCheckHandler.removeCallbacksAndMessages(null)
        statusUpdateHandler.removeCallbacksAndMessages(null)
    }

    // ----------------------------------------------------
    // 5. SAVE TASK (Firebase Write)
    // ----------------------------------------------------
    private fun saveTask(
        taskId: String?,
        title: String,
        details: String,
        date: String,
        time: String
    ) {
        val userId = auth.currentUser?.uid ?: return

        var timestampValue = System.currentTimeMillis()
        var dueDateTimeValue = 0L

        if (date != "No Date Set" && time != "No Time Set") {
            try {
                val deadlineCalendar = parseDateTimeToCalendar(date, time)
                timestampValue = deadlineCalendar?.timeInMillis ?: System.currentTimeMillis()
                dueDateTimeValue = timestampValue
            } catch (e: Exception) {
                Log.e("ToDoList", "Timestamp parsing failed.", e)
            }
        }

        val taskMap = hashMapOf(
            "title" to title,
            "details" to details,
            "date" to date,
            "time" to time,
            "done" to false,
            "timestamp" to timestampValue,
            "dueDateTime" to dueDateTimeValue,
            "reminderSet" to false
        )

        val taskRef = db.collection("students").document(userId).collection("tasks")

        if (taskId == null) {
            taskRef.add(taskMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Task Added Successfully!", Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed adding task", Toast.LENGTH_SHORT).show()
                }
        } else {
            taskRef.document(taskId).update(taskMap as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Task Updated Successfully!", Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed updating task", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
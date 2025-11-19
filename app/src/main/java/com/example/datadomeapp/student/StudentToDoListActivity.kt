package com.example.datadomeapp.student

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
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
    val timestamp: Long = 0L
)

class StudentToDoListActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

    // Constants
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
    private val UPCOMING_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_to_do_list)

        if (auth.currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        loadTasks()
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
        val userId = auth.currentUser?.uid
        if (userId == null) return

        Log.d("ToDoListDebug", "Attempting to load tasks for User ID: $userId")

        db.collection("students").document(userId)
            .collection("tasks")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->

                Log.d("ToDoListDebug", "Query successful. Found ${result.size()} tasks.")

                allTasks = result.documents.mapNotNull { doc ->
                    doc.toObject(TaskItem::class.java)?.copy(
                        taskId = doc.id,
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }

                updateTaskStats()
                renderTasks()
            }
            .addOnFailureListener { e ->
                Log.e("ToDoListDebug", "Firestore Query Failed!", e)
                Toast.makeText(this, "Failed to loading tasks.", Toast.LENGTH_LONG).show()
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
    private fun renderTasks() {
        taskContainer.removeAllViews()

        val activeTasks = allTasks.filter { !it.done }
        val doneTasks = allTasks.filter { it.done }

        // 1. Render Active Tasks
        if (activeTasks.isNotEmpty()) {
            activeTasks.forEach { task ->
                taskContainer.addView(createTaskView(task))
            }
        } else if (allTasks.isEmpty()) {
            // Empty state is handled by updateTaskStats()
            return
        }

        // 2. Render Done Tasks if visible
        if (isDoneTasksVisible && doneTasks.isNotEmpty()) {
            // Add section header for completed tasks
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

        // --- Deadline Status Logic ---
        var statusMessage = ""
        var statusColor = Color.BLACK
        if (!task.done) {
            val now = System.currentTimeMillis()
            var deadlineTimeMs: Long? = null

            if (task.date != "No Date Set" && task.time != "No Time Set") {
                try {
                    val dateTimeString = "${task.date} ${task.time}"
                    val deadlineDate = dateTimeFormat.parse(dateTimeString)
                    deadlineTimeMs = deadlineDate?.time

                    if (deadlineTimeMs != null) {
                        if (deadlineTimeMs < now) {
                            statusMessage = "❗️ OVERDUE"
                            statusColor = Color.RED
                        } else if (deadlineTimeMs - now <= UPCOMING_THRESHOLD_MS) {
                            statusMessage = "⚠️ DEADLINE SOON"
                            statusColor = Color.parseColor("#FFA500") // Orange
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ToDoList", "Failed to parse date/time: ${task.date} ${task.time}", e)
                }
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
    // 4. ADD/EDIT DIALOG (Helper) - UNCHANGED
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

        var selectedDate = currentDate
        var selectedTime = currentTime

        etTitle.setText(currentTitle)
        etDetails.setText(currentDetails)
        btnDate.text = selectedDate
        btnTime.text = selectedTime

        // Date Picker Logic
        btnDate.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, y, m, d ->
                val calendar = Calendar.getInstance()
                calendar.set(y, m, d)
                selectedDate = dateFormat.format(calendar.time)
                btnDate.text = selectedDate
            }, year, month, day)

            dpd.datePicker.minDate = System.currentTimeMillis()
            dpd.show()
        }

        // Time Picker Logic
        btnTime.setOnClickListener {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val minute = c.get(Calendar.MINUTE)

            val tpd = TimePickerDialog(this, { _, h, m ->
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, m)
                selectedTime = timeFormat.format(calendar.time)
                btnTime.text = selectedTime
            }, hour, minute, false)
            tpd.show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (taskId == null) "Add New Task" else "Edit Task")
            .setView(dialogView)
            .setPositiveButton(if (taskId == null) "Add" else "Save") { dialog, which ->
                val title = etTitle.text.toString().trim()
                val details = etDetails.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(this, "Title cannot be empty.", Toast.LENGTH_SHORT).show()
                } else {
                    saveTask(taskId, title, details, selectedDate, selectedTime)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ----------------------------------------------------
    // 5. SAVE TASK (Firebase Write) - UNCHANGED
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
        if (date != "No Date Set" && time != "No Time Set") {
            try {
                val dateTimeString = "$date $time"
                val parsedDate = dateTimeFormat.parse(dateTimeString)
                timestampValue = parsedDate?.time ?: timestampValue
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
            "timestamp" to timestampValue
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
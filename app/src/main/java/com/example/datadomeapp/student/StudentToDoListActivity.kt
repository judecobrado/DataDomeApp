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
import com.example.datadomeapp.R
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
    private lateinit var tvToggleDone: TextView

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

        taskContainer = findViewById(R.id.taskContainer)
        tvToggleDone = findViewById(R.id.tvToggleDone)

        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            showAddEditTaskDialog()
        }

        tvToggleDone.setOnClickListener {
            isDoneTasksVisible = !isDoneTasksVisible
            renderTasks()
        }

        loadTasks()
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

                // Paggamit ng standard na toObject(TaskItem::class.java)
                allTasks = result.documents.mapNotNull { doc ->
                    doc.toObject(TaskItem::class.java)?.copy(
                        taskId = doc.id,
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }

                renderTasks()
            }
            .addOnFailureListener { e ->
                Log.e("ToDoListDebug", "Firestore Query Failed!", e)
                Toast.makeText(this, "Error loading tasks: ${e.message}.", Toast.LENGTH_LONG).show()
            }
    }

    // ----------------------------------------------------
    // 2. RENDERING LOGIC (UI Update)
    // ----------------------------------------------------
    private fun renderTasks() {
        taskContainer.removeAllViews()

        val activeTasks = allTasks.filter { !it.done }
        val doneTasks = allTasks.filter { it.done }

        val arrow = if (isDoneTasksVisible) "▲ Hide" else "▼ Show"
        tvToggleDone.text = "$arrow Completed Tasks (${doneTasks.size})"
        tvToggleDone.visibility = if (doneTasks.isNotEmpty()) View.VISIBLE else View.GONE

        // 1. Render Active Tasks
        if (activeTasks.isNotEmpty()) {
            activeTasks.forEach { task ->
                taskContainer.addView(createTaskView(task))
            }
        } else if (allTasks.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "You have no tasks yet. Click 'Add Task' to start!"
                textSize = 16f
                setTextColor(Color.GRAY)
                setPadding(0, 50, 0, 0)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            taskContainer.addView(tvEmpty)
        }

        // 2. Render Done Tasks
        if (isDoneTasksVisible && doneTasks.isNotEmpty()) {
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                    setMargins(0, 32, 0, 16)
                }
                setBackgroundColor(Color.LTGRAY)
            }
            taskContainer.addView(divider)

            doneTasks.forEach { task ->
                taskContainer.addView(createTaskView(task))
            }
        }
    }

    // ----------------------------------------------------
    // 3. TASK VIEW CREATION (Utility)
    // ----------------------------------------------------
    private fun createTaskView(task: TaskItem): View {
        val userId = auth.currentUser?.uid ?: return LinearLayout(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(if (task.done) 0xFFDDDDDD.toInt() else 0xFFEFEFEF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        // --- View Elements ---
        val tvTitle = TextView(this).apply {
            text = "Title: ${task.title}"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (task.done) Color.GRAY else Color.BLACK)
        }
        val tvDetails = TextView(this).apply { text = "Details: ${task.details}" }
        val tvDateTime = TextView(this).apply { text = "Date: ${task.date}  Time: ${task.time}" }
        val cbDone = CheckBox(this).apply {
            text = "Mark as Done"
            isChecked = task.done
            isEnabled = !task.done
        }
        val btnEdit = Button(this).apply { text = "Edit"; isEnabled = !task.done }
        val btnDelete = Button(this).apply { text = "Delete"; isEnabled = !task.done }

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnEdit)
            addView(btnDelete)
        }

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
                            statusMessage = "❗️ OVERDUE (Expired)"
                            statusColor = Color.RED
                        } else if (deadlineTimeMs - now <= UPCOMING_THRESHOLD_MS) {
                            statusMessage = "⚠️ UPCOMING (Deadline soon)"
                            statusColor = Color.parseColor("#FFA500") // Orange
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ToDoList", "Failed to parse date/time: ${task.date} ${task.time}", e)
                }
            }
        }


        // --- Final Assembly and Styling ---
        container.addView(tvTitle)
        container.addView(tvDetails)
        container.addView(tvDateTime)

        if (statusMessage.isNotEmpty() && !task.done) {
            val tvStatus = TextView(this).apply {
                text = statusMessage
                setTextColor(statusColor)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
            }
            container.addView(tvStatus)
            tvTitle.setTextColor(statusColor)
        }

        container.addView(cbDone)
        container.addView(btnLayout)

        // Apply Strike-Through if Done
        if (task.done) {
            tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvDetails.paintFlags = tvDetails.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvDateTime.paintFlags = tvDateTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            btnEdit.visibility = View.GONE
            btnDelete.visibility = View.GONE
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
                            loadTasks()
                        }
                }
            }

            btnEdit.setOnClickListener {
                showAddEditTaskDialog(task.taskId, task.title, task.details, task.date, task.time)
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Delete") { _, _ ->
                        db.collection("students").document(userId)
                            .collection("tasks").document(task.taskId)
                            .delete()
                            .addOnSuccessListener { loadTasks() }
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

        var selectedDate = currentDate
        var selectedTime = currentTime

        etTitle.setText(currentTitle)
        etDetails.setText(currentDetails)
        btnDate.text = selectedDate
        btnTime.text = selectedTime

        // Date Picker Logic - NAAYOS NA ANG PAST DATE
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

            // ✅ FIX: Hahadlangan ang Past Dates
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
            // New Task
            taskRef.add(taskMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Task Added.", Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
        } else {
            // Update Existing Task
            taskRef.document(taskId).update(taskMap as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Task Updated.", Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
        }
    }

    // ----------------------------------------------------
    // 6. DELETE OLD TASKS (Optional Cleanup)
    // ----------------------------------------------------
    private fun deleteOldTasks(userId: String) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

        db.collection("students").document(userId)
            .collection("tasks")
            .whereEqualTo("done", true)
            .whereLessThan("timestamp", thirtyDaysAgo)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
            }
    }
}
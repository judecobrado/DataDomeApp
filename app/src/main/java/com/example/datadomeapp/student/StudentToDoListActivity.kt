package com.example.datadomeapp.student

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StudentToDoListActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var taskContainer: LinearLayout
    private lateinit var btnAddTask: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Siguraduhin na ito ang tamang layout file name
        setContentView(R.layout.activity_student_todo)

        taskContainer = findViewById(R.id.taskContainer)
        btnAddTask = findViewById(R.id.btnAddTask)

        loadTasks()

        btnAddTask.setOnClickListener { showAddEditTaskDialog() }
    }

    // Function para magpakita ng dialog para sa Add/Edit Task
    private fun showAddEditTaskDialog(taskId: String? = null, oldTitle: String? = null,
                                      oldDetails: String? = null, oldDate: String? = null, oldTime: String? = null) {

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDetails = dialogView.findViewById<EditText>(R.id.etDetails)
        val etDate = dialogView.findViewById<EditText>(R.id.etDate)
        val etTime = dialogView.findViewById<EditText>(R.id.etTime)

        etTitle.setText(oldTitle)
        etDetails.setText(oldDetails)
        etDate.setText(oldDate)
        etTime.setText(oldTime)

        // Date picker setup
        etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val listener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                val formatted = String.format("%02d/%02d/%04d", month + 1, dayOfMonth, year)
                etDate.setText(formatted)
            }
            DatePickerDialog(this, listener, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Time picker setup (12h AM/PM format)
        etTime.setOnClickListener {
            val cal = Calendar.getInstance()
            val listener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                val amPm = if (hourOfDay >= 12) "PM" else "AM"
                val hour = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
                val formatted = String.format("%02d:%02d %s", hour, minute, amPm)
                etTime.setText(formatted)
            }
            // Set 'is24HourView' to false for 12-hour format
            TimePickerDialog(this, listener, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (taskId == null) "Add Task" else "Edit Task")
            .setView(dialogView)
            .setPositiveButton(if (taskId == null) "Add" else "Update") { _, _ ->
                val title = etTitle.text.toString().trim()
                val details = etDetails.text.toString().trim()
                val date = etDate.text.toString().trim()
                val time = etTime.text.toString().trim()
                val userId = auth.currentUser?.uid ?: return@setPositiveButton

                if (title.isEmpty() || details.isEmpty() || date.isEmpty() || time.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Base map for Task data
                val taskMap = mutableMapOf<String, Any>(
                    "studentId" to userId,
                    "title" to title,
                    "details" to details,
                    "date" to date,
                    "time" to time,
                    "timestamp" to System.currentTimeMillis()
                )

                if (taskId == null) {
                    // Add New Task (Initial done status is false)
                    taskMap["done"] = false
                    db.collection("students").document(userId)
                        .collection("tasks")
                        .add(taskMap)
                        .addOnSuccessListener { loadTasks() }
                        .addOnFailureListener { Toast.makeText(this, "Failed to add task", Toast.LENGTH_SHORT).show() }
                } else {
                    // Edit Existing Task
                    db.collection("students").document(userId)
                        .collection("tasks").document(taskId)
                        .update(taskMap)
                        .addOnSuccessListener { loadTasks() }
                        .addOnFailureListener { Toast.makeText(this, "Failed to update task", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Function para kumuha at magpakita ng tasks
    private fun loadTasks() {
        val userId = auth.currentUser?.uid ?: return
        taskContainer.removeAllViews() // Clear existing views

        db.collection("students").document(userId)
            .collection("tasks")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val taskId = doc.id
                    val title = doc.getString("title") ?: ""
                    val details = doc.getString("details") ?: ""
                    val date = doc.getString("date") ?: ""
                    val time = doc.getString("time") ?: ""
                    val done = doc.getBoolean("done") ?: false // Kunin ang 'done' status

                    // -------------------------
                    // Dynamically create View elements (as before)
                    // -------------------------
                    val container = LinearLayout(this)
                    container.orientation = LinearLayout.VERTICAL
                    container.setPadding(20, 20, 20, 20)
                    container.setBackgroundColor(0xFFEFEFEF.toInt()) // Light gray background
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 0, 16)
                    container.layoutParams = lp

                    val tvTitle = TextView(this).apply { text = "Title: $title"; textSize = 18f }
                    val tvDetails = TextView(this).apply { text = "Details: $details" }
                    val tvDateTime = TextView(this).apply { text = "Date: $date  Time: $time" }
                    val cbDone = CheckBox(this).apply { text = "Mark as Done"; isChecked = done } // Set checkbox state

                    val btnEdit = Button(this).apply { text = "Edit" }
                    val btnDelete = Button(this).apply { text = "Delete" }

                    val btnLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(btnEdit)
                        addView(btnDelete)
                    }

                    container.addView(tvTitle)
                    container.addView(tvDetails)
                    container.addView(tvDateTime)
                    container.addView(cbDone)
                    container.addView(btnLayout)

                    // -------------------------
                    // Styling for Done Tasks
                    // -------------------------
                    if (done) {
                        // Mag-apply ng strike-through sa text
                        tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        tvDetails.paintFlags = tvDetails.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        tvDateTime.paintFlags = tvDateTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        container.setBackgroundColor(0xFFDDDDDD.toInt()) // Mas darker na gray
                    }


                    taskContainer.addView(container)

                    // -------------------------
                    // Task Listeners (Actions)
                    // -------------------------

                    // FIX: Update 'done' status, HINDI I-DELETE
                    cbDone.setOnCheckedChangeListener { _, isChecked ->
                        val doneMap = hashMapOf("done" to isChecked)
                        db.collection("students").document(userId)
                            .collection("tasks").document(taskId)
                            .update(doneMap as Map<String, Any>)
                            .addOnSuccessListener {
                                // I-reload ang tasks para makita ang updated style/status
                                loadTasks()
                            }
                    }

                    btnEdit.setOnClickListener {
                        showAddEditTaskDialog(taskId, title, details, date, time)
                    }

                    btnDelete.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("Delete Task")
                            .setMessage("Are you sure you want to delete this task?")
                            .setPositiveButton("Delete") { _, _ ->
                                db.collection("students").document(userId)
                                    .collection("tasks").document(taskId)
                                    .delete()
                                    .addOnSuccessListener { loadTasks() }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading tasks: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
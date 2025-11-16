package com.example.datadomeapp.student

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.StudentSubject
import com.example.datadomeapp.models.ClassAssignment
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentFullScheduleActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlScheduleMatrix: TableLayout
    private lateinit var tvScheduleStatus: TextView

    private var studentId: String? = null

    // Days of the week in order
    private val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val daysFullName = mapOf(
        "Mon" to "Monday",
        "Tue" to "Tuesday",
        "Wed" to "Wednesday",
        "Thu" to "Thursday",
        "Fri" to "Friday",
        "Sat" to "Saturday"
    )

    // Time slots for the matrix with 30-minute intervals (7:00 AM to 7:00 PM)
    private val timeSlots = generateTimeSlots()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_full_schedule)

        tlScheduleMatrix = findViewById(R.id.tlScheduleMatrix)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)

        studentId = intent.getStringExtra("USER_ID")

        if (studentId.isNullOrEmpty()) {
            tvScheduleStatus.text = "Error: Student ID not found"
            return
        }

        loadFullSchedule()
    }

    private fun generateTimeSlots(): List<String> {
        val slots = mutableListOf<String>()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        val calendar = Calendar.getInstance()

        // Set start time to 7:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        // Generate slots from 7:00 AM to 7:00 PM (12 hours = 24 slots)
        for (i in 0 until 24) {
            slots.add(timeFormat.format(calendar.time))
            calendar.add(Calendar.MINUTE, 30) // Add 30 minutes
        }

        return slots
    }

    private fun loadFullSchedule() {
        tvScheduleStatus.text = "Loading your weekly schedule..."
        tvScheduleStatus.visibility = View.VISIBLE

        // Clear existing rows
        if (tlScheduleMatrix.childCount > 0) {
            tlScheduleMatrix.removeAllViews()
        }

        // Step 1: Get student subjects
        firestore.collection("students")
            .document(studentId!!)
            .collection("subjects")
            .get()
            .addOnSuccessListener { studentSnapshot ->

                val studentSubjects = studentSnapshot.documents.mapNotNull {
                    it.toObject(StudentSubject::class.java)
                }

                val assignmentNos = studentSubjects
                    .map { it.assignmentNo }
                    .filter { it.isNotEmpty() }
                    .distinct()

                if (assignmentNos.isEmpty()) {
                    showNoScheduleMessage()
                    return@addOnSuccessListener
                }

                // Step 2: Get class assignments
                firestore.collection("classAssignments")
                    .whereIn("assignmentNo", assignmentNos)
                    .get()
                    .addOnSuccessListener { assignmentSnapshot ->

                        val classAssignments = assignmentSnapshot.documents.mapNotNull {
                            it.toObject(ClassAssignment::class.java)
                        }

                        // Step 3: Create schedule matrix
                        val weeklySchedule = buildWeeklySchedule(studentSubjects, classAssignments)

                        // Step 4: Display matrix
                        displayScheduleMatrix(weeklySchedule)

                        tvScheduleStatus.visibility = View.GONE
                    }
                    .addOnFailureListener { e ->
                        tvScheduleStatus.text = "Error loading schedule: ${e.message}"
                        Log.e("FULL_SCHEDULE", "Failed to load class assignments", e)
                    }
            }
            .addOnFailureListener { e ->
                tvScheduleStatus.text = "Error loading subjects: ${e.message}"
                Log.e("FULL_SCHEDULE", "Failed to load student subjects", e)
            }
    }

    private fun buildWeeklySchedule(
        studentSubjects: List<StudentSubject>,
        classAssignments: List<ClassAssignment>
    ): Map<String, Map<String, ScheduleCell>> {

        val weeklySchedule = mutableMapOf<String, MutableMap<String, ScheduleCell>>()

        // Initialize matrix structure
        timeSlots.forEach { timeSlot ->
            weeklySchedule[timeSlot] = mutableMapOf()
            daysOfWeek.forEach { day ->
                weeklySchedule[timeSlot]!![day] = ScheduleCell.EMPTY
            }
        }

        // Fill matrix with actual classes
        for (subject in studentSubjects) {
            val assignment = classAssignments.find { it.assignmentNo == subject.assignmentNo } ?: continue

            for (slot in assignment.scheduleSlots.values) {
                if (!daysOfWeek.contains(slot.day)) continue

                // Find all time slots that fall within this class period
                val classTimeSlots = findTimeSlotsInRange(slot.startTime, slot.endTime)

                classTimeSlots.forEach { timeSlot ->
                    weeklySchedule[timeSlot]!![slot.day] = ScheduleCell(
                        subjectCode = subject.subjectCode,
                        sectionName = slot.sectionBlock,
                        room = slot.roomLocation,
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        isCurrent = isCurrentClass(slot.day, slot.startTime, slot.endTime),
                        isUpcoming = isUpcomingClass(slot.day, slot.startTime)
                    )
                }
            }
        }

        return weeklySchedule
    }

    private fun findTimeSlotsInRange(startTime: String, endTime: String): List<String> {
        val slotsInRange = mutableListOf<String>()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

        try {
            val start = timeFormat.parse(startTime)
            val end = timeFormat.parse(endTime)

            val displayFormat = SimpleDateFormat("h:mm a", Locale.US)
            val calendar = Calendar.getInstance()

            timeSlots.forEach { slot ->
                val slotTime = displayFormat.parse(slot)
                if (slotTime in start..end) {
                    slotsInRange.add(slot)
                }
            }
        } catch (e: Exception) {
            Log.e("TIME_SLOTS", "Error parsing time: ${e.message}")
        }

        return slotsInRange
    }

    private fun displayScheduleMatrix(weeklySchedule: Map<String, Map<String, ScheduleCell>>) {
        // Create header row (Days)
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#1F3A93"))
        }

        // Add empty cell for time column header
        val timeHeader = createHeaderCell("TIME")
        timeHeader.setBackgroundColor(Color.parseColor("#1F3A93"))
        headerRow.addView(timeHeader)

        // Add day headers
        daysOfWeek.forEach { dayAbbr ->
            val dayCell = createHeaderCell("${dayAbbr}\n${daysFullName[dayAbbr]}")
            headerRow.addView(dayCell)
        }

        tlScheduleMatrix.addView(headerRow)

        // Add time slots and schedule data
        timeSlots.forEach { timeSlot ->
            val row = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )

                // Add subtle separator between hours
                if (timeSlot.endsWith(":00 AM") || timeSlot.endsWith(":00 PM")) {
                    setPadding(0, 1, 0, 0)
                }
            }

            // Time slot header - show only hours for cleaner look
            val displayTime = if (timeSlot.endsWith(":00 AM") || timeSlot.endsWith(":00 PM")) {
                timeSlot
            } else {
                "" // Empty for :30 slots
            }

            val timeCell = createTimeCell(displayTime)
            row.addView(timeCell)

            // Schedule cells for each day
            daysOfWeek.forEach { day ->
                val scheduleCell = weeklySchedule[timeSlot]!![day]!!
                val dayCell = createDayCell(scheduleCell, timeSlot)
                row.addView(dayCell)
            }

            tlScheduleMatrix.addView(row)
        }
    }

    private fun createHeaderCell(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 8)
            setBackgroundColor(Color.parseColor("#1F3A93"))
        }
    }

    private fun createTimeCell(time: String): TextView {
        return TextView(this).apply {
            this.text = time
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(2, 4, 2, 4)
            setBackgroundColor(Color.parseColor("#F8F9FA"))
            // Make time cells slightly taller for better readability
            setMinHeight(resources.getDimensionPixelSize(R.dimen.time_cell_height))
        }
    }

    private fun createDayCell(scheduleCell: ScheduleCell, timeSlot: String): TextView {
        return TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(2, 4, 2, 4)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setMinHeight(resources.getDimensionPixelSize(R.dimen.schedule_cell_height))

            when {
                scheduleCell.isCurrent -> {
                    // Current/live class - Green background
                    setBackgroundColor(Color.parseColor("#E8F5E8"))
                    setTextColor(Color.parseColor("#155724"))
                    text = "● ${scheduleCell.subjectCode}\n${scheduleCell.room}"
                }
                scheduleCell.isUpcoming -> {
                    // Upcoming class - Yellow background
                    setBackgroundColor(Color.parseColor("#FFF3CD"))
                    setTextColor(Color.parseColor("#856404"))
                    text = "${scheduleCell.subjectCode}\n${scheduleCell.room}"
                }
                scheduleCell != ScheduleCell.EMPTY -> {
                    // Regular class - Light blue background
                    setBackgroundColor(Color.parseColor("#E3F2FD"))
                    setTextColor(Color.parseColor("#0D47A1"))

                    // Show full info only for the first time slot of the class
                    if (isFirstTimeSlotOfClass(scheduleCell, timeSlot)) {
                        text = "${scheduleCell.subjectCode}\n${scheduleCell.room}\n${scheduleCell.startTime}-${scheduleCell.endTime}"
                    } else {
                        text = "┃" // Vertical line to show class continuation
                    }
                }
                else -> {
                    // Empty slot - White background
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.parseColor("#999999"))
                    text = ""
                }
            }
        }
    }

    private fun isFirstTimeSlotOfClass(scheduleCell: ScheduleCell, currentTimeSlot: String): Boolean {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        return try {
            val classStart = timeFormat.parse("${scheduleCell.startTime}")
            val currentSlot = timeFormat.parse(currentTimeSlot)
            currentSlot == classStart
        } catch (e: Exception) {
            true // Fallback to showing full info
        }
    }

    private fun isCurrentClass(day: String, startTime: String, endTime: String): Boolean {
        val currentDay = SimpleDateFormat("EEE", Locale.US).format(Date())
        if (currentDay != day) return false

        return isTimeInRange(startTime, endTime)
    }

    private fun isUpcomingClass(day: String, startTime: String): Boolean {
        val currentDay = SimpleDateFormat("EEE", Locale.US).format(Date())
        if (currentDay != day) return false

        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val currentTime = timeFormat.format(Date())

        return startTime > currentTime
    }

    private fun isTimeInRange(startTime: String, endTime: String): Boolean {
        try {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val current = timeFormat.format(Date())
            val currentTime = timeFormat.parse(current)
            val start = timeFormat.parse(startTime)
            val end = timeFormat.parse(endTime)

            return currentTime in start..end
        } catch (e: Exception) {
            return false
        }
    }

    private fun showNoScheduleMessage() {
        tvScheduleStatus.text = "No schedule found for the current semester.\nPlease check your enrollment or contact administration."
    }

    // Data class to represent a schedule cell
    data class ScheduleCell(
        val subjectCode: String = "",
        val sectionName: String = "",
        val room: String = "",
        val startTime: String = "",
        val endTime: String = "",
        val isCurrent: Boolean = false,
        val isUpcoming: Boolean = false
    ) {
        companion object {
            val EMPTY = ScheduleCell()
        }
    }
}
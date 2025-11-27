package com.example.datadomeapp.teacher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.TimeSlot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

class TeacherScheduleMatrixActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlScheduleMatrix: TableLayout
    private lateinit var tvScheduleStatus: TextView
    private lateinit var mainLayout: LinearLayout
    private lateinit var weekDaysContainer: LinearLayout
    private lateinit var timelineContainer: LinearLayout
    private lateinit var selectedDayText: TextView
    private lateinit var currentDateText: TextView
    private var selectedDay: String = "Mon"

    // Days of the week in order (Monday to Saturday only)
    private val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val daysFullName = mapOf(
        "Mon" to "Monday",
        "Tue" to "Tuesday",
        "Wed" to "Wednesday",
        "Thu" to "Thursday",
        "Fri" to "Friday",
        "Sat" to "Saturday"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_daily_schedule)

        setupViews()
        loadTeacherSchedule()
    }

    private fun setupViews() {
        mainLayout = findViewById(R.id.mainLayout)
        tlScheduleMatrix = findViewById(R.id.tlScheduleMatrix)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        weekDaysContainer = findViewById(R.id.weekDaysContainer)
        timelineContainer = findViewById(R.id.timelineContainer)
        selectedDayText = findViewById(R.id.selectedDayText)
        currentDateText = findViewById(R.id.currentDateText)

        // Set current date and initial selected day
        updateCurrentDate()
        updateSelectedDayDisplay()
    }

    private fun updateCurrentDate() {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
        val currentDate = dateFormat.format(Date())
        currentDateText.text = currentDate

        // Auto-select current day
        val currentDay = SimpleDateFormat("EEE", Locale.US).format(Date())
        selectedDay = when (currentDay) {
            "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" -> currentDay
            else -> "Mon" // Default to Monday if it's Sunday
        }
    }

    private fun loadTeacherSchedule() {
        val currentTeacherUid = auth.currentUser?.uid
        if (currentTeacherUid == null) {
            showErrorMessage("Error: Teacher not logged in")
            return
        }

        showLoading("Loading your weekly schedule...")

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", currentTeacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val classAssignments = snapshot.documents.mapNotNull {
                    it.toObject(ClassAssignment::class.java)?.copy(assignmentNo = it.id)
                }

                if (classAssignments.isEmpty()) {
                    showNoScheduleMessage()
                    return@addOnSuccessListener
                }

                // Create daily schedule
                val dailySchedule = buildDailySchedule(classAssignments)

                // Display weekly schedule
                displayWeeklySchedule(dailySchedule)
                hideLoading()
            }
            .addOnFailureListener { e ->
                showErrorMessage("Error loading schedule: ${e.message}")
                Log.e("TEACHER_SCHEDULE", "Failed to load class assignments", e)
            }
    }

    private fun buildDailySchedule(classAssignments: List<ClassAssignment>): Map<String, List<DailyEvent>> {
        val dailyEvents = mutableMapOf<String, MutableList<DailyEvent>>()

        // Initialize with empty lists for each day
        daysOfWeek.forEach { day ->
            dailyEvents[day] = mutableListOf()
        }

        // Fill with actual classes
        for (assignment in classAssignments) {
            for (slot in assignment.scheduleSlots.values) {
                if (!daysOfWeek.contains(slot.day)) continue

                val event = DailyEvent(
                    subjectCode = assignment.subjectCode,
                    subjectTitle = assignment.subjectTitle,
                    sectionName = slot.sectionBlock,
                    room = slot.roomLocation,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    teacherName = assignment.teacherName ?: "N/A",
                    isCurrent = isCurrentClass(slot.day, slot.startTime, slot.endTime),
                    isUpcoming = isUpcomingClass(slot.day, slot.startTime),
                    color = getEventColor(assignment.subjectCode),
                    assignment = assignment,
                    timeSlot = slot
                )

                dailyEvents[slot.day]!!.add(event)
            }
        }

        // Sort events by start time for each day
        dailyEvents.values.forEach { events ->
            events.sortBy { it.startTime }
        }

        return dailyEvents
    }

    private fun displayWeeklySchedule(dailySchedule: Map<String, List<DailyEvent>>) {
        // Create week days selector
        createWeekDaysSelector(dailySchedule)

        // Update selected day display
        updateSelectedDayDisplay()

        // Display timeline for selected day
        displayTimelineForSelectedDay(dailySchedule)
    }

    private fun createWeekDaysSelector(dailySchedule: Map<String, List<DailyEvent>>) {
        weekDaysContainer.removeAllViews()

        daysOfWeek.forEachIndexed { index, day ->
            val dayContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                }
                gravity = Gravity.CENTER
                setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
                background = ContextCompat.getDrawable(this@TeacherScheduleMatrixActivity, R.drawable.day_selector_background)

                // Set click listener
                setOnClickListener {
                    selectedDay = day
                    updateDaySelection()
                    updateSelectedDayDisplay()
                    displayTimelineForSelectedDay(dailySchedule)
                }
            }

            val dayText = TextView(this).apply {
                text = day
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.text_secondary))
                setTypeface(typeface, android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
            }

            // Show dot indicator if day has classes
            val hasClasses = dailySchedule[day]?.isNotEmpty() == true
            if (hasClasses) {
                val dotIndicator = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(6),
                        dpToPx(6)
                    ).apply {
                        setMargins(0, dpToPx(4), 0, 0)
                    }
                    setBackgroundColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.gold))
                }
                dayContainer.addView(dotIndicator)
            }

            dayContainer.addView(dayText)
            weekDaysContainer.addView(dayContainer)
        }

        // Update selection after creating all views
        updateDaySelection()
    }

    private fun updateDaySelection() {
        for (i in 0 until weekDaysContainer.childCount) {
            val dayContainer = weekDaysContainer.getChildAt(i) as LinearLayout
            val dayText = dayContainer.getChildAt(if (dayContainer.childCount > 1) 1 else 0) as TextView
            val day = daysOfWeek[i]

            if (day == selectedDay) {
                dayContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_red))
                dayText.setTextColor(ContextCompat.getColor(this, R.color.white))
                dayText.setTypeface(dayText.typeface, android.graphics.Typeface.BOLD)
            } else {
                dayContainer.setBackgroundResource(R.drawable.day_selector_background)
                dayText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                dayText.setTypeface(dayText.typeface, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun updateSelectedDayDisplay() {
        val fullDayName = daysFullName[selectedDay] ?: selectedDay
        selectedDayText.text = fullDayName
    }

    private fun displayTimelineForSelectedDay(dailySchedule: Map<String, List<DailyEvent>>) {
        timelineContainer.removeAllViews()

        val selectedDayEvents = dailySchedule[selectedDay] ?: emptyList()

        // TIME COLUMN - Fixed height to match events column
        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(70),
                calculateTotalTimelineHeight()
            )
            setBackgroundColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.white))
        }

        // Add hourly time labels (7:00 AM to 7:00 PM)
        for (hour in 7..19) {
            val timeText = when {
                hour == 12 -> "12:00 PM"
                hour > 12 -> "${hour - 12}:00 PM"
                else -> "$hour:00 AM"
            }

            val timeCell = TextView(this).apply {
                text = timeText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(60) // Each hour slot is 60dp tall
                ).apply {
                    gravity = Gravity.TOP
                }
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            timeColumn.addView(timeCell)
        }

        timelineContainer.addView(timeColumn)

        // EVENTS COLUMN - Fixed height
        val eventsColumn = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                calculateTotalTimelineHeight(),
                1f
            )
            setBackgroundColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.background_light))
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        // Add hourly lines
        addTimeSlotLines(eventsColumn)

        // Add event views
        selectedDayEvents.forEach { event ->
            val eventView = createEventView(event)
            eventsColumn.addView(eventView)
        }

        // Show message if no classes for selected day
        if (selectedDayEvents.isEmpty()) {
            val noClassesText = TextView(this).apply {
                text = "No classes scheduled for $selectedDay"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.text_secondary))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(200)
                }
            }
            eventsColumn.addView(noClassesText)
        }

        timelineContainer.addView(eventsColumn)
    }

    private fun calculateTotalTimelineHeight(): Int {
        // From 7:00 AM to 7:00 PM = 12 hours = 720 minutes
        // Using 1dp per minute = 720dp total height
        return dpToPx(780)
    }

    private fun addTimeSlotLines(container: FrameLayout) {
        // Add lines for each hour from 7:00 AM to 7:00 PM (13 lines total)
        for (i in 0..12) {
            val line = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    1
                ).apply {
                    topMargin = i * dpToPx(60) // Each hour line (60 minutes = 60dp)
                }
                setBackgroundColor(ContextCompat.getColor(this@TeacherScheduleMatrixActivity, R.color.card_stroke_color))
            }
            container.addView(line)
        }
    }

    private fun createEventView(event: DailyEvent): LinearLayout {
        val topPosition = calculateTopPosition(event.startTime)
        val height = calculateEventHeight(event.startTime, event.endTime)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(height.toInt())
            ).apply {
                setMargins(dpToPx(2), dpToPx(topPosition.toInt()), dpToPx(2), 0)
            }
            setBackgroundColor(event.color)
            setPadding(dpToPx(8))
            elevation = dpToPx(2).toFloat()

            // Add border for current class
            if (event.isCurrent) {
                background = ContextCompat.getDrawable(this@TeacherScheduleMatrixActivity, R.drawable.current_class_border)
            }

            // Make event clickable
            setOnClickListener {
                showEventDetails(event)
            }

            // Event title
            val titleText = TextView(this@TeacherScheduleMatrixActivity).apply {
                text = event.subjectCode
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            // Event time
            val timeText = TextView(this@TeacherScheduleMatrixActivity).apply {
                text = "${event.startTime} - ${event.endTime}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.WHITE)
                setPadding(0, dpToPx(2), 0, 0)
            }

            // Section and room information
            val sectionRoomText = TextView(this@TeacherScheduleMatrixActivity).apply {
                text = "${event.sectionName} • ${event.room}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.WHITE)
                setPadding(0, dpToPx(2), 0, 0)
            }

            addView(titleText)
            addView(timeText)
            addView(sectionRoomText)
        }
    }

    private fun showEventDetails(event: DailyEvent) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(event.subjectCode)
            .setMessage(
                "Subject: ${event.subjectTitle}\n" +
                        "Time: ${event.startTime} - ${event.endTime}\n" +
                        "Room: ${event.room}\n" +
                        "Section: ${event.sectionName}\n" +
                        "Teacher: ${event.teacherName}"
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Class Details") { dialog, _ ->
                navigateToClassDetails(event)
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun navigateToClassDetails(event: DailyEvent) {
        val intent = Intent(this, ClassDetailsActivity::class.java)
        intent.putExtra("ASSIGNMENT_ID", event.assignment?.assignmentNo)
        intent.putExtra("CLASS_NAME", "${event.subjectTitle} - ${event.sectionName}")
        intent.putExtra("SUBJECT_CODE", event.subjectCode)
        startActivity(intent)
    }

    private fun calculateTopPosition(startTime: String): Float {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        return try {
            val start = timeFormat.parse(startTime)
            val calendar = Calendar.getInstance().apply { time = start }

            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val amPm = calendar.get(Calendar.AM_PM)

            // Convert to 24-hour format for easier calculation
            var hour24 = hour
            if (amPm == Calendar.PM && hour24 != 12) {
                hour24 += 12
            } else if (amPm == Calendar.AM && hour24 == 12) {
                hour24 = 0
            }

            val totalMinutes = hour24 * 60 + minute
            val startMinutes = 7 * 60 // 7:00 AM = 420 minutes

            // Calculate minutes from 7:00 AM and convert to dp (1dp per minute)
            val minutesFromStart = totalMinutes - startMinutes

            val offset = 10f

            // Ensure position is within visible range
            (minutesFromStart.toFloat() + offset).coerceIn(0f, 720f)
        } catch (e: Exception) {
            Log.e("TIME_CALC", "Error calculating top position for $startTime: ${e.message}")
            0f
        }
    }

    private fun calculateEventHeight(startTime: String, endTime: String): Float {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        return try {
            val start = timeFormat.parse(startTime)
            val end = timeFormat.parse(endTime)

            val durationMillis = end.time - start.time
            val durationMinutes = durationMillis / (1000 * 60)

            // Return duration in minutes (1dp per minute)
            durationMinutes.toFloat().coerceAtLeast(30f) // Minimum height of 30 minutes
        } catch (e: Exception) {
            Log.e("TIME_CALC", "Error calculating event height: ${e.message}")
            60f // Default height (1 hour)
        }
    }

    private fun getEventColor(subjectCode: String): Int {
        // Generate consistent color based on subject code using red palette
        val colors = listOf(
            ContextCompat.getColor(this, R.color.primary_red),
            ContextCompat.getColor(this, R.color.primary_red_dark),
            ContextCompat.getColor(this, R.color._9b1c1f),
            ContextCompat.getColor(this, R.color.color_absent),
            ContextCompat.getColor(this, R.color.red_700),
            ContextCompat.getColor(this, R.color.status_cheating),
            ContextCompat.getColor(this, R.color.status_expired),
            ContextCompat.getColor(this, R.color.design_default_color_error),
            ContextCompat.getColor(this, R.color.poor_red)
        )
        val index = subjectCode.hashCode().absoluteValue % colors.size
        return colors[index]
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

    // Helper methods for status messages
    private fun showLoading(message: String) {
        tvScheduleStatus.text = message
        tvScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvScheduleStatus.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        tvScheduleStatus.visibility = View.GONE
    }

    private fun showErrorMessage(message: String) {
        tvScheduleStatus.text = message
        tvScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.poor_red))
        tvScheduleStatus.visibility = View.VISIBLE
    }

    private fun showNoScheduleMessage() {
        tvScheduleStatus.text = "No classes scheduled for the current semester."
        tvScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvScheduleStatus.visibility = View.VISIBLE
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    data class DailyEvent(
        val subjectCode: String = "",
        val subjectTitle: String = "",
        val sectionName: String = "",
        val room: String = "",
        val startTime: String = "",
        val endTime: String = "",
        val teacherName: String = "",
        val isCurrent: Boolean = false,
        val isUpcoming: Boolean = false,
        val color: Int = Color.BLUE,
        val assignment: ClassAssignment? = null,
        val timeSlot: TimeSlot? = null
    )
}
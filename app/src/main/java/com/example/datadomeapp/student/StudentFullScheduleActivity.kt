package com.example.datadomeapp.student

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
import com.example.datadomeapp.models.StudentSubject
import com.example.datadomeapp.models.ClassAssignment
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

class StudentFullScheduleActivity : AppCompatActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlScheduleMatrix: TableLayout
    private lateinit var tvScheduleStatus: TextView
    private lateinit var mainLayout: LinearLayout
    private lateinit var weekDaysContainer: LinearLayout
    private lateinit var timelineContainer: LinearLayout
    private lateinit var selectedDayText: TextView
    private lateinit var currentDateText: TextView
    private var studentId: String? = null
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

    // Time slots for the day view (7:00 AM to 7:00 PM)
    private val timeSlots = generateTimeSlots()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_daily_schedule)

        setupViews()
        studentId = intent.getStringExtra("USER_ID")

        if (studentId.isNullOrEmpty()) {
            tvScheduleStatus.text = "Error: Student ID not found"
            tvScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.poor_red))
            return
        }

        loadFullSchedule()
    }

    private fun setupViews() {
        mainLayout = findViewById(R.id.mainLayout)
        tlScheduleMatrix = findViewById(R.id.tlScheduleMatrix)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        weekDaysContainer = findViewById(R.id.weekDaysContainer)
        timelineContainer = findViewById(R.id.timelineContainer)
        selectedDayText = findViewById(R.id.selectedDayText)
        currentDateText = findViewById(R.id.currentDateText)

        // Set current date
        updateCurrentDate()
    }

    private fun updateCurrentDate() {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
        val currentDate = dateFormat.format(Date())
        currentDateText.text = currentDate
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

                        // Step 3: Create daily schedule
                        val dailySchedule = buildDailySchedule(studentSubjects, classAssignments)

                        // Step 4: Display weekly schedule
                        displayWeeklySchedule(dailySchedule)
                        tvScheduleStatus.visibility = View.GONE
                    }
                    .addOnFailureListener { e ->
                        tvScheduleStatus.text = "Error loading schedule: ${e.message}"
                        tvScheduleStatus.setTextColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.poor_red))
                        Log.e("WEEKLY_SCHEDULE", "Failed to load class assignments", e)
                    }
            }
            .addOnFailureListener { e ->
                tvScheduleStatus.text = "Failed to laod subjects."
                tvScheduleStatus.setTextColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.poor_red))
                Log.e("WEEKLY_SCHEDULE", "Failed to load student subjects", e)
            }
    }

    private fun buildDailySchedule(
        studentSubjects: List<StudentSubject>,
        classAssignments: List<ClassAssignment>
    ): Map<String, List<DailyEvent>> {
        val dailyEvents = mutableMapOf<String, MutableList<DailyEvent>>()

        // Initialize with empty lists for each day
        daysOfWeek.forEach { day ->
            dailyEvents[day] = mutableListOf()
        }

        // Fill with actual classes
        for (subject in studentSubjects) {
            val assignment = classAssignments.find {
                it.assignmentNo == subject.assignmentNo
            } ?: continue

            for (slot in assignment.scheduleSlots.values) {
                if (!daysOfWeek.contains(slot.day)) continue

                val event = DailyEvent(
                    subjectCode = subject.subjectCode,
                    subjectTitle = subject.subjectTitle,
                    sectionName = slot.sectionBlock,
                    room = slot.roomLocation,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    teacherName = subject.teacherName,
                    isCurrent = isCurrentClass(slot.day, slot.startTime, slot.endTime),
                    isUpcoming = isUpcomingClass(slot.day, slot.startTime),
                    color = getEventColor(subject.subjectCode)
                )

                dailyEvents[slot.day]!!.add(event)
            }
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
                background = ContextCompat.getDrawable(this@StudentFullScheduleActivity, R.drawable.day_selector_background)

                // Set click listener
                setOnClickListener {
                    selectedDay = day
                    updateDaySelection()
                    updateSelectedDayDisplay()
                    displayTimelineForSelectedDay(dailySchedule)
                }

                // Initial selection
                if (day == selectedDay) {
                    setBackgroundColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.primary_red))
                }
            }

            val dayText = TextView(this).apply {
                text = day
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (day == selectedDay) ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.white)
                else ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.text_secondary))
                setTypeface(typeface, if (day == selectedDay) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
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
                    setBackgroundColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.gold))
                }
                dayContainer.addView(dotIndicator)
            }

            dayContainer.addView(dayText)
            weekDaysContainer.addView(dayContainer)
        }
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

        // TIME COLUMN
        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(70),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.white))
        }

        // Add time labels
        timeSlots.forEach { time ->
            val timeCell = TextView(this).apply {
                text = if (time.endsWith(":00 AM") || time.endsWith(":00 PM")) time else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.text_secondary))
                setPadding(dpToPx(4), dpToPx(28), dpToPx(4), dpToPx(28))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_HORIZONTAL
            }
            timeColumn.addView(timeCell)
        }

        timelineContainer.addView(timeColumn)

        // EVENTS COLUMN
        val eventsColumn = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setBackgroundColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.background_light))
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        // Add time slot lines
        addTimeSlotLines(eventsColumn)

        // Add event views
        selectedDayEvents.forEach { event ->
            val eventView = createEventView(event)
            eventsColumn.addView(eventView)
        }

        timelineContainer.addView(eventsColumn)
    }

    private fun addTimeSlotLines(container: FrameLayout) {
        for (i in 0..timeSlots.size) {
            val line = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    1
                ).apply {
                    topMargin = i * dpToPx(60)
                }
                setBackgroundColor(ContextCompat.getColor(this@StudentFullScheduleActivity, R.color.card_stroke_color))
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
                background = ContextCompat.getDrawable(this@StudentFullScheduleActivity, R.drawable.current_class_border)
            }

            // Make event clickable
            setOnClickListener {
                showEventDetails(event)
            }

            // Event title
            val titleText = TextView(this@StudentFullScheduleActivity).apply {
                text = event.subjectCode
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            // Event time
            val timeText = TextView(this@StudentFullScheduleActivity).apply {
                text = "${event.startTime} - ${event.endTime}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.WHITE)
                setPadding(0, dpToPx(2), 0, 0)
            }

            // Room information
            val roomText = TextView(this@StudentFullScheduleActivity).apply {
                text = event.room
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.WHITE)
                setPadding(0, dpToPx(2), 0, 0)
            }

            addView(titleText)
            addView(timeText)
            addView(roomText)
        }
    }

    private fun showEventDetails(event: DailyEvent) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(event.subjectCode)
            .setMessage(
                "Subject: ${event.subjectTitle}\n" +
                        "Time: ${event.startTime} - ${event.endTime}\n" +
                        "Room: ${event.room}\n" +
                        "Teacher: ${event.teacherName}\n" +
                        "Section: ${event.sectionName}"
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.show()
    }

    private fun calculateTopPosition(startTime: String): Float {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        return try {
            val start = timeFormat.parse(startTime)
            val calendar = Calendar.getInstance().apply { time = start }
            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val isPM = calendar.get(Calendar.AM_PM) == Calendar.PM

            // Convert to 24-hour format for calculation
            var totalHours = hour + if (isPM) 12 else 0
            if (totalHours == 12 && !isPM) totalHours = 0 // Handle 12 AM
            if (totalHours == 24) totalHours = 12 // Handle 12 PM

            val totalMinutes = totalHours * 60 + minute
            val startMinutes = 7 * 60 // Calendar starts at 7:00 AM

            ((totalMinutes - startMinutes) / 60.0f) * dpToPx(60) // 60 pixels per hour
        } catch (e: Exception) {
            0f
        }
    }

    private fun calculateEventHeight(startTime: String, endTime: String): Float {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        return try {
            val start = timeFormat.parse(startTime)
            val end = timeFormat.parse(endTime)
            val duration = end.time - start.time
            val hours = duration / (1000 * 60 * 60).toFloat()
            hours * dpToPx(60) // 60 pixels per hour
        } catch (e: Exception) {
            dpToPx(60).toFloat() // Default height (1 hour)
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

    private fun showNoScheduleMessage() {
        tvScheduleStatus.text = "No schedule found for the current semester.\nPlease check your enrollment or contact administration."
        tvScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
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
        val color: Int = Color.BLUE
    )
}
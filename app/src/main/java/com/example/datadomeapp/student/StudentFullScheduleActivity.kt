package com.example.datadomeapp.student

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
// Import BOTH models
import com.example.datadomeapp.models.ScheduleItem
import com.example.datadomeapp.models.StudentSubject
import com.google.firebase.firestore.FirebaseFirestore

class StudentFullScheduleActivity : AppCompatActivity() {

    private val TAG = "ScheduleActivity"

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var spinnerDaySelector: Spinner
    private lateinit var tvScheduleStatus: TextView
    private lateinit var llScheduleContainer: LinearLayout

    private lateinit var currentStudentUserId: String

    // ✅ FINAL TARGET MODEL: Gagamitin na ang bago mong ScheduleItem
    private var allScheduleItems: List<ScheduleItem> = emptyList()
    private var uniqueTimeSlots: List<String> = emptyList()

    private val weekDaysList = arrayListOf(
        "Buong Linggo",
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_full_schedule)

        spinnerDaySelector = findViewById(R.id.spinnerDaySelector)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        llScheduleContainer = findViewById(R.id.llScheduleContainer)

        currentStudentUserId = intent.getStringExtra("USER_ID") ?: run {
            Log.e(TAG, "FATAL: No USER_ID passed via Intent. Using placeholder 'DDS-0007'.")
            "DDS-0007"
        }

        setupDaySelector()
        fetchAndStoreAllScheduleData()
    }

    private fun fetchAndStoreAllScheduleData() {
        tvScheduleStatus.text = "Loading personal schedule..."
        tvScheduleStatus.visibility = View.VISIBLE
        llScheduleContainer.removeAllViews()
        Log.d(TAG, "Fetching data from path: students/${currentStudentUserId}/subjects")

        firestore.collection("students")
            .document(currentStudentUserId)
            .collection("subjects")
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Firestore query successful. Documents found: ${snapshot.size()}")

                // 1. Basahin muna ang RAW data gamit ang StudentSubject model
                val allStudentSubjects = snapshot.documents
                    .mapNotNull { it.toObject(StudentSubject::class.java) }

                // 2. I-parse at i-map sa bago mong ScheduleItem model
                allScheduleItems = allStudentSubjects.mapNotNull { studentSubject ->
                    studentSubject.schedule.let { scheduleString ->
                        val parsed = parseScheduleString(scheduleString)

                        if (parsed == null) {
                            Log.e(TAG, "Failed to parse schedule string: ${scheduleString} for subject ${studentSubject.subjectCode}")
                            return@mapNotNull null
                        }

                        val (day, start, end) = parsed

                        Log.d(TAG, "Parsed: ${studentSubject.subjectCode} -> Day: $day, Time: $start-$end")

                        // ✅ MAPPING: I-map ang StudentSubject fields sa bago mong ScheduleItem
                        ScheduleItem(
                            subjectCode = studentSubject.subjectCode,
                            subjectTitle = studentSubject.subjectTitle,
                            sectionId = studentSubject.sectionName, // Mapped sectionName to sectionId
                            startTime = start,
                            endTime = end,
                            dayOfWeek = day,
                            venue = studentSubject.sectionName, // Ginagamit ang sectionName bilang Venue
                            instructor = studentSubject.teacherName // Mapped teacherName to instructor
                            // semester: Pwede ring kunin mula sa ibang document kung mayroon
                        )
                    }
                }
                    .sortedWith(compareBy<ScheduleItem> {
                        weekDaysList.indexOf(it.dayOfWeek)
                    }.thenBy {
                        it.startTime
                    })

                // NEW: Kunin ang unique time slots para sa Matrix columns
                if (allScheduleItems.isNotEmpty()) {
                    uniqueTimeSlots = allScheduleItems
                        .map { "${it.startTime}-${it.endTime}" }
                        .distinct()
                        .sorted()
                    Log.d(TAG, "Unique Time Slots generated: $uniqueTimeSlots")
                }

                Log.d(TAG, "Final Schedule Items loaded and parsed: ${allScheduleItems.size}")

                if (allScheduleItems.isEmpty()) {
                    tvScheduleStatus.text = "🎉 Walang schedule data na nakita sa iyong subjects!"
                    return@addOnSuccessListener
                }

                displaySchedule(weekDaysList.first())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching schedule: Failed to connect or read from Firestore.", e)
                tvScheduleStatus.text = "Error loading schedule: ${e.localizedMessage}"
                tvScheduleStatus.visibility = View.VISIBLE
            }
    }

    // ----------------------------------------------------
    // HELPER FUNCTION: Parsing the schedule string
    // ----------------------------------------------------
    private fun parseScheduleString(schedule: String): Triple<String, String, String>? {
        val parts = schedule.split(" ")
        if (parts.size < 2) return null

        val dayAbbreviation = parts[0]
        val timeParts = parts[1].split("-")

        if (timeParts.size < 2) return null

        val fullDay = when (dayAbbreviation.toLowerCase()) {
            "mon" -> "Monday"
            "tue" -> "Tuesday"
            "wed" -> "Wednesday"
            "thu" -> "Thursday"
            "fri" -> "Friday"
            "sat" -> "Saturday"
            in weekDaysList.map { it.toLowerCase() } -> dayAbbreviation
            else -> {
                Log.e(TAG, "Unknown day format: $dayAbbreviation")
                return null
            }
        }

        val startTime = timeParts[0]
        val endTime = timeParts[1]

        return Triple(fullDay, startTime, endTime)
    }

    // ----------------------------------------------------
    // DISPLAY LOGIC: Matrix View
    // ----------------------------------------------------

    private fun setupDaySelector() {
        // ... (same as before)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, weekDaysList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDaySelector.adapter = adapter

        spinnerDaySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = weekDaysList[position]
                displaySchedule(selectedOption)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun displaySchedule(selectedDay: String) {
        llScheduleContainer.removeAllViews()
        tvScheduleStatus.visibility = View.GONE

        if (allScheduleItems.isEmpty()) {
            tvScheduleStatus.text = "Walang schedule data."
            tvScheduleStatus.visibility = View.VISIBLE
            return
        }

        if (selectedDay == weekDaysList.first()) {
            loadFullWeeklyScheduleMatrix()
        } else {
            loadSingleDayScheduleList(selectedDay)
        }
    }

    // ----------------------------------------------------
    // Matrix View for "Buong Linggo"
    // ----------------------------------------------------
    private fun loadFullWeeklyScheduleMatrix() {
        if (uniqueTimeSlots.isEmpty()) {
            tvScheduleStatus.text = "🎉 Walang schedule sa linggong ito!"
            tvScheduleStatus.visibility = View.VISIBLE
            return
        }

        val tableLayout = TableLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            for (i in 0..uniqueTimeSlots.size) {
                setColumnStretchable(i, true)
            }
            isShrinkAllColumns = false
            setBackgroundColor(Color.GRAY)
            setPadding(1, 1, 1, 1)
        }

        tableLayout.addView(createMatrixHeaderRow())

        val weekDaysOrder = weekDaysList.subList(1, weekDaysList.size)
        val groupedSchedule = allScheduleItems.groupBy { it.dayOfWeek }

        var hasClasses = false
        weekDaysOrder.forEach { day ->
            val dayClasses = groupedSchedule[day] ?: emptyList()
            val dayRow = createMatrixRow(day, dayClasses)
            tableLayout.addView(dayRow)
            if (dayClasses.isNotEmpty()) hasClasses = true
        }

        llScheduleContainer.addView(tableLayout)
        if (!hasClasses) {
            tvScheduleStatus.text = "🎉 Walang schedule sa linggong ito!"
            tvScheduleStatus.visibility = View.VISIBLE
        }
    }

    private fun createMatrixHeaderRow(): TableRow {
        val headerRow = TableRow(this).apply { setBackgroundColor(Color.WHITE) }

        // Unang Cell: Corner
        headerRow.addView(TextView(this@StudentFullScheduleActivity).apply {
            text = "DAY"
            setPadding(8, 8, 8, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        })

        // Time Slot Cells
        uniqueTimeSlots.forEach { slot ->
            headerRow.addView(TextView(this@StudentFullScheduleActivity).apply {
                text = slot
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(8, 8, 8, 8)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
            })
        }
        return headerRow
    }

    private fun createMatrixRow(day: String, classes: List<ScheduleItem>): TableRow {
        val row = TableRow(this).apply { setBackgroundColor(Color.WHITE) }

        // Unang Cell: Day Header
        row.addView(TextView(this).apply {
            text = day.substring(0, 3) // Shorten to 3 letters
            setPadding(8, 16, 8, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        })

        // Ikalawa hanggang Huling Cell: Time Slot Cells
        val classesMap = classes.associateBy { "${it.startTime}-${it.endTime}" }

        uniqueTimeSlots.forEach { slot ->
            val subject = classesMap[slot]
            val cell = TextView(this).apply {
                if (subject != null) {
                    // Gamit ang bago mong fields: subjectCode at venue
                    text = "${subject.subjectCode}\n${subject.venue}"
                    setBackgroundColor(Color.parseColor("#C3E6CB")) // Light Green
                    setTextColor(Color.BLACK)
                } else {
                    text = ""
                    setBackgroundColor(Color.WHITE)
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(8, 16, 8, 16)
                gravity = Gravity.CENTER
                setSingleLine(false)
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.MATCH_PARENT
                ).apply { setMargins(1, 1, 1, 1) }
            }
            row.addView(cell)
        }

        return row
    }

    // ----------------------------------------------------
    // List View for Single Day (Fallback for Single Day selection)
    // ----------------------------------------------------
    private fun loadSingleDayScheduleList(day: String) {
        val classes = allScheduleItems.filter { it.dayOfWeek == day }

        if (classes.isNotEmpty()) {
            llScheduleContainer.addView(createDayHeader(day))
            llScheduleContainer.addView(createDayTable(classes))
        } else {
            tvScheduleStatus.text = "🎉 Walang klase tuwing $day!"
            tvScheduleStatus.visibility = View.VISIBLE
        }
    }

    // --- DISPLAY HELPER FUNCTIONS (Para sa List View) ---

    private fun createDayHeader(day: String): TextView {
        return TextView(this).apply {
            text = day.toUpperCase()
            textSize = 18f
            setPadding(8, 16, 8, 4)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setTextColor(Color.parseColor("#3F51B5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createDayTable(classes: List<ScheduleItem>): TableLayout {
        val tableLayout = TableLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setColumnStretchable(0, true)
            setColumnStretchable(1, true)
            setBackgroundColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }

        tableLayout.addView(createTableHeader())

        classes.forEach { item ->
            tableLayout.addView(createScheduleRow(item))
        }

        return tableLayout
    }

    private fun createTableHeader(): TableRow {
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }

        val headerTitles = arrayOf("TIME", "SUBJECT / SECTION")
        val weights = floatArrayOf(1f, 1.5f)

        headerTitles.forEachIndexed { index, title ->
            val tvHeader = TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(8, 8, 8, 8)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.START
                setTextColor(Color.parseColor("#607D8B"))
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weights[index])
            }
            headerRow.addView(tvHeader)
        }
        return headerRow
    }

    private fun createScheduleRow(item: ScheduleItem): TableRow {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 10, 0, 10)
        }

        val tvTime = TextView(this).apply {
            text = "${item.startTime} - ${item.endTime}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.START
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvSubject = TextView(this).apply {
            text = "${item.subjectCode} - ${item.subjectTitle}\nSec: ${item.sectionId} (Room: ${item.venue})" // Gamit ang sectionId at venue
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.START
            setTextColor(Color.parseColor("#333333"))
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        row.addView(tvTime)
        row.addView(tvSubject)
        return row
    }
}
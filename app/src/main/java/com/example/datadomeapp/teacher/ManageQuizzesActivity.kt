package com.example.datadomeapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.BaseActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuizAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.datadomeapp.models.ClassDisplayDetails
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ManageQuizzesActivity : BaseActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddQuiz: Button
    private lateinit var tvNoQuizzes: TextView
    private lateinit var progressBarLoading: ProgressBar
    private var currentAssignmentId: String? = null
    private var currentClassName: String? = null
    private val quizList = mutableListOf<Quiz>()
    private val displayedQuizList = mutableListOf<Quiz>()
    private lateinit var adapter: QuizAdapter
    private lateinit var tabFilter: com.google.android.material.tabs.TabLayout

    private val classDetailsMap = mutableMapOf<String, ClassDisplayDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_quizzes)

        tabFilter = findViewById(R.id.tabFilter)
        tabFilter.addTab(tabFilter.newTab().setText("All"))
        tabFilter.addTab(tabFilter.newTab().setText("Exams"))
        tabFilter.addTab(tabFilter.newTab().setText("Quizzes"))

        tabFilter.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                filterQuizzes(tab.text.toString())
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}

            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // Also apply filter again when reselecting the same tab
                filterQuizzes(tab?.text.toString())
            }
        })

        progressBarLoading = findViewById(R.id.progressBarLoading)
        currentAssignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        currentClassName = intent.getStringExtra("CLASS_NAME")

        recyclerView = findViewById(R.id.recyclerViewQuizzes)
        btnAddQuiz = findViewById(R.id.btnAddQuiz)
        tvNoQuizzes = findViewById(R.id.tvNoQuizzes)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuizAdapter(
            quizzes = displayedQuizList,
            classDetailsMap = classDetailsMap,
            editClickListener = { quiz -> openQuizEditor(quiz) },
            deleteClickListener = { quiz -> deleteQuiz(quiz) },
            publishClickListener = { quiz -> togglePublish(quiz) },
            setTimeClickListener = { quiz -> setScheduledTime(quiz) },
            viewClickListener = { quiz -> viewQuizResults(quiz) }
        )
        recyclerView.adapter = adapter

        btnAddQuiz.setOnClickListener { openQuizEditor(null) }

        loadAllClassDetails(auth.currentUser?.uid ?: "")
        loadQuizzes()
    }

    private fun loadAllClassDetails(teacherUid: String) {
        if (teacherUid.isEmpty()) return

        firestore.collection("classAssignments")
            .whereEqualTo("teacherUid", teacherUid)
            .get()
            .addOnSuccessListener { snapshot ->
                classDetailsMap.clear()
                for (doc in snapshot.documents) {
                    val assignmentId = doc.id
                    val subjectTitle = doc.getString("subjectTitle") ?: "N/A Subject"
                    val subjectCode = doc.getString("subjectCode") ?: "N/A"
                    val yearLevelRaw = doc.getString("yearLevel") ?: "N/A"
                    val courseCodeRaw = doc.getString("courseCode") ?: "N/A"
                    var sectionBlock = doc.getString("section") ?: ""

                    val scheduleSlots = doc.get("scheduleSlots") as? Map<*, *>
                    if (scheduleSlots != null && scheduleSlots.isNotEmpty()) {
                        val firstSlotMap = scheduleSlots.values.first() as? Map<String, Any>
                        sectionBlock = (firstSlotMap?.get("sectionBlock") as? String) ?: sectionBlock
                    }

                    val yearNumber = yearLevelRaw.filter { it.isDigit() }.ifEmpty { "N/A" }
                    val courseCode = courseCodeRaw.uppercase(Locale.ROOT)
                    val constructedSectionId = "$subjectCode|$courseCode|$yearNumber|$sectionBlock"

                    val details = ClassDisplayDetails(
                        sectionId = constructedSectionId,
                        subjectTitle = subjectTitle
                    )
                    classDetailsMap[assignmentId] = details
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun filterQuizzes(filter: String) {
        displayedQuizList.clear()
        displayedQuizList.addAll(
            when (filter) {
                "Exams" -> quizList.filter { it.quizType.equals("Exam", ignoreCase = true) }
                "Quizzes" -> quizList.filter { it.quizType.equals("Quiz", ignoreCase = true) }
                else -> quizList
            }
        )
        adapter.notifyDataSetChanged()
        toggleEmptyMessage(displayedQuizList)
    }


    override fun getLoadingProgressBar(): ProgressBar? = progressBarLoading

    private fun loadQuizzes() {
        val teacherUid = auth.currentUser?.uid ?: return
        val assignmentIdToFilter = currentAssignmentId
        showLoading()

        val isGlobalView = assignmentIdToFilter.isNullOrEmpty()

        db.child("quizzes").orderByChild("teacherUid").equalTo(teacherUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    quizList.clear()

                    for (child in snapshot.children) {
                        val quizId = child.child("quizId").getValue(String::class.java) ?: continue
                        val assignmentId = child.child("assignmentId").getValue(String::class.java) ?: ""

                        if (assignmentIdToFilter != null && assignmentId != assignmentIdToFilter) continue
                        if (isGlobalView) {
                            val isPublished = child.child("isPublished").getValue(Boolean::class.java) ?: false
                            val scheduledDateTime = child.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
                            val scheduledEndDateTime = child.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L

                            val tempQuiz = Quiz(
                                quizId = quizId,
                                assignmentId = assignmentId,
                                teacherUid = teacherUid,
                                title = "",
                                questions = emptyList(),
                                isPublished = isPublished,
                                scheduledDateTime = scheduledDateTime,
                                scheduledEndDateTime = scheduledEndDateTime
                            )
                            if (!isQuizOngoing(tempQuiz) && !isQuizFinished(tempQuiz)) continue
                        }

                        val title = child.child("title").getValue(String::class.java) ?: ""
                        val description = child.child("description").getValue(String::class.java) ?: ""
                        val isPublished = child.child("isPublished").getValue(Boolean::class.java) ?: false
                        val scheduledDateTime = child.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
                        val scheduledEndDateTime = child.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L
                        val quizType = child.child("quizType").getValue(String::class.java) ?: "Quiz"

                        val quiz = Quiz(
                            quizId = quizId,
                            assignmentId = assignmentId,
                            teacherUid = teacherUid,
                            title = title,
                            description = description,
                            questions = emptyList(),
                            isPublished = isPublished,
                            scheduledDateTime = scheduledDateTime,
                            scheduledEndDateTime = scheduledEndDateTime,
                            quizType = quizType
                        )
                        quizList.add(quiz)
                    }

                    sortQuizzes(quizList)
                    val currentTabText = tabFilter.getTabAt(tabFilter.selectedTabPosition)?.text.toString()
                    filterQuizzes(currentTabText)

                    toggleEmptyMessage(displayedQuizList)

                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(
                        this@ManageQuizzesActivity,
                        "Failed to load quizzes",
                        Toast.LENGTH_SHORT
                    ).show()
                    toggleEmptyMessage(displayedQuizList)
                }
            })
    }

    private fun getQuizStatusValue(quiz: Quiz): Int {
        return when {
            isQuizOngoing(quiz) -> 5        // 🔝 ONGOING → pinakamataas
            quiz.isPublished && quiz.scheduledDateTime > 0L && !isQuizOngoing(quiz) && !isQuizFinished(quiz) -> 4 // Published upcoming
            !quiz.isPublished && quiz.scheduledDateTime > 0L -> 3 // Draft / Unpublished with time
            !quiz.isPublished && quiz.scheduledDateTime == 0L -> 2 // Draft / Unpublished no time
            isQuizFinished(quiz) -> 1       // 🏁 FINISHED → lowest
            else -> 0                        // fallback / no status
        }
    }

    private fun sortQuizzes(list: MutableList<Quiz>) {
        list.sortWith(compareByDescending<Quiz> { getQuizStatusValue(it) }
            .thenBy { quiz ->
                val status = getQuizStatusValue(quiz)
                if (status == 4 || status == 3) quiz.scheduledDateTime else Long.MAX_VALUE
            }
            .thenByDescending { quiz ->
                if (getQuizStatusValue(quiz) == 2) quiz.scheduledEndDateTime else 0L
            })
    }

    private fun deleteQuiz(quiz: Quiz) {
        if (isQuizOngoing(quiz)) {
            Toast.makeText(this, "Cannot delete an ongoing quiz.", Toast.LENGTH_LONG).show()
            return
        }
        if (isQuizFinished(quiz)) {
            Toast.makeText(this, "Cannot delete a finished quiz.", Toast.LENGTH_LONG).show()
            return
        }

        db.child("quizzes").child(quiz.quizId).removeValue()
            .addOnSuccessListener { Toast.makeText(this, "Quiz deleted", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, "Failed to delete quiz", Toast.LENGTH_SHORT).show() }
    }

    private fun togglePublish(quiz: Quiz) {
        if (isQuizOngoing(quiz)) {
            Toast.makeText(this, "Cannot change publish status while ongoing.", Toast.LENGTH_LONG).show()
            return
        }

        val newStatus = !quiz.isPublished
        val quizId = quiz.quizId

        if (newStatus) {
            if (quiz.scheduledDateTime <= 0L || quiz.scheduledEndDateTime <= 0L) {
                Toast.makeText(this, "Please set valid start and end time first.", Toast.LENGTH_LONG).show()
                setScheduledTime(quiz, true)
                return
            }
            db.child("quizzes").child(quizId).child("isPublished").setValue(true)
                .addOnSuccessListener { Toast.makeText(this, "Quiz Published", Toast.LENGTH_SHORT).show() }
        } else {
            db.child("quizzes").child(quizId).child("isPublished").setValue(false)
                .addOnSuccessListener {
                    val updates = mapOf("scheduledDateTime" to 0L, "scheduledEndDateTime" to 0L)
                    db.child("quizzes").child(quizId).updateChildren(updates)
                    Toast.makeText(this, "Quiz Unpublished", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setScheduledTime(quiz: Quiz, isCalledAfterPublish: Boolean = false) {
        if (isQuizOngoing(quiz) || isQuizFinished(quiz)) {
            Toast.makeText(this, "Cannot update schedule for an ongoing or finished quiz.", Toast.LENGTH_LONG).show()
            return
        }

        val scheduleCalendar = Calendar.getInstance()
        if (quiz.scheduledDateTime > 0) scheduleCalendar.timeInMillis = quiz.scheduledDateTime

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                scheduleCalendar.set(year, month, day)
                showStartTimePicker(quiz, scheduleCalendar, isCalledAfterPublish)
            },
            scheduleCalendar.get(Calendar.YEAR),
            scheduleCalendar.get(Calendar.MONTH),
            scheduleCalendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun showStartTimePicker(quiz: Quiz, scheduleCalendar: Calendar, isCalledAfterPublish: Boolean) {
        val timePicker = TimePickerDialog(
            this,
            { _, startHour, startMinute ->
                scheduleCalendar.set(Calendar.HOUR_OF_DAY, startHour)
                scheduleCalendar.set(Calendar.MINUTE, startMinute)
                val startTime = scheduleCalendar.timeInMillis
                val currentTime = System.currentTimeMillis()
                if (startTime < currentTime + 300000L) {
                    Toast.makeText(this, "Start time must be at least 5 minutes from now.", Toast.LENGTH_LONG).show()
                    showStartTimePicker(quiz, scheduleCalendar, isCalledAfterPublish)
                    return@TimePickerDialog
                }
                showEndTimePicker(quiz, scheduleCalendar, startTime, isCalledAfterPublish)
            },
            scheduleCalendar.get(Calendar.HOUR_OF_DAY),
            scheduleCalendar.get(Calendar.MINUTE),
            false
        )
        timePicker.setTitle("SET START TIME")
        timePicker.show()
    }

    private fun showEndTimePicker(quiz: Quiz, scheduleCalendar: Calendar, startTime: Long, isCalledAfterPublish: Boolean) {
        val endCalendar = scheduleCalendar.clone() as Calendar
        val MIN_INTERVAL_MS = 5 * 60 * 1000L

        endCalendar.timeInMillis = startTime + 60 * 60 * 1000L

        val timePicker = TimePickerDialog(
            this,
            { _, endHour, endMinute ->
                endCalendar.set(Calendar.HOUR_OF_DAY, endHour)
                endCalendar.set(Calendar.MINUTE, endMinute)
                val endTime = endCalendar.timeInMillis
                if (endTime <= startTime) {
                    Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_LONG).show()
                    showEndTimePicker(quiz, scheduleCalendar, startTime, isCalledAfterPublish)
                    return@TimePickerDialog
                }
                if (endTime - startTime < MIN_INTERVAL_MS) {
                    Toast.makeText(this, "Quiz duration must be at least 5 minutes.", Toast.LENGTH_LONG).show()
                    showEndTimePicker(quiz, scheduleCalendar, startTime, isCalledAfterPublish)
                    return@TimePickerDialog
                }

                val updates = mutableMapOf<String, Any>(
                    "scheduledDateTime" to startTime,
                    "scheduledEndDateTime" to endTime
                )

                if (isCalledAfterPublish || quiz.isPublished) updates["isPublished"] = true

                db.child("quizzes").child(quiz.quizId).updateChildren(updates)
                    .addOnSuccessListener {
                        val startSdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                        val endSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val successMessage = "Quiz schedule set:\n${startSdf.format(Date(startTime))} - ${endSdf.format(Date(endTime))}"
                        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
                    }
            },
            endCalendar.get(Calendar.HOUR_OF_DAY),
            endCalendar.get(Calendar.MINUTE),
            false
        )
        timePicker.setTitle("SET END TIME")
        timePicker.show()
    }

    private fun isQuizFinished(quiz: Quiz): Boolean {
        val currentTime = System.currentTimeMillis()
        val endTime = quiz.scheduledEndDateTime
        return quiz.isPublished && endTime > 0L && currentTime > endTime
    }

    private fun isQuizOngoing(quiz: Quiz): Boolean {
        val currentTime = System.currentTimeMillis()
        val startTime = quiz.scheduledDateTime
        val endTime = quiz.scheduledEndDateTime
        return quiz.isPublished && startTime > 0L && endTime > 0L && currentTime in startTime..endTime
    }

    private fun openQuizEditor(quiz: Quiz?) {
        if (quiz != null && (isQuizOngoing(quiz) || isQuizFinished(quiz))) {
            viewQuizResults(quiz)
            return
        }

        if (currentAssignmentId.isNullOrEmpty() && quiz == null) {
            Toast.makeText(this, "Please select a specific class to create a new quiz.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, CreateQuizActivity::class.java)
        intent.putExtra("ASSIGNMENT_ID", currentAssignmentId)
        intent.putExtra("CLASS_NAME", currentClassName)
        quiz?.let { intent.putExtra("QUIZ_ID", it.quizId) }
        startActivity(intent)
    }

    private fun viewQuizResults(quiz: Quiz) {
        // 1. Ihanda ang data para sa Result Dashboard
        val isOngoing = isQuizOngoing(quiz)
        val status = if (isOngoing) "Live View" else "View Results"

        // 2. Ipakita ang Toast
        Toast.makeText(this, "Opening $status for: ${quiz.title}", Toast.LENGTH_SHORT).show()

        // 3. I-launch ang TAMANG activity
        val intent = Intent(this, QuizMonitoringActivity::class.java).apply {
            putExtra("QUIZ_ID", quiz.quizId)
            putExtra("ASSIGNMENT_ID", quiz.assignmentId)
            putExtra("IS_ONGOING", isOngoing)
            putExtra("SCHEDULED_END_DATE_TIME", quiz.scheduledEndDateTime)
            putExtra("QUIZ_TITLE", quiz.title)
            putExtra("QUIZ_TYPE", quiz.quizType)
        }
        startActivity(intent)
    }

    private fun toggleEmptyMessage(listToCheck: List<Quiz> = displayedQuizList) {
        if (listToCheck.isEmpty()) {
            val currentTabText = tabFilter.getTabAt(tabFilter.selectedTabPosition)?.text.toString()
            tvNoQuizzes.text = when (currentTabText) {
                "Quizzes" -> "No Quizzes yet."
                "Exams" -> "No Exams yet."
                else -> "No Quizzes or Exams yet."
            }
            fadeIn(tvNoQuizzes)
            recyclerView.visibility = View.GONE
        } else {
            fadeOut(tvNoQuizzes)
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun fadeIn(view: View) {
        view.visibility = View.VISIBLE
        val fade = AlphaAnimation(0f, 1f)
        fade.duration = 300
        view.startAnimation(fade)
    }

    private fun fadeOut(view: View) {
        val fade = AlphaAnimation(1f, 0f)
        fade.duration = 300
        fade.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                view.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        view.startAnimation(fade)
    }
}

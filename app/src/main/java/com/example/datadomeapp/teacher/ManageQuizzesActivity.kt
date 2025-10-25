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
    private lateinit var adapter: QuizAdapter

    private val classDetailsMap = mutableMapOf<String, ClassDisplayDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_quizzes)

        progressBarLoading = findViewById(R.id.progressBarLoading)

        currentAssignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        currentClassName = intent.getStringExtra("CLASS_NAME")


        recyclerView = findViewById(R.id.recyclerViewQuizzes)
        btnAddQuiz = findViewById(R.id.btnAddQuiz)
        tvNoQuizzes = findViewById(R.id.tvNoQuizzes)

        if (currentAssignmentId.isNullOrEmpty()) {
            // Kung Global View, itago ang Add Quiz button
            btnAddQuiz.visibility = View.GONE
        } else {
            // Kung Class View, tiyaking visible ito (default na ito, pero mas maganda kung explicit)
            btnAddQuiz.visibility = View.VISIBLE
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuizAdapter(
            quizzes = quizList,
            classDetailsMap = classDetailsMap,
            editClickListener = { quiz -> openQuizEditor(quiz) },
            deleteClickListener = { quiz -> deleteQuiz(quiz) },
            publishClickListener = { quiz -> togglePublish(quiz) },
            setTimeClickListener = { quiz -> setScheduledTime(quiz)
            }
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

                    // --- PAGKUHA NG RAW DATA ---
                    var subjectTitle = doc.getString("subjectTitle") ?: "N/A Subject"
                    val subjectCode = doc.getString("subjectCode") ?: "N/A"
                    val yearLevelRaw = doc.getString("yearLevel") ?: "N/A"
                    val couseCodeRow = doc.getString("courseCode") ?: "N/A"
                    var sectionBlock = doc.getString("section") ?: ""

                    // Kukunin ang Section Block mula sa slot kung mas detalyado
                    val scheduleSlots = doc.get("scheduleSlots") as? Map<*, *>
                    if (scheduleSlots != null && scheduleSlots.isNotEmpty()) {
                        val firstSlotMap = scheduleSlots.values.first() as? Map<String, Any>
                        sectionBlock = (firstSlotMap?.get("sectionBlock") as? String) ?: sectionBlock
                    }

                    // --- ASSEMBLE NEW sectionId STRING ---

                    // 1. I-normalize ang Year Level (E.g., "1st Year" -> "1")
                    val yearNumber = yearLevelRaw.filter { it.isDigit() }.ifEmpty { "N/A" }

                    // 2. FIX: I-assume natin na ang Course Code ay ang mga letters ng Subject Code.
                    // **Kailangan mong palitan ang logic na ito kung alam mo kung saan kukunin ang totoong BSIT/BSED, etc.**
                    val courseCode = couseCodeRow.uppercase(Locale.ROOT)

                    // 3. I-construct ang Section String.
                    // Format: SUBJECTCODE|COURSECODE|YEAR|SECTION (Gamit ang '|' bilang separator)
                    val constructedSectionId = "$subjectCode|$courseCode|$yearNumber|$sectionBlock"
                    // --- END OF NEW FETCHING LOGIC ---

                    val details = ClassDisplayDetails(
                        sectionId = constructedSectionId,
                        subjectTitle = subjectTitle
                    )
                    classDetailsMap[assignmentId] = details
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                // Handle error
            }
    }

    override fun getLoadingProgressBar(): ProgressBar? = progressBarLoading

// -------------------------------------------------------------------------------------

    private fun loadQuizzes() {
        val teacherUid = auth.currentUser?.uid ?: return
        val assignmentIdToFilter = currentAssignmentId
        showLoading()

        val isGlobalView = assignmentIdToFilter.isNullOrEmpty()

        // 1. I-query ang LAHAT ng quizzes ng teacher.
        db.child("quizzes").orderByChild("teacherUid").equalTo(teacherUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()

                    quizList.clear()
                    for (child in snapshot.children) {
                        val quizId = child.child("quizId").getValue(String::class.java) ?: continue

                        // 🎯 CRITICAL FIX: I-extract ang assignmentId mula sa database
                        val assignmentId = child.child("assignmentId").getValue(String::class.java) ?: ""

                        // 2. Client-Side Filtering: I-skip ang quiz na hindi tugma sa current class
                        if (assignmentIdToFilter != null && assignmentId != assignmentIdToFilter) {
                            continue
                        }

                        if (isGlobalView) {
                            // Kailangan muna i-construct ang Quiz object para ma-check ang status
                            val isPublished = child.child("isPublished").getValue(Boolean::class.java) ?: false
                            val scheduledDateTime = child.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
                            val scheduledEndDateTime = child.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L

                            val tempQuiz = Quiz(
                                quizId = quizId,
                                assignmentId = assignmentId,
                                teacherUid = teacherUid,
                                title = "", // Hindi kailangan ang title para sa status check
                                questions = emptyList(),
                                isPublished = isPublished,
                                scheduledDateTime = scheduledDateTime,
                                scheduledEndDateTime = scheduledEndDateTime
                            )

                            // Haharangin ang Drafts at Scheduled (hindi pa Ongoing o Finished)
                            if (!isQuizOngoing(tempQuiz) && !isQuizFinished(tempQuiz)) {
                                continue
                            }
                        }

                        // Kung nakapasa sa filter, ituloy ang pag-construct ng Quiz object
                        val title = child.child("title").getValue(String::class.java) ?: ""
                        val isPublished = child.child("isPublished").getValue(Boolean::class.java) ?: false
                        val scheduledDateTime = child.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
                        val scheduledEndDateTime = child.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L

                        val quiz = Quiz(
                            quizId = quizId,
                            assignmentId = assignmentId,
                            teacherUid = teacherUid,
                            title = title,
                            questions = emptyList(),
                            isPublished = isPublished,
                            scheduledDateTime = scheduledDateTime,
                            scheduledEndDateTime = scheduledEndDateTime
                        )
                        quizList.add(quiz)
                    }

                    // ✅ I-sort ang listahan gamit ang bagong logic
                    sortQuizzes(quizList)

                    adapter.notifyDataSetChanged()
                    toggleEmptyMessage()
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(
                        this@ManageQuizzesActivity,
                        "Failed to load quizzes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }


    /**
     * Nagre-return ng numerical value para sa sorting hierarchy:
     * Mas mataas na number = Mas mataas na priority.
     * 5: Ongoing | 4: Scheduled (Published) | 3: Draft (May Set Time) | 2: Finished | 1: Draft (Walang Time)
     */
    private fun getQuizStatusValue(quiz: Quiz): Int {
        // 1. ONGOING (Pinakamataas)
        if (isQuizOngoing(quiz)) {
            return 5
        }

        // 2. SCHEDULED (Published at may time, pero hindi pa ongoing)
        if (quiz.isPublished && quiz.scheduledDateTime > 0L) {
            return 4
        }

        // 3. DRAFT NA MAY TIME SET (Hindi Published, pero may time)
        if (!quiz.isPublished && quiz.scheduledDateTime > 0L) {
            return 3
        }

        // 4. FINISHED (Tapos na)
        if (isQuizFinished(quiz)) {
            return 2
        }

        // 5. DRAFT / UNPUBLISHED (Walang schedule at hindi published)
        return 1
    }

    /**
     * Nagsasagawa ng multi-level sorting.
     */
    private fun sortQuizzes(list: MutableList<Quiz>) {
        list.sortWith(compareByDescending<Quiz> { quiz ->
            getQuizStatusValue(quiz)
        }.thenBy { quiz ->
            // Secondary sort (Status 4 & 3 - Scheduled/Draft w/ Time): by Start Time (Ascending)
            val status = getQuizStatusValue(quiz)
            if (status == 4 || status == 3) {
                quiz.scheduledDateTime // Pinakamalapit na schedule ang mauna
            } else {
                Long.MAX_VALUE
            }
        }.thenByDescending { quiz ->
            // Tertiary sort (Status 2 - Finished): by End Time (Descending)
            if (getQuizStatusValue(quiz) == 2) {
                quiz.scheduledEndDateTime // Pinakahuling natapos ang mauna
            } else {
                0L
            }
        })
    }

// -------------------------------------------------------------------------------------

    private fun deleteQuiz(quiz: Quiz) {

        // 1. CHECK: Block delete for Ongoing Quiz
        if (isQuizOngoing(quiz)) {
            Toast.makeText(this, "Cannot delete an ongoing quiz.", Toast.LENGTH_LONG).show()
            return
        }

        // 2. CHECK: Block delete for Finished Quiz
        if (isQuizFinished(quiz)) {
            Toast.makeText(this, "Cannot delete a finished quiz. You may unpublish it instead to archive the results.", Toast.LENGTH_LONG).show()
            return
        }

        // If safe to delete, proceed
        db.child("quizzes").child(quiz.quizId).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to delete quiz",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

// -------------------------------------------------------------------------------------

    private fun togglePublish(quiz: Quiz) {

        if (isQuizOngoing(quiz)) {
            Toast.makeText(this, "Cannot change publish status while the quiz is ongoing.", Toast.LENGTH_LONG).show()
            return
        }

        val newStatus = !quiz.isPublished
        val quizId = quiz.quizId

        if (newStatus == true) { // PUBLISH
            // CHECK: Kailangang may schedule para mag-publish
            if (quiz.scheduledDateTime <= 0L || quiz.scheduledEndDateTime <= 0L) {
                Toast.makeText(this, "Please set a valid start and end time to publish the quiz.", Toast.LENGTH_LONG).show()
                setScheduledTime(quiz, true) // Force schedule and auto-publish after successful set
                return
            }

            // May schedule na, direkta nang i-publish
            db.child("quizzes").child(quizId).child("isPublished").setValue(true)
                .addOnSuccessListener {
                    Toast.makeText(this, "Quiz Published", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to publish quiz", Toast.LENGTH_SHORT).show()
                }

        } else { // UNPUBLISH
            db.child("quizzes").child(quizId).child("isPublished").setValue(false)
                .addOnSuccessListener {
                    Toast.makeText(this, "Quiz Unpublished", Toast.LENGTH_SHORT).show()
                    if (quiz.scheduledDateTime > 0L) {
                        val updates = mapOf(
                            "scheduledDateTime" to 0L,
                            "scheduledEndDateTime" to 0L
                        )
                        db.child("quizzes").child(quizId).updateChildren(updates)
                            .addOnSuccessListener {
                                // Schedule cleared notification handled by main Toast
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update publish status", Toast.LENGTH_SHORT).show()
                }
        }
    }

// -------------------------------------------------------------------------------------

    private fun setScheduledTime(quiz: Quiz, isCalledAfterPublish: Boolean = false) {
        // ... (existing checks for ongoing/finished/published status)
        if (isQuizOngoing(quiz) || isQuizFinished(quiz)) {
            Toast.makeText(this, "Cannot update schedule for an ongoing or finished quiz.", Toast.LENGTH_LONG).show()
            return
        }

        // ✅ INIBA: Tanggalin ang pag-check ng isPublished dito at ilipat sa bandang huli
        // Ang teacher ay pwedeng mag-set ng time kahit Draft pa lang, pero ia-update natin ang message.
        // I-check na lang kung hindi pa published, at maglagay ng reminder.

        val scheduleCalendar = Calendar.getInstance()
        if (quiz.scheduledDateTime > 0) {
            scheduleCalendar.timeInMillis = quiz.scheduledDateTime
        }

        // --- 1. Date Picker ---
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                scheduleCalendar.set(year, month, day)
                // Pwede nang direkta tumawag sa showStartTimePicker
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
                if (startTime < currentTime + 600000L) {
                    Toast.makeText(this, "Start time must be at least 10 minutes from the current time.", Toast.LENGTH_LONG).show()
                    showStartTimePicker(quiz, scheduleCalendar, isCalledAfterPublish)
                    return@TimePickerDialog
                }

                showEndTimePicker(quiz, scheduleCalendar, startTime, isCalledAfterPublish)
            },
            scheduleCalendar.get(Calendar.HOUR_OF_DAY),
            scheduleCalendar.get(Calendar.MINUTE),
            false
        )
        // ✅ Dito idadagdag ang Title
        timePicker.setTitle("SET START TIME")
        timePicker.show()
    }

    private fun showEndTimePicker(quiz: Quiz, scheduleCalendar: Calendar, startTime: Long, isCalledAfterPublish: Boolean) {

        val endCalendar = scheduleCalendar.clone() as Calendar
        val MIN_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes in milliseconds

        if (quiz.scheduledEndDateTime > 0) {
            endCalendar.timeInMillis = quiz.scheduledEndDateTime
            endCalendar.set(Calendar.YEAR, scheduleCalendar.get(Calendar.YEAR))
            endCalendar.set(Calendar.MONTH, scheduleCalendar.get(Calendar.MONTH))
            endCalendar.set(
                Calendar.DAY_OF_MONTH,
                scheduleCalendar.get(Calendar.DAY_OF_MONTH)
            )
        } else {
            // Default to 1 hour after start time, or 5 mins if start time is default
            endCalendar.timeInMillis = startTime + 60 * 60 * 1000L
        }

        val timePicker = TimePickerDialog(
            this,
            { _, endHour, endMinute ->
                endCalendar.set(Calendar.HOUR_OF_DAY, endHour)
                endCalendar.set(Calendar.MINUTE, endMinute)
                val endTime = endCalendar.timeInMillis

                // ✅ VALIDATION 1: End time must be after Start time (immediately)
                if (endTime <= startTime) {
                    Toast.makeText(
                        this,
                        "End time must be after start time.",
                        Toast.LENGTH_LONG
                    ).show()
                    showEndTimePicker(quiz, scheduleCalendar, startTime, isCalledAfterPublish)
                    return@TimePickerDialog
                }

                // ✅ VALIDATION 2: Minimum 5-minute interval
                val interval = endTime - startTime
                if (interval < MIN_INTERVAL_MS) {
                    Toast.makeText(
                        this,
                        "Quiz duration must be at least 5 minutes.",
                        Toast.LENGTH_LONG
                    ).show()
                    showEndTimePicker(quiz, scheduleCalendar, startTime, isCalledAfterPublish)
                    return@TimePickerDialog
                }

                // 4. Save Start, End Time, and potentially Publish status to Firebase
                val updates = mutableMapOf<String, Any>(
                    "scheduledDateTime" to startTime,
                    "scheduledEndDateTime" to endTime
                )

                if (isCalledAfterPublish || quiz.isPublished) {
                    updates["isPublished"] = true
                }

                // ✅ I-check kung draft pa rin at hindi pinilit mag-publish (Hindi isCalledAfterPublish)
                if (!quiz.isPublished && !isCalledAfterPublish) {
                    Toast.makeText(this, "Time set successfully! Please Publish the quiz to activate the schedule.", Toast.LENGTH_LONG).show()
                }

                db.child("quizzes").child(quiz.quizId)
                    .updateChildren(updates)
                    .addOnSuccessListener {
                        val startSdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                        val endSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

                        val action = if (isCalledAfterPublish || quiz.isPublished) "Published and scheduled" else "schedule updated"
                        val successMessage = "Quiz $action:\n${startSdf.format(Date(startTime))} - ${endSdf.format(Date(endTime))}"

                        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to set schedule: ${e.message}", Toast.LENGTH_LONG).show()
                    }

            },
            endCalendar.get(Calendar.HOUR_OF_DAY),
            endCalendar.get(Calendar.MINUTE),
            false
        )
        // ✅ Dito idadagdag ang Title
        timePicker.setTitle("SET END TIME")
        timePicker.show()
    }

// -------------------------------------------------------------------------------------

    private fun isQuizFinished(quiz: Quiz): Boolean {
        val currentTime = System.currentTimeMillis()
        val endTime = quiz.scheduledEndDateTime

        // Finish ang quiz kung published, may end time, at lumipas na ang end time.
        return quiz.isPublished && endTime > 0L && currentTime > endTime
    }

    private fun isQuizOngoing(quiz: Quiz): Boolean {
        val currentTime = System.currentTimeMillis()
        val startTime = quiz.scheduledDateTime
        val endTime = quiz.scheduledEndDateTime

        // Check kung ang quiz ay published, may set schedule, at nasa loob ng time frame (inclusive)
        return quiz.isPublished && startTime > 0L && endTime > 0L && currentTime >= startTime && currentTime <= endTime
    }

    private fun openQuizEditor(quiz: Quiz?) {
        if (quiz != null && (isQuizOngoing(quiz) || isQuizFinished(quiz))) {
            // VIEW MODE: Kapag Ongoing o Finished
            viewQuizResults(quiz) // Tatawag sa placeholder function
            return
        }

        if (currentAssignmentId.isNullOrEmpty() && quiz == null) {
            Toast.makeText(this, "Please select a specific class to create a new quiz.", Toast.LENGTH_SHORT).show()
            return // Haharangin ang pag-create
        }

        // EDIT MODE: Kung Draft, Scheduled, o walang quiz (Add New)
        val intent = Intent(this, CreateQuizActivity::class.java)

        intent.putExtra("ASSIGNMENT_ID", currentAssignmentId)
        intent.putExtra("CLASS_NAME", currentClassName)

        quiz?.let { intent.putExtra("QUIZ_ID", it.quizId) }
        startActivity(intent)
    }

    private fun viewQuizResults(quiz: Quiz) {
        val status = if (isQuizOngoing(quiz)) "Live View" else "View Results"
        Toast.makeText(this, "Opening $status for: ${quiz.title}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleEmptyMessage() {
        if (quizList.isEmpty()) {
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
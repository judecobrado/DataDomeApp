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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.teacher.adapters.QuizAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ManageQuizzesActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddQuiz: Button
    private lateinit var tvNoQuizzes: TextView

    private val quizList = mutableListOf<Quiz>()
    private lateinit var adapter: QuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_quizzes)

        recyclerView = findViewById(R.id.recyclerViewQuizzes)
        btnAddQuiz = findViewById(R.id.btnAddQuiz)
        tvNoQuizzes = findViewById(R.id.tvNoQuizzes)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuizAdapter(
            quizzes = quizList,
            editClickListener = { quiz -> openQuizEditor(quiz) },
            deleteClickListener = { quiz -> deleteQuiz(quiz) },
            publishClickListener = { quiz -> togglePublish(quiz) },
            setTimeClickListener = { quiz -> setScheduledTime(quiz) }
        )
        recyclerView.adapter = adapter

        btnAddQuiz.setOnClickListener { openQuizEditor(null) }

        loadQuizzes()
    }

    private fun loadQuizzes() {
        val teacherUid = auth.currentUser?.uid ?: return

        db.child("quizzes").orderByChild("teacherUid").equalTo(teacherUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    quizList.clear()
                    for (child in snapshot.children) {
                        val quizId = child.child("quizId").getValue(String::class.java) ?: continue
                        val assignmentId =
                            child.child("assignmentId").getValue(String::class.java) ?: ""
                        val title = child.child("title").getValue(String::class.java) ?: ""
                        val isPublished =
                            child.child("isPublished").getValue(Boolean::class.java) ?: false
                        val scheduledDateTime =
                            child.child("scheduledDateTime").getValue(Long::class.java) ?: 0L
                        val scheduledEndDateTime =
                            child.child("scheduledEndDateTime").getValue(Long::class.java) ?: 0L

                        // ---------------- Questions ----------------
                        val questionsSnapshot = child.child("questions")
                        val questionsList = mutableListOf<Question>()
                        for (qSnap in questionsSnapshot.children) {
                            val type = qSnap.child("type").getValue(String::class.java)
                            val questionText =
                                qSnap.child("questionText").getValue(String::class.java) ?: ""

                            when (type?.lowercase()) {
                                "tf", "truefalse" -> {
                                    val answer =
                                        qSnap.child("answer").getValue(Boolean::class.java) ?: true
                                    questionsList.add(Question.TrueFalse(questionText, answer))
                                }

                                "matching" -> {
                                    val options = qSnap.child("options")
                                        .getValue(object : GenericTypeIndicator<List<String>>() {})
                                        ?: emptyList()
                                    val matches = qSnap.child("matches")
                                        .getValue(object : GenericTypeIndicator<List<String>>() {})
                                        ?: emptyList()
                                    questionsList.add(
                                        Question.Matching(
                                            questionText,
                                            options,
                                            matches
                                        )
                                    )
                                }

                                "mc", "multiplechoice" -> {
                                    val options = qSnap.child("options")
                                        .getValue(object : GenericTypeIndicator<List<String>>() {})
                                        ?: emptyList()
                                    val correctIndex =
                                        qSnap.child("correctAnswerIndex").getValue(Int::class.java)
                                            ?: 0
                                    questionsList.add(
                                        Question.MultipleChoice(
                                            questionText,
                                            options,
                                            correctIndex
                                        )
                                    )
                                }
                            }
                        }

                        val quiz = Quiz(
                            quizId = quizId,
                            assignmentId = assignmentId,
                            teacherUid = teacherUid,
                            title = title,
                            questions = questionsList,
                            isPublished = isPublished,
                            scheduledDateTime = scheduledDateTime,
                            scheduledEndDateTime = scheduledEndDateTime
                        )
                        quizList.add(quiz)
                    }
                    adapter.notifyDataSetChanged()
                    toggleEmptyMessage()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@ManageQuizzesActivity,
                        "Failed to load quizzes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
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

    private fun openQuizEditor(quiz: Quiz?) {
        val intent = Intent(this, CreateQuizActivity::class.java)
        quiz?.let { intent.putExtra("QUIZ_ID", it.quizId) }
        startActivity(intent)
    }

    private fun deleteQuiz(quiz: Quiz) {
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

    private fun isQuizOngoing(quiz: Quiz): Boolean {
        val currentTime = System.currentTimeMillis()
        val startTime = quiz.scheduledDateTime
        val endTime = quiz.scheduledEndDateTime

        // Check kung ang quiz ay published, may set schedule, at nasa loob ng time frame (inclusive)
        return quiz.isPublished && startTime > 0L && endTime > 0L && currentTime >= startTime && currentTime <= endTime
    }


    private fun togglePublish(quiz: Quiz) {

        if (isQuizOngoing(quiz)) {
            Toast.makeText(this, "Cannot change publish status while the quiz is ongoing.", Toast.LENGTH_LONG).show()
            return
        }

        val newStatus = !quiz.isPublished

        // I-update muna ang publish status sa Firebase
        db.child("quizzes").child(quiz.quizId).child("isPublished").setValue(newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, if (newStatus) "Quiz Published" else "Quiz Unpublished", Toast.LENGTH_SHORT).show()

                if (newStatus) {
                    // KUNG NAG-PUBLISH, mag-set ng schedule
                    setScheduledTime(quiz, true)
                } else {
                    // ✅ TAMANG LOGIC: KUNG IN-UNPUBLISH, I-CLEAR ANG SCHEDULE
                    val updates = mapOf(
                        "scheduledDateTime" to 0L,
                        "scheduledEndDateTime" to 0L
                    )
                    db.child("quizzes").child(quiz.quizId).updateChildren(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Quiz schedule cleared.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to clear schedule.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update publish status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setScheduledTime(quiz: Quiz, isCalledAfterPublish: Boolean = false) {
        // 1. CHECK: Lockdown kung ongoing ang quiz.
        if (isQuizOngoing(quiz)) {
            Toast.makeText(this, "Cannot update schedule while the quiz is ongoing.", Toast.LENGTH_LONG).show()
            return
        }

        // Kung hindi galing sa 'Publish' click, at hindi pa published, mag-display ng error.
        if (!isCalledAfterPublish && !quiz.isPublished) {
            Toast.makeText(this, "Publish the quiz first before setting a schedule.", Toast.LENGTH_LONG).show()
            return
        }

        // Calendar instance to hold the selected Date and Times
        val scheduleCalendar = Calendar.getInstance()

        // 💡 TAMANG PAGBABAGO: Gamitin ang existing schedule time bilang default
        if (quiz.scheduledDateTime > 0) {
            scheduleCalendar.timeInMillis = quiz.scheduledDateTime
        }

        // ❌ TANGGALIN: Ang code para sa setTimeButton.text ay MALI dito.
        // HINDI pwede i-access ang views ng RecyclerView item sa Activity.

        // --- 1. Date Picker (Kukunin lang ang Araw) ---
        DatePickerDialog(
            // ... (Ipinagpapatuloy ang logic mo)
            // ... (Lahat ng logic mo sa Start/End Time pickers ay tama, kasama ang validation)

            this,
            { _, year, month, day ->
                scheduleCalendar.set(year, month, day)

                // --- 2. Start Time Picker (12-hour format) ---
                TimePickerDialog(
                    this,
                    { _, startHour, startMinute ->
                        scheduleCalendar.set(Calendar.HOUR_OF_DAY, startHour)
                        scheduleCalendar.set(Calendar.MINUTE, startMinute)
                        val startTime = scheduleCalendar.timeInMillis

                        // --- 3. End Time Picker (12-hour format) ---
                        val endCalendar = scheduleCalendar.clone() as Calendar

                        // 💡 PAGPAPADALI: Kung may existing end time, gamitin ito bilang default
                        if (quiz.scheduledEndDateTime > 0) {
                            endCalendar.timeInMillis = quiz.scheduledEndDateTime
                            endCalendar.set(Calendar.YEAR, scheduleCalendar.get(Calendar.YEAR))
                            endCalendar.set(Calendar.MONTH, scheduleCalendar.get(Calendar.MONTH))
                            endCalendar.set(
                                Calendar.DAY_OF_MONTH,
                                scheduleCalendar.get(Calendar.DAY_OF_MONTH)
                            )
                        } else {
                            endCalendar.add(Calendar.HOUR_OF_DAY, 1)
                        }


                        TimePickerDialog(
                            this,
                            { _, endHour, endMinute ->
                                endCalendar.set(Calendar.HOUR_OF_DAY, endHour)
                                endCalendar.set(Calendar.MINUTE, endMinute)
                                val endTime = endCalendar.timeInMillis

                                // ✅ TAMANG VALIDATION: End time must be after Start time
                                if (endTime <= startTime) {
                                    Toast.makeText(
                                        this,
                                        "End time must be after start time on the same day.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@TimePickerDialog
                                }

                                // 4. Save Start and End Time to Firebase
                                val updates = mapOf(
                                    "scheduledDateTime" to startTime,
                                    "scheduledEndDateTime" to endTime
                                )

                                db.child("quizzes").child(quiz.quizId)
                                    .updateChildren(updates)
                                    .addOnSuccessListener {
                                        // Display confirmation
                                        val startSdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                        val endSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

                                        val successMessage = "Quiz scheduled:\n" +
                                                "${startSdf.format(Date(startTime))} - ${endSdf.format(Date(endTime))}"
                                        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed to set schedule: ${e.message}", Toast.LENGTH_LONG).show()
                                    }

                            },
                            endCalendar.get(Calendar.HOUR_OF_DAY),
                            endCalendar.get(Calendar.MINUTE),
                            false
                        ).show()

                    },
                    scheduleCalendar.get(Calendar.HOUR_OF_DAY),
                    scheduleCalendar.get(Calendar.MINUTE),
                    false
                ).show()

            },
            scheduleCalendar.get(Calendar.YEAR),
            scheduleCalendar.get(Calendar.MONTH),
            scheduleCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
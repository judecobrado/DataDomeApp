package com.example.datadomeapp.student

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.text.SimpleDateFormat
import java.util.*

class StudentQuizAdapter(
    private val quizzes: MutableList<StudentQuizItem>,
    private val clickListener: (StudentQuizItem) -> Unit
) : RecyclerView.Adapter<StudentQuizAdapter.QuizViewHolder>() {

    private val sdfDateTime = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    private val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        QuizViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_student_quiz, parent, false))

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) = holder.bind(quizzes[position])


    override fun getItemCount(): Int = quizzes.size

    fun updateList(newList: List<StudentQuizItem>) {
        quizzes.clear()

        val sortedList = newList.sortedWith(compareBy<StudentQuizItem> { item ->
            val currentTime = System.currentTimeMillis()
            val studentStatus = item.studentStatus
            val isRetakeValid = studentStatus == "RETAKE_GRANTED" && item.retakeDeadline > 0L && currentTime < item.retakeDeadline

            // PRIORITY LEVELS:
            when {

                // LEVEL 1: Ongoing & Retake (PINAKA TAAS)
                isRetakeValid -> 1
                currentTime >= item.quiz.scheduledDateTime && studentStatus in setOf("NOT_STARTED", "IN_PROGRESS", "EXAM_READY") -> 2

                // LEVEL 2: Upcoming quizzes (SUNOD - by start date)
                else -> 3
            }
        }.thenBy {
            // Within same level, sort by start date (mas malapit na date mas maaga)
            it.quiz.scheduledDateTime
        })

        quizzes.addAll(sortedList)
        notifyDataSetChanged()
    }

    inner class QuizViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvQuizTitle)
        private val tvCourseInfo: TextView = itemView.findViewById(R.id.tvCourseInfo)
        private val tvTypeTag: TextView = itemView.findViewById(R.id.tvQuizTypeTag)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tvQuizSchedule)
        private val btnStartQuiz: Button = itemView.findViewById(R.id.btnStartQuiz)

        fun bind(item: StudentQuizItem) {
            val quiz = item.quiz
            tvTitle.text = quiz.title

            // COURSE INFO
            if (item.courseCode.isNotEmpty() && item.subjectTitle.isNotEmpty()) {
                tvCourseInfo.text = "${item.courseCode} - ${item.subjectTitle}"
                tvCourseInfo.visibility = View.VISIBLE
            } else {
                tvCourseInfo.visibility = View.GONE
            }

            // QUIZ TYPE TAG
            val isExam = quiz.quizType.equals("Exam", ignoreCase = true)
            tvTypeTag.text = quiz.quizType.uppercase(Locale.ROOT)
            tvTypeTag.setBackgroundColor(if (isExam) Color.parseColor("#C62828") else Color.parseColor("#1B5E20"))

            // SIMPLIFIED SCHEDULE - ACTUAL START AND END TIME ONLY
            val startTime = quiz.scheduledDateTime
            val endTime = quiz.scheduledEndDateTime

            if (startTime > 0L && endTime > 0L) {
                val scheduleText = "${sdfDateTime.format(Date(startTime))} - ${sdfTime.format(Date(endTime))}"
                tvSchedule.text = scheduleText
            } else {
                tvSchedule.text = "Schedule not set"
            }

            val currentTime = System.currentTimeMillis()
            val studentStatus = item.studentStatus
            val retakeDeadline = item.retakeDeadline
            val isRetakeValid = studentStatus == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime < retakeDeadline

            // BUTTON STATE LOGIC
            when {
                // Case 0: Not Scheduled
                startTime == 0L || endTime == 0L -> {
                    setButtonState("Not Available", false, "#757575")
                }
                // Case 1: NOT YET AVAILABLE
                currentTime < startTime -> {
                    setButtonState("Wait to Start", false, "#FF9800")
                }
                // Case 2: RETAKE/REOPEN GRANTED
                isRetakeValid -> {
                    setButtonState("START RETAKE", true, "#00C853")
                }
                // Case 3: ACCESS REVOKED
                studentStatus == "ACCESS_REVOKED" -> {
                    setButtonState("View Status", true, "#C62828")
                }
                // Case 4: ATTEMPTED/FINISHED, MISSED, or RETAKE EXPIRED
                // Case 4: ATTEMPTED/FINISHED, MISSED, or RETAKE EXPIRED
                studentStatus in setOf("COMPLETED", "TIME_EXPIRED", "CHEATING", "UNATTEMPTED_TIME_EXPIRED") ||
                        (studentStatus == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime >= retakeDeadline) -> {

                    val buttonText: String
                    val buttonColor: String
                    val isClickable: Boolean

                    // BAGONG LOGIC: Iba't ibang button text base sa status
                    buttonText = when (studentStatus) {
                        "COMPLETED" -> "View Results"
                        "TIME_EXPIRED" -> "Time Expired ⏰"
                        "CHEATING" -> "Violation Alert ⚠️"
                        "UNATTEMPTED_TIME_EXPIRED" -> "Missed Quiz ❌"
                        "RETAKE_GRANTED" -> "Retake Expired ⌛"
                        else -> "View Status"
                    }

                    // COLOR CODING
                    buttonColor = when (studentStatus) {
                        "COMPLETED" -> "#757575" // Gray - normal finished
                        "TIME_EXPIRED" -> "#FF9800" // Orange - time issue
                        "CHEATING" -> "#C62828" // Red - violation
                        "UNATTEMPTED_TIME_EXPIRED" -> "#FF9800" // Orange - missed
                        "RETAKE_GRANTED" -> "#757575" // Gray - expired retake
                        else -> "#757575"
                    }

                    // CLICKABLE ONLY FOR COMPLETED QUIZZES
                    isClickable = studentStatus == "COMPLETED"

                    setButtonState(buttonText, isClickable, buttonColor)
                }
                // Case 5: ONGOING (No status or NOT_STARTED status)
                currentTime in startTime..endTime -> {
                    setButtonState("START NOW", true, "#00C853")
                }
                else -> {
                    setButtonState("View Results", true, "#757575")
                }
            }

            // CLICK LISTENER
            if (btnStartQuiz.isEnabled) {
                btnStartQuiz.setOnClickListener {
                    clickListener(item)
                }
            } else {
                btnStartQuiz.setOnClickListener {
                    Toast.makeText(itemView.context, "This quiz was missed and cannot be started.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun setButtonState(text: String, isEnabled: Boolean, colorHex: String) {
            btnStartQuiz.apply {
                this.text = text
                this.isEnabled = isEnabled
                setBackgroundColor(Color.parseColor(colorHex))
            }
        }
    }
}
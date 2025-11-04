package com.example.datadomeapp.student

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.text.SimpleDateFormat
import java.util.*

// Tumatanggap ng List<StudentQuizItem>
class StudentQuizAdapter(
    private val quizzes: MutableList<StudentQuizItem>,
    // Tumatanggap ng StudentQuizItem sa click
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
        quizzes.addAll(newList)
        notifyDataSetChanged()
    }

    inner class QuizViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvQuizTitle)
        private val tvTypeTag: TextView = itemView.findViewById(R.id.tvQuizTypeTag)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tvQuizSchedule)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvQuizDescription)
        private val btnStartQuiz: Button = itemView.findViewById(R.id.btnStartQuiz)

        fun bind(item: StudentQuizItem) {
            val quiz = item.quiz
            tvTitle.text = quiz.title
            val isExam = quiz.quizType.equals("Exam", ignoreCase = true)
            tvTypeTag.text = quiz.quizType.uppercase(Locale.ROOT)
            tvTypeTag.setBackgroundColor(if (isExam) Color.parseColor("#C62828") else Color.parseColor("#1B5E20"))

            val currentTime = System.currentTimeMillis()
            val startTime = quiz.scheduledDateTime
            val endTime = quiz.scheduledEndDateTime

            val studentStatus = item.studentStatus
            val retakeDeadline = item.retakeDeadline
            val isRetakeValid = studentStatus == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime < retakeDeadline

            // --- CENTRAL UI LOGIC based on Time AND Firestore Status ---

            when {
                // Case 0: Not Scheduled
                startTime == 0L || endTime == 0L -> {
                    tvSchedule.text = "Status: ❌ Not Scheduled by Teacher."
                    setButtonState("Not Available", false, "#757575")
                }
                // Case 1: NOT YET AVAILABLE
                currentTime < startTime -> {
                    tvSchedule.text = "Status: 🗓️ Starts: ${sdfDateTime.format(Date(startTime))} - ${sdfTime.format(Date(endTime))}"
                    setButtonState("Wait to Start", false, "#FF9800")
                }
                // Case 2: RETAKE/REOPEN GRANTED
                isRetakeValid -> {
                    tvSchedule.text = "Status: 🔄 RETAKE GRANTED (Expires: ${sdfDateTime.format(Date(retakeDeadline))})"
                    setButtonState("START RETAKE", true, "#00C853")
                }
                // Case 3: ACCESS REVOKED
                studentStatus == "ACCESS_REVOKED" -> {
                    tvSchedule.text = "Status: 🔒 ACCESS REVOKED"
                    setButtonState("View Status", true, "#C62828")
                }
                // Case 4: ATTEMPTED/FINISHED, MISSED, or RETAKE EXPIRED
                studentStatus in setOf("COMPLETED", "TIME_EXPIRED", "CHEATING", "UNATTEMPTED_TIME_EXPIRED") ||
                        (studentStatus == "RETAKE_GRANTED" && retakeDeadline > 0L && currentTime >= retakeDeadline) -> {

                    val statusTag = when (studentStatus) {
                        "COMPLETED" -> "FINISHED ✅"
                        "TIME_EXPIRED" -> "TIME UP 🚨"
                        "CHEATING" -> "CHEATING ALERT ⚠️"
                        "UNATTEMPTED_TIME_EXPIRED" -> "MISSED QUIZ ❌"
                        "RETAKE_GRANTED" -> "RETAKE EXPIRED ⏳"
                        else -> "FINISHED"
                    }
                    tvSchedule.text = "Status: 🏁 $statusTag (Ended: ${sdfDateTime.format(Date(endTime))})"

                    // ⭐ UPDATED LOGIC FOR BUTTON STATE
                    val buttonText: String
                    val buttonColor: String
                    val isClickable: Boolean // <-- New variable

                    if (studentStatus == "UNATTEMPTED_TIME_EXPIRED") {
                        buttonText = "MISSED QUIZ"
                        buttonColor = "#FF9800" // Orange
                        isClickable = false // <-- DISABLING THE BUTTON
                    } else {
                        buttonText = "View Results"
                        buttonColor = "#757575" // Gray
                        isClickable = true
                    }

                    setButtonState(buttonText, isClickable, buttonColor)
                }
                // Case 5: ONGOING (No status or NOT_STARTED status)
                currentTime in startTime..endTime -> {
                    tvSchedule.text = "Status: 🟢 ONGOING (Ends: ${sdfTime.format(Date(endTime))})"
                    setButtonState("START NOW", true, "#00C853")
                }
                // Default fallback
                else -> {
                    tvSchedule.text = "Status: ❓ Unknown/Expired Schedule"
                    setButtonState("View Results", true, "#757575")
                }
            }

            // Only set the click listener if the button should navigate somewhere (i.e., not MISSED)
            if (btnStartQuiz.isEnabled) {
                // ⭐ Ipinapasa ang buong item
                btnStartQuiz.setOnClickListener {
                    clickListener(item)
                }
            } else {
                // Kung disabled (MISSED QUIZ), mag-set ng Toast o walang action
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

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
import com.example.datadomeapp.models.Quiz
import java.text.SimpleDateFormat
import java.util.*

class StudentQuizAdapter(
    private val quizzes: MutableList<Quiz>, // mutable list now
    private val clickListener: (Quiz) -> Unit
) : RecyclerView.Adapter<StudentQuizAdapter.QuizViewHolder>() {

    private val sdfDateTime = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    private val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        QuizViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_student_quiz, parent, false))

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) = holder.bind(quizzes[position])

    override fun getItemCount(): Int = quizzes.size

    // New method to update list
    fun updateList(newList: List<Quiz>) {
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

        fun bind(quiz: Quiz) {
            tvTitle.text = quiz.title
            val isExam = quiz.quizType.equals("Exam", ignoreCase = true)
            tvTypeTag.text = quiz.quizType.uppercase(Locale.ROOT)
            tvTypeTag.setBackgroundColor(if (isExam) Color.parseColor("#C62828") else Color.parseColor("#1B5E20"))

            val currentTime = System.currentTimeMillis()
            val startTime = quiz.scheduledDateTime
            val endTime = quiz.scheduledEndDateTime

            when {
                startTime == 0L || endTime == 0L -> {
                    tvSchedule.text = "Status: ❌ Not Scheduled by Teacher."
                    btnStartQuiz.apply { text = "Not Available"; isEnabled = false; setBackgroundColor(Color.parseColor("#757575")) }
                }
                currentTime < startTime -> {
                    tvSchedule.text = "Status: 🗓️ Starts: ${sdfDateTime.format(Date(startTime))} - ${sdfTime.format(Date(endTime))}"
                    btnStartQuiz.apply { text = "Wait to Start"; isEnabled = false; setBackgroundColor(Color.parseColor("#FF9800")) }
                }
                currentTime in startTime..endTime -> {
                    tvSchedule.text = "Status: 🟢 ONGOING (Ends: ${sdfTime.format(Date(endTime))})"
                    btnStartQuiz.apply { text = "START NOW"; isEnabled = true; setBackgroundColor(Color.parseColor("#00C853")) }
                }
                else -> { // currentTime > endTime
                    tvSchedule.text = "Status: 🏁 FINISHED (Ended: ${sdfDateTime.format(Date(endTime))})"
                    btnStartQuiz.apply { text = "View Results"; isEnabled = true; setBackgroundColor(Color.parseColor("#757575")) }
                }
            }

            btnStartQuiz.setOnClickListener {
                if (btnStartQuiz.isEnabled) clickListener(quiz)
                else Toast.makeText(itemView.context, "This ${quiz.quizType} is not yet available.", Toast.LENGTH_SHORT).show()
            }

            tvDescription.apply {
                if (quiz.description.isNotEmpty()) { text = "Description: ${quiz.description}"; visibility = View.VISIBLE }
                else visibility = View.GONE
            }
        }
    }
}

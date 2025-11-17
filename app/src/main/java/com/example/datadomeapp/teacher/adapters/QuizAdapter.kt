package com.example.datadomeapp.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Quiz
import com.example.datadomeapp.models.ClassDisplayDetails
import java.text.SimpleDateFormat
import java.util.*

class QuizAdapter(
    private val quizzes: MutableList<Quiz>,
    private val classDetailsMap: Map<String, ClassDisplayDetails>,
    private val editClickListener: (Quiz) -> Unit,
    private val deleteClickListener: (Quiz) -> Unit,
    private val publishClickListener: (Quiz) -> Unit,
    private val setTimeClickListener: (Quiz) -> Unit,
    private val viewClickListener: (Quiz) -> Unit
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    private val START_SDF = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    private val END_SDF = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.teacher_quiz_quiz, parent, false)
        return QuizViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        holder.bind(quizzes[position])
    }

    override fun getItemCount(): Int = quizzes.size

    fun updateList(newQuizzes: List<Quiz>) {
        quizzes.clear()
        quizzes.addAll(newQuizzes)
        notifyDataSetChanged()
    }

    inner class QuizViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvQuizTitle)
        private val tvType: TextView = itemView.findViewById(R.id.tvQuizType)
        private val tvDateTimeStatus: TextView = itemView.findViewById(R.id.tvQuizDateTime)
        private val tvClassDetails: TextView = itemView.findViewById(R.id.tvQuizClassDetails)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEditQuiz)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDeleteQuiz)
        private val btnPublish: Button = itemView.findViewById(R.id.btnPublishToggle)
        private val btnSetTime: Button = itemView.findViewById(R.id.btnSetTime)

        // ✅ ADDITION: CardView reference - make sure your XML has this ID
        // Since your XML doesn't have an explicit ID for CardView, we'll use the root CardView
        private val cardView: CardView = itemView as CardView

        private fun isQuizFinished(quiz: Quiz): Boolean {
            val currentTime = System.currentTimeMillis()
            val endTime = quiz.scheduledEndDateTime
            return quiz.isPublished && endTime > 0L && currentTime > endTime
        }

        private fun isQuizOngoing(quiz: Quiz): Boolean {
            val currentTime = System.currentTimeMillis()
            val startTime = quiz.scheduledDateTime
            val endTime = quiz.scheduledEndDateTime
            return quiz.isPublished && startTime > 0L && endTime > 0L && currentTime >= startTime && currentTime <= endTime
        }

        fun bind(quiz: Quiz) {
            val isOngoing = isQuizOngoing(quiz)
            val isFinished = isQuizFinished(quiz)
            val isPublished = quiz.isPublished

            val isViewMode = isOngoing || isFinished

            // ✅ ADDITION: Set card color based on status
            setCardColor(quiz, isOngoing, isFinished, isPublished)

            val canDelete = !isPublished && !isViewMode
            val canTogglePublish = !isOngoing && !isFinished
            val canSetTime = isPublished && !isOngoing && !isFinished

            tvTitle.text = quiz.title

            tvType.text = quiz.quizType.uppercase(Locale.getDefault())
            tvType.setTextColor(
                if (quiz.quizType.equals("Exam", ignoreCase = true)) 0xFFD32F2F.toInt()
                else 0xFF1976D2.toInt()
            )

            tvDateTimeStatus.text = formatDateTimeRange(
                quiz.scheduledDateTime,
                quiz.scheduledEndDateTime,
                isOngoing,
                isFinished,
                isPublished
            )

            val details = classDetailsMap[quiz.assignmentId]
            if (details != null) {
                val sectionId = details.sectionId.trim().uppercase(Locale.ROOT)
                val parts = sectionId.split('|', limit = 4).map { it.trim() }
                val subjectCode = parts.getOrNull(0) ?: "N/A Code"
                val courseCode = parts.getOrNull(1) ?: "N/A Course"
                val yearLevel = parts.getOrNull(2) ?: "N/A Year"
                val sectionBlock = parts.getOrNull(3) ?: "N/A Section"

                val displayYearSection = when {
                    yearLevel.filter { it.isDigit() }.isNotEmpty() && sectionBlock.filter { it.isLetterOrDigit() }.isNotEmpty()
                        -> "${yearLevel.filter { it.isDigit() }}-${sectionBlock.filter { it.isLetterOrDigit() }}"
                    yearLevel.filter { it.isDigit() }.isNotEmpty() -> yearLevel.filter { it.isDigit() }
                    else -> "N/A Section"
                }

                tvClassDetails.text = "$courseCode $displayYearSection - ${details.subjectTitle} ($subjectCode)"
            } else {
                tvClassDetails.text = "Class ID: ${quiz.assignmentId}"
            }

            btnEdit.apply {
                text = if (isViewMode) "VIEW" else "Edit"
                if (isViewMode) {
                    setOnClickListener { viewClickListener(quiz) }
                } else {
                    setOnClickListener { editClickListener(quiz) }
                }
            }

            btnDelete.apply {
                if (isPublished && !isViewMode) {
                    text = "VIEW"
                    visibility = View.VISIBLE
                    setOnClickListener { viewClickListener(quiz) }
                } else {
                    text = "DELETE"
                    visibility = if (canDelete) View.VISIBLE else View.GONE
                    setOnClickListener { if (canDelete) deleteClickListener(quiz) }
                }
            }

            btnSetTime.apply {
                text = if (quiz.scheduledDateTime > 0L) "UPDATE TIME" else "SET TIME"
                visibility = if (canSetTime) View.VISIBLE else View.GONE
                setOnClickListener { if (canSetTime) setTimeClickListener(quiz) }
            }

            btnPublish.apply {
                text = if (isPublished) "Unpublish" else "Publish"
                visibility = if (canTogglePublish) View.VISIBLE else View.GONE
                setOnClickListener { if (canTogglePublish) publishClickListener(quiz) }
            }
        }

        /**
         * ✅ ADDITION: Function to set card color based on quiz status
         */
        private fun setCardColor(quiz: Quiz, isOngoing: Boolean, isFinished: Boolean, isPublished: Boolean) {
            val colorRes = when {
                isOngoing -> R.color.quiz_ongoing
                isFinished -> R.color.quiz_finished
                isPublished && quiz.scheduledDateTime > 0L -> R.color.quiz_scheduled
                !isPublished && quiz.scheduledDateTime > 0L -> R.color.quiz_draft_time_set
                else -> R.color.quiz_draft_no_time
            }

            // Set the card background color
            cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, colorRes))

            // Optional: Adjust elevation based on status for better visual hierarchy
            when {
                isOngoing -> cardView.cardElevation = 8f
                isFinished -> cardView.cardElevation = 2f
                else -> cardView.cardElevation = 4f
            }
        }

        private fun formatDateTimeRange(startTimeMillis: Long, endTimeMillis: Long, isOngoing: Boolean, isFinished: Boolean, isPublished: Boolean): String {

            val timeDetails = if (startTimeMillis > 0L) {
                val timeRange = "${START_SDF.format(Date(startTimeMillis))} - ${END_SDF.format(Date(endTimeMillis))}"
                timeRange
            } else {
                "No Time Set"
            }

            val statusText = when {
                isOngoing -> "Status: ONGOING"
                isFinished -> "Status: FINISHED"
                isPublished && startTimeMillis > 0L -> "Status: SCHEDULED"
                !isPublished && startTimeMillis > 0L -> "Status: DRAFT (Time Set)"
                else -> "Status: DRAFT (No Time Set)"
            }

            return "$statusText\n$timeDetails"
        }
    }
}
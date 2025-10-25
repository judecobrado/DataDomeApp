package com.example.datadomeapp.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Quiz
import java.text.SimpleDateFormat
import java.util.*

class QuizAdapter(
    private val quizzes: MutableList<Quiz>,
    private val editClickListener: (Quiz) -> Unit,
    private val deleteClickListener: (Quiz) -> Unit,
    private val publishClickListener: (Quiz) -> Unit,
    private val setTimeClickListener: (Quiz) -> Unit
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.teacher_quiz_quiz, parent, false)
        return QuizViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        holder.bind(quizzes[position])
    }

    override fun getItemCount(): Int = quizzes.size

    fun updateQuiz(updatedQuiz: Quiz) {
        val index = quizzes.indexOfFirst { it.quizId == updatedQuiz.quizId }
        if (index != -1) {
            quizzes[index] = updatedQuiz
            notifyItemChanged(index)
        }
    }

    fun removeQuiz(quiz: Quiz) {
        val index = quizzes.indexOf(quiz)
        if (index != -1) {
            quizzes.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    inner class QuizViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvQuizTitle)
        // ✅ Ginamit ang tamang ID mula sa XML
        private val tvDateTimeStatus: TextView = itemView.findViewById(R.id.tvQuizDateTime)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEditQuiz)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDeleteQuiz)
        private val btnPublish: Button = itemView.findViewById(R.id.btnPublishToggle)
        private val btnSetTime: Button = itemView.findViewById(R.id.btnSetTime)

        // Helper functions (Inilipat ang mga ito sa loob ng ViewHolder para sa mas madaling paggamit)
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
        // End of Helper functions

        fun bind(quiz: Quiz) {
            val isOngoing = isQuizOngoing(quiz)
            val isFinished = isQuizFinished(quiz)
            val isViewMode = isOngoing || isFinished // True if Ongoing or Finished
            val canDelete = !isViewMode
            val canTogglePublish = !isOngoing
            val isPublished = quiz.isPublished

            tvTitle.text = quiz.title
            tvDateTimeStatus.text = formatDateTimeRange(quiz.scheduledDateTime, quiz.scheduledEndDateTime, isOngoing, isFinished, isPublished)

            // --- EDIT / VIEW BUTTON LOGIC ---
            btnEdit.apply {
                // VIEW button lang kapag Ongoing o Finished, pero laging VISIBLE
                text = if (isViewMode) "VIEW" else "Edit"
                isEnabled = true
                alpha = 1.0f // Tiyaking visible
                setOnClickListener { editClickListener(quiz) }
            }

            // --- DELETE BUTTON LOGIC ---
            btnDelete.apply {
                // ✅ Ngayon ay GONE na kapag hindi pwedeng i-delete
                visibility = if (canDelete) View.VISIBLE else View.GONE
                setOnClickListener { if (canDelete) deleteClickListener(quiz) }
            }

            // --- SET TIME BUTTON LOGIC ---
            btnSetTime.apply {
                val canSetTime = isPublished && !isViewMode
                text = if (quiz.scheduledDateTime > 0L) "UPDATE TIME" else "SET TIME"
                // ✅ Ngayon ay GONE na kapag hindi pwedeng i-set ang time
                visibility = if (canSetTime) View.VISIBLE else View.GONE
                setOnClickListener { if (canSetTime) setTimeClickListener(quiz) }
            }

            // --- PUBLISH BUTTON LOGIC ---
            btnPublish.apply {
                // ✅ Ngayon ay GONE na kapag Ongoing
                text = if (isPublished) "Unpublish" else "Publish"
                visibility = if (canTogglePublish) View.VISIBLE else View.GONE
                setOnClickListener { if (canTogglePublish) publishClickListener(quiz) }
            }
        }

        /**
         * I-format ang start at end time at isama ang status, checking ang isPublished status.
         */
        private fun formatDateTimeRange(startTimeMillis: Long, endTimeMillis: Long, isOngoing: Boolean, isFinished: Boolean, isPublished: Boolean): String {

            val timeDetails = if (startTimeMillis > 0L) {
                val startSdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                val endSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeRange = "${startSdf.format(Date(startTimeMillis))} - ${endSdf.format(Date(endTimeMillis))}"
                timeRange
            } else {
                "No Time Set"
            }

            // Status Check Logic (Pinagsama ang isPublished at Time check)
            val statusText = when {
                isOngoing -> "Status: 🟢 ONGOING"
                isFinished -> "Status: 🏁 FINISHED"
                isPublished && startTimeMillis > 0L -> "Status: 🗓️ SCHEDULED"
                !isPublished && startTimeMillis > 0L -> "Status: 🕒 DRAFT (Time Set)"
                else -> "Status: 📝 DRAFT (No Time Set)"
            }

            return "$statusText\n$timeDetails"
        }
    }
}

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
    private val quizzes: MutableList<Quiz>, // Make mutable to allow updates
    private val editClickListener: (Quiz) -> Unit,
    private val deleteClickListener: (Quiz) -> Unit,
    private val publishClickListener: (Quiz) -> Unit,
    private val setTimeClickListener: (Quiz) -> Unit
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quiz, parent, false)
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
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvQuizDateTime)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEditQuiz)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDeleteQuiz)
        private val btnPublish: Button = itemView.findViewById(R.id.btnPublishToggle)
        private val btnSetTime: Button = itemView.findViewById(R.id.btnSetTime)

        fun bind(quiz: Quiz) {
            tvTitle.text = quiz.title
            tvDateTime.text = formatDateTime(quiz.scheduledDateTime)

            btnEdit.setOnClickListener { editClickListener(quiz) }
            btnDelete.setOnClickListener { deleteClickListener(quiz) }
            btnPublish.apply {
                text = if (quiz.isPublished) "Unpublish" else "Publish"
                setOnClickListener { publishClickListener(quiz) }
            }
            btnSetTime.setOnClickListener { setTimeClickListener(quiz) }
        }

        private fun formatDateTime(timeMillis: Long): String {
            return if (timeMillis > 0L) {
                val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(timeMillis))
            } else {
                "No time set"
            }
        }
    }
}

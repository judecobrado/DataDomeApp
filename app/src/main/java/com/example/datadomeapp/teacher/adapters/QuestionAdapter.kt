package com.example.datadomeapp.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question

class QuestionAdapter(
    private val questions: MutableList<Question>, // mutable para puwede i-update
    private val editClickListener: (Question) -> Unit,
    private val deleteClickListener: (Question) -> Unit
) : RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_question, parent, false)
        return QuestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questions[position])
    }

    override fun getItemCount(): Int = questions.size

    // ------------------ NEW METHOD ------------------
    fun updateQuestions(newQuestions: List<Question>) {
        questions.clear()
        questions.addAll(newQuestions)
        notifyDataSetChanged()
    }

    inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvQuestionText: TextView = itemView.findViewById(R.id.tvQuestionText)
        private val tvQuestionType: TextView = itemView.findViewById(R.id.tvQuestionType)
        private val tvOptions: TextView = itemView.findViewById(R.id.tvOptions)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditQuestion)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteQuestion)

        fun bind(question: Question) {
            tvQuestionText.text = question.questionText
            tvOptions.visibility = View.VISIBLE

            when (question) {
                is Question.TrueFalse -> {
                    tvQuestionType.text = "True / False"
                    tvOptions.text = "Answer: ${if (question.answer) "True" else "False"}"
                }
                is Question.MultipleChoice -> {
                    tvQuestionType.text = "Multiple Choice"
                    tvOptions.text = question.options.mapIndexed { index, option ->
                        val correctMark = if (index == question.correctAnswerIndex) " (Correct)" else ""
                        "${index + 1}. $option$correctMark"
                    }.joinToString("\n")
                }
                is Question.Matching -> {
                    tvQuestionType.text = "Matching"
                    tvOptions.text = question.options.zip(question.matches)
                        .joinToString("\n") { (l, r) -> "$l → $r" }
                }
            }

            btnEdit.setOnClickListener { editClickListener(question) }
            btnDelete.setOnClickListener { deleteClickListener(question) }
        }
    }
}

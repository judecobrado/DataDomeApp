package com.example.datadomeapp.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question // This is the FLATTENED data class

class QuestionAdapter(
    private val questions: MutableList<Question>,
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

    // ------------------ NEW METHOD (No Change) ------------------
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

            // 🛑 FIX: Use the 'type' string property instead of the 'is' keyword
            when (question.type.uppercase()) {
                "TF" -> {
                    tvQuestionType.text = "True / False"
                    // Safely access the nullable 'answer' field
                    val answerText = if (question.answer == true) "True" else if (question.answer == false) "False" else "N/A"
                    tvOptions.text = "Answer: $answerText"
                }
                "MC" -> {
                    tvQuestionType.text = "Multiple Choice"
                    // Safely access nullable 'options' and 'correctAnswerIndex'
                    val options = question.options ?: emptyList()
                    val correctIndex = question.correctAnswerIndex

                    if (options.isNotEmpty()) {
                        tvOptions.text = options.mapIndexed { index, option ->
                            val correctMark = if (index == correctIndex) " (Correct)" else ""
                            "${index + 1}. $option$correctMark"
                        }.joinToString("\n")
                    } else {
                        tvOptions.text = "No options available."
                    }
                }
                "MATCHING" -> {
                    tvQuestionType.text = "Matching"
                    // Safely access nullable 'options' (left) and 'matches' (right)
                    val options = question.options ?: emptyList()
                    val matches = question.matches ?: emptyList()

                    if (options.isNotEmpty() && matches.isNotEmpty() && options.size == matches.size) {
                        tvOptions.text = options.zip(matches)
                            .joinToString("\n") { (l, r) -> "$l → $r" }
                    } else {
                        tvOptions.text = "Matching pairs incomplete."
                    }
                }
                else -> {
                    tvQuestionType.text = "Unknown Type"
                    tvOptions.visibility = View.GONE
                }
            }

            btnEdit.setOnClickListener { editClickListener(question) }
            btnDelete.setOnClickListener { deleteClickListener(question) }
        }
    }
}
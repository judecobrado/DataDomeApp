package com.example.datadomeapp.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Question

class QuestionAdapter(
    private val questions: List<Question>,
    private val editClickListener: (Question) -> Unit,
    private val deleteClickListener: (Question) -> Unit
) : RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder>() {

    inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuestion: TextView = itemView.findViewById(R.id.tvQuestion)
        val btnEdit: Button = itemView.findViewById(R.id.btnEditQuestion)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteQuestion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_question, parent, false)
        return QuestionViewHolder(view)
    }

    override fun getItemCount(): Int = questions.size

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        val question = questions[position]

        holder.tvQuestion.text = when (question) {
            is Question.TrueFalse -> question.questionText + " (True/False)"
            is Question.Matching -> question.questionText + " (Matching)"
        }

        holder.btnEdit.setOnClickListener { editClickListener(question) }
        holder.btnDelete.setOnClickListener { deleteClickListener(question) }
    }
}

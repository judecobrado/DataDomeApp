package com.example.datadomeapp.teacher

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R

class EditableActivityScoreAdapter(
    private var scores: List<ActivityScoreData>,
    private val studentId: String,
    private val category: String,
    private var isEditable: Boolean = false, // Default to false (view mode)
    private val isPublished: Boolean = false,
    private val onScoreUpdate: (ActivityScoreData) -> Unit
) : RecyclerView.Adapter<EditableActivityScoreAdapter.EditableScoreViewHolder>() {

    class EditableScoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvActivityTitle: TextView = view.findViewById(R.id.tvActivityTitle)
        val etRawScore: EditText = view.findViewById(R.id.etRawScore)
        val tvMaxPoints: TextView = view.findViewById(R.id.tvMaxPoints)
        val tvCalculatedPercent: TextView = view.findViewById(R.id.tvCalculatedPercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditableScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_editable_activity_score, parent, false)
        return EditableScoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: EditableScoreViewHolder, position: Int) {
        val scoreData = scores[position]

        holder.tvActivityTitle.text = scoreData.title

        // Format raw score display
        holder.etRawScore.setText(if (scoreData.rawScore % 1.0 == 0.0) {
            "%.0f".format(scoreData.rawScore)
        } else {
            "%.1f".format(scoreData.rawScore)
        })

        // Format max points display
        holder.tvMaxPoints.text = " / ${if (scoreData.maxPoints % 1.0 == 0.0) {
            "%.0f".format(scoreData.maxPoints)
        } else {
            "%.1f".format(scoreData.maxPoints)
        }}"

        holder.tvCalculatedPercent.text = "50%-Base: ${"%.2f".format(scoreData.score50Base)}%"

        // UPDATED: Better view mode handling
        val shouldEnableEditing = isEditable && !isPublished

        if (shouldEnableEditing) {
            holder.etRawScore.isEnabled = true
            holder.etRawScore.alpha = 1.0f
            holder.etRawScore.hint = "Enter score"

            // Remove previous watchers to avoid duplicates
            holder.etRawScore.tag?.let {
                holder.etRawScore.removeTextChangedListener(it as TextWatcher)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newRawScore = s.toString().toDoubleOrNull() ?: 0.0
                    val newPercentage = if (scoreData.maxPoints > 0) {
                        (newRawScore / scoreData.maxPoints) * 100.0
                    } else 0.0

                    val newScore50Base = maxOf(50.0, newPercentage)

                    val updatedScore = scoreData.copy(
                        rawScore = newRawScore,
                        score50Base = newScore50Base
                    )

                    holder.tvCalculatedPercent.text = "50%-Base: ${"%.2f".format(newScore50Base)}%"

                    onScoreUpdate(updatedScore)
                }
            }

            holder.etRawScore.addTextChangedListener(textWatcher)
            holder.etRawScore.tag = textWatcher
        } else {
            // View mode - disabled but still readable
            holder.etRawScore.isEnabled = false
            holder.etRawScore.alpha = 0.8f // Slightly dimmed but still readable
            holder.etRawScore.hint = ""

            // Remove any text watchers in view mode
            holder.etRawScore.tag?.let {
                holder.etRawScore.removeTextChangedListener(it as TextWatcher)
            }
        }
    }

    override fun getItemCount() = scores.size

    fun updateScores(newScores: List<ActivityScoreData>) {
        scores = newScores
        notifyDataSetChanged()
    }

    // NEW: Function to toggle edit mode
    fun setEditable(editable: Boolean) {
        isEditable = editable
        notifyDataSetChanged()
    }
}
package com.example.datadomeapp.teacher

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.text.InputFilter
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R

fun Double.isInteger(): Boolean = this % 1 == 0.0

class EditableActivityScoreAdapter(
    private var scores: List<ActivityScoreData>,
    private val studentId: String,
    private val category: String,
    private var isEditable: Boolean = false,
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

        // Safety check
        if (scoreData.maxPoints <= 0) {
            holder.tvCalculatedPercent.text = "50%-Base: 0.00%"
            holder.etRawScore.isEnabled = false
            holder.etRawScore.setText("0")
            return
        }

        holder.tvActivityTitle.text = scoreData.title

        // Format displays
        holder.etRawScore.setText(if (scoreData.rawScore.isInteger()) {
            "%.0f".format(scoreData.rawScore)
        } else {
            "%.1f".format(scoreData.rawScore)
        })

        holder.tvMaxPoints.text = " / ${if (scoreData.maxPoints.isInteger()) {
            "%.0f".format(scoreData.maxPoints)
        } else {
            "%.1f".format(scoreData.maxPoints)
        }}"

        holder.tvCalculatedPercent.text = "50%-Base: ${"%.2f".format(scoreData.score50Base)}%"

        // IMPROVED INPUT FILTER
        holder.etRawScore.filters = arrayOf<InputFilter>(InputFilter { source, start, end, dest, dstart, dend ->
            val newText = dest.toString().replaceRange(dstart, dend, source.subSequence(start, end))

            if (newText.isEmpty()) return@InputFilter source

            if (newText.matches(Regex("^\\d+\\.?\\d*$")) && newText.count { it == '.' } <= 1) {
                source
            } else {
                ""
            }
        })

        val shouldEnableEditing = isEditable && !isPublished

        if (shouldEnableEditing) {
            holder.etRawScore.isEnabled = true
            holder.etRawScore.alpha = 1.0f
            holder.etRawScore.hint = "Enter score"

            // Clean up previous watcher
            holder.etRawScore.tag?.let {
                holder.etRawScore.removeTextChangedListener(it as TextWatcher)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val inputText = s.toString()
                    if (inputText.isBlank() || inputText == ".") {
                        holder.tvCalculatedPercent.text = "50%-Base: 0.00%"
                        return
                    }

                    val newRawScore = inputText.toDoubleOrNull() ?: 0.0

                    if (!isValidScore(newRawScore, scoreData)) {
                        // Reset to original
                        holder.etRawScore.setText(if (scoreData.rawScore.isInteger()) {
                            "%.0f".format(scoreData.rawScore)
                        } else {
                            "%.1f".format(scoreData.rawScore)
                        })
                        showValidationError(holder.itemView, scoreData)
                        return
                    }

                    val newPercentage = (newRawScore / scoreData.maxPoints) * 100.0
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
            holder.etRawScore.isEnabled = false
            holder.etRawScore.alpha = 0.8f
            holder.etRawScore.hint = ""

            holder.etRawScore.tag?.let {
                holder.etRawScore.removeTextChangedListener(it as TextWatcher)
            }
        }
    }

    // Add this method
    override fun onViewRecycled(holder: EditableScoreViewHolder) {
        super.onViewRecycled(holder)
        holder.etRawScore.tag?.let {
            holder.etRawScore.removeTextChangedListener(it as TextWatcher)
        }
        holder.etRawScore.tag = null
    }

    /**
     * Validate if the new score is within acceptable range
     * Rules:
     * 1. Cannot be lower than existing raw score
     * 2. Cannot exceed maximum points
     * 3. Must be non-negative
     */
    private fun isValidScore(newScore: Double, scoreData: ActivityScoreData): Boolean {
        // Rule 1: Cannot be lower than existing score
        if (newScore < scoreData.rawScore) {
            return false
        }

        // Rule 2: Cannot exceed maximum points
        if (newScore > scoreData.maxPoints) {
            return false
        }

        // Rule 3: Must be non-negative
        if (newScore < 0) {
            return false
        }

        return true
    }

    /**
     * Show appropriate validation error message
     */
    private fun showValidationError(view: View, scoreData: ActivityScoreData) {
        val context = view.context
        Toast.makeText(
            context,
            "Invalid score! Must be between ${scoreData.rawScore} and ${scoreData.maxPoints}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun getItemCount() = scores.size

    fun updateScores(newScores: List<ActivityScoreData>) {
        scores = newScores
        notifyDataSetChanged()
    }

    // Function to toggle edit mode
    fun setEditable(editable: Boolean) {
        isEditable = editable
        notifyDataSetChanged()
    }
}
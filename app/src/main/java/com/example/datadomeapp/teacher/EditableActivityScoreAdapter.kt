package com.example.datadomeapp.teacher

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.utils.DecimalInputFilter
import com.example.datadomeapp.utils.isInteger
import com.example.datadomeapp.utils.safePercentage
import com.example.datadomeapp.utils.safeMax
import com.example.datadomeapp.utils.toDoubleSafe


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
            holder.etRawScore.setText("*")
            return
        }

        holder.tvActivityTitle.text = scoreData.title

        // Format displays - show empty for zero scores
        holder.etRawScore.setText(
            when {
                scoreData.rawScore == 0.0 -> "*"
                scoreData.rawScore.isInteger() -> "%.0f".format(scoreData.rawScore)
                else -> "%.1f".format(scoreData.rawScore)
            }
        )

        holder.tvMaxPoints.text = " / ${
            if (scoreData.maxPoints.isInteger()) "%.0f".format(scoreData.maxPoints)
            else "%.1f".format(scoreData.maxPoints)
        }"

        holder.tvCalculatedPercent.text = "50%-Base: ${"%.2f".format(scoreData.score50Base)}%"

        // Improved input filter
        holder.etRawScore.filters = arrayOf(DecimalInputFilter(1))

        val shouldEnableEditing = isEditable && !isPublished && category != "Exam"

        if (shouldEnableEditing) {
            holder.etRawScore.isEnabled = true
            holder.etRawScore.alpha = 1.0f
            holder.etRawScore.hint = "Enter score"

            // Clean up previous watcher
            (holder.etRawScore.tag as? TextWatcher)?.let {
                holder.etRawScore.removeTextChangedListener(it)
            }

            val textWatcher = createScoreTextWatcher(holder, scoreData)
            holder.etRawScore.addTextChangedListener(textWatcher)
            holder.etRawScore.tag = textWatcher
        } else {
            holder.etRawScore.isEnabled = false
            holder.etRawScore.alpha = 0.8f
            holder.etRawScore.hint = ""

            (holder.etRawScore.tag as? TextWatcher)?.let {
                holder.etRawScore.removeTextChangedListener(it)
            }
        }
    }

    private fun createScoreTextWatcher(
        holder: EditableScoreViewHolder,
        scoreData: ActivityScoreData
    ): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val inputText = s.toString()

                if (inputText.isBlank() || inputText == ".") {
                    // Update to zero when field is cleared
                    val updatedScore = scoreData.copy(
                        rawScore = 0.0,
                        score50Base = 0.0
                    )
                    holder.tvCalculatedPercent.text = "50%-Base: 0.00%"
                    onScoreUpdate(updatedScore)
                    return
                }

                val newRawScore = inputText.toDoubleSafe()

                if (!isValidScore(newRawScore, scoreData)) {
                    // Reset to original
                    holder.etRawScore.setText(
                        when {
                            scoreData.rawScore == 0.0 -> ""
                            scoreData.rawScore.isInteger() -> "%.0f".format(scoreData.rawScore)
                            else -> "%.1f".format(scoreData.rawScore)
                        }
                    )
                    showValidationError(holder.itemView, scoreData)
                    return
                }

                val percentage = newRawScore.safePercentage(scoreData.maxPoints)
                val newScore50Base = percentage.safeMax(50.0)

                val updatedScore = scoreData.copy(
                    rawScore = newRawScore,
                    score50Base = newScore50Base
                )

                holder.tvCalculatedPercent.text = "50%-Base: ${"%.2f".format(newScore50Base)}%"
                onScoreUpdate(updatedScore)
            }
        }
    }

    override fun onViewRecycled(holder: EditableScoreViewHolder) {
        super.onViewRecycled(holder)
        (holder.etRawScore.tag as? TextWatcher)?.let {
            holder.etRawScore.removeTextChangedListener(it)
        }
        holder.etRawScore.tag = null
    }

    private fun isValidScore(newScore: Double, scoreData: ActivityScoreData): Boolean {
        // Rule 1: Cannot be lower than existing score
        //if (newScore > scoreData.rawScore) {
            //return false
        //}

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

    private fun showValidationError(view: View, scoreData: ActivityScoreData) {
        Toast.makeText(
            view.context,
            "Invalid score! Must be between 0 and ${scoreData.maxPoints}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun getItemCount() = scores.size

    fun updateScores(newScores: List<ActivityScoreData>) {
        scores = newScores
        notifyDataSetChanged()
    }

    fun setEditable(editable: Boolean) {
        isEditable = editable
        notifyDataSetChanged()
    }
}
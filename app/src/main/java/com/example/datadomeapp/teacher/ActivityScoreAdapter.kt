package com.example.datadomeapp.teacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R

// NOTE: Ensure ActivityScoreData is accessible (it was defined in GradeInputActivity.kt)



/**
 * Adapter to display individual activity scores (Quiz, Exam, Assignment)
 * within the detailed score dialog.
 * @param scores List of ActivityScoreData
 */
class ActivityScoreAdapter(
    // FIX: Changed the expected data type to the richer ActivityScoreData class
    private val scores: List<ActivityScoreData>
) :
    RecyclerView.Adapter<ActivityScoreAdapter.ScoreViewHolder>() {

    class ScoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvActivityTitle: TextView = view.findViewById(R.id.tvActivityTitle)
        val tvActivityScore: TextView = view.findViewById(R.id.tvActivityScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_score, parent, false)
        return ScoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        val data = scores[position]

        holder.tvActivityTitle.text = data.title

        val scoreDisplay: String

        if (data.maxPoints > 0.0) {
            // Che-check kung ang rawScore ay isang integer (walang decimal part)
            val rawScoreString = if (data.rawScore % 1.0 == 0.0) {
                // Kung integer, i-format ito nang walang decimal places (hal. 10)
                "%.0f".format(data.rawScore)
            } else {
                // Kung may decimal, i-format ito sa 1 decimal place (hal. 10.5)
                "%.1f".format(data.rawScore)
            }

            // Che-check kung ang maxPoints ay isang integer
            val maxPointsString = if (data.maxPoints % 1.0 == 0.0) {
                // Kung integer, i-format ito nang walang decimal places (hal. 20)
                "%.0f".format(data.maxPoints)
            } else {
                // Kung may decimal, i-format ito sa 1 decimal place (hal. 20.0)
                "%.1f".format(data.maxPoints)
            }

            // Pagsasama-sama ng display string: Raw Score / Max Points (50%-Base: Calculated Score%)
            scoreDisplay = "$rawScoreString / $maxPointsString (50%-Base: ${"%.2f".format(data.score50Base)}%)"
        } else {
            // Handle case where Max Points is zero or missing
            scoreDisplay = "N/A (50%-Base: ${"%.2f".format(data.score50Base)}%)"
        }

        holder.tvActivityScore.text = scoreDisplay
    }

    override fun getItemCount() = scores.size
}
package com.example.datadomeapp.teacher.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.teacher.StudentMonitoringData
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class QuizMonitoringAdapter(
    // Ang Constructor na gumagamit ng Lambdas ay TAMA na
    private var dataList: List<StudentMonitoringData>,
    private val onRetakeClick: (StudentMonitoringData) -> Unit,
    private val onIntegrityClick: (StudentMonitoringData) -> Unit
) : RecyclerView.Adapter<QuizMonitoringAdapter.MonitorViewHolder>() {

    fun updateList(newList: List<StudentMonitoringData>) {
        // Ang sorting ay ginagawa na sa ViewModel, pero pwedeng i-sort ulit dito kung kailangan
        dataList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonitorViewHolder {
        // Tiyakin na ang R.layout.list_item_monitoring ay ang tamang layout file ninyo
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_monitoring,
            parent,
            false
        )
        return MonitorViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonitorViewHolder, position: Int) {
        val currentItem = dataList[position]
        holder.bind(currentItem)
    }

    override fun getItemCount() = dataList.size

    inner class MonitorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStudentName: TextView = itemView.findViewById(R.id.tvStudentName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvScore: TextView = itemView.findViewById(R.id.tvScore)
        private val tvCheats: TextView = itemView.findViewById(R.id.tvCheats)
        private val tvLastUpdate: TextView = itemView.findViewById(R.id.tvLastUpdate)
        private val btnRetake: Button = itemView.findViewById(R.id.btnRetake)
        private val btnViewIntegrity: ImageButton = itemView.findViewById(R.id.btnViewIntegrity)


        fun bind(data: StudentMonitoringData) {
            tvStudentName.text = data.studentName

            // ⭐ UPDATE 1: SCORE LOGIC
            // Ipakita ang final score (kabilang ang bagong UNATTEMPTED_TIME_EXPIRED)
            tvScore.text = if (data.status == "COMPLETED" ||
                data.status == "TIME_EXPIRED" ||
                data.status == "UNATTEMPTED_TIME_EXPIRED") { // DINAGDAG ITO
                data.score.toString()
            } else {
                // Ipakita ang question number
                "Q${data.score + 1}"
            }

            tvCheats.text = "Cheats: ${data.cheatCount}"

            tvLastUpdate.text = if (data.lastUpdate > 0) {
                val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                "Updated: ${dateFormat.format(Date(data.lastUpdate))}"
            } else {
                // Maari rin itong ma-trigger ng UNATTEMPTED_TIME_EXPIRED na walang lastUpdate
                "Not yet active"
            }

            // --- Status at Color Logic ---
            tvStatus.text = formatStatus(data.status, data.cheatCount)
            setStatusColor(data.status, data.cheatCount)

            // --- Retake Button Logic ---
            // ⭐ UPDATE 2: RETAKE LOGIC
            // Lalabas ang Retake button kapag tapos na, expired, missed, o granted na.
            val showRetake = data.status == "COMPLETED" ||
                    data.status == "TIME_EXPIRED" ||
                    data.status == "RETAKE_GRANTED" ||
                    data.status == "UNATTEMPTED_TIME_EXPIRED" || // DINAGDAG ITO
                    data.cheatCount >= 5 // Base sa inyong lumang logic

            btnRetake.visibility = if (showRetake) View.VISIBLE else View.GONE

            if (showRetake) {
                btnRetake.setOnClickListener { onRetakeClick(data) }
            }

            // ⭐ UPDATE 3: INTEGRITY LOGIC
            val showIntegrityLog = data.cheatCount > 0 || data.status == "COMPLETED" || data.status == "TIME_EXPIRED" || data.status == "UNATTEMPTED_TIME_EXPIRED"
            btnViewIntegrity.visibility = if (showIntegrityLog) View.VISIBLE else View.GONE

            if (showIntegrityLog) {
                btnViewIntegrity.setOnClickListener { onIntegrityClick(data) }

                val integrityColor = when {
                    data.cheatCount > 0 -> R.color.status_expired // Pula kapag may cheat
                    // Iba pang finished status
                    else -> R.color.status_not_started // Neutral/Gray kapag tapos na lang
                }
                btnViewIntegrity.setColorFilter(ContextCompat.getColor(itemView.context, integrityColor))
            } else {
                btnViewIntegrity.setOnClickListener(null)
            }
        }

        private fun formatStatus(status: String, cheatCount: Int): String {
            // ⭐ UPDATE 4: STATUS FORMATTING
            return when (status) {
                "IN_PROGRESS" -> if (cheatCount > 0) "CHEATING ALERT ⚠️" else "TAKING QUIZ ⏱️"
                "COMPLETED" -> "FINISHED ✅"
                "NOT_STARTED" -> "PENDING ⚪"
                "RETAKE_GRANTED" -> "RETAKE ALLOWED 🔄"
                "TIME_EXPIRED" -> "TIME UP 🚨"
                "UNATTEMPTED_TIME_EXPIRED" -> "MISSED QUIZ ❌" // CRITICAL NEW STATUS DISPLAY
                else -> status
            }
        }

        private fun setStatusColor(status: String, cheatCount: Int) {
            val context = itemView.context
            // ⭐ UPDATE 5: COLOR HANDLING
            val colorRes = when {
                cheatCount > 0 && status != "COMPLETED" -> R.color.status_expired
                status == "IN_PROGRESS" -> R.color.status_in_progress
                status == "COMPLETED" -> R.color.status_completed
                status == "TIME_EXPIRED" || status == "UNATTEMPTED_TIME_EXPIRED" -> R.color.status_expired // Parehong Dark Red/Orange
                status == "RETAKE_GRANTED" -> R.color.status_retake
                status == "NOT_STARTED" -> R.color.status_not_started
                else -> android.R.color.black
            }
            tvStatus.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
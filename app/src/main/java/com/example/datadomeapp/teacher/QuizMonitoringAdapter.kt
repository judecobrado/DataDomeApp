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
    // ⭐ UPDATE 1: INALIS ang onRetakeClick lambda
    private var dataList: List<StudentMonitoringData>,
    private val onIntegrityClick: (StudentMonitoringData) -> Unit // Ito na lang ang natira
) : RecyclerView.Adapter<QuizMonitoringAdapter.MonitorViewHolder>() {

    fun updateList(newList: List<StudentMonitoringData>) {
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
        // ⭐ UPDATE 2: INALIS ang btnRetake Button mula sa deklarasyon at layout
        // private val btnRetake: Button = itemView.findViewById(R.id.btnRetake) // ALISIN ITO
        private val btnViewIntegrity: ImageButton = itemView.findViewById(R.id.btnViewIntegrity)


        fun bind(data: StudentMonitoringData) {
            tvStudentName.text = data.studentName

            // ⭐ UPDATE 3: SCORE LOGIC (Idinagdag ang ACCESS_REVOKED)
            tvScore.text = if (data.status == "COMPLETED" ||
                data.status == "TIME_EXPIRED" ||
                data.status == "UNATTEMPTED_TIME_EXPIRED" ||
                data.status == "ACCESS_REVOKED") { // DINAGDAG ITO
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
                "Not yet active"
            }

            // --- Status at Color Logic ---
            tvStatus.text = formatStatus(data.status, data.cheatCount)
            setStatusColor(data.status, data.cheatCount)

            // ⭐ UPDATE 4: INALIS ang Retake Button Logic

            // --- INTEGRITY LOGIC ---
            // Lalabas ang Integrity Log button kung may cheat o tapos na
            val showIntegrityLog = data.cheatCount > 0 ||
                    data.status == "COMPLETED" ||
                    data.status == "TIME_EXPIRED" ||
                    data.status == "UNATTEMPTED_TIME_EXPIRED" ||
                    data.status == "ACCESS_REVOKED" // DINAGDAG ITO

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
            // ⭐ UPDATE 5: STATUS FORMATTING (Idinagdag ang ACCESS_REVOKED)
            return when (status) {
                "IN_PROGRESS" -> if (cheatCount > 0) "CHEATING ALERT ⚠️" else "TAKING QUIZ ⏱️"
                "COMPLETED" -> "FINISHED ✅"
                "NOT_STARTED" -> "PENDING ⚪"
                "RETAKE_GRANTED" -> "RETAKE ALLOWED 🔄"
                "TIME_EXPIRED" -> "TIME UP 🚨"
                "UNATTEMPTED_TIME_EXPIRED" -> "MISSED QUIZ ❌"
                "ACCESS_REVOKED" -> "BLOCKED 🔒" // CRITICAL NEW STATUS DISPLAY
                else -> status
            }
        }

        private fun setStatusColor(status: String, cheatCount: Int) {
            val context = itemView.context
            // ⭐ UPDATE 6: COLOR HANDLING (Idinagdag ang ACCESS_REVOKED)
            val colorRes = when {
                cheatCount > 0 && status != "COMPLETED" -> R.color.status_expired
                status == "IN_PROGRESS" -> R.color.status_in_progress
                status == "COMPLETED" -> R.color.status_completed
                status == "TIME_EXPIRED" || status == "UNATTEMPTED_TIME_EXPIRED" -> R.color.status_expired // Dark Red/Orange
                status == "RETAKE_GRANTED" -> R.color.status_retake
                status == "ACCESS_REVOKED" -> R.color.status_expired // Gamitin ang red/dark orange para sa blocked
                status == "NOT_STARTED" -> R.color.status_not_started
                else -> android.R.color.black
            }
            tvStatus.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
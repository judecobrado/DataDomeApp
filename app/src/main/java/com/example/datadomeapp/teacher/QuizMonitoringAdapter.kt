package com.example.datadomeapp.teacher.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.teacher.StudentMonitoringData
import com.google.android.material.button.MaterialButton // ADD THIS IMPORT
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class QuizMonitoringAdapter(
    private var dataList: List<StudentMonitoringData>,
    private val onIntegrityClick: (StudentMonitoringData) -> Unit,
    private val onAccessControlClick: (StudentMonitoringData) -> Unit
) : RecyclerView.Adapter<QuizMonitoringAdapter.MonitorViewHolder>() {

    fun updateList(newList: List<StudentMonitoringData>) {
        dataList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonitorViewHolder {
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
        private val tvStudentId: TextView = itemView.findViewById(R.id.tvStudentId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvScore: TextView = itemView.findViewById(R.id.tvScore)
        private val tvCheats: TextView = itemView.findViewById(R.id.tvCheats)
        private val tvLastUpdate: TextView = itemView.findViewById(R.id.tvLastUpdate)

        // ⭐ FIX: Change from ImageButton to MaterialButton
        private val btnViewIntegrity: MaterialButton = itemView.findViewById(R.id.btnViewIntegrity)
        private val btnAccessControl: MaterialButton = itemView.findViewById(R.id.btnAccessControl)

        fun bind(data: StudentMonitoringData) {
            tvStudentName.text = data.studentName
            tvStudentId.text = data.id

            tvScore.text = if (data.status == "COMPLETED" ||
                data.status == "TIME_EXPIRED" ||
                data.status == "UNATTEMPTED_TIME_EXPIRED" ||
                data.status == "ACCESS_REVOKED") {
                data.score.toString()
            } else {
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

            setupAccessControlButton(data)

            // --- INTEGRITY LOGIC ---
            val showIntegrityLog = data.cheatCount > 0 ||
                    data.status == "COMPLETED" ||
                    data.status == "TIME_EXPIRED" ||
                    data.status == "UNATTEMPTED_TIME_EXPIRED" ||
                    data.status == "ACCESS_REVOKED"

            btnViewIntegrity.visibility = if (showIntegrityLog) View.VISIBLE else View.GONE

            if (showIntegrityLog) {
                btnViewIntegrity.setOnClickListener { onIntegrityClick(data) }

                // For MaterialButton, use setStrokeColor instead of setColorFilter
                val integrityColor = when {
                    data.cheatCount > 0 -> R.color.status_expired
                    else -> R.color.status_not_started
                }
                btnViewIntegrity.setStrokeColorResource(integrityColor)
            } else {
                btnViewIntegrity.setOnClickListener(null)
            }
        }

        private fun setupAccessControlButton(data: StudentMonitoringData) {
            val context = itemView.context

            val isManageable = data.status == "IN_PROGRESS" || data.status == "CHEATING" ||
                    data.status == "COMPLETED" || data.status == "TIME_EXPIRED" ||
                    data.status == "NOT_STARTED" || data.status == "EXAM_READY" ||
                    data.status == "UNATTEMPTED_TIME_EXPIRED" || data.status == "ACCESS_REVOKED"

            val shouldShowButton = isManageable

            if (shouldShowButton) {
                btnAccessControl.visibility = View.VISIBLE
                btnAccessControl.setOnClickListener { onAccessControlClick(data) }

                val iconRes: Int
                val backgroundColor: Int

                when (data.status) {

                    "COMPLETED", "TIME_EXPIRED", "CHEATED_MAX" -> {
                        iconRes = R.drawable.ic_security_info
                        backgroundColor = R.color.status_retake
                    }
                    "EXAM_READY", "NOT_STARTED", "UNATTEMPTED_TIME_EXPIRED", "ACCESS_REVOKED" -> {
                        iconRes = R.drawable.ic_security_info
                        backgroundColor = R.color.status_in_progress
                    }
                    else -> {
                        iconRes = R.drawable.ic_security_info
                        backgroundColor = android.R.color.darker_gray
                    }
                }

                try {
                    btnAccessControl.setIconResource(iconRes)
                    btnAccessControl.setBackgroundColor(ContextCompat.getColor(context, backgroundColor))
                } catch (e: Exception) {
                    btnAccessControl.setIconResource(R.drawable.ic_security_info)
                }

            } else {
                btnAccessControl.visibility = View.GONE
                btnAccessControl.setOnClickListener(null)
            }
        }

        private fun formatStatus(status: String, cheatCount: Int): String {
            return when (status) {
                "IN_PROGRESS" -> if (cheatCount > 0) "CHEATING ALERT ⚠️" else "TAKING ⏱️"
                "COMPLETED" -> "FINISHED"
                "NOT_STARTED" -> "PENDING"
                "RETAKE_GRANTED" -> "RETAKE ALLOWED"
                "TIME_EXPIRED" -> "TIME UP!"
                "UNATTEMPTED_TIME_EXPIRED" -> "MISSED!"
                "ACCESS_REVOKED" -> "BLOCKED 🔒" // CRITICAL NEW STATUS DISPLAY
                else -> status
            }
        }

        private fun setStatusColor(status: String, cheatCount: Int) {
            val context = itemView.context
            val colorRes = when {
                cheatCount > 0 && status != "COMPLETED" -> R.color.status_expired
                status == "IN_PROGRESS" -> R.color.status_in_progress
                status == "COMPLETED" -> R.color.status_completed
                status == "TIME_EXPIRED" || status == "UNATTEMPTED_TIME_EXPIRED" -> R.color.status_expired
                status == "RETAKE_GRANTED" -> R.color.status_retake
                status == "ACCESS_REVOKED" -> R.color.status_expired
                status == "NOT_STARTED" -> R.color.status_not_started
                else -> android.R.color.black
            }
            tvStatus.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
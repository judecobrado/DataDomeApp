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
    private val onIntegrityClick: (StudentMonitoringData) -> Unit,
    private val onAccessControlClick: (StudentMonitoringData) -> Unit
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
        private val tvStudentId: TextView = itemView.findViewById(R.id.tvStudentId)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvScore: TextView = itemView.findViewById(R.id.tvScore)
        private val tvCheats: TextView = itemView.findViewById(R.id.tvCheats)
        private val tvLastUpdate: TextView = itemView.findViewById(R.id.tvLastUpdate)
        // ⭐ UPDATE 2: INALIS ang btnRetake Button mula sa deklarasyon at layout
        // private val btnRetake: Button = itemView.findViewById(R.id.btnRetake) // ALISIN ITO
        private val btnViewIntegrity: ImageButton = itemView.findViewById(R.id.btnViewIntegrity)
        private val btnAccessControl: ImageButton = itemView.findViewById(R.id.btnAccessControl)


        fun bind(data: StudentMonitoringData) {
            tvStudentName.text = data.studentName

            tvStudentId.text = data.id

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

            setupAccessControlButton(data)

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

        private fun setupAccessControlButton(data: StudentMonitoringData) {
            val context = itemView.context

            // ⭐ FIX: Tiyakin na lumabas ang button kahit IN_PROGRESS/CHEATING para sa RESTART
            // Ipakita ang button kung may aksyon man para sa ongoing o finished.
            val isManageable = data.status == "IN_PROGRESS" || data.status == "CHEATING" ||
                    data.status == "COMPLETED" || data.status == "TIME_EXPIRED" ||
                    data.status == "NOT_STARTED" || data.status == "EXAM_READY" ||
                    data.status == "UNATTEMPTED_TIME_EXPIRED" || data.status == "ACCESS_REVOKED"

            val shouldShowButton = isManageable

            if (shouldShowButton) {
                btnAccessControl.visibility = View.VISIBLE
                btnAccessControl.setOnClickListener { onAccessControlClick(data) }

                val iconRes: Int
                val iconColor: Int

                when (data.status) {
                    "IN_PROGRESS", "CHEATING" -> {
                        // Action: RESTART QUIZ (Taking/Cheating)
                        iconRes = R.drawable.ic_security_info // Assuming you have an icon for refresh/restart
                        iconColor = R.color.status_retake // Warning color
                    }
                    "COMPLETED", "TIME_EXPIRED", "CHEATED_MAX" -> {
                        // Action: GRANT RETAKE (Finished)
                        iconRes = R.drawable.ic_security_info
                        iconColor = R.color.status_retake // Orange/Yellow
                    }
                    "EXAM_READY", "NOT_STARTED", "UNATTEMPTED_TIME_EXPIRED", "ACCESS_REVOKED" -> {
                        // Action: START / OPEN ACCESS (Not yet started/Blocked)
                        iconRes = R.drawable.ic_security_info
                        iconColor = R.color.status_in_progress // Ongoing color
                    }
                    else -> {
                        iconRes = R.drawable.ic_security_info
                        iconColor = android.R.color.darker_gray
                    }
                }

                try {
                    btnAccessControl.setImageResource(iconRes)
                } catch (e: Exception) {
                    // Fallback kung walang ic_refresh
                    btnAccessControl.setImageResource(R.drawable.ic_security_info)
                }
                btnAccessControl.setColorFilter(ContextCompat.getColor(context, iconColor))

            } else {
                // Itago ang button kung walang kailangang gawin
                btnAccessControl.visibility = View.GONE
                btnAccessControl.setOnClickListener(null)
            }
        }

        private fun formatStatus(status: String, cheatCount: Int): String {
            // ⭐ UPDATE 5: STATUS FORMATTING (Idinagdag ang ACCESS_REVOKED)
            return when (status) {
                "IN_PROGRESS" -> if (cheatCount > 0) "CHEATING ALERT ⚠️" else "TAKING ⏱️"
                "COMPLETED" -> "FINISHED ✅"
                "NOT_STARTED" -> "PENDING ⚪"
                "RETAKE_GRANTED" -> "RETAKE ALLOWED 🔄"
                "TIME_EXPIRED" -> "TIME UP 🚨"
                "UNATTEMPTED_TIME_EXPIRED" -> "MISSED ❌"
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
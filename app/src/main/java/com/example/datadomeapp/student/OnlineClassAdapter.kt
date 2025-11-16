package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.OnlineClassAssignment
import com.example.datadomeapp.models.ClassSchedule

class OnlineClassAdapter(private val classList: List<OnlineClassAssignment>) :
    RecyclerView.Adapter<OnlineClassAdapter.OnlineClassViewHolder>() {

    class OnlineClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSubjectCode: TextView = itemView.findViewById(R.id.tvSubjectCode)
        val tvSubjectTitle: TextView = itemView.findViewById(R.id.tvSubjectTitle)
        val tvTeacherName: TextView = itemView.findViewById(R.id.tvTeacherName)
        val tvScheduleTime: TextView = itemView.findViewById(R.id.tvScheduleTime)
        val tvSectionName: TextView = itemView.findViewById(R.id.tvSectionName)
        val tvRoom: TextView = itemView.findViewById(R.id.tvRoom)
        val tvOnlineLinkStatus: TextView = itemView.findViewById(R.id.tvOnlineLinkStatus)
        val llClassItem: LinearLayout = itemView.findViewById(R.id.llClassItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineClassViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_online_class, parent, false)
        return OnlineClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnlineClassViewHolder, position: Int) {
        val classAssignment = classList[position]
        val context = holder.itemView.context

        // Set basic subject info
        holder.tvSubjectCode.text = classAssignment.subjectCode
        holder.tvSubjectTitle.text = classAssignment.subjectTitle
        holder.tvTeacherName.text = classAssignment.teacherName
        holder.tvSectionName.text = classAssignment.sectionName

        // ✅ FIXED: Use classAssignment.allSchedules directly from the data class
        val scheduleText = if (classAssignment.allSchedules.isNotEmpty()) {
            classAssignment.allSchedules.joinToString("\n") { schedule ->
                "${schedule.day} ${schedule.startTime} - ${schedule.endTime}"
            }
        } else {
            "${classAssignment.day} ${classAssignment.startTime} - ${classAssignment.endTime}".trim()
        }
        holder.tvScheduleTime.text = scheduleText

        // ✅ FIXED: Use classAssignment.allSchedules directly from the data class
        val roomText = if (classAssignment.allSchedules.isNotEmpty()) {
            val rooms = classAssignment.allSchedules.map { it.room }.distinct()
            if (rooms.size == 1) {
                "Room: ${rooms.first()}"
            } else {
                "Rooms: ${rooms.joinToString(", ")}"
            }
        } else {
            "Room: ${classAssignment.roomNumber}"
        }
        holder.tvRoom.text = roomText

        val link = classAssignment.onlineClassLink

        if (!link.isNullOrEmpty() && link != "No online class link yet.") {
            // Active / clickable
            holder.llClassItem.isEnabled = true
            holder.llClassItem.alpha = 1f // fully opaque
            holder.tvOnlineLinkStatus.text = "Click to Join Online Class"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#00796B"))
            holder.tvOnlineLinkStatus.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_online, 0, 0, 0
            )

            holder.llClassItem.setOnClickListener {
                val normalizedLink = if (link.startsWith("http")) link else "https://$link"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedLink))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            holder.llClassItem.setOnLongClickListener {
                // Copy link to clipboard
                val clipboard = ContextCompat.getSystemService(context, android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("Online Class Link", link)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Online Link Copied!", Toast.LENGTH_SHORT).show()
                true
            }

        } else {
            // Disabled / faded
            holder.llClassItem.isEnabled = false
            holder.llClassItem.alpha = 0.6f // faded look
            holder.tvOnlineLinkStatus.text = "No Online Link Set"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#D32F2F"))
            holder.tvOnlineLinkStatus.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_offline, 0, 0, 0
            )

            holder.llClassItem.setOnClickListener {
                Toast.makeText(
                    context,
                    "${classAssignment.subjectTitle}: No online link provided yet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            holder.llClassItem.setOnLongClickListener(null)
        }
    }

    override fun getItemCount() = classList.size
}
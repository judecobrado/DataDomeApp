package com.example.datadomeapp.student

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
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

class OnlineClassAdapter(private val classList: List<OnlineClassAssignment>) :
    RecyclerView.Adapter<OnlineClassAdapter.OnlineClassViewHolder>() {

    class OnlineClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSubjectCode: TextView = itemView.findViewById(R.id.tvSubjectCode)
        val tvTeacherName: TextView = itemView.findViewById(R.id.tvTeacherName)
        val tvScheduleTime: TextView = itemView.findViewById(R.id.tvScheduleTime)
        val tvSectionName: TextView = itemView.findViewById(R.id.tvSectionName) // <-- add this
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

        holder.tvSubjectCode.text = "${classAssignment.subjectCode} - ${classAssignment.subjectTitle}"
        holder.tvTeacherName.text = "Teacher: ${classAssignment.teacherName}"
        holder.tvSectionName.text = "Section: ${classAssignment.sectionName ?: "N/A"}"

        val scheduleDisplay = if (classAssignment.day.isNotEmpty()) {
            "${classAssignment.day} ${classAssignment.startTime} - ${classAssignment.endTime}".trim()
        } else {
            "No schedule set"
        }
        holder.tvScheduleTime.text = "Schedule: $scheduleDisplay"

        val link = classAssignment.onlineClassLink

        if (!link.isNullOrEmpty() && link != "No online class link yet.") {
            // Active / clickable
            holder.llClassItem.isEnabled = true
            holder.llClassItem.alpha = 1f // fully opaque
            holder.tvOnlineLinkStatus.text = "Click to Join Online Class"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#00796B"))

            holder.llClassItem.setOnClickListener {
                val normalizedLink = if (link.startsWith("http")) link else "https://$link"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedLink))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open link.", Toast.LENGTH_SHORT).show()
                }
            }

        } else {
            // Disabled / faded
            holder.llClassItem.isEnabled = false
            holder.llClassItem.alpha = 0.5f // faded look
            holder.tvOnlineLinkStatus.text = "No Online Link Set"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#D32F2F"))

            holder.llClassItem.setOnClickListener {
                Toast.makeText(
                    context,
                    "${classAssignment.subjectTitle}: No online link provided yet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun getItemCount() = classList.size
}

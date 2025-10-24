package com.example.datadomeapp.teacher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment

class ClassAdapter(
    private val items: List<ClassAssignment>,
    private val detailClickListener: (ClassAssignment) -> Unit,
    private val setLinkClickListener: (ClassAssignment) -> Unit
) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {

    inner class ClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubjectName)
        val tvSection: TextView = view.findViewById(R.id.tvSectionName)
        val tvSchedule: TextView = view.findViewById(R.id.tvSchedule)
        val tvOnlineLinkStatus: TextView = view.findViewById(R.id.tvOnlineLinkStatus)
        val btnSetLink: Button = view.findViewById(R.id.btnSetLink)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.teacher_item_class_selection, parent, false)
        return ClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val currentItem = items[position]

        // Schedule display
        val scheduleDetail = currentItem.scheduleSlots.values.joinToString(" / ") { slot ->
            "${slot.day} ${slot.startTime}-${slot.endTime} (${slot.roomLocation})"
        }

        // Course & Section display
        val primarySlot = currentItem.scheduleSlots.values.firstOrNull()
        val courseName = currentItem.courseCode ?: "N/A"
        val sectionName = primarySlot?.sectionBlock ?: "A"
        holder.tvSection.text = "Course: $courseName - $sectionName"

        holder.tvSubject.text = "${currentItem.subjectCode ?: "N/A"} - ${currentItem.subjectTitle}"
        holder.tvSchedule.text = "Schedule: $scheduleDetail"

        // Online link display
        val onlineLinkText = if (currentItem.onlineClassLink.isNullOrEmpty()) {
            "Status: 🚫 No Link Set"
        } else {
            "Link: ${currentItem.onlineClassLink}"
        }
        holder.tvOnlineLinkStatus.text = onlineLinkText

        // Set link button text dynamically
        holder.btnSetLink.text = if (currentItem.onlineClassLink.isNullOrEmpty()) {
            "Set Online Link"
        } else {
            "Update"
        }

        // Click to open online link
        holder.tvOnlineLinkStatus.setOnClickListener {
            val rawLink = currentItem.onlineClassLink?.trim()
            if (rawLink.isNullOrEmpty()) {
                Toast.makeText(holder.itemView.context, "No online link available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val url = if (!rawLink.startsWith("http://") && !rawLink.startsWith("https://")) {
                "https://$rawLink"
            } else rawLink

            if (!isValidOnlineLink(url)) {
                Toast.makeText(
                    holder.itemView.context,
                    "Invalid link. Only Google Meet or Zoom links are allowed.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                holder.itemView.context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(holder.itemView.context, "No app found to open this link", Toast.LENGTH_LONG).show()
            }
        }

        // Item click
        holder.itemView.setOnClickListener {
            detailClickListener(currentItem)
        }

        // Set/Update link button click
        holder.btnSetLink.setOnClickListener {
            setLinkClickListener(currentItem)
        }
    }

    override fun getItemCount(): Int = items.size

    // Validate link
    private fun isValidOnlineLink(link: String): Boolean {
        val normalized = link.trim()
        return normalized.contains("meet.google.com") || normalized.contains("zoom.us/j")
    }
}

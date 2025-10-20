package com.example.datadomeapp.student

import android.content.Intent
import android.net.Uri
import android.graphics.Color
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
import com.example.datadomeapp.models.OnlineClassAssignment // Ang bagong data class mo

class OnlineClassAdapter(private val classList: List<OnlineClassAssignment>) :
    RecyclerView.Adapter<OnlineClassAdapter.OnlineClassViewHolder>() {

    class OnlineClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSubjectCode: TextView = itemView.findViewById(R.id.tvSubjectCode)
        val tvSectionName: TextView = itemView.findViewById(R.id.tvSectionName)
        val tvScheduleTime: TextView = itemView.findViewById(R.id.tvScheduleTime)
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

        // I-set ang Subject Code at Section Name
        // Gumagamit tayo ng assignmentId at courseCode na galing sa data mo
        holder.tvSubjectCode.text = "${classAssignment.courseCode} - ${classAssignment.assignmentId}"
        holder.tvSectionName.text = "Section: ${classAssignment.sectionName}"

        // GINAGAWA ANG SCHEDULE STRING MULA SA 3 FIELDS (day, startTime, endTime)
        val scheduleDisplay = "${classAssignment.day} ${classAssignment.startTime} - ${classAssignment.endTime}"
        // Ang roomNumber ay available sa data mo
        holder.tvScheduleTime.text = "Schedule: $scheduleDisplay (Room: ${classAssignment.roomNumber})"

        // ✅ PAGKUHA NG LINK - DITO GAGAMITIN ANG onlineClassLink
        val link = classAssignment.onlineClassLink

        if (!link.isNullOrEmpty()) {
            // May Link
            holder.tvOnlineLinkStatus.text = "Click to Join Online Class"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#00796B"))

            holder.tvOnlineLinkStatus.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(context, R.drawable.ic_link_available),
                null, null, null
            )

            holder.llClassItem.setOnClickListener {
                try {
                    // Tinitiyak na may http/https bago buksan
                    val fullLink = if (link.startsWith("http")) link else "https://$link"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullLink))
                    it.context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("OnlineAdapter", "Error opening link: ${e.message}")
                    Toast.makeText(it.context, "Invalid link or no app to handle: $link", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // Walang Link
            holder.tvOnlineLinkStatus.text = "No Online Link Set"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#D32F2F"))

            holder.tvOnlineLinkStatus.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(context, R.drawable.ic_link_unavailable),
                null, null, null
            )

            holder.llClassItem.setOnClickListener {
                Toast.makeText(it.context, "${classAssignment.courseCode}: Walang Online Class Link pa.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = classList.size
}
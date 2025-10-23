package com.example.datadomeapp.student

import android.content.ActivityNotFoundException
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
import com.example.datadomeapp.models.OnlineClassAssignment

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

        holder.tvSubjectCode.text = "${classAssignment.courseCode} - ${classAssignment.assignmentId}"
        holder.tvSectionName.text = "Section: ${classAssignment.sectionName}"

        val scheduleDisplay = "${classAssignment.day} ${classAssignment.startTime} - ${classAssignment.endTime}"
        holder.tvScheduleTime.text = "Schedule: $scheduleDisplay (Room: ${classAssignment.roomNumber})"

        val link = classAssignment.onlineClassLink

        if (!link.isNullOrEmpty()) {
            holder.tvOnlineLinkStatus.text = "Click to Join Online Class"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#00796B"))
            holder.tvOnlineLinkStatus.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(context, R.drawable.ic_link_available),
                null, null, null
            )

            holder.llClassItem.setOnClickListener {
                val normalizedLink = if (link.startsWith("http")) link else "https://$link"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedLink))
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // ⚠️ If no app installed → open in browser fallback
                    val webFallback = when {
                        link.contains("meet.google.com") -> "https://meet.google.com/"
                        link.contains("zoom.us") -> "https://zoom.us/"
                        else -> normalizedLink
                    }
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)))
                    } catch (ex: Exception) {
                        Toast.makeText(context, "No app or browser found to open this link.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("OnlineAdapter", "Error opening link: ${e.message}")
                    Toast.makeText(context, "Invalid or unsupported link.", Toast.LENGTH_LONG).show()
                }
            }

        } else {
            holder.tvOnlineLinkStatus.text = "No Online Link Set"
            holder.tvOnlineLinkStatus.setTextColor(Color.parseColor("#D32F2F"))
            holder.tvOnlineLinkStatus.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(context, R.drawable.ic_link_unavailable),
                null, null, null
            )
            holder.llClassItem.setOnClickListener {
                Toast.makeText(context, "${classAssignment.courseCode}: No online link provided yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = classList.size
}

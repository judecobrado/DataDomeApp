package com.example.datadomeapp.teacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button // NEW IMPORT
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment

class ClassAdapter(
    private val classList: List<ClassAssignment>,
    // 1. Existing Listener: Para sa pagpunta sa Class Details (kapag clinick ang buong item)
    private val detailClickListener: (ClassAssignment) -> Unit,
    // ✅ 2. NEW Listener: Para sa pag-click ng "Set Link" Button
    private val setLinkClickListener: (ClassAssignment) -> Unit
) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {

    class ClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubjectName)
        val tvSection: TextView = view.findViewById(R.id.tvSectionName)
        val tvSchedule: TextView = view.findViewById(R.id.tvSchedule)
        // ✅ NEW VIEWS
        val tvOnlineLinkStatus: TextView = view.findViewById(R.id.tvOnlineLinkStatus)
        val btnSetLink: Button = view.findViewById(R.id.btnSetOnlineLink)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.teacher_item_class_selection, parent, false)
        return ClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val currentItem = classList[position]

        // --- Existing Bind Logic ---
        val scheduleString = "${currentItem.day} ${currentItem.startTime}-${currentItem.endTime}"
        holder.tvSubject.text = currentItem.subjectTitle
        holder.tvSection.text = "Section: ${currentItem.sectionName}"
        // NOTE: Pinalitan ko ang enrolledCount/maxCapacity dahil wala ito sa iyong model definition
        holder.tvSchedule.text = "Schedule: $scheduleString"
        // ---------------------------

        // ✅ NEW: Bind Logic para sa Online Link
        if (currentItem.onlineClassLink.isNotEmpty()) {
            holder.tvOnlineLinkStatus.text = "Status: 🔗 Link Set"
            holder.btnSetLink.text = "Update Link"
        } else {
            holder.tvOnlineLinkStatus.text = "Status: 🚫 No Link Set"
            holder.btnSetLink.text = "Set Link"
        }

        // 1. Bind Existing Detail Listener (sa buong item)
        holder.itemView.setOnClickListener {
            detailClickListener(currentItem)
        }

        // 2. Bind NEW Set Link Listener (sa button lang)
        holder.btnSetLink.setOnClickListener {
            setLinkClickListener(currentItem)
        }
    }

    override fun getItemCount() = classList.size
}
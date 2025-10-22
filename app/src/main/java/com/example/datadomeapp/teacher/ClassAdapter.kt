package com.example.datadomeapp.teacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment // I-check kung tama ang path na ito!

class ClassAdapter(
    private val items: List<ClassAssignment>,
    private val detailClickListener: (ClassAssignment) -> Unit, // 1st listener (Item Click)
    private val setLinkClickListener: (ClassAssignment) -> Unit // 2nd listener (Button Click)
) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {

    inner class ClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubjectName) // Subject Title
        val tvSection: TextView = view.findViewById(R.id.tvSectionName)
        val tvSchedule: TextView = view.findViewById(R.id.tvSchedule)
        val tvOnlineLinkStatus: TextView = view.findViewById(R.id.tvOnlineLinkStatus)
        val btnSetLink: TextView = view.findViewById(R.id.btnSetLink) // Button or TextView for setting link

        // Dagdag na TextViews (Kung ginagamit sa layout)
        // val tvTeacher: TextView = view.findViewById(R.id.tvTeacherName)
        // val tvCapacity: TextView = view.findViewById(R.id.tvCapacity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        // I-check kung tama ang layout file na ginagamit (teacher_item_class_selection)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.teacher_item_class_selection, parent, false)
        return ClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val currentItem = items[position]

        // 🟢 FIX 1: I-join ang LAHAT ng schedule slots para sa kumpletong detalye
        val scheduleDetail = currentItem.scheduleSlots.values.joinToString(" / ") { slot ->
            "${slot.day} ${slot.startTime}-${slot.endTime} (${slot.roomLocation})"
        }

        // Kukunin ang section block mula sa unang slot (Kung gagamitin ang field na 'sectionBlock')
        val sectionBlock = currentItem.scheduleSlots.values.firstOrNull()?.sectionBlock ?: "N/A"

        // --- Schedule & Section Display ---
        holder.tvSubject.text = currentItem.subjectTitle
        holder.tvSection.text = "Section: $sectionBlock"
        holder.tvSchedule.text = "Schedule: $scheduleDetail"
        // --- End Schedule Logic ---

        // 🟢 FIX 2: Gamitin ang tamang field name (assuming 'onlineLink' ang final name sa ClassAssignment model)
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

    override fun getItemCount(): Int = items.size
}
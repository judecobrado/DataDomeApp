package com.example.datadomeapp.student

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.StudentSubject

class SubjectRecyclerAdapter(
    // Ang click listener ay ipinapasa mula sa Activity
    private val subjects: MutableList<StudentSubject>,
    private val onItemClick: (StudentSubject) -> Unit
) : RecyclerView.Adapter<SubjectRecyclerAdapter.SubjectViewHolder>() {

    private val assignmentCounts = mutableMapOf<String, Int>()

    /**
     * I-update ang assignment count at i-refresh ang listahan
     */
    fun updateAssignmentCount(assignmentNo: String, count: Int) {
        assignmentCounts[assignmentNo] = count
        notifyDataSetChanged()
    }

    // --- 1. ViewHolder Class ---
    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Tiyaking tama ang ID references mula sa item_subject_custom.xml
        val tvSubjectTitleCode: TextView = itemView.findViewById(R.id.tvSubjectTitleCode)
        val tvSectionBlock: TextView = itemView.findViewById(R.id.tvSectionBlock)
        val tvAssignmentCount: TextView = itemView.findViewById(R.id.tvAssignmentCount)
        val ivSubjectIcon: ImageView = itemView.findViewById(R.id.ivSubjectIcon)
        val ivSectionIcon: ImageView = itemView.findViewById(R.id.ivSectionIcon)
        val ivAssignmentIcon: ImageView = itemView.findViewById(R.id.ivAssignmentIcon)

        fun bind(subject: StudentSubject) {
            itemView.setOnClickListener { onItemClick(subject) }
        }
    }

    // --- 2. Create View Holder ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        // Dito ini-inflate ang CardView item layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject, parent, false)
        return SubjectViewHolder(view)
    }

    // --- 3. Bind View Holder (Data Population) ---
    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjects[position]
        holder.bind(subject)

        // 1. Subject Title and Code
        holder.tvSubjectTitleCode.text = "${subject.subjectCode} - ${subject.subjectTitle}"

        // 2. Section/Block Info
        val sectionInfo = if (subject.sectionBlock.isNotEmpty() || subject.sectionName.isNotEmpty()) {
            val block = subject.sectionBlock.takeIf { it.isNotEmpty() } ?: ""
            val name = subject.sectionName.takeIf { it.isNotEmpty() } ?: ""
            "Section: $block${if (block.isNotEmpty() && name.isNotEmpty()) " - " else ""}$name"
        } else {
            "No Section Info"
        }
        holder.tvSectionBlock.text = sectionInfo

        // 3. Assignment Count
        val count = assignmentCounts[subject.assignmentNo]
        holder.tvAssignmentCount.text = when {
            count != null && count >= 0 -> "Assignments: $count"
            else -> "Assignments: Loading..."
        }

        // 4. Icon Setting (Optional, depende kung ginamit niyo ang icons)
        holder.ivSubjectIcon.setImageResource(R.drawable.ic_security_info)
        holder.ivSectionIcon.setImageResource(R.drawable.ic_security_info)
        holder.ivAssignmentIcon.setImageResource(R.drawable.ic_security_info)
    }

    // --- 4. Item Count ---
    override fun getItemCount(): Int = subjects.size
}
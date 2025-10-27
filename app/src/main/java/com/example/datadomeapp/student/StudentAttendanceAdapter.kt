package com.example.datadomeapp.student

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.util.Locale

// StudentSubjectAttendance is implicitly resolved because it's in the same package (StudentDataModels.kt)

class StudentAttendanceAdapter(private val list: List<StudentSubjectAttendance>) :
    RecyclerView.Adapter<StudentAttendanceAdapter.AttendanceSummaryViewHolder>() {

    class AttendanceSummaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSubject: TextView = itemView.findViewById(R.id.tvAttSubject)
        val tvSummary: TextView = itemView.findViewById(R.id.tvAttSummary)
        val tvPercentage: TextView = itemView.findViewById(R.id.tvAttPercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceSummaryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_attendance_summary, parent, false)
        return AttendanceSummaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceSummaryViewHolder, position: Int) {
        val item = list[position]
        val context = holder.itemView.context

        holder.tvSubject.text = "${item.subjectCode} - ${item.subjectTitle}"

        holder.tvSummary.text =
            "Classes: ${item.totalClasses} | " +
                    "Present: ${item.totalPresent} | " +
                    "Absent: ${item.totalAbsent} | " +
                    "Late: ${item.totalLate} | " +
                    "Excused: ${item.totalExcused}"

        holder.tvPercentage.text = String.format(Locale.US, "%.2f%%", item.attendancePercentage)

        val colorResId = if (item.attendancePercentage >= 80.0)
            R.color.color_success
        else
            R.color.color_warning

        holder.tvPercentage.setTextColor(context.getColor(colorResId))
    }

    override fun getItemCount() = list.size
}
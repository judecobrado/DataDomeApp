package com.example.datadomeapp.student

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

class StudentAttendanceAdapter(private val list: List<StudentSubjectAttendance>) :
    RecyclerView.Adapter<StudentAttendanceAdapter.AttendanceSummaryViewHolder>() {

    class AttendanceSummaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSubjectCode: TextView = itemView.findViewById(R.id.tvSubjectCode)
        val tvSubjectTitle: TextView = itemView.findViewById(R.id.tvSubjectTitle)
        val tvAttendancePercentage: TextView = itemView.findViewById(R.id.tvAttendancePercentage)
        val progressAttendance: LinearProgressIndicator = itemView.findViewById(R.id.progressAttendance)
        val tvPresentCount: TextView = itemView.findViewById(R.id.tvPresentCount)
        val tvAbsentCount: TextView = itemView.findViewById(R.id.tvAbsentCount)
        val tvLateCount: TextView = itemView.findViewById(R.id.tvLateCount)
        val tvExcusedCount: TextView = itemView.findViewById(R.id.tvExcusedCount)
        val tvTotalClasses: TextView = itemView.findViewById(R.id.tvTotalClasses)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceSummaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance_summary, parent, false)
        return AttendanceSummaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceSummaryViewHolder, position: Int) {
        val item = list[position]
        val context = holder.itemView.context

        holder.tvSubjectCode.text = item.subjectCode
        holder.tvSubjectTitle.text = item.subjectTitle

        val percentageInt = item.attendancePercentage.toInt()
        holder.tvAttendancePercentage.text = "$percentageInt%"
        holder.progressAttendance.progress = percentageInt

        holder.tvPresentCount.text = item.totalPresent.toString()
        holder.tvAbsentCount.text = item.totalAbsent.toString()
        holder.tvLateCount.text = item.totalLate.toString()
        holder.tvExcusedCount.text = item.totalExcused.toString()
        holder.tvTotalClasses.text = item.totalClasses.toString()

        when {
            item.attendancePercentage >= 90 -> {
                holder.tvStatus.text = "Excellent"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.excellent_green)) // FIXED: Added closing parenthesis
                holder.tvAttendancePercentage.setTextColor(ContextCompat.getColor(context, R.color.excellent_green))
            }
            item.attendancePercentage >= 80 -> {
                holder.tvStatus.text = "Good"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.good_green))
                holder.tvAttendancePercentage.setTextColor(ContextCompat.getColor(context, R.color.good_green))
            }
            item.attendancePercentage >= 75 -> {
                holder.tvStatus.text = "Fair"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.fair_orange))
                holder.tvAttendancePercentage.setTextColor(ContextCompat.getColor(context, R.color.fair_orange))
            }
            else -> {
                holder.tvStatus.text = "Poor"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.poor_red))
                holder.tvAttendancePercentage.setTextColor(ContextCompat.getColor(context, R.color.poor_red))
            }
        }
    }

    override fun getItemCount() = list.size
}
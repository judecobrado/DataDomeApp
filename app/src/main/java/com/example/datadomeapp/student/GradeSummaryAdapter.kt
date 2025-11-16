package com.example.datadomeapp.student

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R

class GradeSummaryAdapter(private var gradeList: List<GradeSummary>) :
    RecyclerView.Adapter<GradeSummaryAdapter.GradeViewHolder>() {

    class GradeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubjectCode: TextView = view.findViewById(R.id.tvSubjectCode)
        val tvSubjectTitle: TextView = view.findViewById(R.id.tvSubjectTitle)
        val tvPrelimGrade: TextView = view.findViewById(R.id.tvPrelimGrade)
        val tvMidtermGrade: TextView = view.findViewById(R.id.tvMidtermGrade)
        val tvFinalGrade: TextView = view.findViewById(R.id.tvFinalGrade)
        val tvAverageGrade: TextView = view.findViewById(R.id.tvAverageGrade)
        val tvGradePoint: TextView = view.findViewById(R.id.tvGradePoint)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade_summary, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val grade = gradeList[position]

        holder.tvSubjectCode.text = grade.subjectCode
        holder.tvSubjectTitle.text = grade.subjectTitle

        // Display grades or asterisks if not available
        holder.tvPrelimGrade.text = if (grade.prelimGrade >= 0) "%.2f%%".format(grade.prelimGrade) else "*"
        holder.tvMidtermGrade.text = if (grade.midtermGrade >= 0) "%.2f%%".format(grade.midtermGrade) else "*"
        holder.tvFinalGrade.text = if (grade.finalGrade >= 0) "%.2f%%".format(grade.finalGrade) else "*"

        // ONLY SHOW FINAL COMPUTATIONS IF FINALS GRADE IS AVAILABLE
        if (grade.finalGrade >= 0 && grade.prelimGrade >= 0 && grade.midtermGrade >= 0) {
            // All grades are available - show final computations
            holder.tvAverageGrade.text = "%.2f%%".format(grade.averageGrade)

            val gradePoint = calculateGradePoint(grade.averageGrade)
            val status = getStatus(grade.averageGrade)

            holder.tvGradePoint.text = gradePoint
            holder.tvStatus.text = status

            // Set colors
            val statusColor = if (status == "Passed") Color.parseColor("#2E7D32") else Color.parseColor("#D32F2F")
            holder.tvStatus.setTextColor(statusColor)

            val gradePointColor = when (gradePoint) {
                "1.00", "1.25", "1.50", "1.75" -> Color.parseColor("#2E7D32") // Dark Green
                "2.00", "2.25", "2.50", "2.75" -> Color.parseColor("#FF9800") // Orange
                "3.00" -> Color.parseColor("#1976D2") // Blue
                else -> Color.parseColor("#D32F2F") // Red for 5.00 - Failed
            }
            holder.tvGradePoint.setTextColor(gradePointColor)
            holder.tvAverageGrade.setTextColor(Color.parseColor("#333333"))
        } else {
            // Some grades are missing - show asterisks
            holder.tvAverageGrade.text = "*"
            holder.tvGradePoint.text = "*"
            holder.tvStatus.text = "*"

            // Set gray color for placeholder values
            val grayColor = Color.parseColor("#999999")
            holder.tvStatus.setTextColor(grayColor)
            holder.tvGradePoint.setTextColor(grayColor)
            holder.tvAverageGrade.setTextColor(grayColor)
        }
    }

    private fun calculateGradePoint(finalGrade: Double): String {
        return when {
            finalGrade >= 99 -> "1.00"
            finalGrade >= 97 -> "1.25"
            finalGrade >= 94 -> "1.50"
            finalGrade >= 90 -> "1.75"
            finalGrade >= 87 -> "2.00"
            finalGrade >= 84 -> "2.25"
            finalGrade >= 81 -> "2.50"
            finalGrade >= 78 -> "2.75"
            finalGrade >= 75 -> "3.00"
            else -> "5.00"
        }
    }

    private fun getStatus(finalGrade: Double): String {
        return if (finalGrade >= 75) "Passed" else "Failed"
    }

    override fun getItemCount(): Int = gradeList.size

    fun updateGrades(newGradeList: List<GradeSummary>) {
        gradeList = newGradeList
        notifyDataSetChanged()
    }
}
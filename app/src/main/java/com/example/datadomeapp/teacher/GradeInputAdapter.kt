package com.example.datadomeapp.teacher

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student

class GradeInputAdapter(
    private val gradeDataList: List<GradeData>,
    private val listener: OnStudentClickListener,
    private var isPublished: Boolean = false
) : RecyclerView.Adapter<GradeInputAdapter.GradeViewHolder>() {

    fun setPublishedState(published: Boolean) {
        isPublished = published
        notifyDataSetChanged()
    }

    fun isGradesPublished(): Boolean = isPublished

    // Map for easy access using StudentDocId (StudentDocId -> GradeData)
    private val studentGrades: MutableMap<String, GradeData> =
        gradeDataList.associateBy { it.studentDocId }.mapValues { (_, gradeData) -> gradeData.copy() }.toMutableMap()

    // List for display purposes
    private val studentsForDisplay: List<GradeData> = gradeDataList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade_input, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val gradeData = studentsForDisplay[position]
        holder.bind(gradeData)

        // Create Student object for click listener
        val studentForClick = Student(
            id = gradeData.studentDocId,
            firstName = gradeData.firstName,
            lastName = gradeData.lastName
        )

        // Add Click Listener to the entire item view
        holder.itemView.setOnClickListener {
            listener.onStudentClicked(studentForClick)
        }
    }

    override fun getItemCount(): Int = studentsForDisplay.size

    /**
     * Get all updated grades (including finalGrade) for saving to Firestore
     */
    fun getCurrentGrades(): MutableMap<String, GradeData> {
        return studentGrades
    }

    /**
     * Update student grade when detailed scores are modified
     */
    fun updateStudentGrade(studentId: String, newQuiz: Double, newExam: Double, newAssignment: Double) {
        studentGrades[studentId]?.let { gradeData ->
            gradeData.quiz = newQuiz
            gradeData.exam = newExam
            gradeData.assignment = newAssignment
            // Recalculate final grade
            calculateTotal(gradeData)
        }
        notifyDataSetChanged()
    }

    /**
     * Update student attendance and recitation when modified in detailed dialog
     */
    fun updateStudentAttendanceRecitation(studentId: String, newAttendance: Double, newRecitation: Double) {
        studentGrades[studentId]?.let { gradeData ->
            gradeData.attendance = newAttendance
            gradeData.recitation = newRecitation
            // Recalculate final grade
            calculateTotal(gradeData)
        }
        notifyDataSetChanged()
    }

    inner class GradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStudentName: TextView = itemView.findViewById(R.id.tvStudentName)
        private val tvAttendance: TextView = itemView.findViewById(R.id.tvAttendance)
        private val tvQuiz: TextView = itemView.findViewById(R.id.tvQuiz)
        private val tvRecitation: TextView = itemView.findViewById(R.id.tvRecitation)
        private val tvAssignment: TextView = itemView.findViewById(R.id.tvAssignment)
        private val tvExam: TextView = itemView.findViewById(R.id.tvExam)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvTotal)

        fun bind(gradeData: GradeData) {
            tvStudentName.text = "${gradeData.lastName}, ${gradeData.firstName}"

            val grades = studentGrades[gradeData.studentDocId]!! // Get using Doc ID

            // Set values to TextViews
            tvAttendance.text = String.format("%.2f", grades.attendance)
            tvQuiz.text = String.format("%.2f", grades.quiz)
            tvRecitation.text = String.format("%.2f", grades.recitation)
            tvAssignment.text = String.format("%.2f", grades.assignment)
            tvExam.text = String.format("%.2f", grades.exam)
            tvTotal.text = calculateTotal(grades)

            if (isPublished) {
                itemView.alpha = 0.7f
                itemView.isEnabled = false
            } else {
                itemView.alpha = 1.0f
                itemView.isEnabled = true
            }
        }

        private fun calculateTotal(grades: GradeData): String {
            // Weights based on a standard 100% component grading system
            val WEIGHT_ATTENDANCE = 0.10 // 10%
            val WEIGHT_RECITATION = 0.10  // 10%
            val WEIGHT_QUIZ = 0.20        // 20%
            val WEIGHT_ASSIGNMENT = 0.20  // 20%
            val WEIGHT_EXAM = 0.40        // 40%

            val attendance = grades.attendance ?: 0.0
            val quiz = grades.quiz ?: 0.0
            val recitation = grades.recitation ?: 0.0
            val assignment = grades.assignment ?: 0.0
            val exam = grades.exam ?: 0.0

            val computedTotal = (attendance * WEIGHT_ATTENDANCE) +
                    (quiz * WEIGHT_QUIZ) +
                    (recitation * WEIGHT_RECITATION) +
                    (assignment * WEIGHT_ASSIGNMENT) +
                    (exam * WEIGHT_EXAM)

            // Save computedTotal to GradeData before displaying
            grades.finalGrade = String.format("%.2f", computedTotal).toDouble()

            return String.format("%.2f", computedTotal)
        }
    }

    /**
     * Helper function to calculate total grade (accessible from both ViewHolder and adapter)
     */
    private fun calculateTotal(grades: GradeData): String {
        // Weights based on a standard 100% component grading system
        val WEIGHT_ATTENDANCE = 0.10 // 10%
        val WEIGHT_RECITATION = 0.10  // 10%
        val WEIGHT_QUIZ = 0.20        // 20%
        val WEIGHT_ASSIGNMENT = 0.20  // 20%
        val WEIGHT_EXAM = 0.40        // 40%

        val attendance = grades.attendance ?: 0.0
        val quiz = grades.quiz ?: 0.0
        val recitation = grades.recitation ?: 0.0
        val assignment = grades.assignment ?: 0.0
        val exam = grades.exam ?: 0.0

        val computedTotal = (attendance * WEIGHT_ATTENDANCE) +
                (quiz * WEIGHT_QUIZ) +
                (recitation * WEIGHT_RECITATION) +
                (assignment * WEIGHT_ASSIGNMENT) +
                (exam * WEIGHT_EXAM)

        // Save computedTotal to GradeData before displaying
        grades.finalGrade = String.format("%.2f", computedTotal).toDouble()

        return String.format("%.2f", computedTotal)
    }

    /**
     * Data class to store all grade components and context
     */
    data class GradeData(
        val studentDocId: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val subjectId: String = "",
        val gradingPeriod: String = "",
        // Using 50.0 as default floor score for all categories
        var attendance: Double = 50.0,
        var recitation: Double = 50.0,
        var quiz: Double = 50.0,
        var exam: Double = 50.0,
        var assignment: Double = 50.0,
        var finalGrade: Double = 0.0
    )
}
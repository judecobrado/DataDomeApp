package com.example.datadomeapp.teacher

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student

class GradeInputAdapter(
    private val students: List<Student>,
    private val gradingPeriod: String
) : RecyclerView.Adapter<GradeInputAdapter.GradeViewHolder>() {

    // You can store temporary grades here if needed
    private val studentGrades: MutableMap<String, GradeData> = mutableMapOf()

    init {
        // Initialize with empty grades
        students.forEach {
            studentGrades[it.id] = GradeData()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade_input, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val student = students[position]
        holder.bind(student)
    }

    override fun getItemCount(): Int = students.size

    inner class GradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStudentName: TextView = itemView.findViewById(R.id.tvStudentName)
        private val etAttendance: EditText = itemView.findViewById(R.id.etAttendance)
        private val etQuiz: EditText = itemView.findViewById(R.id.etQuiz)
        private val etRecitation: EditText = itemView.findViewById(R.id.etRecitation)
        private val etAssignment: EditText = itemView.findViewById(R.id.etAssignment)
        private val etExam: EditText = itemView.findViewById(R.id.etExam)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvTotal)

        fun bind(student: Student) {
            tvStudentName.text = "${student.lastName}, ${student.firstName}"

            val grades = studentGrades[student.id]!!

            // Set existing values
            etAttendance.setText(grades.attendance?.toString() ?: "")
            etQuiz.setText(grades.quiz?.toString() ?: "")
            etRecitation.setText(grades.recitation?.toString() ?: "")
            etAssignment.setText(grades.assignment?.toString() ?: "")
            etExam.setText(grades.exam?.toString() ?: "")

            // TextWatchers to update values
            etAttendance.addTextChangedListener(createTextWatcher(student.id, "attendance", tvTotal))
            etQuiz.addTextChangedListener(createTextWatcher(student.id, "quiz", tvTotal))
            etRecitation.addTextChangedListener(createTextWatcher(student.id, "recitation", tvTotal))
            etAssignment.addTextChangedListener(createTextWatcher(student.id, "assignment", tvTotal))
            etExam.addTextChangedListener(createTextWatcher(student.id, "exam", tvTotal))

            // Compute initial total
            tvTotal.text = calculateTotal(grades)
        }

        private fun createTextWatcher(studentId: String, field: String, tvTotal: TextView): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val value = s.toString().toDoubleOrNull()
                    val grades = studentGrades[studentId] ?: GradeData()
                    when (field) {
                        "attendance" -> grades.attendance = value
                        "quiz" -> grades.quiz = value
                        "recitation" -> grades.recitation = value
                        "assignment" -> grades.assignment = value
                        "exam" -> grades.exam = value
                    }
                    studentGrades[studentId] = grades
                    tvTotal.text = calculateTotal(grades)
                }
            }
        }

        private fun calculateTotal(grades: GradeData): String {
            // Example weights:
            // Attendance 10%, Quiz 20%, Recitation 10%, Assignment 20%, Exam 40%
            val attendance = grades.attendance ?: 0.0
            val quiz = grades.quiz ?: 0.0
            val recitation = grades.recitation ?: 0.0
            val assignment = grades.assignment ?: 0.0
            val exam = grades.exam ?: 0.0

            val total = attendance * 0.1 + quiz * 0.2 + recitation * 0.1 + assignment * 0.2 + exam * 0.4
            return "%.2f".format(total)
        }
    }

    // Helper class to store grades temporarily
    data class GradeData(
        var attendance: Double? = null,
        var quiz: Double? = null,
        var recitation: Double? = null,
        var assignment: Double? = null,
        var exam: Double? = null
    )
}

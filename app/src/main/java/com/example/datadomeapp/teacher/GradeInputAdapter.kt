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
    private val gradingPeriod: String,
    private val initialGrades: MutableMap<String, GradeData>
) : RecyclerView.Adapter<GradeInputAdapter.GradeViewHolder>() {

    private val studentGrades: MutableMap<String, GradeData> = initialGrades.mapValues { (_, gradeData) -> gradeData.copy() }.toMutableMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        // Tiyakin na ang layout file na ito ay meron at tama ang IDs.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade_input, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val student = students[position]
        holder.bind(student)
    }

    override fun getItemCount(): Int = students.size

    /**
     * Kukunin ang lahat ng updated grades (kasama ang finalGrade) para i-save sa Firestore.
     */
    fun getCurrentGrades(): MutableMap<String, GradeData> {
        return studentGrades
    }

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
            tvTotal.text = calculateTotal(grades) // Compute initial total

            // I-clear muna ang mga lumang TextWatchers kung meron
            // (Ito ay kailangan para maiwasan ang multiple listener calls sa RecyclerView)
            etAttendance.removeTextChangedListener(etAttendance.tag as? TextWatcher)
            etQuiz.removeTextChangedListener(etQuiz.tag as? TextWatcher)
            etRecitation.removeTextChangedListener(etRecitation.tag as? TextWatcher)
            etAssignment.removeTextChangedListener(etAssignment.tag as? TextWatcher)
            etExam.removeTextChangedListener(etExam.tag as? TextWatcher)


            // I-set up ang mga bagong TextWatchers at i-store ang reference sa tag
            val attendanceWatcher = createTextWatcher(student.id, "attendance", tvTotal)
            etAttendance.addTextChangedListener(attendanceWatcher)
            etAttendance.tag = attendanceWatcher

            val quizWatcher = createTextWatcher(student.id, "quiz", tvTotal)
            etQuiz.addTextChangedListener(quizWatcher)
            etQuiz.tag = quizWatcher

            val recitationWatcher = createTextWatcher(student.id, "recitation", tvTotal)
            etRecitation.addTextChangedListener(recitationWatcher)
            etRecitation.tag = recitationWatcher

            val assignmentWatcher = createTextWatcher(student.id, "assignment", tvTotal)
            etAssignment.addTextChangedListener(assignmentWatcher)
            etAssignment.tag = assignmentWatcher

            val examWatcher = createTextWatcher(student.id, "exam", tvTotal)
            etExam.addTextChangedListener(examWatcher)
            etExam.tag = examWatcher

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

            // I-save ang computedTotal sa GradeData bago i-display
            grades.finalGrade = "%.2f".format(computedTotal).toDouble()

            return "%.2f".format(computedTotal)
        }
    }

    /**
     * Helper class to store all grade components.
     * Dapat pareho ang field names dito sa ginamit sa GradeInputActivity save logic.
     */
    data class GradeData(
        var attendance: Double? = null,
        var quiz: Double? = null,
        var recitation: Double? = null,
        var assignment: Double? = null,
        var exam: Double? = null,
        var finalGrade: Double? = null
    )
}
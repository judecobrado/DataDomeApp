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
import com.example.datadomeapp.models.Student // Still needed for the OnStudentClickListener interface

class GradeInputAdapter(
    // ✅ FIX 1: Palitan ang input parameter sa List<GradeData>
    private val gradeDataList: List<GradeData>,
    private val listener: OnStudentClickListener
) : RecyclerView.Adapter<GradeInputAdapter.GradeViewHolder>() {

    // ✅ FIX 2: Gawing map ang listahan para madali ang pag-access gamit ang Doc ID.
    // Kopyahin ang initial grades para sa local modification (StudentDocId -> GradeData)
    private val studentGrades: MutableMap<String, GradeData> =
        gradeDataList.associateBy { it.studentDocId }.mapValues { (_, gradeData) -> gradeData.copy() }.toMutableMap()

    // ✅ FIX 3: Gamitin ang gradeDataList para sa display.
    private val studentsForDisplay: List<GradeData> = gradeDataList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grade_input, parent, false)
        return GradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: GradeViewHolder, position: Int) {
        val gradeData = studentsForDisplay[position] // ✅ FIX 4: Kumuha ng GradeData
        holder.bind(gradeData)

        // ✅ FIX 5: Gumawa ng Student object mula sa GradeData para ipasa sa listener
        val studentForClick = Student(
            id = gradeData.studentDocId,
            firstName = gradeData.firstName,
            lastName = gradeData.lastName
        )

        // --- NEW: Add Click Listener to the entire item view ---
        holder.itemView.setOnClickListener {
            listener.onStudentClicked(studentForClick)
        }
    }

    override fun getItemCount(): Int = studentsForDisplay.size // ✅ FIX 6: Use studentsForDisplay

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

        // ✅ FIX 7: Binago ang input parameter sa GradeData
        fun bind(gradeData: GradeData) {
            tvStudentName.text = "${gradeData.lastName}, ${gradeData.firstName}"

            val grades = studentGrades[gradeData.studentDocId]!! // ✅ Kinuha gamit ang Doc ID

            // Set existing values. Removed ?: "" since default is 50.0
            etAttendance.setText(grades.attendance.toString())
            etQuiz.setText(grades.quiz.toString())
            etRecitation.setText(grades.recitation.toString())
            etAssignment.setText(grades.assignment.toString())
            etExam.setText(grades.exam.toString())
            tvTotal.text = calculateTotal(grades) // Compute initial total

            // I-clear muna ang mga lumang TextWatchers kung meron
            etAttendance.removeTextChangedListener(etAttendance.tag as? TextWatcher)
            etQuiz.removeTextChangedListener(etQuiz.tag as? TextWatcher)
            etRecitation.removeTextChangedListener(etRecitation.tag as? TextWatcher)
            etAssignment.removeTextChangedListener(etAssignment.tag as? TextWatcher)
            etExam.removeTextChangedListener(etExam.tag as? TextWatcher)

            // ✅ FIX 8: Gumamit ng StudentDocId para sa Text Watcher
            val studentDocId = gradeData.studentDocId

            // I-set up ang mga bagong TextWatchers at i-store ang reference sa tag
            val attendanceWatcher = createTextWatcher(studentDocId, "attendance", tvTotal)
            etAttendance.addTextChangedListener(attendanceWatcher)
            etAttendance.tag = attendanceWatcher

            val quizWatcher = createTextWatcher(studentDocId, "quiz", tvTotal)
            etQuiz.addTextChangedListener(quizWatcher)
            etQuiz.tag = quizWatcher

            val recitationWatcher = createTextWatcher(studentDocId, "recitation", tvTotal)
            etRecitation.addTextChangedListener(recitationWatcher)
            etRecitation.tag = recitationWatcher

            val assignmentWatcher = createTextWatcher(studentDocId, "assignment", tvTotal)
            etAssignment.addTextChangedListener(assignmentWatcher)
            etAssignment.tag = assignmentWatcher

            val examWatcher = createTextWatcher(studentDocId, "exam", tvTotal)
            etExam.addTextChangedListener(examWatcher)
            etExam.tag = examWatcher

        }

        private fun createTextWatcher(studentId: String, field: String, tvTotal: TextView): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {

                    val grades = studentGrades[studentId] ?: GradeData()

                    // 1. Convert text to Double?
                    val inputScore = s.toString().toDoubleOrNull()

                    // 2. Safely assign the value:
                    // Since all defaults are 50.0, any invalid input (like "") can be treated as 50.0
                    val safeValue = inputScore ?: 50.0

                    when (field) {
                        "attendance" -> grades.attendance = safeValue
                        "quiz" -> grades.quiz = safeValue
                        "recitation" -> grades.recitation = safeValue
                        "assignment" -> grades.assignment = safeValue
                        "exam" -> grades.exam = safeValue
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
     * Helper class to store all grade components and context.
     */
    data class GradeData(
        val studentDocId: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val subjectId: String = "",
        val gradingPeriod: String = "",
        // Ginagamit ang 50.0 bilang default floor score para sa lahat ng kategorya
        var attendance: Double = 50.0,
        var recitation: Double = 50.0,
        var quiz: Double = 50.0,
        var exam: Double = 50.0,
        var assignment: Double = 50.0,
        var finalGrade: Double = 0.0
    )
}
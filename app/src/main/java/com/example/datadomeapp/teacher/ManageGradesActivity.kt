package com.example.datadomeapp.teacher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R

class ManageGradesActivity : AppCompatActivity() {

    private lateinit var tvGradesHeader: TextView
    private lateinit var btnPrelim: Button
    private lateinit var btnMidterm: Button
    private lateinit var btnFinals: Button

    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_manage_grades) // ⬅️ I-assume na ito ang layout mo

        // --- Kunin ang Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")
        className = intent.getStringExtra("CLASS_NAME")

        // --- View Binding ---
        tvGradesHeader = findViewById(R.id.tvGradesHeader)
        btnPrelim = findViewById(R.id.btnPrelim)
        btnMidterm = findViewById(R.id.btnMidterm)
        btnFinals = findViewById(R.id.btnFinals)

        tvGradesHeader.text = "Manage Grades for\n$className"


        // --- Button Click Listeners ---

        btnPrelim.setOnClickListener {
            // ➡️ Dito papasok ang bagong activity/fragment na magpapakita ng actual grades
            navigateToGradingPeriod("Prelim")
        }

        btnMidterm.setOnClickListener {
            navigateToGradingPeriod("Midterm")
        }

        btnFinals.setOnClickListener {
            navigateToGradingPeriod("Finals")
        }
    }

    /**
     * Ililipat sa isang activity para sa aktwal na pag-input/pag-view ng grades
     * batay sa napiling grading period (Prelim/Midterm/Finals).
     */
    private fun navigateToGradingPeriod(period: String) {
        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || className.isNullOrEmpty()) {
            // Basic check
            return
        }

        // ⚠️ I-assume na GAGAWA ka ng isang bagong Activity (e.g., GradeInputActivity)
        // para sa aktwal na pag-input ng grades
        val intent = Intent(this, GradeInputActivity::class.java) // ⬅️ I-adjust ito
        intent.putExtra("ASSIGNMENT_ID", assignmentId)
        intent.putExtra("SUBJECT_CODE", subjectCode)
        intent.putExtra("CLASS_NAME", className)
        intent.putExtra("GRADING_PERIOD", period) // ⬅️ I-pasa ang Period
        startActivity(intent)
    }
}
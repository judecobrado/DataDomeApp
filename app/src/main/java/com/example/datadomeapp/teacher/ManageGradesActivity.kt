package com.example.datadomeapp.teacher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R // Tiyaking tama ang iyong R import

class ManageGradesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pansamantalang gumamit ng generic layout o gawin ang sarili niyang layout
        setContentView(R.layout.activity_manage_grades)

        // Dito natin ilalagay ang logic para sa pag-manage ng grades sa susunod na hakbang

        // Kuhanin ang Intent Extras:
        val assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        val subjectCode = intent.getStringExtra("SUBJECT_CODE")
        val className = intent.getStringExtra("CLASS_NAME")
    }
}
package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R

class ChooseStudentTypeActivity : AppCompatActivity() {

    private lateinit var btnNewStudent: Button
    private lateinit var btnExistingStudent: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_student_type)

        btnNewStudent = findViewById(R.id.btnNewStudent)
        btnExistingStudent = findViewById(R.id.btnExistingStudent)

        // NEW STUDENT — email verification flow
        btnNewStudent.setOnClickListener {
            startActivity(Intent(this, VerifyEmailActivity::class.java))
        }

        // EXISTING STUDENT — login + enroll flow
        btnExistingStudent.setOnClickListener {
            startActivity(Intent(this, EnrollExistingStudentActivity::class.java))
        }
    }
}

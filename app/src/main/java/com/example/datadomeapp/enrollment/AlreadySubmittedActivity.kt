package com.example.datadomeapp.enrollment

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R

class AlreadySubmittedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_already_submitted)

        val tvMessage = findViewById<TextView>(R.id.tvMessage)
        tvMessage.text = "You have already submitted your enrollment. Thank you!"
    }
}
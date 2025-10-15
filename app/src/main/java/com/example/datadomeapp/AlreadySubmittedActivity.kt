package com.example.datadomeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class AlreadySubmittedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_already_submitted)

        val tvMessage = findViewById<TextView>(R.id.tvMessage)
        tvMessage.text = "You have already submitted your enrollment. Thank you!"
    }
}

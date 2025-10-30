package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
// Import ang iyong main activity class
// 🛑 IMPORTANT: PALITAN ITO NG ACTUAL CLASS NAME NG IYONG MAIN ACTIVITY
import com.example.datadomeapp.MainActivity

class AlreadySubmittedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment_already_submitted)

        val tvMessage = findViewById<TextView>(R.id.tvMessage)
        val btnBackToHome = findViewById<Button>(R.id.btnBackToHome)

        tvMessage.text = "You have already submitted your enrollment. Thank you! Your application is now pending review."

        // 1. Setup Button Listener: Back to Home
        btnBackToHome.setOnClickListener {
            navigateToHome()
        }

        // 2. Override the Phone's Back Button behavior
        // Kapag pinindot ang back button ng phone, mag-navigate din pabalik sa Home.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome()
            }
        })
    }

    // Function para sa pag-navigate pabalik sa main/home screen
    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        // Linisin ang back stack upang hindi na bumalik sa form/submitted screen
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
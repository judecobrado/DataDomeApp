package com.example.datadomeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Directly go to LoginActivity
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

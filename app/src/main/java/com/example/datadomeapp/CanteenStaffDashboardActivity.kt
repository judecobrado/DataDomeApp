package com.example.datadomeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CanteenStaffDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var tvWelcome: TextView // Declared
    private lateinit var btnLogout: Button // Declared

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_canteen_staff)

        // --- IDINAGDAG/INAYOS NA INITIALIZATION ---
        tvWelcome = findViewById(R.id.tvWelcome) // IN-INITIALIZE
        btnLogout = findViewById(R.id.btnLogout) // IN-INITIALIZE
        // ------------------------------------------

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val firstName = doc.getString("firstName") ?: ""
                    val lastName = doc.getString("lastName") ?: ""
                    // Removed unused email variable
                    tvWelcome.text = "Welcome, $firstName $lastName!"
                } else {
                    tvWelcome.text = "Welcome!"
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching info: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // Dito na ginagamit ang btnLogout, kaya dapat initialized na ito
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
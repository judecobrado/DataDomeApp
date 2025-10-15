package com.example.datadomeapp.canteen

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class MenuItem(
    var id: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var available: Boolean = true,
    var imageUrl: String = "",
    val canteenName: String = "" // NEW: store name
)

class CanteenStaffDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnMenu: Button
    private lateinit var btnOrders: Button
    private lateinit var btnReports: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_canteen_staff)

        tvWelcome = findViewById(R.id.tvWelcome)
        btnLogout = findViewById(R.id.btnLogout)
        btnMenu = findViewById(R.id.btnMenu)
        btnOrders = findViewById(R.id.btnOrders)
        btnReports = findViewById(R.id.btnReports)

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

        // --- MENU MANAGEMENT ---
        btnMenu.setOnClickListener {
            startActivity(Intent(this, MenuManagementActivity::class.java))
        }

        // Orders and Reports buttons can be implemented later
        btnOrders.setOnClickListener {
            Toast.makeText(this, "Order Processing coming soon", Toast.LENGTH_SHORT).show()
        }

        btnReports.setOnClickListener {
            Toast.makeText(this, "Sales Reports coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}
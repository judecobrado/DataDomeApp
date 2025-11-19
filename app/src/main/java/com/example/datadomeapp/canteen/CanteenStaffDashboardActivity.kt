package com.example.datadomeapp.canteen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import com.google.android.material.card.MaterialCardView
import android.widget.LinearLayout
import android.view.View // Import para sa View.GONE at View.VISIBLE
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.LoginActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CanteenStaffDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnMenu: LinearLayout
    private lateinit var btnOrders: LinearLayout
    private lateinit var btnReports: LinearLayout
    private lateinit var btnBalance: LinearLayout
    private lateinit var btnTopUp: LinearLayout

    private var staffUid: String? = null
    private var staffCanteenName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tiyakin na ang R.layout.canteen_dashboard ay mayroon nang 'btnTopUp' ID
        setContentView(R.layout.canteen_dashboard)

        // Initialize Views
        btnLogout = findViewById(R.id.btnLogout)
        btnMenu = findViewById(R.id.btnMenu)
        btnOrders = findViewById(R.id.btnOrders)
        btnReports = findViewById(R.id.btnReports)
        btnBalance = findViewById(R.id.btnBalance)
        btnTopUp = findViewById(R.id.btnTopUp)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        staffUid = currentUser.uid

        // Load Staff Data
        loadStaffData()

        // Setup Button Listeners
        setupButtonListeners()
    }

    private fun loadStaffData() {
        // --- STEP 1: Fetch the Canteen Staff ID from the 'users' collection ---
        firestore.collection("users").document(staffUid!!)
            .get()
            .addOnSuccessListener { doc ->
                val canteenStaffId = doc.getString("canteenStaffId")

                if (canteenStaffId.isNullOrEmpty()) {
                    Toast.makeText(this, "Error: Staff ID missing from user record.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // --- STEP 2: Use the ID to fetch the full data from 'canteen_staff' ---
                fetchFullStaffData(canteenStaffId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching user link: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchFullStaffData(canteenStaffId: String) {
        firestore.collection("canteen_staff").document(canteenStaffId)
            .get()
            .addOnSuccessListener { staffDoc ->
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching staff details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ⭐ NEW FUNCTION: Grouped all button listeners
    private fun setupButtonListeners() {
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnMenu.setOnClickListener {
            val intent = Intent(this, MenuManagementActivity::class.java)
            // Pass the canteen name for context in the next activity
            intent.putExtra("canteenName", staffCanteenName)
            startActivity(intent)
        }

        // ✅ REDIRECT TO TOP-UP ACTIVITY
        btnTopUp.setOnClickListener {
            val intent = Intent(this, TopUpActivity::class.java)
            startActivity(intent)
        }

        btnOrders.setOnClickListener {
            // ⭐ Pinalitan ang Toast ng Intent sa POSActivity
            val intent = Intent(this, POSActivity::class.java)
            startActivity(intent)
        }

        // ✅ REDIRECT REPORTS BUTTON TO BALANCE INQUIRY ACTIVITY
        btnBalance.setOnClickListener {
            val intent = Intent(this, BalanceInquiryActivity::class.java)
            startActivity(intent)
        }

        btnReports.setOnClickListener {
            // Palitan ang BalanceInquiryActivity ng CanteenReportsActivity
            val intent = Intent(this, CanteenReportsActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Helper function to convert a Base64 string back into a Bitmap image.
     */
    private fun base64ToBitmap(base64: String): Bitmap {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        // CRITICAL: Added try-catch for safety
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            // Return a dummy bitmap or throw, depending on preference
            throw IllegalArgumentException("Invalid Base64 format for image.", e)
        }
    }
}
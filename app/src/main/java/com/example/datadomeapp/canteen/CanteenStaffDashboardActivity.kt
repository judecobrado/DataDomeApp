package com.example.datadomeapp.canteen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
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

    // NEW: For Canteen Name Title (Matches tvCanteenTitle in XML)
    private lateinit var tvCanteenTitle: TextView

    // Existing: Now for Staff Welcome Name (Matches tvWelcome in XML)
    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnMenu: Button
    private lateinit var btnOrders: Button
    private lateinit var btnReports: Button
    private lateinit var ivCanteenImage: ImageView

    private var staffUid: String? = null
    private var staffCanteenName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_dashboard)

        // Initialize the TextViews based on your new two-line structure
        tvCanteenTitle = findViewById(R.id.tvCanteenTitle)
        tvWelcome = findViewById(R.id.tvWelcome)

        btnLogout = findViewById(R.id.btnLogout)
        btnMenu = findViewById(R.id.btnMenu)
        btnOrders = findViewById(R.id.btnOrders)
        btnReports = findViewById(R.id.btnReports)
        ivCanteenImage = findViewById(R.id.ivCanteenImage)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        staffUid = currentUser.uid

        // --- STEP 1: Fetch the Canteen Staff ID from the 'users' collection ---
        firestore.collection("users").document(staffUid!!)
            .get()
            .addOnSuccessListener { doc ->
                val canteenStaffId = doc.getString("canteenStaffId")

                if (canteenStaffId.isNullOrEmpty()) {
                    tvCanteenTitle.text = "Canteen Dashboard"
                    tvWelcome.text = "Staff data link not found."
                    Toast.makeText(this, "Error: Staff ID missing from user record.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // --- STEP 2: Use the ID to fetch the full data from 'canteen_staff' ---
                fetchFullStaffData(canteenStaffId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching user link: ${e.message}", Toast.LENGTH_SHORT).show()
            }

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

        btnOrders.setOnClickListener {
            Toast.makeText(this, "Order Processing coming soon", Toast.LENGTH_SHORT).show()
        }

        btnReports.setOnClickListener {
            Toast.makeText(this, "Sales Reports coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Fetches the complete staff record from the canteen_staff collection using the Staff ID.
     */
    private fun fetchFullStaffData(canteenStaffId: String) {
        firestore.collection("canteen_staff").document(canteenStaffId)
            .get()
            .addOnSuccessListener { staffDoc ->
                if (staffDoc.exists()) {
                    val firstName = staffDoc.getString("firstName") ?: ""
                    val lastName = staffDoc.getString("lastName") ?: ""
                    staffCanteenName = staffDoc.getString("canteenName")

                    // Set Canteen Name (first line)
                    tvCanteenTitle.text = staffCanteenName ?: "Canteen Dashboard"
                    tvCanteenTitle.textSize = 28f // Ensure prominence

                    // Set Staff Welcome Name (second line)
                    tvWelcome.text = "Welcome, $firstName $lastName!"
                    tvWelcome.textSize = 18f // Ensure hierarchy

                    // Retrieve image path (storeImageUrl) and display
                    val base64Image = staffDoc.getString("storeImageUrl")

                    if (!base64Image.isNullOrEmpty()) {
                        ivCanteenImage.setImageBitmap(base64ToBitmap(base64Image))
                    }
                } else {
                    tvCanteenTitle.text = "Error"
                    tvWelcome.text = "Staff data not found."
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching staff details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Helper function to convert a Base64 string back into a Bitmap image.
     */
    private fun base64ToBitmap(base64: String): Bitmap {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
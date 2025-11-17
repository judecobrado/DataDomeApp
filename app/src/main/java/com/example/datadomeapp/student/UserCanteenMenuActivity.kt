package com.example.datadomeapp.student

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserCanteenMenuActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvWalletBalance: TextView
    private lateinit var etSearch: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: CanteenMenuAdapter
    private lateinit var layoutWallet: LinearLayout
    private var allItems: List<CanteenMenuItem> = emptyList()

    private var userType: String = "student"
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_canteen_menu)

        // --- Initialize views ---
        tvWalletBalance = findViewById(R.id.tvWalletBalance)
        etSearch = findViewById(R.id.etSearch)
        recyclerView = findViewById(R.id.rvMenu)
        progressBar = findViewById(R.id.progressBar)
        layoutWallet = findViewById(R.id.layoutWallet)

        userType = intent.getStringExtra("USER_TYPE") ?: "student"
        userId = intent.getStringExtra("USER_ID") ?: auth.currentUser?.uid.orEmpty()

        // --- Setup adapter ---
        adapter = CanteenMenuAdapter { item ->
            showItemPreview(item)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadWalletBalance()
        loadMenuItems()

        // --- Search listener ---
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        layoutWallet.setOnClickListener { // Palitan ang tvWalletBalance ng layoutWallet
            val intent = Intent(this, UserCanteenItemHistoryActivity::class.java)
            // Siguraduhin na ang userId ay napalitan na ng T-XXXX kung teacher
            intent.putExtra("STUDENT_ID", userId)
            startActivity(intent)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- FIRESTORE LOADERS ---
    // ---------------------------------------------------------------------------------------------

    private fun loadWalletBalance() {
        if (userId.isEmpty()) {
            tvWalletBalance.text = "₱0.00"
            Log.w("UserBalance", "User ID is empty")
            return
        }

        if (userType == "teacher") {
            // --- Teacher: fetch by 'uid' field ---
            firestore.collection("teachers")
                .whereEqualTo("uid", userId) // userId dito ay ang Firebase UID
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.firstOrNull()
                    if (doc != null) {
                        Log.d("UserBalance", "Teacher doc: ${doc.data}")
                        val balance = doc.getDouble("balance") ?: 0.0
                        tvWalletBalance.text = "₱${String.format("%.0f", balance)}"

                        // ⭐️ CRITICAL FIX: Kunin ang T-XXXX (teacherId) at i-store sa 'this.userId'
                        val teacherIdValue = doc.getString("teacherId")
                        if (teacherIdValue != null) {
                            this.userId = teacherIdValue // Naging T-XXXX na ang userId para sa History Activity
                            Log.d("UserBalance", "Updated userId to Teacher ID: ${this.userId}")
                        }

                    } else {
                        Log.w("UserBalance", "Teacher document not found for uid=$userId")
                        tvWalletBalance.text = "₱0.00"
                        Toast.makeText(this, "Balance not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("UserBalance", "Failed to fetch teacher balance", e)
                    tvWalletBalance.text = "₱0.00"
                    Toast.makeText(this, "Failed to load balance", Toast.LENGTH_SHORT).show()
                }
        } else {
            // --- Student: fetch by document ID (userId/DDS-XXXX) ---
            firestore.collection("students")
                .document(userId) // userId dito ay ang DDS-XXXX (Document ID)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        Log.d("UserBalance", "Student doc: ${doc.data}")
                        val balance = doc.getDouble("balance") ?: 0.0
                        tvWalletBalance.text = "₱${String.format("%.0f", balance)}"
                        // Walang pagbabago, mananatiling DDS-XXXX ang this.userId
                    } else {
                        Log.w("UserBalance", "Student document not found for userId=$userId")
                        tvWalletBalance.text = "₱0.00"
                        Toast.makeText(this, "Balance not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("UserBalance", "Failed to fetch student balance", e)
                    tvWalletBalance.text = "₱0.00"
                    Toast.makeText(this, "Failed to load balance", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadMenuItems() {
        progressBar.visibility = android.view.View.VISIBLE
        firestore.collection("canteenMenu")
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = android.view.View.GONE
                allItems = snapshot.documents.mapNotNull { doc ->
                    CanteenMenuItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "N/A",
                        price = doc.getDouble("price") ?: 0.0,
                        imageBase64 = doc.getString("imageUrl") ?: ""
                    )
                }
                adapter.submitList(allItems)
            }
            .addOnFailureListener {
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Failed to load menu", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------------------------------------------------------------------------------------
    // --- PREVIEW DIALOG ---
    // ---------------------------------------------------------------------------------------------

    private fun showItemPreview(item: CanteenMenuItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_item_preview, null)
        val ivImage = dialogView.findViewById<ImageView>(R.id.ivPreviewImage)
        val tvName = dialogView.findViewById<TextView>(R.id.tvPreviewName)
        val tvPrice = dialogView.findViewById<TextView>(R.id.tvPreviewPrice)

        // Texts
        tvName.text = item.name
        tvPrice.text = "₱${String.format("%.0f", item.price)}"

        // Decode Base64 image
        if (item.imageBase64.isNotEmpty()) {
            try {
                val decodedBytes = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                ivImage.setImageBitmap(bmp)
            } catch (e: Exception) {
                ivImage.setImageResource(R.drawable.ic_image_placeholder)
            }
        } else {
            ivImage.setImageResource(R.drawable.ic_image_placeholder)
        }

        // Dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .create()

        dialog.show()
    }

    // ---------------------------------------------------------------------------------------------
    // --- FILTER ---
    // ---------------------------------------------------------------------------------------------

    private fun filterList(query: String) {
        val filtered = allItems.filter { it.name.contains(query, ignoreCase = true) }
        adapter.submitList(filtered)
    }
}

package com.example.datadomeapp

import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Data class for Canteen Staff
data class CanteenStaff(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: String = "canteen_staff" // role used for login
)

class ManageCanteenStaffActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val staffCollection = firestore.collection("users")

    private val staffList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_canteen_staff)

        // Handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.canteen_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val listView = findViewById<ListView>(R.id.lvStaff)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnAdd = findViewById<Button>(R.id.btnAddStaff)
        val btnDelete = findViewById<Button>(R.id.btnDeleteStaff)
        val btnBack = findViewById<Button>(R.id.btnBackStaff)

        // Setup list adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, staffList)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Load staff from Firestore
        loadStaffList()

        // Add staff button
        btnAdd.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create Firebase Auth account
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        val uid = result.result?.user?.uid ?: return@addOnCompleteListener
                        val staff = CanteenStaff(email, firstName, lastName)

                        // Save staff to Firestore using UID as document ID
                        staffCollection.document(uid).set(staff)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Staff added!", Toast.LENGTH_LONG).show()
                                etFirstName.text.clear()
                                etLastName.text.clear()
                                etEmail.text.clear()
                                etPassword.text.clear()
                                loadStaffList()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save info: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Failed to create account: ${result.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Delete staff button
        btnDelete.setOnClickListener {
            val pos = listView.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val emailToDelete = staffList[pos]

                // Query by email
                staffCollection.whereEqualTo("email", emailToDelete).get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) {
                            Toast.makeText(this, "Staff not found", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(this, "Staff removed", Toast.LENGTH_SHORT).show()
                        loadStaffList()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error deleting staff: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Select a staff to delete", Toast.LENGTH_SHORT).show()
            }
        }

        // Back button
        btnBack.setOnClickListener { finish() }
    }

    // Helper function to load staff from Firestore
    private fun loadStaffList() {
        staffCollection.whereEqualTo("role", "canteen_staff")
            .get()
            .addOnSuccessListener { snapshot ->
                staffList.clear()
                for (doc in snapshot.documents) {
                    val email = doc.getString("email")
                    email?.let { staffList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load staff", Toast.LENGTH_SHORT).show()
            }
    }
}

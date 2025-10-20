package com.example.datadomeapp.admin

import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.datadomeapp.models.CanteenStaff

class ManageCanteenStaffActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val staffCollection = firestore.collection("users")

    private val staffList = ArrayList<CanteenStaff>()
    private lateinit var adapter: ArrayAdapter<String>
    private var selectedStaffIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.admin_canteenstaff_management)

        val etCanteenName = findViewById<EditText>(R.id.etCanteenName)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnAdd = findViewById<Button>(R.id.btnAddStaff)
        val btnDelete = findViewById<Button>(R.id.btnDeleteStaff)
        val btnEdit = findViewById<Button>(R.id.btnEditStaff) // new edit button
        val btnBack = findViewById<Button>(R.id.btnBackStaff)
        val listView = findViewById<ListView>(R.id.lvStaff)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, ArrayList<String>())
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        loadStaffOnce() // load all staff

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedStaffIndex = position // only store index, do not populate yet
        }

        // 🔹 Edit Button
        btnEdit.setOnClickListener {
            if (selectedStaffIndex != -1) {
                val staff = staffList[selectedStaffIndex]
                etFirstName.setText(staff.firstName)
                etLastName.setText(staff.lastName)
                etEmail.setText(staff.email)
                etCanteenName.setText(staff.canteenName)
                etPassword.setText("") // password left empty
            } else {
                Toast.makeText(this, "Select a staff to edit", Toast.LENGTH_SHORT).show()
            }
        }

        btnAdd.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val canteenName = etCanteenName.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || canteenName.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Update if editing
            val existingStaff = staffList.find { it.email == email }
            if (existingStaff != null) {
                val updatedStaff = CanteenStaff(email, firstName, lastName, canteenName)
                staffCollection.document(existingStaff.uid ?: email).set(updatedStaff)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Staff updated!", Toast.LENGTH_SHORT).show()
                        clearFields(etFirstName, etLastName, etEmail, etPassword, etCanteenName)
                        loadStaffOnce()
                        selectedStaffIndex = -1
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                if (password.isEmpty()) {
                    Toast.makeText(this, "Password required for new staff", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // create new staff
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            val uid = result.result?.user?.uid ?: return@addOnCompleteListener
                            val staff = CanteenStaff(email, firstName, lastName, canteenName, uid)
                            staffCollection.document(uid).set(staff)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Staff added!", Toast.LENGTH_SHORT).show()
                                    clearFields(etFirstName, etLastName, etEmail, etPassword, etCanteenName)
                                    loadStaffOnce()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Failed to create account: ${result.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        btnDelete.setOnClickListener {
            if (selectedStaffIndex != -1) {
                val staff = staffList[selectedStaffIndex]
                staffCollection.document(staff.uid ?: staff.email).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Staff removed!", Toast.LENGTH_SHORT).show()
                        clearFields(etFirstName, etLastName, etEmail, etPassword, etCanteenName)
                        loadStaffOnce()
                        selectedStaffIndex = -1
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Select staff to delete", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun clearFields(vararg fields: EditText) {
        fields.forEach { it.text.clear() }
    }

    private fun loadStaffOnce() {
        staffCollection.whereEqualTo("role", "canteen_staff").get()
            .addOnSuccessListener { snapshot ->
                staffList.clear()
                val displayList = ArrayList<String>()
                snapshot.documents.forEach { doc ->
                    val staff = doc.toObject(CanteenStaff::class.java)
                    staff?.let {
                        it.uid = doc.id
                        staffList.add(it)
                        displayList.add("${it.email} (${it.canteenName})")
                    }
                }
                adapter.clear()
                adapter.addAll(displayList)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load staff: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

// Updated CanteenStaff data class
data class CanteenStaff(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val canteenName: String = "",
    var uid: String? = null,
    val role: String = "canteen_staff"
)

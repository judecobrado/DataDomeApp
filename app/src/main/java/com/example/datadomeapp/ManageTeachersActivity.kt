package com.example.datadomeapp

import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Data model for Teacher
data class Teacher(
    val email: String = "",
    val role: String = "teacher"
)

class ManageTeachersActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    private val teacherList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    // Hindi na gagamitin ang hardcoded credentials dahil tinanggal ang re-login.
    // private val adminEmail = "admin@datadome.com"
    // private val adminPassword = "admin123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_teachers)

        // ... (View Insets, UI setup) ...

        val lvTeachers = findViewById<ListView>(R.id.lvTeachers)
        val etEmail = findViewById<EditText>(R.id.etTeacherEmail)
        val etPassword = findViewById<EditText>(R.id.etTeacherPassword)
        val btnAdd = findViewById<Button>(R.id.btnAddTeacher)
        val btnDelete = findViewById<Button>(R.id.btnDeleteTeacher)
        val btnBack = findViewById<Button>(R.id.btnBackTeacher)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, teacherList)
        lvTeachers.adapter = adapter
        lvTeachers.choiceMode = ListView.CHOICE_MODE_SINGLE

        // 🔹 PAGBABAGO: Gumamit ng One-Time Get() imbes na addSnapshotListener (para mas mabilis ang initial load)
        loadTeachersOnce()

        // 🔹 Add Teacher
        btnAdd.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create new teacher account
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result?.user?.uid
                        val teacher = Teacher(email)
                        if (uid != null) {
                            usersCollection.document(uid).set(teacher)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Teacher added: $email", Toast.LENGTH_LONG).show()
                                    etEmail.text.clear()
                                    etPassword.text.clear()

                                    // 🛑 TINANGGAL NA ERROR: Inalis ang Admin Re-login block dito.
                                    // Ang Admin session ay mananatiling valid.

                                    // I-reload ang listahan pagkatapos mag-add
                                    loadTeachersOnce()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Firestore error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Auth error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // 🔹 Delete Teacher (No change needed here)
        btnDelete.setOnClickListener {
            val pos = lvTeachers.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                Toast.makeText(this, "Select a teacher to delete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val email = teacherList[pos]
            usersCollection.whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { docs ->
                    for (doc in docs) {
                        doc.reference.delete()
                    }
                    Toast.makeText(this, "Teacher deleted: $email", Toast.LENGTH_SHORT).show()
                    // I-reload ang listahan pagkatapos mag-delete
                    loadTeachersOnce()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    // IDINAGDAG: Function para sa one-time load ng teachers
    private fun loadTeachersOnce() {
        usersCollection.whereEqualTo("role", "teacher").get()
            .addOnSuccessListener { snapshot ->
                teacherList.clear()
                snapshot.documents.forEach { doc ->
                    val email = doc.getString("email")
                    email?.let { teacherList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to load teachers: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
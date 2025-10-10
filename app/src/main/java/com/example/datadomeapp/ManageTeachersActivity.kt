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

    // Replace these with your admin’s real credentials
    private val adminEmail = "admin@datadome.com"
    private val adminPassword = "admin123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_teachers)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.teacher_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val lvTeachers = findViewById<ListView>(R.id.lvTeachers)
        val etEmail = findViewById<EditText>(R.id.etTeacherEmail)
        val etPassword = findViewById<EditText>(R.id.etTeacherPassword)
        val btnAdd = findViewById<Button>(R.id.btnAddTeacher)
        val btnDelete = findViewById<Button>(R.id.btnDeleteTeacher)
        val btnBack = findViewById<Button>(R.id.btnBackTeacher)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, teacherList)
        lvTeachers.adapter = adapter
        lvTeachers.choiceMode = ListView.CHOICE_MODE_SINGLE

        // 🔹 Load only teachers from Firestore
        usersCollection.whereEqualTo("role", "teacher")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load teachers: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                teacherList.clear()
                snapshot?.documents?.forEach { doc ->
                    val email = doc.getString("email")
                    email?.let { teacherList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

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
                        val uid = auth.currentUser?.uid
                        val teacher = Teacher(email)
                        if (uid != null) {
                            usersCollection.document(uid).set(teacher)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Teacher added: $email", Toast.LENGTH_SHORT).show()
                                    etEmail.text.clear()
                                    etPassword.text.clear()

                                    // 🔹 Re-login as admin (since Firebase auto-logs into the new teacher)
                                    auth.signOut()
                                    auth.signInWithEmailAndPassword(adminEmail, adminPassword)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Reconnected as admin", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Failed to re-login admin: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Firestore error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Auth error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // 🔹 Delete Teacher
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
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}

package com.example.datadomeapp

import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class Student(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val birthday: String = "",
    val role: String = "student" // para ma-identify sa login
)

class ManageStudentsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val studentsCollection = firestore.collection("users") // lahat ng users sa isang collection

    private val studentList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_students)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.student_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val listView = findViewById<ListView>(R.id.lvStudents)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etBirthday = findViewById<EditText>(R.id.etBirthday)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnAdd = findViewById<Button>(R.id.btnAddStudent)
        val btnDelete = findViewById<Button>(R.id.btnDeleteStudent)
        val btnBack = findViewById<Button>(R.id.btnBackStudents)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, studentList)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Load students from Firestore
        studentsCollection.whereEqualTo("role", "student")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                studentList.clear()
                snapshot?.documents?.forEach { doc ->
                    val email = doc.getString("email")
                    email?.let { studentList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

        // Add student
        btnAdd.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val birthday = etBirthday.text.toString().trim()
            val email = etEmail.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || birthday.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = birthday.take(4)
            val password = firstName.replace(" ", "") + year

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        val student = Student(email, firstName, lastName, birthday)
                        studentsCollection.document(result.result?.user?.uid ?: email)
                            .set(student)
                            .addOnSuccessListener {
                                etFirstName.text.clear()
                                etLastName.text.clear()
                                etBirthday.text.clear()
                                etEmail.text.clear()
                                Toast.makeText(this, "Student added!\nPassword: $password", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save info: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Failed to create account: ${result.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Delete student
        btnDelete.setOnClickListener {
            val pos = listView.checkedItemPosition
            if (pos != ListView.INVALID_POSITION) {
                val email = studentList[pos]
                // Cannot delete from Auth in client app, but remove from Firestore
                studentsCollection.whereEqualTo("email", email)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(this, "Student removed from Firestore", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Select a student to delete", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { finish() }
    }
}ss

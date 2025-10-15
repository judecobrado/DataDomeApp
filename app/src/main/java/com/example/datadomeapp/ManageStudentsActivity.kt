package com.example.datadomeapp

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// ⬅️ I-define ang Data Class (Dapat nasa labas ng ManageStudentsActivity class, o sa sariling file)
data class Student(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val birthday: String = "",
    val email: String = ""
)

class ManageStudentsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etBirthday: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnAddStudent: Button
    private lateinit var btnDeleteStudent: Button
    private lateinit var lvStudents: ListView
    private lateinit var btnBackStudents: Button

    private val studentList = mutableListOf<Student>()
    private val studentDisplayList = mutableListOf<String>()

    private lateinit var adapter: ArrayAdapter<String>
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_students) // ⬅️ Kukunin ang layout dito

        // ✅ Connect all your views (Tiyakin na tugma ang IDs sa XML)
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etBirthday = findViewById(R.id.etBirthday)
        etEmail = findViewById(R.id.etEmail)
        btnAddStudent = findViewById(R.id.btnAddStudent)
        btnDeleteStudent = findViewById(R.id.btnDeleteStudent)
        lvStudents = findViewById(R.id.lvStudents)
        btnBackStudents = findViewById(R.id.btnBackStudents)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, studentDisplayList)
        lvStudents.adapter = adapter
        lvStudents.choiceMode = ListView.CHOICE_MODE_SINGLE

        startFirestoreListener()

        // ✅ Add student logic
        btnAddStudent.setOnClickListener {
            addStudentToFirestore()
        }

        // ✅ Delete selected student
        btnDeleteStudent.setOnClickListener {
            deleteStudentFromFirestore()
        }

        btnBackStudents.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
    }

    private fun startFirestoreListener() {
        firestoreListener = firestore.collection("students")
            .addSnapshotListener { snapshot, e ->

                if (e != null) {
                    Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    studentList.clear()
                    studentDisplayList.clear()

                    for (doc in snapshot.documents) {
                        val student = doc.toObject(Student::class.java)?.copy(id = doc.id)
                        if (student != null) {
                            studentList.add(student)
                            val displayInfo = "${student.firstName} ${student.lastName}\nEmail: ${student.email}"
                            studentDisplayList.add(displayInfo)
                        }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    private fun addStudentToFirestore() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val birthday = etBirthday.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || birthday.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val studentData = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "birthday" to birthday,
            "email" to email
        )

        firestore.collection("students").add(studentData)
            .addOnSuccessListener {
                Toast.makeText(this, "Student added successfully!", Toast.LENGTH_SHORT).show()
                clearInputs()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding student: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun deleteStudentFromFirestore() {
        val position = lvStudents.checkedItemPosition

        if (position != ListView.INVALID_POSITION) {
            val studentToDelete = studentList[position]
            val studentId = studentToDelete.id

            firestore.collection("students").document(studentId).delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Student deleted!", Toast.LENGTH_SHORT).show()
                    lvStudents.clearChoices()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error deleting student: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(this, "Select a student to delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearInputs() {
        etFirstName.text.clear()
        etLastName.text.clear()
        etBirthday.text.clear()
        etEmail.text.clear()
    }
}
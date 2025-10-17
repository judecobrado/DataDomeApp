package com.example.datadomeapp.admin

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.AppUser // Import AppUser
import com.example.datadomeapp.models.Teacher // Import Teacher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManageTeachersActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    private val teachersCollection = firestore.collection("teachers") // New Collection

    // Map<TeacherEmail, UID>
    private val teacherMap = mutableMapOf<String, String>()
    private val teacherList = ArrayList<String>() // Gagamitin ang email display
    private lateinit var adapter: ArrayAdapter<String>

    // 🛑 New variable for the Name field
    private lateinit var etName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_teachers)

        val lvTeachers = findViewById<ListView>(R.id.lvTeachers)
        val etEmail = findViewById<EditText>(R.id.etTeacherEmail)
        val etPassword = findViewById<EditText>(R.id.etTeacherPassword)
        etName = findViewById<EditText>(R.id.etTeacherName) // 🛑 Added initialization for etTeacherName
        val btnAdd = findViewById<Button>(R.id.btnAddTeacher)
        val btnDelete = findViewById<Button>(R.id.btnDeleteTeacher)
        val btnBack = findViewById<Button>(R.id.btnBackTeacher)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, teacherList)
        lvTeachers.adapter = adapter
        lvTeachers.choiceMode = ListView.CHOICE_MODE_SINGLE

        loadTeachersOnce()

        // 🔹 Add Teacher Logic (Multi-Collection Save)
        btnAdd.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val name = etName.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Enter email, password, and name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // 1. Create Firebase Auth Account
                    val userCredential = auth.createUserWithEmailAndPassword(email, password).await()
                    val uid = userCredential.user?.uid

                    if (uid != null) {
                        // 2. Generate new Teacher ID (T-0001)
                        val newTeacherId = generateTeacherId()

                        // 3. Save to 'users' collection (Authentication record)
                        val appUser = AppUser(uid = uid, email = email, role = "teacher")
                        usersCollection.document(uid).set(appUser).await()

                        // 4. Save to 'teachers' collection (Profile record)
                        // 🛑 Ang teacher ay mayroon na ngayong uid at teacherId
                        val teacherProfile = Teacher(uid = uid, teacherId = newTeacherId, name = name)
                        teachersCollection.document(newTeacherId).set(teacherProfile).await()

                        Toast.makeText(this@ManageTeachersActivity, "Teacher added: $name ($newTeacherId)", Toast.LENGTH_LONG).show()
                        etEmail.text.clear()
                        etPassword.text.clear()
                        etName.text.clear()
                        loadTeachersOnce()
                    }
                } catch (e: Exception) {
                    // Maaaring mag-fail ito dahil sa weak password o duplicate email
                    Toast.makeText(this@ManageTeachersActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 🔹 DELETE Teacher Logic (Multi-Collection Deletion)
        btnDelete.setOnClickListener {
            val pos = lvTeachers.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                Toast.makeText(this, "Select a teacher to delete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emailToDelete = teacherList[pos]
            val uidToDelete = teacherMap[emailToDelete] // UID from the map

            if (uidToDelete != null) {
                // 🛑 Fixed: Calling the non-suspending function
                deleteTeacherAccount(uidToDelete, emailToDelete)
            } else {
                Toast.makeText(this, "Error: UID not found.", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    // ----------------------------------------------------
    // Helper Functions
    // ----------------------------------------------------

    // New Function: Generate unique Teacher ID (Must be called in a CoroutineScope)
    private suspend fun generateTeacherId(): String {
        return try {
            val snapshot = teachersCollection
                .orderBy("teacherId", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await() // Ito ay nasa suspend function, kaya tama

            val lastId = snapshot.documents.firstOrNull()?.getString("teacherId")
            val nextNumber = if (lastId != null && lastId.startsWith("T-")) {
                lastId.substringAfter("T-").toIntOrNull()?.plus(1) ?: 1
            } else 1
            "T-" + String.format("%04d", nextNumber)
        } catch (e: Exception) {
            "T-0001"
        }
    }


    // 🔹 Delete Teacher Account (Users, Teachers, and Auth) - FIXED
    private fun deleteTeacherAccount(uid: String, email: String) {

        // 1. Hanapin ang Teacher Profile Record gamit ang UID
        teachersCollection.whereEqualTo("uid", uid).get()
            .addOnSuccessListener { snapshot ->

                val teacherDoc = snapshot.documents.firstOrNull()
                val teacherId = teacherDoc?.id // Ito ang Document ID (T-00xx) sa teachers collection

                val batch = firestore.batch()

                // 2. Delete record from 'users' collection (Auth Record)
                batch.delete(usersCollection.document(uid))

                // 3. Delete record from 'teachers' collection (Profile Record)
                if (teacherId != null) {
                    batch.delete(teachersCollection.document(teacherId))
                }

                // 4. Commit Batch
                batch.commit()
                    .addOnSuccessListener {
                        // Success! Lahat ng record ay nabura.
                        // Auth user deletion ay dapat gawin ng Cloud Function o Admin SDK.
                        Toast.makeText(this, "User and Teacher records for $email deleted.", Toast.LENGTH_LONG).show()
                        loadTeachersOnce()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to commit batch delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to find teacher profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    // 🔹 Load Teachers (Kukunin sa 'users' collection para sa email/UID)
    private fun loadTeachersOnce() {
        usersCollection.whereEqualTo("role", "teacher").get()
            .addOnSuccessListener { snapshot ->
                teacherList.clear()
                teacherMap.clear()
                snapshot.documents.forEach { doc ->
                    val email = doc.getString("email")
                    email?.let {
                        teacherList.add(it)
                        // Gamitin ang document ID (na siyang UID)
                        teacherMap[it] = doc.id
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to load teachers: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
package com.example.datadomeapp.admin

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.AppUser
import com.example.datadomeapp.models.Teacher // Siguraduhin na ang Teacher model ay updated
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

class ManageTeachersActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    private val teachersCollection = firestore.collection("teachers")
    // 🟢 Bagong collection reference para sa courses/departments
    private val coursesCollection = firestore.collection("courses")

    // Map<TeacherDisplayString, UID>
    private val teacherMap = mutableMapOf<String, String>()
    private val teacherList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    // 🟢 List at Adapter para sa Department Spinner
    private val departmentList = ArrayList<String>()
    private lateinit var departmentAdapter: ArrayAdapter<String>

    // UI Variables
    private lateinit var etName: EditText
    // 🟢 Pinalitan ng Spinner
    private lateinit var spDepartment: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_manage_teachers)

        // UI Initialization
        val lvTeachers = findViewById<ListView>(R.id.lvTeachers)
        val etEmail = findViewById<EditText>(R.id.etTeacherEmail)
        val etPassword = findViewById<EditText>(R.id.etTeacherPassword)
        etName = findViewById<EditText>(R.id.etTeacherName)
        // 🟢 Initialize spDepartment, dapat ito ang ID ng Spinner sa layout
        spDepartment = findViewById<Spinner>(R.id.spDepartment)
        val btnAdd = findViewById<Button>(R.id.btnAddTeacher)
        val btnDelete = findViewById<Button>(R.id.btnDeleteTeacher)
        val btnBack = findViewById<Button>(R.id.btnBackTeacher)

        // Setup Teachers List View
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, teacherList)
        lvTeachers.adapter = adapter
        lvTeachers.choiceMode = ListView.CHOICE_MODE_SINGLE

        // 🟢 Setup Department Spinner
        departmentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departmentList)
        spDepartment.adapter = departmentAdapter

        // Load data
        loadCourses() // 🟢 I-load muna ang listahan ng departments
        loadTeachersOnce()

        // 🔹 Add Teacher Logic (Multi-Collection Save)
        btnAdd.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val name = etName.text.toString().trim()
            // 🟢 Kunin ang napiling department mula sa Spinner
            val department = spDepartment.selectedItem?.toString()?.trim() ?: ""

            if (email.isEmpty() || password.isEmpty() || name.isEmpty() || department.isEmpty() || department == "Loading..." || department == "No Departments Found" || department == "Error Loading Departments") {
                Toast.makeText(this, "Enter email, password, name, and select a valid department", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // 1. Create Firebase Auth Account
                    val userCredential = auth.createUserWithEmailAndPassword(email, password).await()
                    val uid = userCredential.user?.uid

                    if (uid != null) {
                        // 2. Generate new Teacher ID
                        val newTeacherId = generateTeacherId()

                        // 3. Save to 'users' collection (Authentication record)
                        val appUser = AppUser(uid = uid, email = email, role = "teacher")
                        usersCollection.document(uid).set(appUser).await()

                        // 4. Save to 'teachers' collection (Profile record)
                        val teacherProfile = Teacher(uid = uid, teacherId = newTeacherId, name = name, department = department)
                        teachersCollection.document(newTeacherId).set(teacherProfile).await()

                        Toast.makeText(this@ManageTeachersActivity, "Teacher added: $name ($newTeacherId) - $department", Toast.LENGTH_LONG).show()
                        etEmail.text.clear()
                        etPassword.text.clear()
                        etName.text.clear()
                        // Hindi kailangan i-clear ang Spinner
                        loadTeachersOnce()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageTeachersActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("ManageTeachers", "Error adding teacher", e)
                }
            }
        }

        // 🔹 Delete Teacher Logic
        btnDelete.setOnClickListener {
            val pos = lvTeachers.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                Toast.makeText(this, "Select a teacher to delete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val displayString = teacherList[pos]
            val uidToDelete = teacherMap[displayString]

            if (uidToDelete != null) {
                // Kunin ang email mula sa display string
                val emailToDelete = displayString.substringBefore(" (")
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

    // 🟢 Function to load courses for the Department Spinner
    private fun loadCourses() {
        departmentList.clear()
        departmentList.add("Loading...") // Placeholder
        departmentAdapter.notifyDataSetChanged()

        coursesCollection.get()
            .addOnSuccessListener { snapshot ->
                departmentList.clear()
                if (snapshot.isEmpty) {
                    departmentList.add("No Departments Found")
                } else {
                    // Gamitin ang Document ID (e.g., BSIT) para sa department name
                    snapshot.documents.forEach { doc ->
                        val departmentId = doc.id
                        departmentList.add(departmentId)
                    }
                }
                departmentAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Log.e("ManageTeachers", "Failed to load courses: ${error.message}")
                departmentList.clear()
                departmentList.add("Error Loading Departments")
                departmentAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Failed to load departments.", Toast.LENGTH_SHORT).show()
            }
    }


    // Function: Generate unique Teacher ID
    private suspend fun generateTeacherId(): String {
        return try {
            val snapshot = teachersCollection
                .orderBy("teacherId", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val lastId = snapshot.documents.firstOrNull()?.getString("teacherId")
            val nextNumber = if (lastId != null && lastId.startsWith("T-")) {
                lastId.substringAfter("T-").toIntOrNull()?.plus(1) ?: 1
            } else 1
            "T-" + String.format("%04d", nextNumber)
        } catch (e: Exception) {
            "T-0001"
        }
    }


    // Function: Delete Teacher Account
    private fun deleteTeacherAccount(uid: String, email: String) {

        teachersCollection.whereEqualTo("uid", uid).get()
            .addOnSuccessListener { snapshot ->

                val teacherDoc = snapshot.documents.firstOrNull()
                val teacherId = teacherDoc?.id

                val batch = firestore.batch()
                batch.delete(usersCollection.document(uid))
                if (teacherId != null) {
                    batch.delete(teachersCollection.document(teacherId))
                }

                batch.commit()
                    .addOnSuccessListener {
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


    // Function: Load Teachers
    private fun loadTeachersOnce() {
        usersCollection.whereEqualTo("role", "teacher").get()
            .addOnSuccessListener { usersSnapshot ->
                teacherList.clear()
                teacherMap.clear()

                // I-loop ang lahat ng users na 'teacher'
                val teacherUids = usersSnapshot.documents.map { it.id }
                val teacherEmails = usersSnapshot.documents.associate { it.id to it.getString("email") }

                if (teacherUids.isEmpty()) {
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                // Kukunin ang profile data (Name at Department) mula sa teachers collection
                teachersCollection.whereIn("uid", teacherUids).get()
                    .addOnSuccessListener { teachersSnapshot ->
                        val teacherProfiles = teachersSnapshot.documents.associate { doc ->
                            doc.getString("uid") to doc.toObject(Teacher::class.java)
                        }

                        usersSnapshot.documents.forEach { userDoc ->
                            val uid = userDoc.id
                            val email = teacherEmails[uid]
                            val profile = teacherProfiles[uid]

                            if (email != null && profile != null) {
                                // Updated display format: Email (Name - Department)
                                val display = "$email (${profile.name} - ${profile.department ?: "No Dept"})"
                                teacherList.add(display)
                                teacherMap[display] = uid // I-mapa ang buong display string sa UID
                            } else if (email != null) {
                                // Backup display
                                teacherList.add(email)
                                teacherMap[email] = uid
                            }
                        }
                        adapter.notifyDataSetChanged()
                    }
                    .addOnFailureListener { error ->
                        Log.e("ManageTeachers", "Failed to load teacher profiles: ${error.message}")
                        Toast.makeText(this, "Failed to load teacher profiles.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to load teachers: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
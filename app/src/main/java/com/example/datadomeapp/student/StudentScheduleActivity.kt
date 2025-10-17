package com.example.datadomeapp.student

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
// 🛑 I-assume na mayroon kayong model na 'StudentSubject' sa inyong models package
// Kung wala pa, kailangan niyo itong gawin
// import com.example.datadomeapp.models.StudentSubject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// 🛑 Temporary data class muna kung wala pa kayong StudentSubject model
data class StudentSubject(
    val subjectCode: String = "",
    val sectionName: String = "",
    val assignedTeacherName: String = "",
    val schedule: String = ""
)

class StudentScheduleActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var lvSchedule: ListView
    private lateinit var scheduleList: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_schedule)

        // I-assume na mayroon kayong layout ID na lvStudentSchedule
        lvSchedule = findViewById(R.id.lvStudentSchedule)
        scheduleList = mutableListOf()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, scheduleList)
        lvSchedule.adapter = adapter

        loadStudentSchedule()
    }

    private fun loadStudentSchedule() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. I-fetch ang UID para mahanap ang Student ID (mula sa 'users' collection)
                val userSnapshot = firestore.collection("users").document(currentUser.uid).get().await()
                // I-assume na mayroong "studentId" field sa inyong AppUser model
                val studentId = userSnapshot.getString("studentId")

                if (studentId.isNullOrEmpty()) {
                    Toast.makeText(this@StudentScheduleActivity, "Student ID not found in profile.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // 2. I-fetch ang subjects sa subcollection: students/{studentId}/subjects
                val subjectsSnapshot = firestore.collection("students")
                    .document(studentId)
                    .collection("subjects")
                    .get()
                    .await()

                scheduleList.clear()
                // Gumamit ng StudentSubject data class
                subjectsSnapshot.toObjects(StudentSubject::class.java).forEach { subject ->
                    val display = "${subject.subjectCode} (${subject.sectionName})\n" +
                            "Teacher: ${subject.assignedTeacherName}\n" +
                            "Schedule: ${subject.schedule}"
                    scheduleList.add(display)
                }

                adapter.notifyDataSetChanged()
                if (scheduleList.isEmpty()) {
                    Toast.makeText(this@StudentScheduleActivity, "No subjects assigned yet.", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@StudentScheduleActivity, "Failed to load schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
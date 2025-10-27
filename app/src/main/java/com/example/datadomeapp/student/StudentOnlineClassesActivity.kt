package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.OnlineClassAssignment
import com.google.firebase.auth.FirebaseAuth // Still needed for context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentOnlineClassesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private var studentId: String? = null // To hold the Student ID (e.g., DDS-0005)
    private val classList = mutableListOf<OnlineClassAssignment>()
    private lateinit var classAdapter: OnlineClassAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_online_classes)

        supportActionBar?.title = "Online Class Links"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // GET THE STUDENT ID FROM THE INTENT
        studentId = intent.getStringExtra("STUDENT_ID")

        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "Student ID info missing. Cannot load classes.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewOnlineClasses)
        recyclerView.layoutManager = LinearLayoutManager(this)

        classAdapter = OnlineClassAdapter(classList)
        recyclerView.adapter = classAdapter

        loadSubjectsAndLinks()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSubjectsAndLinks() {
        val currentStudentId = studentId ?: return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. FETCH ALL ENROLLED SUBJECTS (using the path from your image)
                val subjectRecords = firestore.collection("students") // <--- FIXED COLLECTION
                    .document(currentStudentId) // <--- FIXED DOCUMENT ID (DDS-0005)
                    .collection("subjects")
                    .get()
                    .await()

                if (subjectRecords.isEmpty) {
                    Toast.makeText(this@StudentOnlineClassesActivity, "You are not enrolled in any subjects yet.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                classList.clear()

                // 2. ITERATE AND FETCH ONLINE LINK FOR EACH ASSIGNMENT
                for (doc in subjectRecords.documents) {
                    val subjectCode = doc.getString("subjectCode") ?: "N/A"
                    val subjectTitle = doc.getString("subjectTitle") ?: "Subject Title Missing"
                    val teacherName = doc.getString("teacherName") ?: "N/A"
                    val sectionBlock = doc.getString("sectionBlock") ?: "N/A"
                    val assignmentNo = doc.getString("assignmentNo") // <--- CRITICAL REFERENCE ID

                    if (assignmentNo.isNullOrEmpty()) {
                        Log.w("OnlineClasses", "Subject $subjectCode is missing assignmentNo.")
                        classList.add(createPlaceholder(subjectCode, subjectTitle, teacherName, sectionBlock))
                        continue
                    }

                    // 3. FETCH LIVE ONLINE LINK FROM THE CLASS ASSIGNMENT
                    val assignmentDoc = firestore.collection("classAssignments")
                        .document(assignmentNo)
                        .get()
                        .await()

                    val onlineLink = assignmentDoc.getString("onlineClassLink")

                    // Optional: Fetch basic schedule info from the assignment doc
                    val firstSlot = assignmentDoc.get("scheduleSlots.slot1") as? Map<String, String> // Assuming 'slot1' is the key

                    // 4. ADD TO LIST
                    classList.add(
                        OnlineClassAssignment(
                            subjectTitle = subjectTitle,
                            subjectCode = subjectCode,
                            teacherName = teacherName,
                            sectionName = sectionBlock,
                            onlineClassLink = onlineLink ?: "No online class link yet.",
                            day = firstSlot?.get("day") ?: "",
                            startTime = firstSlot?.get("startTime") ?: "",
                            endTime = firstSlot?.get("endTime") ?: "",
                            roomNumber = firstSlot?.get("roomLocation") ?: ""
                        )
                    )
                }

                classAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e("OnlineClasses", "Failed to load subjects or assignments: $e")
                Toast.makeText(this@StudentOnlineClassesActivity, "Failed to load classes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createPlaceholder(code: String, title: String, teacher: String, section: String): OnlineClassAssignment {
        return OnlineClassAssignment(
            subjectTitle = title,
            subjectCode = code,
            teacherName = teacher,
            sectionName = section,
            onlineClassLink = "No online class link yet.",
            day = "",
            startTime = "",
            endTime = "",
            roomNumber = ""
        )
    }
}
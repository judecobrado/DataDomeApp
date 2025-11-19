package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.OnlineClassAssignment
import com.example.datadomeapp.models.ClassSchedule
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentOnlineClassesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private var studentId: String? = null
    private val classList = mutableListOf<OnlineClassAssignment>()
    private lateinit var classAdapter: OnlineClassAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_online_classes)

        supportActionBar?.title = "Online Class Links"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
                // 1. FETCH ALL ENROLLED SUBJECTS
                val subjectRecords = firestore.collection("students")
                    .document(currentStudentId)
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
                    val assignmentNo = doc.getString("assignmentNo")

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

                    // ✅ GET ALL SCHEDULE SLOTS
                    val scheduleSlots = assignmentDoc.get("scheduleSlots") as? Map<String, Map<String, String>>
                    val allSchedules = formatAllSchedules(scheduleSlots)

                    // 4. ADD TO LIST
                    classList.add(
                        OnlineClassAssignment(
                            subjectTitle = subjectTitle,
                            subjectCode = subjectCode,
                            teacherName = teacherName,
                            sectionName = sectionBlock,
                            onlineClassLink = onlineLink ?: "No online class link yet.",
                            day = allSchedules.firstOrNull()?.day ?: "",
                            startTime = allSchedules.firstOrNull()?.startTime ?: "",
                            endTime = allSchedules.firstOrNull()?.endTime ?: "",
                            roomNumber = allSchedules.firstOrNull()?.room ?: "",
                            allSchedules = allSchedules
                        )
                    )
                }

                classAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e("OnlineClasses", "Failed to load subjects or assignments: $e")
                Toast.makeText(this@StudentOnlineClassesActivity, "Failed to load classes.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ FUNCTION TO FORMAT ALL SCHEDULE SLOTS
    private fun formatAllSchedules(scheduleSlots: Map<String, Map<String, String>>?): List<ClassSchedule> {
        val schedules = mutableListOf<ClassSchedule>()

        scheduleSlots?.forEach { (slotKey, slotData) ->
            val day = slotData["day"] ?: ""
            val startTime = slotData["startTime"] ?: ""
            val endTime = slotData["endTime"] ?: ""
            val room = slotData["roomLocation"] ?: ""

            if (day.isNotEmpty() && startTime.isNotEmpty()) {
                schedules.add(ClassSchedule(day, startTime, endTime, room))
            }
        }

        return schedules
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
            roomNumber = "",
            allSchedules = emptyList()
        )
    }
}

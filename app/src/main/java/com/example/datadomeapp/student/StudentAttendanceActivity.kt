package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentAttendanceActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val ATTENDANCE_COLLECTION = "dailyAttendanceRecords"

    private lateinit var recyclerView: RecyclerView
    // StudentAttendanceAdapter is resolved because it's in the same package
    private lateinit var attendanceAdapter: StudentAttendanceAdapter
    private var studentId: String? = null
    // StudentSubjectAttendance is resolved because it's in StudentDataModels.kt (same package)
    private val attendanceList = mutableListOf<StudentSubjectAttendance>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_attendance_summary)

        supportActionBar?.title = "My Attendance Summary"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        studentId = intent.getStringExtra("STUDENT_ID")

        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "Student ID info missing. Cannot load attendance.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewAttendanceSummary)
        recyclerView.layoutManager = LinearLayoutManager(this)

        attendanceAdapter = StudentAttendanceAdapter(attendanceList)
        recyclerView.adapter = attendanceAdapter

        loadAttendanceSummary()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadAttendanceSummary() {
        val currentStudentId = studentId ?: return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. FETCH ALL ENROLLED SUBJECTS from the student's record
                val subjectRecords = firestore.collection("students")
                    .document(currentStudentId)
                    .collection("subjects")
                    .get()
                    .await()

                if (subjectRecords.isEmpty) {
                    Toast.makeText(this@StudentAttendanceActivity, "You are not enrolled in any subjects.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                attendanceList.clear()

                // 2. ITERATE AND CALCULATE ATTENDANCE FOR EACH SUBJECT
                for (doc in subjectRecords.documents) {
                    val subjectCode = doc.getString("subjectCode") ?: "N/A"
                    val subjectTitle = doc.getString("subjectTitle") ?: "Subject Title Missing"
                    val assignmentNo = doc.getString("assignmentNo")

                    if (assignmentNo.isNullOrEmpty()) {
                        Log.w("AttendanceSummary", "Subject $subjectCode is missing assignmentNo.")
                        continue
                    }

                    // 3. FETCH ALL AGGREGATED ATTENDANCE RECORDS for this assignment
                    val attendanceSnapshot = firestore.collection(ATTENDANCE_COLLECTION)
                        .whereEqualTo("assignmentId", assignmentNo)
                        .get()
                        .await()

                    // 4. PROCESS RECORDS AND CALCULATE SUMMARY
                    var presentCount = 0
                    var absentCount = 0
                    var lateCount = 0
                    var excusedCount = 0
                    var totalClasses = 0

                    for (attendanceDoc in attendanceSnapshot.documents) {
                        val statuses = attendanceDoc.get("statuses") as? Map<String, String> ?: continue
                        val studentStatus = statuses[currentStudentId]

                        if (studentStatus != null) {
                            totalClasses++
                            when (studentStatus) {
                                "Present" -> presentCount++
                                "Absent" -> absentCount++
                                "Late" -> lateCount++
                                "Excused" -> excusedCount++
                            }
                        }
                    }

                    // 5. CALCULATE PERCENTAGE
                    val percentage = if (totalClasses > 0) {
                        // (Present + Late) are usually counted as attended classes
                        ((presentCount + lateCount).toDouble() / totalClasses.toDouble()) * 100
                    } else {
                        0.0
                    }

                    // 6. ADD TO LIST
                    attendanceList.add(
                        StudentSubjectAttendance(
                            subjectCode = subjectCode,
                            subjectTitle = subjectTitle,
                            totalClasses = totalClasses,
                            totalPresent = presentCount,
                            totalAbsent = absentCount,
                            totalLate = lateCount,
                            totalExcused = excusedCount,
                            attendancePercentage = percentage,
                            assignmentId = assignmentNo
                        )
                    )
                }

                attendanceAdapter.notifyDataSetChanged()
                if (attendanceList.isEmpty()) {
                    Toast.makeText(this@StudentAttendanceActivity, "No recorded attendance found for your subjects.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("AttendanceSummary", "Failed to load attendance summary: $e")
                Toast.makeText(this@StudentAttendanceActivity, "Failed to load summary: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
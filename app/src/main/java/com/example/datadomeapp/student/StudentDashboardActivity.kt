package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
// Import the StudentSubject model used in your Finalization transaction
import com.example.datadomeapp.models.StudentSubject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// 🛑 Import para sa LibraryActivity (nasa root package: com.example.datadomeapp)
import com.example.datadomeapp.LibraryActivity
// Import para sa ibang activities na nasa com.example.datadomeapp.student package
import com.example.datadomeapp.student.StudentFullScheduleActivity
import com.example.datadomeapp.student.StudentNotesActivity
import com.example.datadomeapp.student.StudentOnlineClassesActivity
import com.example.datadomeapp.student.StudentToDoListActivity


class StudentDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var tlDailySchedule: TableLayout
    private lateinit var tvScheduleStatus: TextView
    private lateinit var tvUserInfo: TextView

    // Variable para sa Section ID at Student ID
    private var studentSectionId: String? = null
    private var studentId: String? = null // Gagamitin ito para sa schedule path

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_dashboard)

        tlDailySchedule = findViewById(R.id.tlDailySchedule)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        tvUserInfo = findViewById(R.id.tvUserInfo)

        if (auth.currentUser == null) {
            // Redirect sa Login Activity
            // startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Simulan ang pagkuha ng data at i-set up ang mga button
        loadStudentInfo()
        setupFeatureButtons()
    }

    /**
     * Kukunin ang Section ID at Student ID ng student mula sa Master Record (students/{uid}).
     */
    private fun loadStudentInfo() {
        val uid = auth.currentUser?.uid ?: return
        tvUserInfo.text = "Loading student info..."

        // I-query ang master 'users' collection para makuha ang studentId.
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val fetchedStudentId = userDoc.getString("studentId")
                val fetchedCourseCode = userDoc.getString("courseCode")
                val fetchedYearLevel = userDoc.getString("yearLevel")

                if (fetchedStudentId.isNullOrEmpty()) {
                    // Walang Student ID. Hindi pa fully enrolled.
                    studentId = null
                    tvUserInfo.text = "Section: N/A | Status: Account created, not enrolled."
                    tvScheduleStatus.text = "🚫 Enrollment not finalized. Contact admin."
                    tvScheduleStatus.visibility = TextView.VISIBLE
                    return@addOnSuccessListener
                }

                // Kapag nakuha na ang Student ID, i-load ang master student record para sa Section ID
                firestore.collection("students").document(fetchedStudentId).get()
                    .addOnSuccessListener { studentDoc ->
                        val fetchedSectionId = studentDoc.getString("sectionId")

                        studentId = fetchedStudentId
                        studentSectionId = fetchedSectionId

                        val displayInfo = "ID: $studentId | Course: $fetchedCourseCode $fetchedYearLevel | Section: ${fetchedSectionId ?: "Irregular"}"
                        tvUserInfo.text = displayInfo

                        // Kapag nakuha na ang Student ID, I-LOAD NA AGAD ang schedule gamit ang Student ID.
                        loadDailySchedule(fetchedStudentId)
                        Log.i("SCHEDULE_DEBUG", "Fetched Student ID: $studentId | Section ID: $studentSectionId")
                    }
                    .addOnFailureListener { e ->
                        // Error sa pag-load ng student master record
                        tvUserInfo.text = "Section: Error loading details."
                        tvScheduleStatus.text = "Error loading student details."
                        tvScheduleStatus.visibility = TextView.VISIBLE
                        Log.e("SCHEDULE_DEBUG", "Failed to load student master record.", e)
                    }
            }
            .addOnFailureListener { e ->
                // Error sa pag-load ng user record
                tvUserInfo.text = "Section: Error loading info."
                tvScheduleStatus.text = "Error loading user data: ${e.localizedMessage}"
                tvScheduleStatus.visibility = TextView.VISIBLE
                Log.e("SCHEDULE_DEBUG", "Failed to load user info.", e)
            }
    }

    /**
     * Nagku-query ito sa database para sa schedule ng kasalukuyang araw gamit ang Student ID.
     */
    private fun loadDailySchedule(studentId: String) {
        // Tiyakin na ang Locale mo ay tumutugma sa ginamit mo sa Admin side
        val currentDay = SimpleDateFormat("EEEE", Locale.US).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        tvScheduleStatus.text = "Loading classes for $currentDay..."
        tvScheduleStatus.visibility = TextView.VISIBLE

        // 🛑 CRITICAL FIX: I-query ang sub-collection ng student subjects.
        firestore.collection("students")
            .document(studentId)
            .collection("subjects")
            .get()
            .addOnSuccessListener { snapshot ->

                // Tiyakin na tinatanggal ang lahat ng dynamic views (simula sa row 1)
                if (tlDailySchedule.childCount > 0) {
                    tlDailySchedule.removeViews(1, tlDailySchedule.childCount - 1)
                }

                // 1. I-filter ang lahat ng subjects sa schedule para lang sa kasalukuyang araw at oras
                val todaySchedule = snapshot.documents.map { it.toObject(StudentSubject::class.java) }
                    .filterNotNull()
                    .mapNotNull { item ->
                        // I-check kung ang schedule string ay naglalaman ng kasalukuyang araw
                        if (!item.schedule.startsWith(currentDay)) return@mapNotNull null

                        // I-parse ang schedule string para makuha ang time
                        val timeParts = item.schedule.substringAfter("$currentDay ").split("-")
                        val startTime = timeParts.getOrNull(0) ?: "N/A"
                        val endTime = timeParts.getOrNull(1) ?: "N/A"

                        // CRITICAL TIME FILTER: Hindi na ipapakita kung tapos na ang oras.
                        if (endTime != "N/A" && endTime.compareTo(currentTime) < 0) {
                            Log.d("SCHEDULE_DEBUG", "Skipping ${item.subjectCode}. End time $endTime is before current time $currentTime.")
                            return@mapNotNull null // Skip na kung tapos na ang klase
                        }

                        // I-return ang data sa isang mas simpleng format
                        mapOf(
                            "subjectCode" to item.subjectCode,
                            "sectionName" to item.sectionName,
                            "startTime" to startTime,
                            "endTime" to endTime,
                            "venue" to "Room A-101 (Placeholder)" // Venue still hardcoded
                        )
                    }
                    .sortedBy { it["startTime"] } // I-sort base sa oras ng pagsisimula



                if (todaySchedule.isEmpty()) {
                    tvScheduleStatus.text = "🎉 Walang natitirang klase ngayong $currentDay!"
                    tvScheduleStatus.visibility = TextView.VISIBLE
                    Log.w("SCHEDULE_DEBUG", "No remaining classes found for Student ID: $studentId on $currentDay.")
                    return@addOnSuccessListener
                }

                // 2. I-populate ang TableLayout
                todaySchedule.forEach { item ->
                    tlDailySchedule.addView(createScheduleRow(item))
                }

                // Tanggalin ang status text kapag may laman na ang table
                tvScheduleStatus.visibility = View.GONE
                Log.i("SCHEDULE_DEBUG", "Found ${todaySchedule.size} remaining classes. Render successful.")
            }
            .addOnFailureListener { e ->
                tvScheduleStatus.text = "Error loading schedule: ${e.localizedMessage}"
                tvScheduleStatus.visibility = View.VISIBLE
                Log.e("SCHEDULE_DEBUG", "Failed to load daily schedule.", e)
            }
    }

    /**
     * Helper function para gumawa ng TableRow para sa bawat class.
     * Gumagamit na ito ng Map<String, String> dahil sa pag-parse natin sa taas.
     */
    private fun createScheduleRow(item: Map<String, String>): TableRow {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            setPadding(0, 10, 0, 10)
        }

        // Column 1: Time
        val tvTime = TextView(this).apply {
            text = "${item["startTime"]} - ${item["endTime"]}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.START
        }

        // Column 2: Subject and Venue
        val tvSubject = TextView(this).apply {
            text = "${item["subjectCode"]} (${item["sectionName"]})\n@ ${item["venue"]}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(8, 8, 8, 8)
            gravity = Gravity.START
            setTextColor(Color.parseColor("#333333"))
        }

        row.addView(tvTime)
        row.addView(tvSubject)
        return row
    }

    private fun setupFeatureButtons() {
        // Logout Button
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            // Dito mo ilalagay ang redirect sa Login Activity
            // startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // I-set up ang mga button na may "Coming Soon" Toast
        // 🛑 Inalis ang R.id.btnLibrary sa listahan.
        val comingSoonIds = listOf(
            R.id.btnAttendance,
            R.id.btnAssignments,
            R.id.btnCanteen
        )

        for (id in comingSoonIds) {
            findViewById<Button>(id).setOnClickListener {
                val buttonText = findViewById<Button>(id).text
                Toast.makeText(this, "$buttonText: Coming Soon!", Toast.LENGTH_SHORT).show()
            }
        }

        // 🛑 FIXED: Library Button (pumunta na sa LibraryActivity)
        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            val intent = Intent(this, LibraryActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnOnlineClasses).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load online classes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Dito tayo magre-redirect sa isang bagong Activity
            val intent = Intent(this, StudentOnlineClassesActivity::class.java)
            intent.putExtra("STUDENT_ID", studentId) // Ipapasa ang Student ID
            startActivity(intent)
        }

        // Full Schedule Button
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            if (studentId.isNullOrEmpty()) {
                Toast.makeText(this, "Student ID is missing. Cannot load full schedule.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Tiyakin na tama ang target activity
            val intent = Intent(this, StudentFullScheduleActivity::class.java)
            // Gamitin ang Student ID bilang "USER_ID" para sa schedule activity
            intent.putExtra("USER_ID", studentId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnToDoList).setOnClickListener {
            val intent = Intent(this, StudentToDoListActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnNotes).setOnClickListener {
            val intent = Intent(this, StudentNotesActivity::class.java)
            startActivity(intent)
        }
    }
}
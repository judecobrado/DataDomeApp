package com.example.datadomeapp.student

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

data class SectionDisplay(val display: String, val block: String)

class CourseEnrollmentActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    private val sectionsCollection = firestore.collection("sections")

    private lateinit var tvEnrollmentStatus: TextView
    private lateinit var spnCourse: Spinner
    private lateinit var spnYear: Spinner
    private lateinit var spnSectionBlock: Spinner
    private lateinit var btnEnroll: Button

    private var studentUid: String? = null
    private var studentCourseCode: String? = null
    private var studentYearLevelKey: String? = null
    private var studentYearLevelDisplay: String? = null

    // ✅ Corrected: Only one declaration for selectedSectionBlock
    private var selectedSectionBlock: String? = null

    private val sectionDisplayList = mutableListOf<SectionDisplay>()
    private lateinit var sectionAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_enrollment)

        tvEnrollmentStatus = findViewById(R.id.tvEnrollmentStatus)
        spnCourse = findViewById(R.id.spnCourse)
        spnYear = findViewById(R.id.spnYear)
        spnSectionBlock = findViewById(R.id.spnSectionBlock)
        btnEnroll = findViewById(R.id.btnEnroll)

        studentUid = auth.currentUser?.uid
        if (studentUid == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sectionAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            mutableListOf("--- Select Section Block ---")
        )
        spnSectionBlock.adapter = sectionAdapter
        btnEnroll.isEnabled = false

        setupListeners()
        loadStudentCourseData()
    }

    private fun setupListeners() {
        spnSectionBlock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSectionBlock = if (position > 0 && sectionDisplayList.isNotEmpty()) {
                    sectionDisplayList[position - 1].block
                } else null
                btnEnroll.isEnabled = selectedSectionBlock != null
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSectionBlock = null
                btnEnroll.isEnabled = false
            }
        }

        btnEnroll.setOnClickListener {
            val block = selectedSectionBlock
            if (block != null) {
                enrollStudent(block)
            } else {
                Toast.makeText(this, "Please select a section to enroll.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStudentCourseData() {
        usersCollection.document(studentUid!!).get()
            .addOnSuccessListener { doc ->
                studentCourseCode = doc.getString("courseCode")?.trim()?.uppercase(Locale.getDefault())
                studentYearLevelDisplay = doc.getString("yearLevel")

                if (studentCourseCode.isNullOrEmpty() || studentYearLevelDisplay.isNullOrEmpty()) {
                    tvEnrollmentStatus.text = "Error: Missing Course/Year Data. Contact Admin."
                    Toast.makeText(this, "Admin needs to set your Course and Year Level first.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                studentYearLevelKey = studentYearLevelDisplay!!.replace(" ", "")
                tvEnrollmentStatus.text = "Ready to Enroll in: ${studentCourseCode} - ${studentYearLevelDisplay}"

                spnCourse.isEnabled = false
                spnYear.isEnabled = false

                loadAvailableSections()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("Enrollment", "Load user data failed: ${e.message}")
            }
    }

    private fun loadAvailableSections() {
        val courseCode = studentCourseCode ?: return
        val yearKey = studentYearLevelKey ?: return

        sectionsCollection.document(courseCode).get()
            .addOnSuccessListener { doc ->
                sectionDisplayList.clear()
                val sectionsMap = doc.get("sections") as? Map<*, *>
                val blocksRaw = sectionsMap?.get(yearKey)

                val availableBlocks = mutableListOf<String>()
                availableBlocks.add("--- Select Section Block ---") // Default prompt

                if (blocksRaw is List<*>) {
                    val blocksList = blocksRaw.filterIsInstance<String>().sorted()
                    blocksList.forEach { block ->
                        sectionDisplayList.add(SectionDisplay("${studentYearLevelDisplay} - $block", block))
                        availableBlocks.add(sectionDisplayList.last().display)
                    }
                }

                sectionAdapter.clear()
                sectionAdapter.addAll(availableBlocks)
                sectionAdapter.notifyDataSetChanged()

                if (sectionDisplayList.isEmpty()) {
                    Toast.makeText(this, "No sections available for your course/year.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load sections: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enrollStudent(selectedBlock: String) {
        val uid = studentUid ?: return
        val course = studentCourseCode ?: return
        val year = studentYearLevelDisplay ?: return

        val yearNumber = year.substring(0, 1)
        val sectionName = "${course.uppercase(Locale.getDefault())}-${yearNumber}-${selectedBlock}"

        val enrollmentData = hashMapOf(
            "isEnrolled" to true,
            "enrolledCourseCode" to course,
            "enrolledYearLevel" to year,
            "enrolledSectionBlock" to selectedBlock,
            "enrolledSectionName" to sectionName,
            "enrollmentDate" to FieldValue.serverTimestamp()
        )

        usersCollection.document(uid).set(enrollmentData, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Enrolled in ${year} - ${selectedBlock} successfully! Welcome!", Toast.LENGTH_LONG).show()
                val intent = Intent(this, StudentDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Enrollment failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("Enrollment", "Save failed: ${e.message}")
            }
    }
}

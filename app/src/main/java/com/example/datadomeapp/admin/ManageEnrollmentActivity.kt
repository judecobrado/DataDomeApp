package com.example.datadomeapp.admin

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.enrollment.EmailSendCallback
import com.example.datadomeapp.enrollment.GmailSender
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp
import java.util.*

// 🟢 NEW MODEL: Para sa pag-iimbak ng reference key sa record ng estudyante
data class StudentAssignmentRecord(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val assignmentNo: String = "", // ⬅️ CRITICAL: Reference sa ClassAssignment ID
    val teacherName: String = "", // Idinagdag: Guro
    val sectionBlock: String = "",
    val onlineLink: String = "",
    val credits: Int = 3,         // Idinagdag: Credits (Default 3)
    val prelim: Int? = null,      // Idinagdag: Grades (Nullable)
    val midterm: Int? = null,
    val final: Int? = null,
    val gwa: String = "",         // Idinagdag: Letter Grade/GWA
    val semester: String = "",      // Idinagdag: Semester
    val academicYear: String = "",  // Idinagdag: Academic Year
    val yearLevel: String = ""      // Idinagdag: Year Level
)

// Local models to avoid conflicts with other files
data class LocalCurriculum(
    val requiredSubjects: List<LocalSubjectEntry> = emptyList()
)

data class LocalSubjectEntry(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val credits: Int = 3
)

data class LocalClassAssignment(
    val assignmentNo: String = "",
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val courseCode: String = "",
    val yearLevel: String = "",
    val teacherName: String = "",
    val teacherId: String = "",
    val enrolledCount: Int = 0,
    val maxCapacity: Int = 50,
    val onlineClassLink: String = "",
    val scheduleSlots: Map<String, LocalTimeSlot> = emptyMap()
)

data class LocalTimeSlot(
    val day: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val roomLocation: String = "",
    val sectionBlock: String = ""
)

class ManageEnrollmentsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var functions: FirebaseFunctions

    private lateinit var tvPendingCount: TextView
    private lateinit var tvApprovedCount: TextView
    private lateinit var tvRejectedCount: TextView
    private lateinit var tvRequestCount: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EnrollmentAdapter
    private val enrollmentList = mutableListOf<Enrollment>()

    private val requiredSubjectsMap = mutableMapOf<String, LocalSubjectEntry>()
    private val availableSectionsMap = mutableMapOf<String, List<LocalClassAssignment>>()

    private val finalYearLevels = listOf("Select Year Level", "1st Year", "2nd Year", "3rd Year", "4th Year")
    private val finalEnrollmentTypes = listOf("Select Type", "Regular", "Irregular")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_enrollment_management)

        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvApprovedCount = findViewById(R.id.tvApprovedCount)
        tvRejectedCount = findViewById(R.id.tvRejectedCount)
        tvRequestCount = findViewById(R.id.tvRequestCount)

        // Tiyakin na tama ang region
        functions = FirebaseFunctions.getInstance("asia-southeast1")

        recyclerView = findViewById(R.id.rvEnrollments)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EnrollmentAdapter(enrollmentList) { enrollment ->
            showEnrollmentDetailDialog(enrollment)
        }
        recyclerView.adapter = adapter

        loadPendingEnrollments()
        loadEnrollmentCounts()

        val btnToggleSignup = findViewById<Button>(R.id.btnToggleSignup)

        // Load current state from Firestore
        firestore.collection("appSettings").document("mainActivity")
            .get()
            .addOnSuccessListener { doc ->
                val enabled = doc.getBoolean("signupEnabled") ?: true
                btnToggleSignup.text = if (enabled) "Disable Signup Button" else "Enable Signup Button"
            }
            .addOnFailureListener {
                btnToggleSignup.text = "Toggle Signup Button"
            }

        // Toggle button click
        btnToggleSignup.setOnClickListener {
            firestore.collection("appSettings").document("mainActivity")
                .get()
                .addOnSuccessListener { doc ->
                    val enabled = doc.getBoolean("signupEnabled") ?: true
                    val newState = !enabled
                    firestore.collection("appSettings").document("mainActivity")
                        .set(mapOf("signupEnabled" to newState))
                        .addOnSuccessListener {
                            btnToggleSignup.text = if (newState) "Disable Signup Button" else "Enable Signup Button"
                            Toast.makeText(this, "Signup button updated!", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }

    private fun loadEnrollmentCounts() {
        Log.d("EnrollmentDebug", "🔄 Loading enrollment counts...")

        // Count Pending Enrollments
        firestore.collection("pendingEnrollments")
            .whereEqualTo("status", "submitted")
            .get()
            .addOnSuccessListener { snapshot ->
                val pendingCount = snapshot.documents.size
                tvPendingCount.text = pendingCount.toString()
                tvRequestCount.text = "($pendingCount)"
                Log.d("EnrollmentDebug", "📊 Pending count: $pendingCount")
            }
            .addOnFailureListener { exception ->
                Log.e("EnrollmentDebug", "❌ Error counting pending enrollments", exception)
                tvPendingCount.text = "0"
            }

        // Count Approved Enrollments (students collection)
        firestore.collection("students")
            .whereEqualTo("status", "Admitted")
            .get()
            .addOnSuccessListener { snapshot ->
                val approvedCount = snapshot.documents.size
                tvApprovedCount.text = approvedCount.toString()
                Log.d("EnrollmentDebug", "📊 Approved count: $approvedCount")
            }
            .addOnFailureListener { exception ->
                Log.e("EnrollmentDebug", "❌ Error counting approved enrollments", exception)
                tvApprovedCount.text = "0"
            }

        // Count Rejected Enrollments
        firestore.collection("notPassedEnrollments")
            .get()
            .addOnSuccessListener { snapshot ->
                val rejectedCount = snapshot.documents.size
                tvRejectedCount.text = rejectedCount.toString()
                Log.d("EnrollmentDebug", "📊 Rejected count: $rejectedCount")
            }
            .addOnFailureListener { exception ->
                Log.e("EnrollmentDebug", "❌ Error counting rejected enrollments", exception)
                tvRejectedCount.text = "0"
            }
    }

    private fun loadPendingEnrollments() {
        Log.d("EnrollmentDebug", "🔄 Starting to load pending enrollments...")

        firestore.collection("pendingEnrollments")
            .whereEqualTo("status", "submitted")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                enrollmentList.clear()
                Log.d("EnrollmentDebug", "📊 Firestore returned ${snapshot.documents.size} documents")

                if (snapshot.isEmpty) {
                    Log.d("EnrollmentDebug", "ℹ️ No pending enrollments found with status 'submitted'")
                    Toast.makeText(this, "No pending enrollments found.", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                    // ✅ DAGDAG: Update pending count to 0 when empty
                    tvPendingCount.text = "0"
                    tvRequestCount.text = "(0)"
                    return@addOnSuccessListener
                }

                for (doc in snapshot.documents) {
                    try {
                        Log.d("EnrollmentDebug", "📄 Processing document: ${doc.id}")
                        val enrollmentData = doc.data ?: emptyMap()

                        // Use the enhanced Enrollment.fromFirestore method
                        val enrollment = Enrollment.fromFirestore(doc.id, enrollmentData)
                        enrollmentList.add(enrollment)

                        Log.d("EnrollmentDebug", "✅ Added enrollment: ${enrollment.firstName} ${enrollment.lastName} - ${enrollment.email}")
                    } catch (e: Exception) {
                        Log.e("EnrollmentDebug", "❌ Error parsing document ${doc.id}", e)
                    }
                }

                adapter.notifyDataSetChanged()
                Log.d("EnrollmentDebug", "🎉 Successfully loaded ${enrollmentList.size} enrollments")

                // ✅ DAGDAG: Update the pending count with the actual loaded count
                tvPendingCount.text = enrollmentList.size.toString()
                tvRequestCount.text = "(${enrollmentList.size})"

                if (enrollmentList.isEmpty()) {
                    Toast.makeText(this, "No valid pending enrollments found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                val errorMsg = "Failed to load enrollments: ${exception.message}"
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                Log.e("EnrollmentDebug", "❌ Error loading pending enrollments", exception)
                // ✅ DAGDAG: Reset counts on error
                tvPendingCount.text = "0"
                tvRequestCount.text = "(0)"
            }
    }

    fun showEnrollmentDetailDialog(e: Enrollment) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_enrollment_detail_dialog, null)

        val tvName = dialogView.findViewById<TextView>(R.id.tvName)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvEmail)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvPhone)
        val tvAddress = dialogView.findViewById<TextView>(R.id.tvAddress)
        val tvDOB = dialogView.findViewById<TextView>(R.id.tvDOB)
        val tvGender = dialogView.findViewById<TextView>(R.id.tvGender)
        val tvCourse = dialogView.findViewById<TextView>(R.id.tvCourse)
        val tvGuardian = dialogView.findViewById<TextView>(R.id.tvGuardian)
        val tvApplicationType = dialogView.findViewById<TextView>(R.id.tvApplicationType)
        val tvFatherInfo = dialogView.findViewById<TextView>(R.id.tvFatherInfo)
        val tvMotherInfo = dialogView.findViewById<TextView>(R.id.tvMotherInfo)
        val tvGuardianRelationship = dialogView.findViewById<TextView>(R.id.tvGuardianRelationship)
        val spinnerFinalYearLevel = dialogView.findViewById<Spinner>(R.id.spinnerFinalYearLevel)
        val spinnerFinalEnrollmentType = dialogView.findViewById<Spinner>(R.id.spinnerFinalEnrollmentType)

        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("1st Year"))
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFinalYearLevel.adapter = yearAdapter
        spinnerFinalYearLevel.isEnabled = false

        // Use direct field access from the Enrollment model
        val courseName = if (e.courseName.isNotEmpty()) e.courseName else e.course
        val courseCode = e.courseCode

        // Parent information from Enrollment fields
        val fatherFirstName = e.fatherFirstName
        val fatherMiddleName = e.fatherMiddleName
        val fatherLastName = e.fatherLastName
        val fatherDOB = e.fatherDOB
        val fatherPhone = e.fatherPhone
        val fatherOccupation = e.fatherOccupation

        val motherFirstName = e.motherFirstName
        val motherMiddleName = e.motherMiddleName
        val motherLastName = e.motherLastName
        val motherDOB = e.motherDOB
        val motherPhone = e.motherPhone
        val motherOccupation = e.motherOccupation

        val guardianRelationship = e.guardianRelationship

        tvEmail.text = "Email: ${e.email}"
        tvPhone.text = "Phone: ${e.phone}"
        tvAddress.text = "Address: ${e.address}"
        tvDOB.text = "DOB: ${e.dateOfBirth}"
        tvGender.text = "Gender: ${e.gender}"
        tvCourse.text = "Course Applied: $courseName (Code: $courseCode)"
        tvGuardian.text = "Guardian: ${e.guardianName} (${e.guardianPhone})"
        tvGuardianRelationship.text = "Guardian Relationship: $guardianRelationship"

        val studentDisplayName = buildString {
            append(e.lastName)
            if (e.studentLastNameExtension.isNotEmpty()) {
                append(" ${e.studentLastNameExtension}")
            }
            append(", ${e.firstName}")
            if (e.middleName.isNotEmpty()) {
                append(" ${e.middleName.first()}.")
            }
        }

        val fatherDisplayName = buildString {
            append(e.fatherFirstName)
            if (e.fatherMiddleName.isNotEmpty()) {
                append(" ${e.fatherMiddleName}")
            }
            append(" ${e.fatherLastName}")
            if (e.fatherLastNameExtension.isNotEmpty()) {
                append(" ${e.fatherLastNameExtension}")
            }
        }

        val motherDisplayName = buildString {
            append(e.motherFirstName)
            if (e.motherMiddleName.isNotEmpty()) {
                append(" ${e.motherMiddleName}")
            }
            append(" ${e.motherLastName}")
            if (e.motherLastNameExtension.isNotEmpty()) {
                append(" ${e.motherLastNameExtension}")
            }
        }

        // ✅ SET DISPLAY NAMES ONLY (separate pa rin sa database)
        tvName.text = "Name: $studentDisplayName"
        tvFatherInfo.text = "Father: $fatherDisplayName\nPhone: ${e.fatherPhone}\nOccupation: ${e.fatherOccupation}\nDOB: ${if (e.fatherDOB.isNotEmpty()) e.fatherDOB else "Not provided"}"
        tvMotherInfo.text = "Mother: $motherDisplayName\nPhone: ${e.motherPhone}\nOccupation: ${e.motherOccupation}\nDOB: ${if (e.motherDOB.isNotEmpty()) e.motherDOB else "Not provided"}"

        val applicationType = if (e.applicationType.isNotEmpty()) e.applicationType else "N/A"
        tvApplicationType.text = "Application Status: $applicationType"

        //val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, finalYearLevels)
        //yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        //spinnerFinalYearLevel.adapter = yearAdapter

        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, finalEnrollmentTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFinalEnrollmentType.adapter = typeAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Admission Decision")
            .setPositiveButton("Admit & Select Subjects", null)
            .setNegativeButton("Reject") { _, _ -> markAsNotPassed(e) }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val finalYearLevel = spinnerFinalYearLevel.selectedItem.toString()
            val finalEnrollmentType = spinnerFinalEnrollmentType.selectedItem.toString()

            if (finalYearLevel == "Select Year Level" || finalEnrollmentType == "Select Type") {
                Toast.makeText(this, "Please set the final Year Level and Enrollment Type.", Toast.LENGTH_LONG).show()
            } else if (courseCode.isEmpty()) {
                Toast.makeText(this, "Course Code is missing. Cannot proceed with subject selection.", Toast.LENGTH_LONG).show()
            } else {
                dialog.dismiss()
                showSubjectSelectionDialog(
                    course = courseCode,
                    finalYearLevel = finalYearLevel,
                    finalEnrollmentType = finalEnrollmentType,
                    studentEmail = e.email,
                    pendingEnrollmentId = e.id
                )
            }
        }
    }

    // ----------------------------------------------------
    // Subject Selection Dialog: Loads curriculum and available sections
    // ----------------------------------------------------
    private fun showSubjectSelectionDialog(
        course: String, finalYearLevel: String, finalEnrollmentType: String, studentEmail: String, pendingEnrollmentId: String
    ) {

        val normalizedCourseCode = course
        val yearLevelClean = finalYearLevel.replace(" ", "")
        val curriculumDocId = "${normalizedCourseCode}_$yearLevelClean"

        Log.d("EnrollmentDebug", "--- Starting Subject Selection ---")
        Log.d("EnrollmentDebug", "Curriculum Doc ID: $curriculumDocId")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. Fetch Curriculum
                val curriculumSnapshot = firestore.collection("curriculums").document(curriculumDocId).get().await()
                val curriculumData = curriculumSnapshot.data
                val requiredSubjects = if (curriculumData != null) {
                    // Manual parsing to avoid model conflicts
                    (curriculumData["requiredSubjects"] as? List<Map<String, Any>>)?.map { subjectMap ->
                        LocalSubjectEntry(
                            subjectCode = subjectMap["subjectCode"] as? String ?: "",
                            subjectTitle = subjectMap["subjectTitle"] as? String ?: "",
                            credits = (subjectMap["credits"] as? Number)?.toInt() ?: 3
                        )
                    } ?: emptyList()
                } else {
                    emptyList()
                }

                requiredSubjectsMap.clear()
                requiredSubjects.forEach { requiredSubjectsMap[it.subjectCode] = it }

                if (requiredSubjects.isEmpty()) {
                    Toast.makeText(this@ManageEnrollmentsActivity, "No required subjects found for $curriculumDocId. Check Curriculum data.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // 2. Fetch Available Sections
                val assignmentSnapshot = firestore.collection("classAssignments")
                    .whereEqualTo("courseCode", normalizedCourseCode)
                    .whereEqualTo("yearLevel", finalYearLevel)
                    .get().await()

                availableSectionsMap.clear()
                val allAssignments = assignmentSnapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    LocalClassAssignment(
                        assignmentNo = doc.id,
                        subjectCode = data["subjectCode"] as? String ?: "",
                        subjectTitle = data["subjectTitle"] as? String ?: "",
                        courseCode = data["courseCode"] as? String ?: "",
                        yearLevel = data["yearLevel"] as? String ?: "",
                        teacherName = data["teacherName"] as? String ?: "",
                        teacherId = data["teacherId"] as? String ?: "",
                        enrolledCount = (data["enrolledCount"] as? Number)?.toInt() ?: 0,
                        maxCapacity = (data["maxCapacity"] as? Number)?.toInt() ?: 50,
                        onlineClassLink = data["onlineClassLink"] as? String ?: "",
                        scheduleSlots = parseScheduleSlots(data["scheduleSlots"] as? Map<String, Map<String, Any>> ?: emptyMap())
                    )
                }

                // I-filter sa Client-Side gamit ang dynamic na maxCapacity
                val availableAssignments = allAssignments.filter { it.enrolledCount < it.maxCapacity }

                availableAssignments.groupBy { it.subjectCode }
                    .forEach { (code, assignments) -> availableSectionsMap[code] = assignments }

                if (availableSectionsMap.isEmpty() && requiredSubjects.isNotEmpty()) {
                    Toast.makeText(this@ManageEnrollmentsActivity, "No available sections found for ${normalizedCourseCode} - ${finalYearLevel}. Double Check Data.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // --- 3. Construct and Show the Interactive Dialog ---
                showInteractiveAssignmentDialog(
                    studentEmail = studentEmail,
                    pendingEnrollmentId = pendingEnrollmentId,
                    requiredSubjects = requiredSubjects,
                    finalYearLevel = finalYearLevel,
                    finalEnrollmentType = finalEnrollmentType,
                    courseCode = normalizedCourseCode
                )

            } catch (e: Exception) {
                Toast.makeText(this@ManageEnrollmentsActivity, "Failed to load schedules: ${e.message}. Check Indexing and Data.", Toast.LENGTH_LONG).show()
                Log.e("EnrollmentDebug", "Schedule fetch failed. Error: ${e.message}", e)
            }
        }
    }

    // Helper function to parse schedule slots
    private fun parseScheduleSlots(slotsData: Map<String, Map<String, Any>>): Map<String, LocalTimeSlot> {
        return slotsData.mapValues { (_, slotData) ->
            LocalTimeSlot(
                day = slotData["day"] as? String ?: "",
                startTime = slotData["startTime"] as? String ?: "",
                endTime = slotData["endTime"] as? String ?: "",
                roomLocation = slotData["roomLocation"] as? String ?: "",
                sectionBlock = slotData["sectionBlock"] as? String ?: ""
            )
        }
    }

    // ----------------------------------------------------
    // Interactive Subject Assignment Dialog
    // ----------------------------------------------------
    private fun showInteractiveAssignmentDialog(
        studentEmail: String, pendingEnrollmentId: String,
        requiredSubjects: List<LocalSubjectEntry>, finalYearLevel: String, finalEnrollmentType: String, courseCode: String
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_enrollment_dialog_finalize, null)
        val selectionList = dialogView.findViewById<LinearLayout>(R.id.llSubjectSelections)
        val btnFinalize = dialogView.findViewById<Button>(R.id.btnFinalizeEnrollment)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEnrollment)

        val dialogTitle = "Enrollment: $finalEnrollmentType ($finalYearLevel)"
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val finalSelections = mutableMapOf<String, LocalClassAssignment?>()

        btnCancel.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Enrollment selection cancelled.", Toast.LENGTH_SHORT).show()
        }

        // 🛑 REGULAR STUDENT LOGIC: SINGLE SECTION SELECTION
        if (finalEnrollmentType == "Regular") {
            // 🟢 FIX: Kukunin ang Section Block mula sa TimeSlot
            val allUniqueSections = availableSectionsMap.values.flatten()
                .flatMap { it.scheduleSlots.values } // Kukunin ang lahat ng TimeSlot objects
                .map { it.sectionBlock } // Kukunin ang Section Block
                .distinct()
                .sorted()

            if (allUniqueSections.isEmpty()) {
                val tvError = TextView(this).apply {
                    text = "🚫 ERROR: No Sections available for $courseCode $finalYearLevel. Please create class assignments first."
                    setTextColor(android.graphics.Color.RED)
                    setPadding(0, 5, 0, 15)
                }
                selectionList.addView(tvError)
                btnFinalize.isEnabled = false
                dialog.show()
                return
            }

            val tvLabel = TextView(this).apply {
                text = "Select Final Section Block for All Subjects (Regular)"
                textSize = 16f
                setPadding(0, 10, 0, 0)
            }
            selectionList.addView(tvLabel)

            val sectionSpinner = Spinner(this).apply {
                val spinnerOptions = allUniqueSections.toMutableList()
                spinnerOptions.add(0, "Select Section...")
                adapter = ArrayAdapter(this@ManageEnrollmentsActivity, android.R.layout.simple_spinner_dropdown_item, spinnerOptions)
            }
            selectionList.addView(sectionSpinner)

            // I-handle ang selection ng section
            sectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    finalSelections.clear()

                    if (position > 0) {
                        val selectedSectionBlock = parent?.getItemAtPosition(position).toString()

                        // Awtomatikong itatala ang lahat ng subjects sa napiling section na ito
                        requiredSubjects.forEach { subject ->
                            // 🟢 FIX: Hahanapin ang ClassAssignment na may TimeSlot na tumutugma sa Section Block
                            val assignment = availableSectionsMap[subject.subjectCode]
                                ?.find { assign ->
                                    assign.scheduleSlots.values.any { slot -> slot.sectionBlock == selectedSectionBlock }
                                }

                            if (assignment != null) {
                                finalSelections[subject.subjectCode] = assignment
                            } else {
                                // Kapag walang assignment na nakita (Missing Subject in Section)
                                finalSelections[subject.subjectCode] = null
                                Log.w("EnrollmentDebug", "WARNING: Subject ${subject.subjectCode} is missing in Section $selectedSectionBlock.")
                            }
                        }
                    } else {
                        finalSelections.clear()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        } else {
            // 🛑 IRREGULAR STUDENT LOGIC: PER-SUBJECT SELECTION
            requiredSubjects.forEach { subject ->
                val textView = TextView(this).apply {
                    text = "${subject.subjectCode} - ${subject.subjectTitle}"
                    textSize = 16f
                    setPadding(0, 10, 0, 0)
                }

                val sections = availableSectionsMap[subject.subjectCode] ?: emptyList()

                val availableAssignments = sections.filter { it.enrolledCount < it.maxCapacity }
                val fullAssignments = sections.filter { it.enrolledCount >= it.maxCapacity }

                val sectionDisplay = mutableListOf<String>()

                // 🟢 FIX: Kukunin ang Schedule details mula sa TimeSlot
                if (availableAssignments.isNotEmpty()) {
                    sectionDisplay.addAll(availableAssignments.map { assignment ->
                        val firstSlot = assignment.scheduleSlots.values.firstOrNull()
                        val scheduleTime = if (firstSlot != null) {
                            // 🟢 FIX: Gumamit ng roomLocation
                            "${firstSlot.day} ${firstSlot.startTime}-${firstSlot.endTime} @${firstSlot.roomLocation}"
                        } else {
                            "N/A Schedule"
                        }
                        // 🟢 FIX: Gumamit ng sectionBlock
                        "${firstSlot?.sectionBlock ?: "N/A Section"} (${assignment.teacherName} - $scheduleTime) [${assignment.enrolledCount}/${assignment.maxCapacity}]"
                    })
                }

                // Optionally show full sections at the end
                sectionDisplay.addAll(fullAssignments.map { assignment ->
                    val firstSlot = assignment.scheduleSlots.values.firstOrNull()
                    val scheduleTime = if (firstSlot != null) {
                        "${firstSlot.day} ${firstSlot.startTime}-${firstSlot.endTime} @${firstSlot.roomLocation}"
                    } else {
                        "N/A Schedule"
                    }
                    "${firstSlot?.sectionBlock ?: "N/A Section"} (${assignment.teacherName} - $scheduleTime) [FULL]"
                })

                sectionDisplay.add(0, "SKIP SUBJECT (Irregular)")

                // Spinner
                val spinner = Spinner(this).apply {
                    adapter = ArrayAdapter(
                        this@ManageEnrollmentsActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        sectionDisplay
                    )
                    setSelection(0)
                    finalSelections[subject.subjectCode] = null

                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            finalSelections[subject.subjectCode] =
                                if (position == 0 || availableAssignments.isEmpty()) null
                                else {
                                    if (position - 1 < availableAssignments.size) availableAssignments[position - 1]
                                    else null
                                }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }

                selectionList.addView(textView)
                selectionList.addView(spinner)
            }
        }

        btnFinalize.setOnClickListener {
            val assignmentsToSave = finalSelections.values.filterNotNull()

            Log.d("EnrollmentDebug", "===== FINAL SELECTIONS =====")
            finalSelections.forEach { (subjectCode, assignment) ->
                if (assignment != null) {
                    val firstSlot = assignment.scheduleSlots.values.firstOrNull()
                    Log.d("EnrollmentDebug", "Subject: $subjectCode | Section: ${firstSlot?.sectionBlock} | Teacher: ${assignment.teacherName} | Slots: ${assignment.scheduleSlots.size}")
                } else {
                    Log.d("EnrollmentDebug", "Subject: $subjectCode | Section: NONE (Skipped or Missing)")
                }
            }

            // Validation
            if (finalEnrollmentType == "Regular" && assignmentsToSave.size < requiredSubjects.size) {
                if (finalSelections.isEmpty()) {
                    Toast.makeText(this, "ERROR: Please select a single Section Block for the Regular Enrollment.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "WARNING: The selected section is missing ${requiredSubjects.size - assignmentsToSave.size} required subject(s). Please choose a complete section.", Toast.LENGTH_LONG).show()
                }
                return@setOnClickListener
            }

            // 🟢 FIX: Check conflict gamit ang UPDATED logic
            if (assignmentsToSave.isNotEmpty() && checkForScheduleConflicts(assignmentsToSave)) {
                Toast.makeText(this, "Schedule Conflict: Two subjects have the same time and day. Please adjust selections.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            // FINALIZATION
            finalizeEnrollmentTransaction(
                studentEmail = studentEmail,
                pendingEnrollmentId = pendingEnrollmentId,
                selectedAssignments = assignmentsToSave,
                finalYearLevel = finalYearLevel,
                finalEnrollmentType = finalEnrollmentType,
                courseCode = courseCode
            )
        }

        dialog.show()
    }

    // ----------------------------------------------------
    // SCHEDULE CONFLICT CHECKER (UPDATED for Map<String, LocalTimeSlot>)
    // ----------------------------------------------------
    private fun checkForScheduleConflicts(assignments: List<LocalClassAssignment>): Boolean {
        val occupiedSlots = mutableListOf<Triple<String, Int, Int>>() // (day, startMin, endMin)

        fun parseTimeToMinutes(timeStr: String): Int {
            val pattern = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)", RegexOption.IGNORE_CASE)
            val match = pattern.find(timeStr.trim()) ?: return -1
            val (hourStr, minuteStr, meridiem) = match.destructured
            var hour = hourStr.toInt()
            val minute = minuteStr.toInt()
            if (meridiem.equals("PM", true) && hour != 12) hour += 12
            if (meridiem.equals("AM", true) && hour == 12) hour = 0
            return hour * 60 + minute
        }

        for (assignment in assignments) {
            for (slot in assignment.scheduleSlots.values) {
                val day = slot.day
                val startMin = parseTimeToMinutes(slot.startTime)
                val endMin = parseTimeToMinutes(slot.endTime)

                if (startMin == -1 || endMin == -1 || startMin >= endMin) {
                    Log.w("EnrollmentDebug", "Skipping invalid slot: ${assignment.subjectCode}")
                    continue
                }

                val conflict = occupiedSlots.any { (existingDay, existingStart, existingEnd) ->
                    existingDay == day && startMin < existingEnd && endMin > existingStart
                }

                if (conflict) {
                    Log.e("EnrollmentDebug", "Schedule conflict found for student")
                    return true
                }

                occupiedSlots.add(Triple(day, startMin, endMin))
            }
        }
        return false
    }

    // ----------------------------------------------------
    // Finalization with Auth Creation and Firestore Transaction (SECURE)
    // ----------------------------------------------------
    private fun finalizeEnrollmentTransaction(
        studentEmail: String, pendingEnrollmentId: String,
        selectedAssignments: List<LocalClassAssignment>, finalYearLevel: String, finalEnrollmentType: String, courseCode: String
    ) {
        // --- Added: Get the current timestamp once for consistency across all records ---
        val enrollmentTimestamp = Timestamp.now()

        generateStudentId { studentId ->

            firestore.collection("pendingEnrollments").document(pendingEnrollmentId).get()
                .addOnSuccessListener { doc ->
                    val enrollmentData = doc.data ?: emptyMap()
                    val e = Enrollment.fromFirestore(doc.id, enrollmentData)

                    if (e.firstName.isEmpty()) {
                        Toast.makeText(this, "Error: Original enrollment record not found or invalid.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    val finalPassword = generatePassword(e.lastName, e.dateOfBirth)

                    // --- Step 3: Firebase Auth User Creation ---
                    auth.createUserWithEmailAndPassword(studentEmail.trim(), finalPassword.trim())
                        .addOnSuccessListener { authResult ->
                            val userUid = authResult.user!!.uid
                            Log.d("EnrollmentDebug", "Auth User created successfully inside finalization: $userUid")

                            // --- Step 4: START FIRESTORE TRANSACTION (Capacity Check) ---
                            firestore.runTransaction { transaction ->
                                val snapshots = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                                for (assignment in selectedAssignments) {
                                    val assignmentRef = firestore.collection("classAssignments").document(assignment.assignmentNo)
                                    val snapshot = transaction.get(assignmentRef)
                                    snapshots[assignment.assignmentNo] = snapshot
                                }

                                for (assignment in selectedAssignments) {
                                    val snapshot = snapshots[assignment.assignmentNo]!!
                                    val currentCount = (snapshot.get("enrolledCount") as? Number)?.toInt() ?: 0
                                    val maxCapacity = (snapshot.get("maxCapacity") as? Number)?.toInt() ?: 50
                                    val sectionBlock = assignment.scheduleSlots.values.firstOrNull()?.sectionBlock ?: assignment.subjectCode
                                    if (currentCount >= maxCapacity) {
                                        throw Exception("Section $sectionBlock is full. Aborting Enrollment.")
                                    }
                                }

                                for (assignment in selectedAssignments) {
                                    val ref = firestore.collection("classAssignments").document(assignment.assignmentNo)
                                    val currentCount = (snapshots[assignment.assignmentNo]!!.get("enrolledCount") as? Number)?.toInt() ?: 0
                                    transaction.update(ref, "enrolledCount", currentCount + 1)
                                }

                                null
                            }.addOnSuccessListener {
                                Log.d("EnrollmentDebug", "Transaction successful. Starting Batch Write.")

                                val batch = firestore.batch()

                                val currentSemester = "1st Semester"
                                val currentAcademicYear = "2025-2026"

                                val assignedSectionBlock = if (finalEnrollmentType == "Regular") {
                                    selectedAssignments.firstOrNull()?.scheduleSlots?.values?.firstOrNull()?.sectionBlock
                                        ?: "${courseCode}_${finalYearLevel.take(1)}A"
                                } else {
                                    "${courseCode}_IRREG_${finalYearLevel.take(1)}"
                                }

                                // 2. CREATE/UPDATE USER RECORD
                                val userRef = firestore.collection("users").document(userUid)
                                batch.set(userRef, mapOf(
                                    "email" to studentEmail,
                                    "role" to "student",
                                    "studentId" to studentId,
                                    "courseCode" to courseCode,
                                    "yearLevel" to finalYearLevel,
                                    "enrollmentType" to finalEnrollmentType
                                ))

                                // 3. SAVE ASSIGNMENTS to Student Record
                                for (assignment in selectedAssignments) {
                                    val primarySectionBlock = assignment.scheduleSlots.values.firstOrNull()?.sectionBlock ?: assignedSectionBlock
                                    val subjectEntry = requiredSubjectsMap[assignment.subjectCode]

                                    val studentAssignmentRecordMap = mapOf(
                                        "subjectCode" to assignment.subjectCode,
                                        "subjectTitle" to assignment.subjectTitle,
                                        "assignmentNo" to assignment.assignmentNo,
                                        "teacherName" to assignment.teacherName,
                                        "sectionBlock" to primarySectionBlock,
                                        "onlineLink" to assignment.onlineClassLink,
                                        "credits" to (subjectEntry?.credits ?: 3),
                                        "prelim" to null,
                                        "midterm" to null,
                                        "final" to null,
                                        "gwa" to "",
                                        "semester" to currentSemester,
                                        "academicYear" to currentAcademicYear,
                                        "yearLevel" to finalYearLevel,
                                        // 🌟 NEW FIELD: Enrollment Status
                                        "status" to "Enrolled",
                                        // 🌟 NEW FIELD: Date Enrolled
                                        "dateEnrolled" to enrollmentTimestamp
                                    )

                                    val yearClean = finalYearLevel.replace(" ", "")
                                    val semClean = currentSemester.replace(" ", "")
                                    val subjectDocId = "${yearClean}_${semClean}_${assignment.subjectCode}"

                                    val subjectRef = firestore.collection("students").document(studentId).collection("subjects").document(subjectDocId)
                                    batch.set(subjectRef, studentAssignmentRecordMap)
                                }

                                // 4. CREATE/UPDATE STUDENT MASTER RECORD
                                val masterRef = firestore.collection("students").document(studentId)

                                batch.set(masterRef, mapOf(
                                    "id" to studentId,
                                    "userUid" to userUid,
                                    // 🌟 UPDATED FIELD: Using the consistent Timestamp
                                    "dateEnrolled" to enrollmentTimestamp,
                                    "academicYear" to currentAcademicYear,
                                    "semester" to currentSemester,
                                    "courseCode" to courseCode,
                                    "courseName" to e.courseName,
                                    "status" to "Admitted",
                                    "isEnrolled" to true,
                                    "yearLevel" to finalYearLevel,
                                    "enrollmentType" to finalEnrollmentType,
                                    "sectionId" to assignedSectionBlock,
                                    "firstName" to e.firstName,
                                    "lastName" to e.lastName,
                                    "middleName" to e.middleName,
                                    "email" to e.email,
                                    "phone" to e.phone,
                                    "address" to e.address,
                                    "dateOfBirth" to e.dateOfBirth,
                                    "gender" to e.gender,
                                    "guardianName" to e.guardianName,
                                    "guardianPhone" to e.guardianPhone,
                                    // 🌟 NEW FIELDS: Parent information and guardian relationship
                                    "guardianRelationship" to e.guardianRelationship,
                                    // Father information
                                    "fatherFirstName" to e.fatherFirstName,
                                    "fatherMiddleName" to e.fatherMiddleName,
                                    "fatherLastName" to e.fatherLastName,
                                    "fatherDOB" to e.fatherDOB,
                                    "fatherPhone" to e.fatherPhone,
                                    "fatherOccupation" to e.fatherOccupation,
                                    // Mother information
                                    "motherFirstName" to e.motherFirstName,
                                    "motherMiddleName" to e.motherMiddleName,
                                    "motherLastName" to e.motherLastName,
                                    "motherDOB" to e.motherDOB,
                                    "motherPhone" to e.motherPhone,
                                    "motherOccupation" to e.motherOccupation,
                                    "studentLastNameExtension" to e.studentLastNameExtension,
                                    "fatherLastNameExtension" to e.fatherLastNameExtension,
                                    "motherLastNameExtension" to e.motherLastNameExtension,
                                    "country" to e.country,
                                    "region" to e.region,
                                    "province" to e.province,
                                    "municipality" to e.municipality,
                                    "barangay" to e.barangay,
                                    "street" to e.street,
                                    "postalCode" to e.postalCode,
                                    "fullAddress" to e.fullAddress
                                ))

                                // 5. Clean up pending enrollment
                                batch.delete(firestore.collection("pendingEnrollments").document(pendingEnrollmentId))

                                batch.commit().addOnSuccessListener {
                                    Toast.makeText(this, "Enrollment Finalized! Student ID: $studentId.", Toast.LENGTH_LONG).show()

                                    // 6. Send enrollment email using GmailSender
                                    sendEnrollmentEmail(studentEmail, studentId, finalPassword)

                                    loadPendingEnrollments()
                                    loadPendingEnrollments()
                                }
                                    .addOnFailureListener { batchError ->
                                        Toast.makeText(this, "Batch Setup Error: ${batchError.message}. Deleting created Auth user.", Toast.LENGTH_LONG).show()
                                        Log.e("EnrollmentDebug", "Batch commit failed.", batchError)
                                        auth.currentUser?.delete()
                                    }

                            }.addOnFailureListener { transactionError ->
                                Toast.makeText(this, "ENROLLMENT FAILED (Capacity Check): ${transactionError.message}. Deleting created Auth user.", Toast.LENGTH_LONG).show()
                                Log.e("EnrollmentDebug", "Transaction failed.", transactionError)
                                auth.currentUser?.delete()
                            }
                        }
                        .addOnFailureListener { authError ->
                            Toast.makeText(this, "Enrollment Failed: Failed to create user account: ${authError.message}", Toast.LENGTH_LONG).show()
                            Log.e("EnrollmentDebug", "Auth Creation failed in finalization.", authError)
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Fatal Error: Cannot retrieve student details for finalization.", Toast.LENGTH_LONG).show()
                }
        }
    }

    // -----------------------------
    // NEW: Send Enrollment Email using GmailSender
    // -----------------------------
    private fun sendEnrollmentEmail(studentEmail: String, studentId: String, password: String) {
        val gmailSender = GmailSender()

        gmailSender.sendEnrollmentEmail(studentEmail, studentId, password, object : EmailSendCallback {
            override fun onSending() {
                Log.d("EnrollmentDebug", "📧 Preparing to send enrollment email to: $studentEmail")
            }

            override fun onSuccess() {
                Log.d("EnrollmentDebug", "✅ Enrollment email sent successfully to: $studentEmail")
            }

            override fun onComplete(success: Boolean) {
                if (!success) {
                    Log.e("EnrollmentDebug", "❌ Failed to send enrollment email to: $studentEmail")
                    // You can show a toast or log the error, but don't block the enrollment process
                    runOnUiThread {
                        Toast.makeText(this@ManageEnrollmentsActivity,
                            "Enrollment completed but email failed to send",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // -----------------------------
    // Mark as Not Passed (Reject)
    // -----------------------------
    private fun markAsNotPassed(e: Enrollment) {
        // Confirmation dialog bago i-reject
        AlertDialog.Builder(this)
            .setTitle("Confirm Rejection")
            .setMessage("Are you sure you want to reject ${e.firstName} ${e.lastName}'s enrollment application? This action cannot be undone.")
            .setPositiveButton("Yes, Reject") { dialog, which ->
                // Ito ang original na reject logic
                performRejection(e)
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    // Hiwalay na function para sa actual na rejection process
    private fun performRejection(e: Enrollment) {
        val rejectionData = hashMapOf<String, Any>(
            "id" to e.id,
            "firstName" to e.firstName,
            "middleName" to e.middleName,
            "lastName" to e.lastName,
            "email" to e.email,
            "phone" to e.phone,
            "address" to e.address,
            "dateOfBirth" to e.dateOfBirth,
            "gender" to e.gender,
            "course" to e.course,
            "yearLevel" to e.yearLevel,
            "guardianName" to e.guardianName,
            "guardianPhone" to e.guardianPhone,
            "guardianRelationship" to e.guardianRelationship,
            // Father information
            "fatherFirstName" to e.fatherFirstName,
            "fatherMiddleName" to e.fatherMiddleName,
            "fatherLastName" to e.fatherLastName,
            "fatherDOB" to e.fatherDOB,
            "fatherPhone" to e.fatherPhone,
            "fatherOccupation" to e.fatherOccupation,
            // Mother information
            "motherFirstName" to e.motherFirstName,
            "motherMiddleName" to e.motherMiddleName,
            "motherLastName" to e.motherLastName,
            "motherDOB" to e.motherDOB,
            "motherPhone" to e.motherPhone,
            "motherOccupation" to e.motherOccupation,
            "studentLastNameExtension" to e.studentLastNameExtension,
            "fatherLastNameExtension" to e.fatherLastNameExtension,
            "motherLastNameExtension" to e.motherLastNameExtension,
            "status" to "rejected",
            "rejectedAt" to Timestamp.now()
        )

        firestore.collection("notPassedEnrollments").document(e.id).set(rejectionData)
        firestore.collection("pendingEnrollments").document(e.id).delete()

        // Send rejection email using GmailSender
        sendRejectionEmail(e.email)

        Toast.makeText(this, "Enrollment application rejected", Toast.LENGTH_SHORT).show()
        loadPendingEnrollments()
        loadEnrollmentCounts()
    }

    // -----------------------------
    // NEW: Send Rejection Email using GmailSender
    // -----------------------------
    private fun sendRejectionEmail(studentEmail: String) {
        val gmailSender = GmailSender()

        gmailSender.sendRejectionEmail(studentEmail, object : EmailSendCallback {
            override fun onSending() {
                Log.d("EnrollmentDebug", "📧 Preparing to send rejection email to: $studentEmail")
            }

            override fun onSuccess() {
                Log.d("EnrollmentDebug", "✅ Rejection email sent successfully to: $studentEmail")
            }

            override fun onComplete(success: Boolean) {
                if (!success) {
                    Log.e("EnrollmentDebug", "❌ Failed to send rejection email to: $studentEmail")
                }
            }
        })
    }

    // -----------------------------
    // Generate unique student ID safely
    // -----------------------------
    private fun generateStudentId(callback: (String) -> Unit) {
        firestore.collection("students")
            .orderBy("id", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val lastId = snapshot.documents.firstOrNull()?.getString("id")
                val nextNumber = if (lastId != null && lastId.startsWith("DDS-")) {
                    lastId.substringAfter("DDS-").toIntOrNull()?.plus(1) ?: 1
                } else 1
                callback("DDS-" + String.format("%04d", nextNumber))
            }
            .addOnFailureListener {
                callback("DDS-0001")
            }
    }

    private fun generatePassword(lastName: String, dob: String): String {
        val cleaned = dob.replace("/", "").replace("-", "")
        return "${lastName.lowercase().take(3)}$cleaned"
    }
}

// -----------------------------
// RecyclerView Adapter
// -----------------------------
class EnrollmentAdapter(
    private val items: List<Enrollment>,
    private val clickListener: (Enrollment) -> Unit
) : RecyclerView.Adapter<EnrollmentAdapter.EnrollmentViewHolder>() {
    inner class EnrollmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvEmail: TextView = view.findViewById(R.id.tvEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnrollmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_enrollment_item, parent, false)
        return EnrollmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: EnrollmentViewHolder, position: Int) {
        val enrollment = items[position]

        // ✅ DISPLAY ONLY: Combine suffix for list display
        val displayName = if (enrollment.studentLastNameExtension.isNotEmpty()) {
            "${enrollment.firstName} ${enrollment.lastName} ${enrollment.studentLastNameExtension}"
        } else {
            "${enrollment.firstName} ${enrollment.lastName}"
        }

        holder.tvName.text = displayName
        holder.tvEmail.text = enrollment.email
        holder.itemView.setOnClickListener { clickListener(enrollment) }
    }

    override fun getItemCount(): Int = items.size
}
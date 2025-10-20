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
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.Curriculum
import com.example.datadomeapp.models.Enrollment
import com.example.datadomeapp.models.StudentSubject
import com.example.datadomeapp.models.SubjectEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp // Import para sa Timestamp.now()
import java.util.*

class ManageEnrollmentsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var functions: FirebaseFunctions

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EnrollmentAdapter
    private val enrollmentList = mutableListOf<Enrollment>()

    private val requiredSubjectsMap = mutableMapOf<String, SubjectEntry>()
    private val availableSectionsMap = mutableMapOf<String, List<ClassAssignment>>()

    private val finalYearLevels = listOf("Select Year Level", "1st Year", "2nd Year", "3rd Year", "4th Year")
    private val finalEnrollmentTypes = listOf("Select Type", "Regular", "Irregular")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_enrollment_management)

        // Tiyakin na tama ang region
        functions = FirebaseFunctions.getInstance("asia-southeast1")

        recyclerView = findViewById(R.id.rvEnrollments)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EnrollmentAdapter(enrollmentList) { enrollment ->
            showEnrollmentDetailDialog(enrollment)
        }
        recyclerView.adapter = adapter

        loadPendingEnrollments()
    }

    private fun loadPendingEnrollments() {
        // ... (No changes needed here) ...
        firestore.collection("pendingEnrollments")
            .whereEqualTo("status", "submitted")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                enrollmentList.clear()
                for (doc in snapshot.documents) {
                    val enrollmentData = doc.data as Map<String, Any>
                    val e = doc.toObject(Enrollment::class.java)?.copy(id = doc.id, data = enrollmentData)
                    if (e != null) enrollmentList.add(e)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load enrollments.", Toast.LENGTH_SHORT).show()
                Log.e("Enrollment", "Error loading pending enrollments", it)
            }
    }


    fun showEnrollmentDetailDialog(e: Enrollment) {
        // ... (No changes needed here) ...
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
        val spinnerFinalYearLevel = dialogView.findViewById<Spinner>(R.id.spinnerFinalYearLevel)
        val spinnerFinalEnrollmentType = dialogView.findViewById<Spinner>(R.id.spinnerFinalEnrollmentType)


        val courseName = e.data["courseName"] as? String ?: e.course
        val courseCode = e.data["courseCode"] as? String ?: ""

        tvName.text = "Name: ${e.lastName}, ${e.firstName} ${e.middleName.firstOrNull() ?: ""}"
        tvEmail.text = "Email: ${e.email}"
        tvPhone.text = "Phone: ${e.phone}"
        tvAddress.text = "Address: ${e.address}"
        tvDOB.text = "DOB: ${e.dateOfBirth}"
        tvGender.text = "Gender: ${e.gender}"
        tvCourse.text = "Course Applied: $courseName (Code: $courseCode)"
        tvGuardian.text = "Guardian: ${e.guardianName} (${e.guardianPhone})"

        val applicationType = e.data["applicationType"] as? String ?: "N/A"
        tvApplicationType.text = "Application Status: $applicationType"


        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, finalYearLevels)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFinalYearLevel.adapter = yearAdapter

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
                val requiredSubjects = curriculumSnapshot.toObject(Curriculum::class.java)?.requiredSubjects ?: emptyList()

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
                val allAssignments = assignmentSnapshot.toObjects(ClassAssignment::class.java)

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

    // ----------------------------------------------------
    // Interactive Subject Assignment Dialog
    // ----------------------------------------------------
    // ----------------------------------------------------
    // Interactive Subject Assignment Dialog (FINAL LOGIC: Single Section vs. Per-Subject)
    // ----------------------------------------------------
    private fun showInteractiveAssignmentDialog(
        studentEmail: String, pendingEnrollmentId: String,
        requiredSubjects: List<SubjectEntry>, finalYearLevel: String, finalEnrollmentType: String, courseCode: String
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_enrollment_dialog_finalize, null)
        val selectionList = dialogView.findViewById<LinearLayout>(R.id.llSubjectSelections)
        val btnFinalize = dialogView.findViewById<Button>(R.id.btnFinalizeEnrollment)

        val dialogTitle = "Enrollment: $finalEnrollmentType ($finalYearLevel)"
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val finalSelections = mutableMapOf<String, ClassAssignment?>()


        // 🛑 REGULAR STUDENT LOGIC: SINGLE SECTION SELECTION
        if (finalEnrollmentType == "Regular") {
            // Kukunin ang lahat ng UNIQUE section names na available para sa course/year na ito
            val allUniqueSections = availableSectionsMap.values.flatten()
                .map { it.sectionName }
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
                text = "Select Final Section for All Subjects (Regular)"
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
                    finalSelections.clear() // I-clear ang lahat ng previous selections

                    if (position > 0) {
                        val selectedSectionName = parent?.getItemAtPosition(position).toString()

                        // Awtomatikong itatala ang lahat ng subjects sa napiling section na ito
                        requiredSubjects.forEach { subject ->
                            // Kukunin ang ClassAssignment na tumutugma sa subject code AT section name
                            val assignment = availableSectionsMap[subject.subjectCode]
                                ?.find { it.sectionName == selectedSectionName }

                            if (assignment != null) {
                                finalSelections[subject.subjectCode] = assignment
                            } else {
                                // Kapag walang assignment na nakita (Missing Subject in Section)
                                finalSelections[subject.subjectCode] = null
                                Log.w("EnrollmentDebug", "WARNING: Subject ${subject.subjectCode} is missing in Section $selectedSectionName.")
                            }
                        }
                    } else {
                        // Kung "Select Section..." ang napili
                        finalSelections.clear()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        } else {
            // 🛑 IRREGULAR STUDENT LOGIC: PER-SUBJECT SELECTION (Existing Code)
            requiredSubjects.forEach { subject ->
                val textView = TextView(this).apply {
                    text = "${subject.subjectCode} - ${subject.subjectTitle}"
                    textSize = 16f
                    setPadding(0, 10, 0, 0)
                }

                val sections = availableSectionsMap[subject.subjectCode] ?: emptyList()
                val spinner = Spinner(this).apply {

                    val sectionDisplay = sections.map {
                        val scheduleTime = "${it.day} ${it.startTime}-${it.endTime}"
                        "${it.sectionName} (${it.teacherName} - ${scheduleTime}) [${it.enrolledCount}/${it.maxCapacity}]"
                    }.toMutableList()

                    // Dito lang may SKIP option
                    sectionDisplay.add(0, "SKIP SUBJECT (Irregular)")


                    adapter = ArrayAdapter(this@ManageEnrollmentsActivity, android.R.layout.simple_spinner_dropdown_item, sectionDisplay)
                    setSelection(0)
                    finalSelections[subject.subjectCode] = null


                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (position == 0) {
                                finalSelections[subject.subjectCode] = null
                            } else {
                                finalSelections[subject.subjectCode] = sections[position - 1]
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }

                selectionList.addView(textView)
                selectionList.addView(spinner)
            }
        }
        // ----------------------------------------------------------------------


        btnFinalize.setOnClickListener {
            val assignmentsToSave = finalSelections.values.filterNotNull()

            // CRITICAL VALIDATION 1: Tiyakin na may napiling section ang Regular
            if (finalEnrollmentType == "Regular" && assignmentsToSave.size < requiredSubjects.size) {
                if (finalSelections.isEmpty()) {
                    Toast.makeText(this, "ERROR: Please select a single Section for the Regular Enrollment.", Toast.LENGTH_LONG).show()
                } else {
                    // Kung may napili, pero may subjects na nawawala sa section na 'yon
                    Toast.makeText(this, "WARNING: The selected section is missing ${requiredSubjects.size - assignmentsToSave.size} required subject(s). Please choose a complete section.", Toast.LENGTH_LONG).show()
                }
                return@setOnClickListener
            }

            // CRITICAL VALIDATION 2: Hindi pwedeng walang assignment ang Irregular, maliban kung nag-skip lahat
            if (finalEnrollmentType == "Irregular" && assignmentsToSave.isEmpty()) {
                Toast.makeText(this, "ERROR: Irregular student must select at least one subject. Otherwise, the enrollment should be rejected.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }


            // SCHEDULE CONFLICT CHECKER (Importante pa rin, lalo na sa Irregular)
            if (checkForScheduleConflicts(assignmentsToSave)) {
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
    // SCHEDULE CONFLICT CHECKER
    // ----------------------------------------------------
    private fun checkForScheduleConflicts(assignments: List<ClassAssignment>): Boolean {
        // ... (No changes needed here) ...
        val occupiedSlots = mutableSetOf<String>()
        for (assignment in assignments) {
            val scheduleKey = "${assignment.day}_${assignment.startTime}_${assignment.endTime}"
            if (occupiedSlots.contains(scheduleKey)) {
                Log.e("EnrollmentDebug", "Conflict found for slot: $scheduleKey")
                return true
            }
            occupiedSlots.add(scheduleKey)
        }
        return false
    }


    // ----------------------------------------------------
    // Finalization with Auth Creation and Firestore Transaction (SECURE)
    // ----------------------------------------------------
    private fun finalizeEnrollmentTransaction(
        studentEmail: String, pendingEnrollmentId: String,
        selectedAssignments: List<ClassAssignment>, finalYearLevel: String, finalEnrollmentType: String, courseCode: String
    ) {
        // --- Step 1: Generate Student ID ---
        generateStudentId { studentId ->

            // --- Step 2: Get Enrollment Data to retrieve Last Name and DOB (for password) ---
            firestore.collection("pendingEnrollments").document(pendingEnrollmentId).get()
                .addOnSuccessListener { doc ->
                    val e = doc.toObject(Enrollment::class.java)
                    if (e == null) {
                        Toast.makeText(this, "Error: Original enrollment record not found.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    val finalPassword = generatePassword(e.lastName, e.dateOfBirth)


                    // --- Step 3: Firebase Auth User Creation (CRITICAL: Delay until here) ---
                    auth.createUserWithEmailAndPassword(studentEmail.trim(), finalPassword.trim())
                        .addOnSuccessListener { authResult ->
                            val userUid = authResult.user!!.uid
                            Log.d("EnrollmentDebug", "Auth User created successfully inside finalization: $userUid")

                            // --- Step 4: START FIRESTORE TRANSACTION (Capacity Check) ---
                            firestore.runTransaction { transaction ->

                                // READ PHASE (All reads must be done first)
                                val snapshots = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                                for (assignment in selectedAssignments) {
                                    val ref = firestore.collection("classAssignments").document(assignment.assignmentId)
                                    val snapshot = transaction.get(ref) // READ
                                    snapshots[assignment.assignmentId] = snapshot
                                }

                                // VALIDATION PHASE (Based on the reads)
                                for (assignment in selectedAssignments) {
                                    val snapshot = snapshots[assignment.assignmentId]!!
                                    val currentCount = (snapshot.get("enrolledCount") as? Number)?.toInt() ?: 0
                                    val maxCapacity = (snapshot.get("maxCapacity") as? Number)?.toInt() ?: 50

                                    if (currentCount >= maxCapacity) {
                                        throw Exception("Section ${assignment.sectionName} is full. Aborting Enrollment.")
                                    }
                                }

                                // WRITE PHASE (All writes after all reads)
                                for (assignment in selectedAssignments) {
                                    val ref = firestore.collection("classAssignments").document(assignment.assignmentId)
                                    val currentCount = (snapshots[assignment.assignmentId]!!.get("enrolledCount") as? Number)?.toInt() ?: 0
                                    transaction.update(ref, "enrolledCount", currentCount + 1) // WRITE
                                }

                                null
                            }.addOnSuccessListener {
                                Log.d("EnrollmentDebug", "Transaction successful. Starting Batch Write.")

                                val batch = firestore.batch()

                                // --- CRITICAL FIX START: Determine Administrative Section ID ---
                                val assignedSectionName = if (finalEnrollmentType == "Regular") {
                                    // Para sa Regular: Kunin ang section name mula sa pinakaunang napiling subject.
                                    selectedAssignments.firstOrNull()?.sectionName
                                        ?: "${courseCode}_${finalYearLevel.take(1)}A" // Fallback sa Default Section 'A'
                                } else {
                                    // Para sa Irregular: Gumamit ng malinaw na identifier na may Course at Year
                                    "${courseCode}_IRREG_${finalYearLevel.take(1)}"
                                }
                                // --- CRITICAL FIX END ---


                                // 2. CREATE/UPDATE USER RECORD (Minimal Auth Data)
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
                                    val scheduleString = "${assignment.day} ${assignment.startTime}-${assignment.endTime}"
                                    val studentSubject = StudentSubject(
                                        subjectCode = assignment.subjectCode,
                                        subjectTitle = assignment.subjectTitle,
                                        sectionName = assignment.sectionName,
                                        teacherId = assignment.teacherUid,
                                        teacherName = assignment.teacherName,
                                        schedule = scheduleString
                                    )
                                    val ref = firestore.collection("students").document(studentId).collection("subjects").document(assignment.subjectCode)
                                    batch.set(ref, studentSubject)
                                }

                                // 4. CREATE/UPDATE STUDENT MASTER RECORD (All Profile Data)
                                val masterRef = firestore.collection("students").document(studentId)

                                // ✅ I-SAVE ANG ASIGNED SECTION ID dito
                                batch.set(masterRef, mapOf(
                                    // --- CRITICAL IDENTIFIERS & AUDIT ---
                                    "id" to studentId,
                                    "userUid" to userUid,
                                    "dateEnrolled" to Timestamp.now(),
                                    "academicYear" to "2025-2026",
                                    "semester" to "1st Semester",

                                    // --- ADMINISTRATIVE / ACADEMIC STATUS ---
                                    "courseCode" to courseCode,
                                    "status" to "Admitted",
                                    "isEnrolled" to true,
                                    "yearLevel" to finalYearLevel,
                                    "enrollmentType" to finalEnrollmentType,
                                    "sectionId" to assignedSectionName, // ⬅️ ITO ang Fix! Ito ang babasahin ng Dashboard!

                                    // --- PERSONAL INFORMATION (Galing sa Enrollment object 'e') ---
                                    "firstName" to e.firstName,
                                    "lastName" to e.lastName,
                                    "middleName" to e.middleName,
                                    "email" to e.email,
                                    "phone" to e.phone,
                                    "address" to e.address,
                                    "dateOfBirth" to e.dateOfBirth,
                                    "gender" to e.gender,

                                    // --- GUARDIAN INFORMATION ---
                                    "guardianName" to e.guardianName,
                                    "guardianPhone" to e.guardianPhone,
                                    "guardianRelationship" to "Unknown"
                                ))

                                // 5. Clean up pending enrollment
                                batch.delete(firestore.collection("pendingEnrollments").document(pendingEnrollmentId))

                                batch.commit().addOnSuccessListener {
                                    Toast.makeText(this, "Enrollment Finalized! Student ID: $studentId.", Toast.LENGTH_LONG).show()

                                    // 6. Send enrollment email
                                    val data = hashMapOf("email" to studentEmail, "studentId" to studentId, "password" to finalPassword)
                                    functions.getHttpsCallable("sendEnrollmentEmail").call(data)

                                    loadPendingEnrollments()
                                }
                                    .addOnFailureListener { batchError ->
                                        Toast.makeText(this, "Batch Setup Error: ${batchError.message}. Deleting created Auth user.", Toast.LENGTH_LONG).show()
                                        Log.e("Enrollment", "Batch commit failed.", batchError)
                                        // If batch fails, delete the Auth user
                                        auth.currentUser?.delete()
                                    }

                            }.addOnFailureListener { transactionError ->
                                Toast.makeText(this, "ENROLLMENT FAILED (Capacity Check): ${transactionError.message}. Deleting created Auth user.", Toast.LENGTH_LONG).show()
                                Log.e("Enrollment", "Transaction failed.", transactionError)
                                // If transaction fails, delete the Auth user
                                auth.currentUser?.delete()
                            }
                        }
                        .addOnFailureListener { authError ->
                            Toast.makeText(this, "Enrollment Failed: Failed to create user account: ${authError.message}", Toast.LENGTH_LONG).show()
                            Log.e("Enrollment", "Auth Creation failed in finalization.", authError)
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Fatal Error: Cannot retrieve student details for finalization.", Toast.LENGTH_LONG).show()
                }

        }
    }


    // -----------------------------
    // Mark as Not Passed (Reject)
    // -----------------------------
    private fun markAsNotPassed(e: Enrollment) {
        // ... (No changes needed here) ...
        firestore.collection("notPassedEnrollments").document(e.id).set(e)
        firestore.collection("pendingEnrollments").document(e.id).delete()

        val rejectData = hashMapOf("email" to e.email)
        functions.getHttpsCallable("sendRejectionEmail").call(rejectData)
            .addOnSuccessListener {
                Toast.makeText(this, "Rejection email sent!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to send rejection email.", Toast.LENGTH_SHORT).show() }

        Toast.makeText(this, "Marked as Rejected", Toast.LENGTH_SHORT).show()
        loadPendingEnrollments()
    }

    // -----------------------------
    // Generate unique student ID safely
    // -----------------------------
    private fun generateStudentId(callback: (String) -> Unit) {
        // ... (No changes needed here) ...
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
        // ... (No changes needed here) ...
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
    // ... (No changes needed here) ...
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
        holder.tvName.text = "${enrollment.firstName} ${enrollment.lastName}"
        holder.tvEmail.text = enrollment.email
        holder.itemView.setOnClickListener { clickListener(enrollment) }
    }

    override fun getItemCount(): Int = items.size
}
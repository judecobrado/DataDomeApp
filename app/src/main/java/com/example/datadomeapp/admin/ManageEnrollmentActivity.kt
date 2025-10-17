package com.example.datadomeapp.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.* // Import all new models
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.datadomeapp.models.SubjectEntry


// -----------------------------
// Activity
// -----------------------------
class ManageEnrollmentsActivity : AppCompatActivity() {

    // Initialize Firebase Instances
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var functions: FirebaseFunctions

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EnrollmentAdapter
    private val enrollmentList = mutableListOf<Enrollment>()

    // Data for the Subject Selection Dialog
    private val requiredSubjectsMap = mutableMapOf<String, SubjectEntry>()
    private val availableSectionsMap = mutableMapOf<String, List<ClassAssignment>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_enrollments)

        functions = FirebaseFunctions.getInstance("asia-southeast1")

        recyclerView = findViewById(R.id.rvEnrollments)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EnrollmentAdapter(enrollmentList) { enrollment ->
            showEnrollmentDetailDialog(enrollment)
        }
        recyclerView.adapter = adapter

        // 🛑 I-a-update ang `loadPendingEnrollments` na matatagpuan sa ibaba
        loadPendingEnrollments()
    }

    // -----------------------------
    // Load pending enrollments (RESTORED)
    // -----------------------------
    private fun loadPendingEnrollments() {
        firestore.collection("pendingEnrollments").get()
            .addOnSuccessListener { snapshot ->
                enrollmentList.clear()
                for (doc in snapshot.documents) {
                    val e = doc.toObject(Enrollment::class.java)?.copy(id = doc.id)
                    if (e != null) enrollmentList.add(e)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load enrollments.", Toast.LENGTH_SHORT).show()
            }
    }


    // -----------------------------
    // Show enrollment detail dialog (Modified to trigger selection dialog)
    // -----------------------------
    private fun showEnrollmentDetailDialog(e: Enrollment) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_enrollment_detail, null)

        // I-fetch at i-update ang lahat ng detalye mula sa Enrollment object (e)
        dialogView.findViewById<TextView>(R.id.tvName).text = "${e.firstName} ${e.middleName} ${e.lastName}"
        dialogView.findViewById<TextView>(R.id.tvEmail).text = "Email: ${e.email}"
        dialogView.findViewById<TextView>(R.id.tvPhone).text = "Phone: ${e.phone}"
        dialogView.findViewById<TextView>(R.id.tvAddress).text = "Address: ${e.address}"
        dialogView.findViewById<TextView>(R.id.tvDOB).text = "DOB: ${e.dateOfBirth}"
        dialogView.findViewById<TextView>(R.id.tvGender).text = "Gender: ${e.gender}"
        dialogView.findViewById<TextView>(R.id.tvCourse).text = "Course: ${e.course}"
        dialogView.findViewById<TextView>(R.id.tvYearLevel).text = "Year Level: ${e.yearLevel}"
        dialogView.findViewById<TextView>(R.id.tvGuardian).text = "Guardian: ${e.guardianName} (${e.guardianPhone})"

        val detailDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnEnroll).setOnClickListener {
            // 🛑 NEW STEP: Pre-enrollment and show selection dialog
            startEnrollmentProcess(e)
            detailDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnDidNotPass).setOnClickListener {
            markAsNotPassed(e)
            detailDialog.dismiss()
        }

        detailDialog.show()
    }

    // ----------------------------------------------------
    // START: Dynamic Enrollment Process
    // ----------------------------------------------------
    private fun startEnrollmentProcess(e: Enrollment) {
        // Generate ID and create Firebase Auth account first
        generateStudentId { newId ->
            val password = generatePassword(e.lastName, e.dateOfBirth)

            auth.createUserWithEmailAndPassword(e.email.trim(), password.trim())
                .addOnSuccessListener {
                    // 1. Create User/Student Records
                    firestore.collection("users").add(mapOf("email" to e.email, "role" to "student", "studentId" to newId))
                    firestore.collection("students").document(newId).set(e.copy(id = newId, status = "enrolled"))

                    // 2. Load and show the dynamic subject selection dialog
                    showSubjectSelectionDialog(newId, e.course, e.yearLevel, e.email, password, e.id)

                }
                .addOnFailureListener { err ->
                    Toast.makeText(this, "Enrollment Error: ${err.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showSubjectSelectionDialog(
        studentId: String, course: String, yearLevel: String, studentEmail: String, password: String, pendingEnrollmentId: String
    ) {
        // Load Required Subjects and Available Sections
        val curriculumDocId = "${course}_${yearLevel.replace(" ", "")}"

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. Fetch Curriculum
                val curriculumSnapshot = firestore.collection("curriculums").document(curriculumDocId).get().await()
                val requiredSubjects = curriculumSnapshot.toObject(Curriculum::class.java)?.requiredSubjects ?: emptyList()

                requiredSubjectsMap.clear()
                requiredSubjects.forEach { requiredSubjectsMap[it.subjectCode] = it }

                // 2. Fetch Available Sections (Capacity < 50)
                val assignmentSnapshot = firestore.collection("classAssignments")
                    .whereEqualTo("courseCode", course)
                    .whereEqualTo("yearLevel", yearLevel)
                    .whereLessThan("currentlyEnrolled", 50L) // Long because Firestore likes Longs
                    .get().await()

                availableSectionsMap.clear()
                assignmentSnapshot.toObjects(ClassAssignment::class.java).groupBy { it.subjectCode }
                    .forEach { (code, assignments) -> availableSectionsMap[code] = assignments }

                // --- 3. Construct and Show the Interactive Dialog ---
                showInteractiveAssignmentDialog(studentId, studentEmail, password, pendingEnrollmentId, requiredSubjects)

            } catch (e: Exception) {
                Toast.makeText(this@ManageEnrollmentsActivity, "Failed to load schedules: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ----------------------------------------------------
    // Interactive Subject Assignment Dialog (Conceptual Layout)
    // ----------------------------------------------------
    private fun showInteractiveAssignmentDialog(
        studentId: String, studentEmail: String, password: String, pendingEnrollmentId: String, requiredSubjects: List<SubjectEntry>
    ) {
        // In a real app, this would be a complex Dialog/Fragment with nested Spinners/Radio buttons.
        // For demonstration, we'll use a simple approach assuming the Admin selects one section per required subject.

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_interactive_assignment, null)
        val selectionList = dialogView.findViewById<LinearLayout>(R.id.llSubjectSelections)
        val btnFinalize = dialogView.findViewById<Button>(R.id.btnFinalizeEnrollment)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Sections for Enrollment (Limit 50)")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // List to hold the selected section for each subject
        val finalSelections = mutableMapOf<String, ClassAssignment?>() // Map<SubjectCode, ClassAssignment>

        // Create UI elements dynamically for each required subject
        requiredSubjects.forEach { subject ->
            val textView = TextView(this).apply {
                text = "${subject.subjectCode} - ${subject.subjectTitle}"
                textSize = 16f
                setPadding(0, 10, 0, 0)
            }
            val spinner = Spinner(this).apply {
                id = View.generateViewId() // Generate unique ID for the Spinner
                val sections = availableSectionsMap[subject.subjectCode] ?: emptyList()
                val sectionDisplay = sections.map {
                    "${it.sectionName} (${it.assignedTeacherName} - ${it.schedule}) [${it.currentlyEnrolled}/${it.maxCapacity}]"
                }.toMutableList()
                sectionDisplay.add(0, "SKIP SUBJECT (Irregular)") // Option for Irregular Students

                adapter = ArrayAdapter(this@ManageEnrollmentsActivity, android.R.layout.simple_spinner_dropdown_item, sectionDisplay)

                // Initialize map with a null selection
                finalSelections[subject.subjectCode] = null

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position == 0) {
                            finalSelections[subject.subjectCode] = null // SKIP
                        } else {
                            finalSelections[subject.subjectCode] = sections[position - 1] // -1 to account for the 'SKIP' option
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            selectionList.addView(textView)
            selectionList.addView(spinner)
        }


        btnFinalize.setOnClickListener {
            dialog.dismiss()
            val assignmentsToSave = finalSelections.values.filterNotNull()
            if (assignmentsToSave.isNotEmpty()) {
                finalizeEnrollmentTransaction(studentId, studentEmail, password, pendingEnrollmentId, assignmentsToSave)
            } else {
                Toast.makeText(this, "No subjects selected. Enrollment aborted.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // ----------------------------------------------------
    // Finalization with Transaction (Handles Capacity Limit)
    // ----------------------------------------------------
    private fun finalizeEnrollmentTransaction(
        studentId: String, studentEmail: String, password: String, pendingEnrollmentId: String, selectedAssignments: List<ClassAssignment>
    ) {

        // 1. Transaction: Update the Class Assignment counts (Ensures Limit 50 is not breached)
        firestore.runTransaction { transaction ->
            for (assignment in selectedAssignments) {
                val ref = firestore.collection("classAssignments").document(assignment.sectionName)
                val snapshot = transaction.get(ref)
                val currentCount = snapshot.getLong("currentlyEnrolled") ?: 0
                val maxCapacity = snapshot.getLong("maxCapacity") ?: 50L

                if (currentCount >= maxCapacity) {
                    throw Exception("Section ${assignment.sectionName} is full.")
                }

                transaction.update(ref, "currentlyEnrolled", currentCount + 1)
            }
            null
        }.addOnSuccessListener {
            // SUCCESS: Capacity counts are updated. Now perform batch writes.

            val batch = firestore.batch()

            // 2. Save Assignment to Student Record
            for (assignment in selectedAssignments) {
                val studentSubject = StudentSubject(
                    assignment.subjectCode, assignment.subjectTitle, assignment.sectionName,
                    assignment.assignedTeacherId, assignment.assignedTeacherName, assignment.schedule
                )
                val ref = firestore.collection("students").document(studentId).collection("subjects").document(assignment.subjectCode)
                batch.set(ref, studentSubject)
            }

            // 3. Clean up pending enrollment
            batch.delete(firestore.collection("pendingEnrollments").document(pendingEnrollmentId))

            batch.commit().addOnSuccessListener {
                // Enrollment complete.
                Toast.makeText(this, "Enrollment Finalized! Subjects Assigned.", Toast.LENGTH_LONG).show()

                // 4. Send enrollment email
                val data = hashMapOf("email" to studentEmail, "studentId" to studentId, "password" to password)
                functions.getHttpsCallable("sendEnrollmentEmail").call(data)

                loadPendingEnrollments()
            }

        }.addOnFailureListener { e ->
            Toast.makeText(this, "ENROLLMENT FAILED: ${e.message}", Toast.LENGTH_LONG).show()
            // Optional: Delete the Firebase Auth account created in startEnrollmentProcess if transaction failed.
        }
    }


    // -----------------------------
    // Mark as Not Passed (RESTORED)
    // -----------------------------
    private fun markAsNotPassed(e: Enrollment) {
        firestore.collection("notPassedEnrollments").document(e.id).set(e)
        firestore.collection("pendingEnrollments").document(e.id).delete()

        val rejectData = hashMapOf("email" to e.email)
        functions.getHttpsCallable("sendRejectionEmail").call(rejectData)
            .addOnSuccessListener {
                Toast.makeText(this, "Rejection email sent!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { /* ... failure toast ... */ }

        Toast.makeText(this, "Marked as Did Not Pass", Toast.LENGTH_SHORT).show()
        loadPendingEnrollments()
    }

    // -----------------------------
    // Generate unique student ID safely (SAME AS BEFORE)
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
        return "${lastName.lowercase()}$cleaned"
    }
}

// -----------------------------
// RecyclerView Adapter (SAME AS BEFORE)
// -----------------------------
class EnrollmentAdapter(
    private val items: List<Enrollment>,
    private val clickListener: (Enrollment) -> Unit
) : RecyclerView.Adapter<EnrollmentAdapter.EnrollmentViewHolder>() {
    // ... (Adapter Code) ...
    inner class EnrollmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvEmail: TextView = view.findViewById(R.id.tvEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnrollmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_enrollment, parent, false)
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
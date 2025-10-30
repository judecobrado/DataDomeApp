package com.example.datadomeapp.admin

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

// Tandaan: Dapat ay nasa com.example.datadomeapp.models package ang class na ito (pero inulit dito para sa completeness)
data class Student(
    val id: String = "", // Firestore Document ID
    val studentId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val courseCode: String = "",
    val yearLevel: String = "",
    val rfidTag: String? = null,
    val userUid: String = "",
    // Possible values: "ACTIVE", "DISABLED", or null/empty (for Not Registered)
    val rfidStatus: String? = null
)

class ManageStudentsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StudentAdapter
    private lateinit var etSearch: EditText
    private lateinit var spinnerFilterCourse: Spinner
    private lateinit var spinnerFilterYear: Spinner

    private val studentList = mutableListOf<Student>()
    private var allStudentsCache = listOf<Student>()

    // RFID/NFC Declarations
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var currentStudentForRfid: Student? = null
    private var rfidDetectionDialog: AlertDialog? = null
    private var isNfcSupported = false

    // Log Tag
    private val TAG = "ManageStudentsActivity"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_manage_students)

        etSearch = findViewById(R.id.etSearchStudent)
        spinnerFilterCourse = findViewById(R.id.spinnerFilterCourse)
        spinnerFilterYear = findViewById(R.id.spinnerFilterYear)
        recyclerView = findViewById(R.id.rvStudents)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = StudentAdapter(studentList) { student ->
            showStudentDetailDialog(student)
        }
        recyclerView.adapter = adapter

        setupNfc()
        loadAllStudents()
        setupFilters()
    }

    // --- NFC Lifecycle Handlers ---

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not supported on this device. RFID features disabled.", Toast.LENGTH_LONG).show()
            isNfcSupported = false
            return
        }

        isNfcSupported = true

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Gumamit ng FLAG_MUTABLE or FLAG_IMMUTABLE
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onResume() {
        super.onResume()
        if (isNfcSupported) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcSupported) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    // 🛑 CRITICAL: Ito ang mag-de-detect ng RFID/NFC scan!
    override fun onNewIntent(intent: Intent) {
        if (!isNfcSupported) {
            super.onNewIntent(intent)
            return
        }

        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            // Gumamit ng getParcelableExtra<Tag> para sa safety
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val rfidHex = bytesToHex(tag.id)
                Log.d("NFC_SCAN", "RFID Detected: $rfidHex")

                if (currentStudentForRfid != null) {
                    // Mode 1: Registration is active
                    saveRfidTag(currentStudentForRfid!!, rfidHex)
                    // HINDI I-D-DISMISS ANG DIALOG DITO
                } else {
                    // Mode 2: Quick Search
                    performRfidQuickSearch(rfidHex)
                }
            }
        }
    }

    // --- RFID Quick Search Function ---

    private fun performRfidQuickSearch(rfidTag: String) {
        // I-search ang student gamit ang RFID tag
        firestore.collection("students")
            .whereEqualTo("rfidTag", rfidTag)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "RFID Tag $rfidTag is not registered to any student.", Toast.LENGTH_LONG).show()
                } else {
                    val studentDoc = snapshot.documents.first()
                    // 🛑 Note: Use toObject with the updated Student class
                    val student = studentDoc.toObject(Student::class.java)?.copy(id = studentDoc.id)

                    if (student != null) {
                        // 1. I-apply ang search filter para ipakita lang ang student na ito
                        etSearch.setText(student.studentId) // Gagamitin ang Student ID sa search box

                        // 2. Ipakita ang detailed dialog agad
                        showStudentDetailDialog(student)

                        Toast.makeText(this, "Student found: ${student.firstName} ${student.lastName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("RFID_SEARCH", "Error searching by RFID tag", e)
            }
    }


    // --- Utility Functions ---

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    // --- Data Loading and Filtering ---

    private fun loadAllStudents() {
        firestore.collection("students")
            .orderBy("lastName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                allStudentsCache = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Student::class.java)?.copy(id = doc.id)
                }

                val courses = allStudentsCache.map { it.courseCode }.distinct().toMutableList()
                courses.add(0, "All Courses")
                setupSpinner(spinnerFilterCourse, courses) { _ -> applyFilters() }

                applyFilters()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("Students", "Error loading students", e)
            }
    }

    private fun setupFilters() {
        val years = listOf("All Year Levels", "1st Year", "2nd Year", "3rd Year", "4th Year")
        setupSpinner(spinnerFilterYear, years) { _ -> applyFilters() }

        etSearch.addTextChangedListener {
            applyFilters()
        }
    }

    private fun setupSpinner(spinner: Spinner, items: List<String>, onItemSelected: (String) -> Unit) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onItemSelected(parent?.getItemAtPosition(position).toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // 🛑 UPDATED: Kasama na ang RFID Tag sa search logic
    private fun applyFilters() {
        val searchText = etSearch.text.toString().trim().lowercase(Locale.getDefault())
        val selectedCourse = spinnerFilterCourse.selectedItem?.toString()
        val selectedYear = spinnerFilterYear.selectedItem?.toString()

        val filteredList = allStudentsCache.filter { student ->
            val courseMatch = selectedCourse == "All Courses" || student.courseCode == selectedCourse
            val yearMatch = selectedYear == "All Year Levels" || student.yearLevel == selectedYear

            // 🟢 UPDATED Search Filter: Kasama na ang RFID Tag
            val searchMatch = searchText.isEmpty() ||
                    student.firstName.lowercase(Locale.getDefault()).contains(searchText) ||
                    student.lastName.lowercase(Locale.getDefault()).contains(searchText) ||
                    student.studentId.lowercase(Locale.getDefault()).contains(searchText) ||
                    (!student.rfidTag.isNullOrEmpty() && student.rfidTag!!.lowercase(Locale.getDefault()).contains(searchText)) // NEW: Check RFID Tag

            courseMatch && yearMatch && searchMatch
        }

        studentList.clear()
        studentList.addAll(filteredList)
        adapter.notifyDataSetChanged()
    }

    // --- RFID/NFC Registration and Reset/Disable Logic ---

    // 🟢 NEW FUNCTION: Check for RFID tag conflict across Students and Teachers (GLOBAL CHECK)
    private suspend fun checkRfidConflict(rfidTag: String, currentStudentDocId: String): Boolean {
        // 1. Check Student Collection (Dapat HINDI ang kasalukuyang student ang gumagamit)
        val studentSnapshot = firestore.collection("students")
            .whereEqualTo("rfidTag", rfidTag)
            .get().await()

        // Conflict kung may ibang student na gumagamit nito (ibang document ID)
        val studentConflict = studentSnapshot.documents.any { doc ->
            doc.id != currentStudentDocId && doc.getString("rfidTag") == rfidTag
        }
        if (studentConflict) return true

        // 2. Check Teacher Collection (Dapat walang teacher na gumagamit)
        val teacherSnapshot = firestore.collection("teachers")
            .whereEqualTo("rfidTag", rfidTag)
            .limit(1)
            .get().await()

        val teacherConflict = !teacherSnapshot.isEmpty
        if (teacherConflict) return true

        return false
    }

    // 🟡 UPDATED LOGIC: Disable
    private fun disableRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Disable")
            .setMessage("Are you sure you want to **DISABLE** the RFID tag for ${student.firstName} ${student.lastName}? This will set the tag to **DISABLED** status, meaning it can't be used for attendance or login.")
            .setPositiveButton("Disable Tag") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)

                // 🛑 CRITICAL: Gamitin ang userUid direkta!
                if (student.userUid.isEmpty()) {
                    Toast.makeText(this, "Disable failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val userRef = firestore.collection("users").document(student.userUid)

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        // 🛑 UPDATE: Set rfidStatus to DISABLED sa Students AT Users
                        val updateData = mapOf("rfidStatus" to "DISABLED")

                        firestore.runBatch { batch ->
                            batch.update(studentRef, updateData)
                            batch.update(userRef, updateData) // CRITICAL: Update Users
                        }.await()

                        Toast.makeText(this@ManageStudentsActivity, "RFID Tag successfully **DISABLED** for ${student.firstName}.", Toast.LENGTH_LONG).show()

                        // I-update ang cache at UI
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidStatus = "DISABLED") else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()
                    } catch (e: Exception) {
                        Toast.makeText(this@ManageStudentsActivity, "Failed to disable RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error disabling RFID tag or syncing user", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 🔄 UPDATED LOGIC: Reset
    private fun resetRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Reset & Re-registration")
            .setMessage("Are you sure you want to **RESET** the RFID tag for ${student.firstName} ${student.lastName}? This will remove the current tag, clear the status, and immediately start the process to scan a NEW one.")
            .setPositiveButton("Reset & Scan New") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)

                // 🛑 CRITICAL: Gamitin ang userUid direkta!
                if (student.userUid.isEmpty()) {
                    Toast.makeText(this, "Reset failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val userRef = firestore.collection("users").document(student.userUid)

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        // 🛑 UPDATE: Remove rfidTag AND rfidStatus sa Students AT Users
                        val updateData = mapOf(
                            "rfidTag" to null,
                            "rfidStatus" to null
                        )

                        firestore.runBatch { batch ->
                            batch.update(studentRef, updateData)
                            batch.update(userRef, updateData) // CRITICAL: Update Users
                        }.await()

                        Toast.makeText(this@ManageStudentsActivity, "RFID Tag cleared. Please scan the new tag now.", Toast.LENGTH_LONG).show()

                        // I-update ang cache at UI
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidTag = null, rfidStatus = null) else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()

                        // 🛑 CRITICAL: Awtomatikong buksan ang scanner pagkatapos mag-reset
                        showRfidDetectionDialog(student.copy(rfidTag = null, rfidStatus = null))

                    } catch (e: Exception) {
                        Toast.makeText(this@ManageStudentsActivity, "Failed to reset RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error resetting RFID tag or syncing user", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStudentDetailDialog(student: Student) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.admin_student_detail_dialog, null)

        val tvName = dialogView.findViewById<TextView>(R.id.tvName)
        val tvId = dialogView.findViewById<TextView>(R.id.tvStudentId)
        val tvCourse = dialogView.findViewById<TextView>(R.id.tvCourseYear)
        val tvRfid = dialogView.findViewById<TextView>(R.id.tvRfidStatus)
        val btnAddRfid = dialogView.findViewById<Button>(R.id.btnAddRfid)
        val btnResetRfid = dialogView.findViewById<Button>(R.id.btnResetRfid)
        val btnDisableRfid = dialogView.findViewById<Button>(R.id.btnDisableRfid)


        tvName.text = "${student.lastName}, ${student.firstName}"
        tvId.text = "ID: ${student.studentId}"
        tvCourse.text = "${student.courseCode} - ${student.yearLevel}"


        // Itago muna ang lahat ng button
        btnAddRfid.visibility = View.GONE
        btnResetRfid.visibility = View.GONE
        btnDisableRfid.visibility = View.GONE


        // 🛑 FINAL LOGIC PARA SA BUTTONS AT TEXT 🛑
        if (!student.rfidTag.isNullOrEmpty()) {
            // May rfidTag (pwedeng ACTIVE o DISABLED)
            when (student.rfidStatus) {
                "ACTIVE" -> {
                    // Case 1: Active (Pwedeng i-Reset o i-Disable)
                    tvRfid.text = "RFID Status: 🟢 ACTIVE (${student.rfidTag})"
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                    btnDisableRfid.text = "Disable RFID" // Tiyakin na Disable ang text
                }
                "DISABLED" -> {
                    // Case 2: Disabled (Pwedeng i-Reset o i-Activate)
                    tvRfid.text = "RFID Status: 🟡 DISABLED (${student.rfidTag})"
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                    btnDisableRfid.text = "Activate RFID" // 🛑 ITO ANG PAGBABAGO: Palitan ang text sa Activate
                }
                else -> {
                    // Case 3: May tag pero walang status (Inconsistent Data)
                    tvRfid.text = "RFID Status: ❓ UNKNOWN TAG STATUS (${student.rfidTag})"
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                    btnDisableRfid.text = "Disable RFID" // Default sa Disable
                }
            }

        } else if (!isNfcSupported) {
            // Case 4: Walang tag AND walang NFC support ang phone.
            tvRfid.text = "RFID Status: ❌ NFC NOT AVAILABLE"

        } else {
            // Case 5: Walang tag AND may NFC support ang phone (Not Registered/Ready for Registration).
            tvRfid.text = "RFID Status: 🔴 NOT REGISTERED"
            btnAddRfid.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Student Profile")
            .setNegativeButton("Close", null)
            .create()

        btnAddRfid.setOnClickListener {
            dialog.dismiss()
            showRfidDetectionDialog(student.copy(rfidTag = null, rfidStatus = null))
        }

        // Reset Click Listener
        btnResetRfid.setOnClickListener {
            dialog.dismiss()
            resetRfidTag(student)
        }

        // Disable/Activate Click Listener (Conditional Logic)
        btnDisableRfid.setOnClickListener {
            dialog.dismiss()

            // 🛑 NEW LOGIC: Tignan kung ano ang current status ng student object
            if (student.rfidStatus == "DISABLED") {
                activateRfidTag(student) // 🟢 Tatawagin ang Activate function
            } else {
                disableRfidTag(student) // 🟡 Tatawagin ang Disable function (para sa ACTIVE o UNKNOWN status)
            }
        }

        dialog.show()
    }

    private fun showRfidDetectionDialog(student: Student) {
        // I-set ang student na ire-register. Ginagamit ito sa onNewIntent
        currentStudentForRfid = student

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rfid_detection, null)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvRfidDetectionStatus)

        tvStatus.text = "Ready to scan RFID/NFC tag for ${student.firstName} ${student.lastName}.\n\nBring the tag near the phone's NFC area."

        rfidDetectionDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("RFID Tag Registration")
            .setPositiveButton("Cancel") { _, _ ->
                currentStudentForRfid = null // I-clear ang selection
                // Kapag nag-cancel, ibalik sa detail dialog
                showStudentDetailDialog(student)
            }
            .setCancelable(false)
            .create()

        rfidDetectionDialog!!.show()
    }

    // 🟢 NEW FUNCTION: Activate RFID Tag for Student
    private fun activateRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Activation")
            .setMessage("Are you sure you want to **ACTIVATE** the RFID tag for ${student.firstName} ${student.lastName}? This will set the tag back to **ACTIVE** status, allowing it to be used for attendance and login.")
            .setPositiveButton("Activate Tag") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)

                if (student.userUid.isEmpty()) {
                    Toast.makeText(this, "Activation failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                val userRef = firestore.collection("users").document(student.userUid)

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        // 🛑 UPDATE: Set rfidStatus to ACTIVE sa Students AT Users
                        val updateData = mapOf("rfidStatus" to "ACTIVE")

                        firestore.runBatch { batch ->
                            batch.update(studentRef, updateData)
                            batch.update(userRef, updateData) // CRITICAL: Update Users
                        }.await()

                        Toast.makeText(this@ManageStudentsActivity, "RFID Tag successfully **ACTIVATED** for ${student.firstName}.", Toast.LENGTH_LONG).show()

                        // I-update ang cache at UI
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidStatus = "ACTIVE") else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()
                        // Ipakita ulit ang dialog para makita ang bagong status
                        showStudentDetailDialog(student.copy(rfidStatus = "ACTIVE"))

                    } catch (e: Exception) {
                        Toast.makeText(this@ManageStudentsActivity, "Failed to activate RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error activating RFID tag or syncing user", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRfidTag(student: Student, rfidTag: String) {
        val studentRef = firestore.collection("students").document(student.id)

        // 🛑 CRITICAL: Gamitin ang userUid direkta!
        if (student.userUid.isEmpty()) {
            Toast.makeText(this, "Registration failed: Missing User UID for this student.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error: Student document is missing userUid field for ${student.studentId}")
            currentStudentForRfid = null
            rfidDetectionDialog?.dismiss()
            return
        }
        val userRef = firestore.collection("users").document(student.userUid)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. Check for GLOBAL conflict (Code remains the same)
                val conflict = checkRfidConflict(rfidTag, student.id)
                if (conflict) {
                    // ... (Conflict handling code remains the same) ...
                    val errorMessage = "❌ ERROR: RFID Tag **$rfidTag** is already registered to another user (Student or Teacher). Tag not assigned."
                    AlertDialog.Builder(this@ManageStudentsActivity)
                        .setTitle("RFID Registration Conflict")
                        .setMessage(errorMessage)
                        .setPositiveButton("Try Again") { dialog, _ -> dialog.dismiss() }
                        .setNegativeButton("Cancel Registration") { dialog, _ ->
                            currentStudentForRfid = null
                            rfidDetectionDialog?.dismiss()
                            showStudentDetailDialog(student)
                        }
                        .setCancelable(false)
                        .show()
                    return@launch
                }

                // 2. I-update ang BOTH Students at Users Collections
                val updateData = mapOf(
                    "rfidTag" to rfidTag,
                    "rfidStatus" to "ACTIVE"
                )

                // Batch Write para siguradong sabay-sabay mag-update (MAS SAFE)
                firestore.runBatch { batch ->
                    batch.update(studentRef, updateData)
                    batch.update(userRef, updateData) // CRITICAL: Update Users gamit ang userUid
                }.await()


                Toast.makeText(this@ManageStudentsActivity, "Successfully registered RFID: $rfidTag. Status: ACTIVE", Toast.LENGTH_LONG).show()

                // I-update ang cache at UI
                val updatedList = allStudentsCache.map {
                    if (it.id == student.id) it.copy(rfidTag = rfidTag, rfidStatus = "ACTIVE") else it
                }
                allStudentsCache = updatedList
                applyFilters()
                currentStudentForRfid = null
                rfidDetectionDialog?.dismiss()

            } catch (e: Exception) {
                Toast.makeText(this@ManageStudentsActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error saving/validating RFID tag or syncing user: ${e.message}", e)
                currentStudentForRfid = null
                rfidDetectionDialog?.dismiss()
                showStudentDetailDialog(student)
            }
        }
    }
}

// -----------------------------
// RecyclerView Adapter
// -----------------------------
class StudentAdapter(
    private val items: List<Student>,
    private val clickListener: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStudentItemName)
        val tvId: TextView = view.findViewById(R.id.tvStudentItemId)
        // Gumamit ng tvRfidStatus na id kung saan man ito idineklara sa admin_student_item.xml
        val tvRfid: TextView = view.findViewById(R.id.tvRfidStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_student_item, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = items[position]
        holder.tvName.text = "${student.lastName}, ${student.firstName}"
        holder.tvId.text = "ID: ${student.studentId} (${student.courseCode})"

        // 🛑 NEW/UPDATED LOGIC: I-check ang rfidStatus
        when (student.rfidStatus) {
            "ACTIVE" -> holder.tvRfid.text = "🟢 ACTIVE"
            "DISABLED" -> holder.tvRfid.text = "🟡 DISABLED"
            else -> holder.tvRfid.text = "🔴 Not Registered" // Kasama rito ang null, empty, at iba pang values
        }

        holder.itemView.setOnClickListener { clickListener(student) }
    }

    override fun getItemCount(): Int = items.size
}
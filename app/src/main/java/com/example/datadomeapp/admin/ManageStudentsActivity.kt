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
    // 🟢 NEW FIELD: rfidStatus
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

    // --- NFC Lifecycle Handlers (No Changes) ---

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not supported on this device. RFID features disabled.", Toast.LENGTH_LONG).show()
            isNfcSupported = false
            return
        }

        isNfcSupported = true

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
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

    // 🛑 CRITICAL: Ito ang mag-de-detect ng RFID/NFC scan! (No Changes)
    override fun onNewIntent(intent: Intent) {
        if (!isNfcSupported) {
            super.onNewIntent(intent)
            return
        }

        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val rfidHex = bytesToHex(tag.id)
                Log.d("NFC_SCAN", "RFID Detected: $rfidHex")

                if (currentStudentForRfid != null) {
                    // Mode 1: Registration is active
                    saveRfidTag(currentStudentForRfid!!, rfidHex)
                    rfidDetectionDialog?.dismiss()
                } else {
                    // Mode 2: Quick Search
                    performRfidQuickSearch(rfidHex)
                }
            }
        }
    }

    // --- RFID Quick Search Function (No Changes) ---

    private fun performRfidQuickSearch(rfidTag: String) {
        // I-search ang student gamit ang RFID tag
        // Note: Kahit "DISABLED" ang status, pwede pa rin itong mahanap dito.
        // Ang rfidTag field ang primary key sa search.
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
                        etSearch.setText(student.studentId)

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


    // --- Utility Functions (No Changes) ---

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

    // --- Data Loading and Filtering (No Changes in logic, relies on updated Student class) ---

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

    private fun applyFilters() {
        val searchText = etSearch.text.toString().trim().lowercase(Locale.getDefault())
        val selectedCourse = spinnerFilterCourse.selectedItem?.toString()
        val selectedYear = spinnerFilterYear.selectedItem?.toString()

        val filteredList = allStudentsCache.filter { student ->
            val courseMatch = selectedCourse == "All Courses" || student.courseCode == selectedCourse
            val yearMatch = selectedYear == "All Year Levels" || student.yearLevel == selectedYear

            // Search Filter: Uses studentId for filtering
            val searchMatch = searchText.isEmpty() ||
                    student.firstName.lowercase(Locale.getDefault()).contains(searchText) ||
                    student.lastName.lowercase(Locale.getDefault()).contains(searchText) ||
                    student.studentId.lowercase(Locale.getDefault()).contains(searchText)

            courseMatch && yearMatch && searchMatch
        }

        studentList.clear()
        studentList.addAll(filteredList)
        adapter.notifyDataSetChanged()
    }

    // --- RFID/NFC Registration and Reset/Disable Logic ---

    // 🟡 UPDATED LOGIC: Disable (Tatanggalin ang tag at mag-se-set ng status na "DISABLED")
    private fun disableRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Disable")
            .setMessage("Are you sure you want to **DISABLE** the RFID tag for ${student.firstName} ${student.lastName}? This will set the tag to **DISABLED** status, meaning it can't be used for attendance.")
            .setPositiveButton("Disable Tag") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)

                // 🛑 UPDATE: Set rfidStatus to DISABLED
                studentRef.update(mapOf(
                    "rfidTag" to student.rfidTag, // Keep the tag, just disable it
                    "rfidStatus" to "DISABLED"
                ))
                    .addOnSuccessListener {
                        Toast.makeText(this, "RFID Tag successfully **DISABLED** for ${student.firstName}.", Toast.LENGTH_LONG).show()

                        // I-update ang cache at UI
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidStatus = "DISABLED") else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()
                        // Isasara lang dito.
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to disable RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error disabling RFID tag", e)
                    }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 🔄 UPDATED LOGIC: Reset (Tatanggalin ang tag at magbubukas ng scanner at clear status)
    private fun resetRfidTag(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Confirm RFID Reset & Re-registration")
            .setMessage("Are you sure you want to **RESET** the RFID tag for ${student.firstName} ${student.lastName}? This will remove the current tag, clear the status, and immediately start the process to scan a NEW one.")
            .setPositiveButton("Reset & Scan New") { dialog, _ ->
                val studentRef = firestore.collection("students").document(student.id)

                // 🛑 UPDATE: Remove rfidTag AND rfidStatus
                studentRef.update(mapOf(
                    "rfidTag" to null,
                    "rfidStatus" to null
                ))
                    .addOnSuccessListener {
                        Toast.makeText(this, "RFID Tag cleared. Please scan the new tag now.", Toast.LENGTH_LONG).show()

                        // I-update ang cache at UI
                        val updatedList = allStudentsCache.map {
                            if (it.id == student.id) it.copy(rfidTag = null, rfidStatus = null) else it
                        }
                        allStudentsCache = updatedList
                        applyFilters()

                        // 🛑 CRITICAL: Awtomatikong buksan ang scanner pagkatapos mag-reset
                        showRfidDetectionDialog(student.copy(rfidTag = null, rfidStatus = null))
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to reset RFID tag: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Error resetting RFID tag", e)
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


        // 🛑 NEW/UPDATED LOGIC: I-check ang rfidTag at rfidStatus para sa tamang display at buttons
        if (!student.rfidTag.isNullOrEmpty()) {
            // May rfidTag (pwedeng ACTIVE o DISABLED)
            when (student.rfidStatus) {
                "ACTIVE" -> {
                    // Case 1: Active
                    tvRfid.text = "RFID Status: 🟢 ACTIVE (${student.rfidTag})"
                    btnAddRfid.visibility = View.GONE
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                }
                "DISABLED" -> {
                    // Case 2: Disabled
                    tvRfid.text = "RFID Status: 🟡 DISABLED (${student.rfidTag})"
                    btnAddRfid.visibility = View.GONE
                    btnResetRfid.visibility = View.VISIBLE // Pwedeng i-reset para makapag-scan ng bago
                    btnDisableRfid.visibility = View.GONE // Naka-disable na, kaya i-hide ang disable button
                }
                else -> {
                    // Case 3: May tag pero walang status (Old/Inconsistent Data)
                    tvRfid.text = "RFID Status: ❓ UNKNOWN TAG STATUS (${student.rfidTag})"
                    btnAddRfid.visibility = View.GONE
                    btnResetRfid.visibility = View.VISIBLE
                    btnDisableRfid.visibility = View.VISIBLE
                }
            }

        } else if (!isNfcSupported) {
            // Case 4: Walang tag AND walang NFC support ang phone.
            tvRfid.text = "RFID Status: ❌ NFC NOT AVAILABLE"
            btnAddRfid.visibility = View.GONE
            btnResetRfid.visibility = View.GONE
            btnDisableRfid.visibility = View.GONE

        } else {
            // Case 5: Walang tag AND may NFC support ang phone (Not Registered/Ready for Registration).
            tvRfid.text = "RFID Status: 🔴 NOT REGISTERED"
            btnAddRfid.visibility = View.VISIBLE
            btnResetRfid.visibility = View.GONE
            btnDisableRfid.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Student Profile")
            .setNegativeButton("Close", null)
            .create()

        btnAddRfid.setOnClickListener {
            dialog.dismiss()
            // Gamitin ang copy ng student na walang rfidTag/rfidStatus para sa registration
            showRfidDetectionDialog(student.copy(rfidTag = null, rfidStatus = null))
        }

        // Reset Click Listener (Reset & Scan New)
        btnResetRfid.setOnClickListener {
            dialog.dismiss()
            resetRfidTag(student)
        }

        // Disable Click Listener (Disable & Close)
        btnDisableRfid.setOnClickListener {
            dialog.dismiss()
            disableRfidTag(student)
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
            }
            .setCancelable(false)
            .create()

        rfidDetectionDialog!!.show()
    }

    // 🟢 UPDATED LOGIC: Save (I-set ang rfidStatus sa "ACTIVE")
    private fun saveRfidTag(student: Student, rfidTag: String) {
        val studentRef = firestore.collection("students").document(student.id)

        // 🛑 NEW VALIDATION: I-check kung may ibang gumagamit na ng RFID tag
        firestore.collection("students")
            .whereEqualTo("rfidTag", rfidTag)
            .get()
            .addOnSuccessListener { querySnapshot ->

                // I-check kung may nakita at HINDI ito ang kasalukuyang estudyante
                val conflict = querySnapshot.documents.any { doc -> doc.id != student.id }

                if (conflict) {
                    // May conflict! Ginagamit na ang tag
                    Toast.makeText(this, "🔴 ERROR: RFID Tag $rfidTag is already registered to another student.", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Conflict: RFID Tag already in use.")
                    currentStudentForRfid = null
                } else if (!student.rfidTag.isNullOrEmpty()) {
                    // Dapat ay hindi na ito mangyari dahil sa UI logic, pero dagdag safety
                    Toast.makeText(this, "🔴 ERROR: Student already has a registered RFID tag. Contact IT to reset.", Toast.LENGTH_LONG).show()
                    currentStudentForRfid = null
                } else {
                    // Walang conflict at walang tag pa. I-save na.
                    // 🛑 UPDATE: Set rfidTag AND rfidStatus to "ACTIVE"
                    studentRef.update(mapOf(
                        "rfidTag" to rfidTag,
                        "rfidStatus" to "ACTIVE"
                    ))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Successfully registered RFID: $rfidTag for ${student.firstName}. Status: ACTIVE", Toast.LENGTH_LONG).show()

                            // I-update ang cache at UI
                            val updatedList = allStudentsCache.map {
                                if (it.id == student.id) it.copy(rfidTag = rfidTag, rfidStatus = "ACTIVE") else it
                            }
                            allStudentsCache = updatedList
                            applyFilters()
                            currentStudentForRfid = null
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to save RFID: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Error saving RFID tag", e)
                            currentStudentForRfid = null
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Validation failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error validating RFID tag", e)
                currentStudentForRfid = null
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
        // 🛑 UPDATE: Use a different ID if needed, or re-use existing TV for status
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

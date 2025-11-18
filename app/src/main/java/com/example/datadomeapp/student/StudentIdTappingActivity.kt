package com.example.datadomeapp.student

import android.app.PendingIntent
import android.content.Intent
import android.content.DialogInterface // ADD THIS IMPORT
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// Data model for attendance session
data class AttendanceSession(
    val assignmentId: String = "",
    val subjectCode: String = "",
    val className: String = "",
    val date: String = "",
    val timeSlot: String = "",
    val displayTimeSlot: String = "",
    val sessionType: String = "" // "ATTENDANCE" or "RECITATION"
)

class StudentIdTappingActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val ATTENDANCE_COLLECTION = "dailyAttendanceRecords"

    private lateinit var tvSessionInfo: TextView
    private lateinit var tvStudentInfo: TextView
    private lateinit var tvTapStatus: TextView
    private lateinit var btnStartAttendance: Button
    private lateinit var btnStartRecitation: Button
    private lateinit var btnStopSession: Button
    private lateinit var rgModeSelection: RadioGroup
    private lateinit var rbAttendanceMode: RadioButton
    private lateinit var rbRecitationMode: RadioButton
    private lateinit var progressBar: ProgressBar

    // NFC/RFID variables
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isNfcSupported = false
    private var isSessionActive = false

    // Current session data
    private var currentSession: AttendanceSession? = null
    private var tappedStudents = mutableSetOf<String>()

    // Log Tag
    private val TAG = "StudentIdTappingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_id_tapping)

        initializeViews()
        setupNfc()
        loadAssignmentData()
        setupClickListeners()
    }

    private fun initializeViews() {
        tvSessionInfo = findViewById(R.id.tvSessionInfo)
        tvStudentInfo = findViewById(R.id.tvStudentInfo)
        tvTapStatus = findViewById(R.id.tvTapStatus)
        btnStartAttendance = findViewById(R.id.btnStartAttendance)
        btnStartRecitation = findViewById(R.id.btnStartRecitation)
        btnStopSession = findViewById(R.id.btnStopSession)
        rgModeSelection = findViewById(R.id.rgModeSelection)
        rbAttendanceMode = findViewById(R.id.rbAttendanceMode)
        rbRecitationMode = findViewById(R.id.rbRecitationMode)
        progressBar = findViewById(R.id.progressBar)

        // Hide stop button initially
        btnStopSession.isVisible = false
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not supported on this device.", Toast.LENGTH_LONG).show()
            isNfcSupported = false
            disableAllButtons()
            return
        }

        isNfcSupported = true
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun disableAllButtons() {
        btnStartAttendance.isEnabled = false
        btnStartRecitation.isEnabled = false
        btnStopSession.isEnabled = false
    }

    private fun loadAssignmentData() {
        val assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        val className = intent.getStringExtra("CLASS_NAME")
        val subjectCode = intent.getStringExtra("SUBJECT_CODE")

        if (assignmentId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing assignment data.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentSession = AttendanceSession(
            assignmentId = assignmentId,
            subjectCode = subjectCode ?: "",
            className = className ?: "",
            date = getCurrentDate(),
            timeSlot = "",
            displayTimeSlot = "",
            sessionType = "ATTENDANCE"
        )

        updateSessionInfo()
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun setupClickListeners() {
        btnStartAttendance.setOnClickListener {
            startSession("ATTENDANCE")
        }

        btnStartRecitation.setOnClickListener {
            startSession("RECITATION")
        }

        btnStopSession.setOnClickListener {
            stopSession()
        }

        rgModeSelection.setOnCheckedChangeListener { _, checkedId ->
            if (isSessionActive) {
                val newType = if (checkedId == R.id.rbAttendanceMode) "ATTENDANCE" else "RECITATION"
                switchSessionType(newType)
            }
        }
    }

    private fun startSession(sessionType: String) {
        if (!isNfcSupported) {
            Toast.makeText(this, "NFC not supported on this device.", Toast.LENGTH_LONG).show()
            return
        }

        showTimeSlotSelection { timeSlot, displayTime ->
            currentSession = currentSession?.copy(
                timeSlot = timeSlot,
                displayTimeSlot = displayTime,
                sessionType = sessionType
            )

            isSessionActive = true
            tappedStudents.clear()

            updateUIForActiveSession()
            updateSessionInfo()

            Toast.makeText(this, "$sessionType session started! Ready for ID tapping.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSession() {
        isSessionActive = false
        tappedStudents.clear()

        updateUIForInactiveSession()
        updateSessionInfo()

        Toast.makeText(this, "Session stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun switchSessionType(newType: String) {
        currentSession = currentSession?.copy(sessionType = newType)
        tappedStudents.clear()
        updateSessionInfo()
        Toast.makeText(this, "Switched to $newType mode", Toast.LENGTH_SHORT).show()
    }

    private fun showTimeSlotSelection(onTimeSlotSelected: (String, String) -> Unit) {
        val options = arrayOf("1-Hour Slots", "2-Hour Slots", "3-Hour Slots")

        AlertDialog.Builder(this)
            .setTitle("Select Duration")
            .setItems(options) { _: DialogInterface, which: Int -> // FIXED: Use DialogInterface
                when (which) {
                    0 -> showOneHourSlots(onTimeSlotSelected)
                    1 -> showTwoHourSlots(onTimeSlotSelected)
                    2 -> showThreeHourSlots(onTimeSlotSelected)
                }
            }
            .setNegativeButton("Cancel", null) // FIXED: Use null or proper listener
            .show()
    }

    private fun showOneHourSlots(onTimeSlotSelected: (String, String) -> Unit) {
        val slots = listOf(
            "07:00 AM - 08:00 AM", "08:00 AM - 09:00 AM", "09:00 AM - 10:00 AM",
            "10:00 AM - 11:00 AM", "11:00 AM - 12:00 PM", "12:00 PM - 01:00 PM",
            "01:00 PM - 02:00 PM", "02:00 PM - 03:00 PM", "03:00 PM - 04:00 PM",
            "04:00 PM - 05:00 PM", "05:00 PM - 06:00 PM", "06:00 PM - 07:00 PM"
        )
        showSlotSelectionDialog(slots, "1H", onTimeSlotSelected)
    }

    private fun showTwoHourSlots(onTimeSlotSelected: (String, String) -> Unit) {
        val slots = listOf(
            "07:00 AM - 09:00 AM", "08:00 AM - 10:00 AM", "09:00 AM - 11:00 AM",
            "10:00 AM - 12:00 PM", "11:00 AM - 01:00 PM", "12:00 PM - 02:00 PM",
            "01:00 PM - 03:00 PM", "02:00 PM - 04:00 PM", "03:00 PM - 05:00 PM",
            "04:00 PM - 06:00 PM", "05:00 PM - 07:00 PM"
        )
        showSlotSelectionDialog(slots, "2H", onTimeSlotSelected)
    }

    private fun showThreeHourSlots(onTimeSlotSelected: (String, String) -> Unit) {
        val slots = listOf(
            "07:00 AM - 10:00 AM", "08:00 AM - 11:00 AM", "09:00 AM - 12:00 PM",
            "10:00 AM - 01:00 PM", "11:00 AM - 02:00 PM", "12:00 PM - 03:00 PM",
            "01:00 PM - 04:00 PM", "02:00 PM - 05:00 PM", "03:00 PM - 06:00 PM",
            "04:00 PM - 07:00 PM"
        )
        showSlotSelectionDialog(slots, "3H", onTimeSlotSelected)
    }

    private fun showSlotSelectionDialog(
        slots: List<String>,
        durationPrefix: String,
        onTimeSlotSelected: (String, String) -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("Select Time Slot ($durationPrefix)")
            .setItems(slots.toTypedArray()) { _: DialogInterface, which: Int -> // FIXED: Use DialogInterface
                val timeSlot = "${durationPrefix}_slot_${which + 1}"
                val displayTime = slots[which]
                onTimeSlotSelected(timeSlot, displayTime)
            }
            .setNegativeButton("Cancel", null) // FIXED: Use null or proper listener
            .show()
    }

    // NFC Lifecycle Methods
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (!isNfcSupported || !isSessionActive) return

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val rfidHex = bytesToHex(tag.id)
                Log.d("ID_TAPPING", "RFID Detected: $rfidHex")
                processStudentTap(rfidHex)
            }
        }
    }

    private fun processStudentTap(rfidTag: String) {
        progressBar.isVisible = true
        tvTapStatus.text = "Processing ID tap..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Find student by RFID tag
                val studentSnapshot = firestore.collection("students")
                    .whereEqualTo("rfidTag", rfidTag)
                    .limit(1)
                    .get()
                    .await()

                if (studentSnapshot.isEmpty) {
                    tvTapStatus.text = "❌ Student not found for this RFID tag"
                    progressBar.isVisible = false
                    return@launch
                }

                val studentDoc = studentSnapshot.documents.first()
                val studentId = studentDoc.id
                val studentName = "${studentDoc.getString("firstName")} ${studentDoc.getString("lastName")}"
                val studentNumber = studentDoc.getString("studentId") ?: ""

                // Check if student is enrolled in this class
                if (!isStudentEnrolled(studentId)) {
                    tvTapStatus.text = "❌ Student not enrolled in this class"
                    progressBar.isVisible = false
                    return@launch
                }

                // Record attendance/recitation
                if (recordStudentAttendance(studentId)) {
                    tvStudentInfo.text = "Student: $studentName\nID: $studentNumber"
                    tvTapStatus.text = "✅ ${currentSession?.sessionType} recorded successfully!"
                    tappedStudents.add(studentId)
                } else {
                    tvTapStatus.text = "⚠️ Already recorded for this session"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing student tap", e)
                tvTapStatus.text = "❌ Error processing tap: ${e.message}"
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    private suspend fun isStudentEnrolled(studentId: String): Boolean {
        val session = currentSession ?: return false

        // Check if student is in the class assignment
        val enrollmentDoc = firestore.collection("classAssignments")
            .document(session.assignmentId)
            .collection("enrolledStudents")
            .document(studentId)
            .get()
            .await()

        return enrollmentDoc.exists()
    }

    private suspend fun recordStudentAttendance(studentId: String): Boolean {
        val session = currentSession ?: return false

        // Check if already recorded in this session
        if (tappedStudents.contains(studentId)) {
            return false
        }

        // Generate record ID
        val recordId = "${session.assignmentId}_${session.date}_${session.timeSlot}"

        // Get existing record or create new one
        val recordDoc = firestore.collection(ATTENDANCE_COLLECTION)
            .document(recordId)
            .get()
            .await()

        val currentStatuses = if (recordDoc.exists()) {
            (recordDoc.get("statuses") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        val currentRecitation = if (recordDoc.exists()) {
            (recordDoc.get("recitationPoints") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        // Update based on session type
        when (session.sessionType) {
            "ATTENDANCE" -> {
                currentStatuses[studentId] = "PRESENT"
            }
            "RECITATION" -> {
                currentRecitation[studentId] = 1L // 1 point for recitation
            }
        }

        // Prepare update data
        val updateData = hashMapOf<String, Any>(
            "assignmentId" to session.assignmentId,
            "subjectCode" to session.subjectCode,
            "date" to session.date,
            "timeSlot" to session.timeSlot,
            "displayTimeSlot" to session.displayTimeSlot,
            "statuses" to currentStatuses,
            "recitationPoints" to currentRecitation
        )

        // Add academic term info if creating new record
        if (!recordDoc.exists()) {
            val termDoc = firestore.document("systemSettings/currentTerm").get().await()
            updateData["academicTerm"] = termDoc.getString("academicTerm") ?: ""
            updateData["academicYear"] = termDoc.getString("academicYear") ?: ""
            updateData["semester"] = termDoc.getString("semester") ?: ""
        }

        // Save to Firestore
        firestore.collection(ATTENDANCE_COLLECTION)
            .document(recordId)
            .set(updateData)
            .await()

        return true
    }

    private fun updateUIForActiveSession() {
        btnStartAttendance.isVisible = false
        btnStartRecitation.isVisible = false
        btnStopSession.isVisible = true
        rgModeSelection.isEnabled = true
    }

    private fun updateUIForInactiveSession() {
        btnStartAttendance.isVisible = true
        btnStartRecitation.isVisible = true
        btnStopSession.isVisible = false
        rgModeSelection.isEnabled = false
        tvStudentInfo.text = "No student data"
        tvTapStatus.text = "Session inactive"
    }

    private fun updateSessionInfo() {
        val session = currentSession
        if (session != null && isSessionActive) {
            tvSessionInfo.text = """
                Class: ${session.className}
                Date: ${session.date}
                Time: ${session.displayTimeSlot}
                Mode: ${session.sessionType}
                Tapped: ${tappedStudents.size} students
            """.trimIndent()
        } else {
            tvSessionInfo.text = "No active session"
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = charArrayOf('0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F')
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
package com.example.datadomeapp.teacher

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class RecordAttendanceActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val ATTENDANCE_COLLECTION = "dailyAttendanceRecords"

    private lateinit var tvAttendanceHeader: TextView
    private lateinit var etAttendanceDate: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView
    private lateinit var btnSaveAttendance: Button
    private lateinit var btnStartIdTapping: Button
    private lateinit var btnViewOverview: Button
    private lateinit var tvTimer: TextView
    private lateinit var tvSessionInfo: TextView

    private var assignmentId: String? = null
    private var className: String? = null
    private var subjectCode: String? = null
    private var currentStudentList = mutableListOf<Student>()
    private lateinit var attendanceAdapter: AttendanceAdapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var isPreviousDay: Boolean = false
    private var isDataModified: Boolean = false
    private var isExistingRecordLoaded: Boolean = false

    // ID Tapping Variables
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isIdTappingActive = false
    private var isSessionActive = false
    private val tappedStudents = mutableSetOf<String>()
    private val tapTimestamps = mutableMapOf<String, Long>()
    private val TAG = "RecordAttendanceActivity"

    // Timer Variables
    private var countDownTimer: CountDownTimer? = null
    private var sessionStartTime: Long = 0
    private var sessionDuration: Long = 0
    private var lateThreshold: Long = 15 * 60 * 1000 // 15 minutes in milliseconds
    private var currentSessionNumber = 1
    private var wakeLock: PowerManager.WakeLock? = null

    // Add minimum session duration (30 minutes in milliseconds)
    private val MIN_SESSION_DURATION = 30 * 60 * 1000L // 30 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_record_attendance)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeViews()
        setupNfc()
        loadAssignmentData()
        setupClickListeners()
    }

    private fun initializeViews() {
        tvAttendanceHeader = findViewById(R.id.tvAttendanceHeader)
        etAttendanceDate = findViewById(R.id.etAttendanceDate)
        recyclerView = findViewById(R.id.recyclerViewAttendance)
        btnSaveAttendance = findViewById(R.id.btnSaveAttendance)
        btnStartIdTapping = findViewById(R.id.btnStartIdTapping)
        btnViewOverview = findViewById(R.id.btnViewOverview)
        tvNoRecords = findViewById(R.id.tvNoRecords)
        tvTimer = findViewById(R.id.tvTimer)
        tvSessionInfo = findViewById(R.id.tvSessionInfo)

        tvTimer.visibility = View.GONE
        tvSessionInfo.visibility = View.GONE
        btnSaveAttendance.visibility = View.GONE // Hide save button initially
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Log.w(TAG, "NFC not available - ID tapping disabled")
            btnStartIdTapping.isEnabled = false
            btnStartIdTapping.text = "Class Session (NFC Not Available)"
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setupClickListeners() {
        etAttendanceDate.setOnClickListener { showDatePickerDialog() }

        setupIdTapping()

        btnSaveAttendance.setOnClickListener {
            if (!isPreviousDay) {
                saveAttendance()
            } else {
                Toast.makeText(this, "Session cannot be modified for a previous date.", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewOverview.setOnClickListener {
            showAttendanceManagementDialog()
        }

        updateSaveButtonState()
    }

    private fun setupIdTapping() {
        btnStartIdTapping.setOnClickListener {
            if (!isIdTappingActive) {
                showTimerDurationDialog()
            } else {
                stopIdTappingSession()
            }
        }
    }

    private fun showTimerDurationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_timer_duration, null)
        val etHours = dialogView.findViewById<EditText>(R.id.etHours)
        val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)

        // Set default values to meet minimum requirement
        etMinutes.setText("30")

        AlertDialog.Builder(this)
            .setTitle("Set Class Session Duration")
            .setView(dialogView)
            .setPositiveButton("Start Session") { dialog, _ ->
                val hours = etHours.text.toString().toIntOrNull() ?: 0
                val minutes = etMinutes.text.toString().toIntOrNull() ?: 0

                val totalMinutes = (hours * 60) + minutes
                val totalDuration = totalMinutes * 60 * 1000L

                // Check if duration meets minimum requirement
                if (totalDuration < MIN_SESSION_DURATION) {
                    Toast.makeText(this, "Session must be at least 30 minutes", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sessionDuration = totalDuration
                startIdTappingSession()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startIdTappingSession() {
        if (isPreviousDay) {
            Toast.makeText(this, "Cannot start session for previous dates", Toast.LENGTH_SHORT).show()
            return
        }

        isIdTappingActive = true
        isSessionActive = true

        // Clear data when starting new session
        clearCurrentSessionData()

        sessionStartTime = System.currentTimeMillis()

        setupBackgroundNfc()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DataDomeApp::NFCWakeLock"
        )
        wakeLock?.acquire(sessionDuration)

        btnStartIdTapping.text = "Stop Session 🔴"
        btnStartIdTapping.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

        tvTimer.visibility = View.VISIBLE
        tvSessionInfo.visibility = View.VISIBLE
        tvSessionInfo.text = "Class Session - ${getDurationText(sessionDuration)}"

        // Show save button when session starts
        btnSaveAttendance.visibility = View.VISIBLE

        startTimer()
        Toast.makeText(this, "Class Session Started! Students can tap for attendance AND recitation.", Toast.LENGTH_LONG).show()

        // Hide the "no session" message when session starts
        tvNoRecords.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun setupBackgroundNfc() {
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun getDurationText(duration: Long): String {
        val hours = duration / (60 * 60 * 1000)
        val minutes = (duration % (60 * 60 * 1000)) / (60 * 1000)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(sessionDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                val seconds = (millisUntilFinished % (60 * 1000)) / 1000

                tvTimer.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
                tvTimer.text = "00:00:00"
                stopIdTappingSession()
                markRemainingStudentsAsAbsent()
                Toast.makeText(this@RecordAttendanceActivity, "Class session timer finished!", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun stopIdTappingSession() {
        isIdTappingActive = false
        isSessionActive = false

        wakeLock?.release()
        wakeLock = null

        countDownTimer?.cancel()

        btnStartIdTapping.text = "Start Session 🟢"
        btnStartIdTapping.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

        tvTimer.visibility = View.GONE
        tvSessionInfo.visibility = View.GONE

        currentSessionNumber++

        Toast.makeText(this, "Session Stopped - ${tappedStudents.size} student interactions", Toast.LENGTH_SHORT).show()
    }

    private fun markRemainingStudentsAsAbsent() {
        if (::attendanceAdapter.isInitialized) {
            currentStudentList.forEach { student ->
                student.id?.let { studentId ->
                    if (!tappedStudents.contains(studentId)) {
                        // Only mark as ABSENT if no status is set
                        val currentStatus = attendanceAdapter.getStudentAttendanceStatus(studentId)
                        if (currentStatus.isEmpty()) {
                            attendanceAdapter.updateStudentStatus(studentId, "ABSENT")
                        }
                    }
                }
            }
            markDataModified()
        }
    }

    // Clear current session data
    private fun clearCurrentSessionData() {
        if (::attendanceAdapter.isInitialized) {
            attendanceAdapter.clearAllData()
        }

        tappedStudents.clear()
        tapTimestamps.clear()

        Log.d(TAG, "Current session data cleared - ready for fresh start")
    }

    override fun onResume() {
        super.onResume()
        if (isNfcSupported() && isIdTappingActive) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcSupported()) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (!isIdTappingActive) return

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val rfidHex = bytesToHex(tag.id)
                Log.d("ID_TAPPING", "RFID Detected: $rfidHex")
                processStudentIdTap(rfidHex)
            }
        }
    }

    private fun processStudentIdTap(rfidTag: String) {
        lifecycleScope.launch {
            try {
                val studentSnapshot = firestore.collection("students")
                    .whereEqualTo("rfidTag", rfidTag)
                    .limit(1)
                    .get()
                    .await()

                if (studentSnapshot.isEmpty) {
                    Toast.makeText(this@RecordAttendanceActivity, "❌ Student not found for this RFID tag", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val studentDoc = studentSnapshot.documents.first()
                val studentId = studentDoc.id
                val studentName = "${studentDoc.getString("firstName")} ${studentDoc.getString("lastName")}"

                if (!isStudentEnrolled(studentId)) {
                    Toast.makeText(this@RecordAttendanceActivity, "❌ $studentName not enrolled in this class", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val success = recordStudentIdTap(studentId)
                if (success) {
                    val currentStatus = if (::attendanceAdapter.isInitialized) {
                        attendanceAdapter.getStudentAttendanceStatus(studentId)
                    } else { "" }
                    val currentPoints = if (::attendanceAdapter.isInitialized) {
                        attendanceAdapter.getStudentRecitationPoints(studentId)
                    } else { 0 }

                    if (currentStatus.isEmpty()) {
                        Toast.makeText(this@RecordAttendanceActivity, "✅ $studentName marked present", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@RecordAttendanceActivity, "✅ $studentName +1 recitation point ($currentPoints total)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@RecordAttendanceActivity, "⚠️ $studentName at max points or absent/excused", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing student tap", e)
                Toast.makeText(this@RecordAttendanceActivity, "❌ Error processing tap", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun isStudentEnrolled(studentId: String): Boolean {
        return currentStudentList.any { it.id == studentId }
    }

    private suspend fun recordStudentIdTap(studentId: String): Boolean {
        val dateToSave = etAttendanceDate.text.toString()

        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty()) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val tapTimeFromStart = currentTime - sessionStartTime
        val isWithinLateThreshold = tapTimeFromStart <= lateThreshold

        tapTimestamps[studentId] = currentTime

        // Use unified system - adapter handles both attendance and recitation
        if (::attendanceAdapter.isInitialized) {
            val success = attendanceAdapter.processStudentTap(studentId, isWithinLateThreshold)

            if (success) {
                tappedStudents.add(studentId)
                Log.d(TAG, "ID Tap processed for $studentId - WithinThreshold: $isWithinLateThreshold")
            } else {
                Log.d(TAG, "ID Tap failed for $studentId - may be at max points or absent/excused")
            }

            markDataModified()
            return success
        }

        return false
    }

    private fun isNfcSupported(): Boolean {
        return nfcAdapter != null
    }

    private fun loadAssignmentData() {
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        className = intent.getStringExtra("CLASS_NAME")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")

        // Handle case where CLASS_NAME might be null
        val displayName = className ?: (subjectCode ?: "Class")
        tvAttendanceHeader.text = "Record Class Session for $displayName"
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing class assignment ID.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val today = dateFormat.format(Date())
        etAttendanceDate.setText(today)
        updateUIForDate(today)

        loadStudentList(assignmentId!!)
    }

    private fun loadStudentList(assignmentId: String) {
        firestore.collection("classAssignments").document(assignmentId).get()
            .addOnSuccessListener { doc ->
                val fetchedSubjectCode = doc.getString("subjectCode")
                val fetchedYearLevel = doc.getString("yearLevel")
                val fetchedSemester = doc.getString("semester")
                val sectionName = doc.getString("section") ?: ""

                if (fetchedSubjectCode == null || fetchedYearLevel == null || fetchedSemester == null) {
                    Toast.makeText(this, "Error: Class details missing.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                this.subjectCode = fetchedSubjectCode

                firestore.collection("students")
                    .whereEqualTo("sectionId", sectionName)
                    .whereEqualTo("yearLevel", fetchedYearLevel)
                    .whereEqualTo("status", "Admitted")
                    .get()
                    .addOnSuccessListener { studentsSnapshot ->
                        val allStudentIds = studentsSnapshot.documents.map { it.id }

                        if (allStudentIds.isEmpty()) {
                            Toast.makeText(this, "No admitted students found for this year level.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        checkStudentEnrollmentBatch(allStudentIds, fetchedSubjectCode, fetchedSemester, fetchedYearLevel)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to load student profiles by year level.", Toast.LENGTH_SHORT).show()
                        Log.e("AttendanceLoader", "Error loading students by year level: $e")
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load class assignment details.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkStudentEnrollmentBatch(
        allStudentIds: List<String>,
        subjectCode: String,
        semester: String,
        yearLevel: String
    ) {
        val finalEnrolledStudents = mutableListOf<Student>()
        tvNoRecords.text = "Checking enrollment status for ${allStudentIds.size} students..."
        tvNoRecords.visibility = View.VISIBLE

        val yearClean = yearLevel.replace(" ", "")
        val semesterCleaned = semester.replace(" ", "").replace("-", "")
        val enrollmentDocId = "${yearClean}_${semesterCleaned}_${subjectCode}"

        lifecycleScope.launch {
            try {
                val studentProfilesQuery = firestore.collection("students")
                    .whereIn(FieldPath.documentId(), allStudentIds)
                    .get().await()

                val studentMap = studentProfilesQuery.documents
                    .mapNotNull { doc -> doc.toObject<Student>()?.copy(id = doc.id) }
                    .associateBy { it.id }

                val enrollmentChecks = allStudentIds.chunked(10).flatMap { idChunk ->
                    idChunk.map { studentId ->
                        async {
                            val subjectRef = firestore.collection("students").document(studentId)
                                .collection("subjects").document(enrollmentDocId)
                            val subjectSnapshot = subjectRef.get().await()
                            if (subjectSnapshot.exists()) studentId else null
                        }
                    }
                }

                val enrolledStudentIds = enrollmentChecks.awaitAll().filterNotNull()

                enrolledStudentIds.forEach { id ->
                    studentMap[id]?.let { finalEnrolledStudents.add(it) }
                }

                currentStudentList.clear()
                currentStudentList.addAll(finalEnrolledStudents.sortedBy { it.lastName })

                if (currentStudentList.isEmpty()) {
                    tvNoRecords.text = "No students are officially enrolled in $subjectCode for $yearLevel $semester."
                    tvNoRecords.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    attendanceAdapter = AttendanceAdapter(
                        studentList = currentStudentList,
                        assignmentId = assignmentId!!,
                        isEditable = !isPreviousDay, // Allow manual excuse only for current dates
                        onDataChanged = { markDataModified() }
                    )
                    recyclerView.adapter = attendanceAdapter

                    // DON'T load existing attendance automatically - start fresh
                    initializeFreshSession()
                }

            } catch (e: Exception) {
                Log.e("AttendanceLoader", "Error validating student enrollment: ${e.message}", e)
                tvNoRecords.text = "Error loading student list. Please check dependencies/data."
                tvNoRecords.visibility = View.VISIBLE
            }
        }
    }

    private fun initializeFreshSession() {
        // Always start with empty data
        if (::attendanceAdapter.isInitialized) {
            attendanceAdapter.updateStatuses(emptyMap(), emptyMap())
            attendanceAdapter.setEditable(!isPreviousDay)
        }

        tappedStudents.clear()
        isExistingRecordLoaded = false
        isDataModified = false

        // Hide save button initially
        btnSaveAttendance.visibility = View.GONE
        updateSaveButtonState()

        // Show the student list but with empty data
        tvNoRecords.text = "No session started. Click 'Start Session' to begin recording attendance."
        tvNoRecords.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE // Make sure this is VISIBLE
    }

    private fun saveAttendance() {
        // Check if session was started and has data
        if (!isIdTappingActive && tappedStudents.isEmpty()) {
            Toast.makeText(this, "Please start a class session first and record some student interactions before saving", Toast.LENGTH_LONG).show()
            return
        }

        val dateToSave = etAttendanceDate.text.toString()

        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Petsa o Subject Code ay kulang.", Toast.LENGTH_LONG).show()
            return
        }

        // Automatically mark all unmarked students as ABSENT before saving
        markAllUnmarkedAsAbsent()

        val (attendanceMap, recitationMap) = attendanceAdapter.getAttendanceAndRecitationMaps()

        showSaveConfirmationDialog(dateToSave, attendanceMap, recitationMap)
    }

    private fun markAllUnmarkedAsAbsent() {
        if (::attendanceAdapter.isInitialized) {
            currentStudentList.forEach { student ->
                student.id?.let { studentId ->
                    val currentStatus = attendanceAdapter.getStudentAttendanceStatus(studentId)
                    if (currentStatus.isEmpty()) {
                        // Only mark as ABSENT if no status is set (not tapped and not manually excused)
                        attendanceAdapter.updateStudentStatus(studentId, "ABSENT")
                    }
                }
            }
            markDataModified()
        }
    }

    private fun showSaveConfirmationDialog(dateToSave: String, attendanceMap: Map<String, String>, recitationMap: Map<String, Int>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_save, null)
        val tvDialogMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)

        // Count students by status
        val presentCount = attendanceMap.values.count { it == "PRESENT" }
        val lateCount = attendanceMap.values.count { it == "LATE" }
        val absentCount = attendanceMap.values.count { it == "ABSENT" }
        val excusedCount = attendanceMap.values.count { it == "EXCUSED" }

        tvDialogMessage.text = "Are you sure you want to save this class session?\n\n" +
                "Summary:\n" +
                "✅ Present: $presentCount\n" +
                "⏰ Late: $lateCount\n" +
                "❌ Absent: $absentCount\n" +
                "📝 Excused: $excusedCount\n\n" +
                "This action cannot be undone."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            performSaveToDatabase(dateToSave, attendanceMap, recitationMap)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun performSaveToDatabase(dateToSave: String, attendanceMap: Map<String, String>, recitationMap: Map<String, Int>) {
        val sessionNum = currentSessionNumber
        val recordId = "${assignmentId}_${dateToSave}_session_${sessionNum}"

        firestore.document("systemSettings/currentTerm")
            .get()
            .addOnSuccessListener { termDoc ->
                val academicTerm = termDoc.getString("academicTerm") ?: ""
                val academicYear = termDoc.getString("academicYear") ?: ""
                val semester = termDoc.getString("semester") ?: ""

                val dailyRecord = hashMapOf(
                    "assignmentId" to assignmentId!!,
                    "subjectCode" to subjectCode!!,
                    "date" to dateToSave,
                    "sessionType" to "CLASS_SESSION",
                    "sessionNumber" to sessionNum,
                    "displaySession" to "Class Session $sessionNum",
                    "statuses" to attendanceMap,
                    "recitationPoints" to recitationMap,
                    "tapTimestamps" to tapTimestamps,
                    "sessionStartTime" to sessionStartTime,
                    "sessionDuration" to sessionDuration,
                    "lateThreshold" to lateThreshold,
                    "academicTerm" to academicTerm,
                    "academicYear" to academicYear,
                    "semester" to semester
                )

                firestore.collection(ATTENDANCE_COLLECTION).document(recordId)
                    .set(dailyRecord)
                    .addOnSuccessListener {
                        Log.i("AttendanceSaver", "Class session saved successfully. Document ID: $recordId")

                        // Clear ALL data after successful save
                        clearDataAfterSave()

                        Toast.makeText(this, "✅ Class Session $sessionNum successfully saved! Start a new session to record more data.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("AttendanceSaver", "Save FAILED: ${e.message}", e)
                        Toast.makeText(this, "Failed to save session: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceSaver", "Failed to fetch current term: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch current term info.", Toast.LENGTH_LONG).show()
            }
    }

    private fun clearDataAfterSave() {
        // Clear current session data
        if (::attendanceAdapter.isInitialized) {
            attendanceAdapter.clearAllData()
        }

        tappedStudents.clear()
        tapTimestamps.clear()

        // Reset session state completely
        isIdTappingActive = false
        isSessionActive = false
        isDataModified = false
        isExistingRecordLoaded = false

        // Update UI to reflect fresh state
        btnStartIdTapping.text = "Start Session 🟢"
        btnStartIdTapping.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

        // Hide save button after saving
        btnSaveAttendance.visibility = View.GONE

        tvTimer.visibility = View.GONE
        tvSessionInfo.visibility = View.GONE

        // Stop timer if running
        countDownTimer?.cancel()

        // Release wake lock
        wakeLock?.release()
        wakeLock = null

        // Update save button state
        updateSaveButtonState()

        // Show message that you need to start a new session - but keep the layout visible
        tvNoRecords.text = "Session saved! Click 'Start Session' to begin a new class session."
        tvNoRecords.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE // Keep this VISIBLE

        Log.d(TAG, "Data cleared after successful save - ready for new session")
    }

    private fun showAttendanceManagementDialog() {
        val date = etAttendanceDate.text.toString()
        if (date.isEmpty()) {
            Toast.makeText(this, "Pumili muna ng petsa", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection(ATTENDANCE_COLLECTION)
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingRecords = snapshot.documents.mapNotNull { doc ->
                    val displaySession = doc.getString("displaySession")
                    val sessionNumber = doc.getLong("sessionNumber")?.toInt()
                    if (displaySession != null && sessionNumber != null) {
                        Pair(displaySession, sessionNumber)
                    } else {
                        null
                    }
                }

                val options = mutableListOf<String>()
                existingRecords.forEach { (displaySession, _) ->
                    options.add("Tingnan: $displaySession")
                }
                options.add("➕ Mag-set ng Bagong Session")

                AlertDialog.Builder(this)
                    .setTitle("Class Sessions para sa $date")
                    .setItems(options.toTypedArray()) { dialog, which ->
                        if (which < existingRecords.size) {
                            val (displaySession, sessionNumber) = existingRecords[which]
                            currentSessionNumber = sessionNumber
                            loadExistingAttendanceForViewing(displaySession)
                        } else {
                            currentSessionNumber = 1
                            initializeFreshSession()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceManagement", "Error fetching existing records: $e")
                Toast.makeText(this, "Failed to load existing session records.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadExistingAttendanceForViewing(displaySession: String) {
        val date = etAttendanceDate.text.toString()

        firestore.collection(ATTENDANCE_COLLECTION)
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("date", date)
            .whereEqualTo("sessionNumber", currentSessionNumber)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val document = snapshot.documents.first()
                val existingAttendance = document.get("statuses") as? Map<String, String> ?: emptyMap()
                val existingRecitationLong = document.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
                val existingRecitation = existingRecitationLong.mapValues { it.value.toInt() }

                if (::attendanceAdapter.isInitialized) {
                    attendanceAdapter.updateStatuses(existingAttendance, existingRecitation)
                    attendanceAdapter.setEditable(false) // View only
                }

                tvNoRecords.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                isExistingRecordLoaded = true
                isDataModified = false
                updateSaveButtonState()

                Toast.makeText(this, "Viewing: $displaySession", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("ATTENDANCE_DEBUG", "Failed to get existing session record.", e)
                Toast.makeText(this, "Error loading session record", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            android.R.style.Theme_Holo_Light_Dialog_MinWidth,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val newDate = dateFormat.format(selectedDate.time)

                etAttendanceDate.setText(newDate)
                updateUIForDate(newDate)

                // Always start fresh when date changes
                initializeFreshSession()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateUIForDate(dateString: String) {
        try {
            val selectedDate = dateFormat.parse(dateString)!!
            val today = dateFormat.parse(dateFormat.format(Date()))!!

            isPreviousDay = selectedDate.before(today)
            btnSaveAttendance.visibility = if (isPreviousDay) View.GONE else View.VISIBLE

            if (isPreviousDay && isIdTappingActive) {
                stopIdTappingSession()
            }
            btnStartIdTapping.isEnabled = !isPreviousDay

            updateSaveButtonState()

        } catch (e: Exception) {
            Log.e("AttendanceDate", "Date parsing error for $dateString: $e")
            isPreviousDay = false
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

    private fun markDataModified() {
        if (!isPreviousDay && !isDataModified) {
            isDataModified = true
            updateSaveButtonState()
            Log.d("RecordAttendance", "Data modified. Unsaved changes detected.")
        }
    }

    private fun updateSaveButtonState() {
        if (isDataModified && !isPreviousDay) {
            btnSaveAttendance.text = "SAVE SESSION 💾"
            btnSaveAttendance.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            btnSaveAttendance.text = "Save Session"
            btnSaveAttendance.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        wakeLock?.release()
    }
}
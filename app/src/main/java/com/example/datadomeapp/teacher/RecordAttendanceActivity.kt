package com.example.datadomeapp.teacher

import android.app.*
import android.content.*
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

    // Broadcast Receiver for timer finished
    private val timerFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "TIMER_FINISHED" -> {
                    stopIdTappingSession()
                    markRemainingStudentsAsAbsent()
                    Toast.makeText(this@RecordAttendanceActivity, "Class session timer finished!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_record_attendance)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeViews()
        setupNfc()
        loadAssignmentData()
        setupClickListeners()
        registerReceivers()
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

    private fun registerReceivers() {
        val intentFilter = IntentFilter().apply {
            // Add your specific broadcast actions here
            // Example: addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            // Example: addAction("YOUR_CUSTOM_ACTION")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Handle broadcast messages here
                when (intent?.action) {
                    // Handle different actions
                    // Example:
                    // ConnectivityManager.CONNECTIVITY_ACTION -> {
                    //     handleNetworkChange()
                    // }
                    // "YOUR_CUSTOM_ACTION" -> {
                    //     handleCustomAction()
                    // }
                }
            }
        }

        // Fixed broadcast receiver registration
        registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
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

        AlertDialog.Builder(this)
            .setTitle("Set Class Session Duration")
            .setView(dialogView)
            .setPositiveButton("Start Session") { dialog, _ ->
                val hours = etHours.text.toString().toIntOrNull() ?: 0
                val minutes = etMinutes.text.toString().toIntOrNull() ?: 0

                if (hours == 0 && minutes == 0) {
                    Toast.makeText(this, "Please set a valid duration", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val totalMinutes = (hours * 60) + minutes
                sessionDuration = totalMinutes * 60 * 1000L
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

        startTimer()
        Toast.makeText(this, "Class Session Started! Students can tap for attendance AND recitation.", Toast.LENGTH_LONG).show()
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

                // FIX: Instead of using broadcast, call the method directly
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
        loadExistingAttendance()
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

        // Auto-save logic removed - wait for manual save only
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
        tvAttendanceHeader.text = "Record Class Session for $className"
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
                    .mapNotNull { doc -> doc.toObject(Student::class.java)?.copy(id = doc.id) }
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
                        isEditable = !isPreviousDay,
                        onDataChanged = { markDataModified() }
                    )
                    recyclerView.adapter = attendanceAdapter

                    loadExistingAttendance()
                }

            } catch (e: Exception) {
                Log.e("AttendanceLoader", "Error validating student enrollment: ${e.message}", e)
                tvNoRecords.text = "Error loading student list. Please check dependencies/data."
                tvNoRecords.visibility = View.VISIBLE
            }
        }
    }

    private fun loadExistingAttendance() {
        val date = etAttendanceDate.text.toString()

        if (date.isEmpty()) {
            tvNoRecords.text = "Please select a date first."
            tvNoRecords.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        updateUIForDate(date)
        btnSaveAttendance.visibility = if (isPreviousDay) View.GONE else View.VISIBLE

        // Load existing record for current session number
        firestore.collection(ATTENDANCE_COLLECTION)
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("date", date)
            .whereEqualTo("sessionNumber", currentSessionNumber)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // No existing record - start fresh
                    if (::attendanceAdapter.isInitialized) {
                        attendanceAdapter.updateStatuses(emptyMap(), emptyMap())
                        attendanceAdapter.setEditable(!isPreviousDay)
                    }

                    tappedStudents.clear()

                    if (isPreviousDay) {
                        tvNoRecords.text = "No session records found for this date."
                        tvNoRecords.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvNoRecords.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                    isExistingRecordLoaded = false
                } else {
                    // Load existing record
                    val document = snapshot.documents.first()
                    val existingAttendance = document.get("statuses") as? Map<String, String> ?: emptyMap()
                    val existingRecitationLong = document.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
                    val existingRecitation = existingRecitationLong.mapValues { it.value.toInt() }

                    if (::attendanceAdapter.isInitialized) {
                        attendanceAdapter.updateStatuses(existingAttendance, existingRecitation)
                        attendanceAdapter.setEditable(!isPreviousDay)
                    }

                    // Update tapped students from loaded data
                    tappedStudents.clear()
                    tappedStudents.addAll(existingAttendance.keys)
                    tappedStudents.addAll(existingRecitation.keys)

                    tvNoRecords.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    isExistingRecordLoaded = true
                }
                isDataModified = false
                updateSaveButtonState()
            }
            .addOnFailureListener { e ->
                Log.e("ATTENDANCE_DEBUG", "Failed to get existing session record.", e)
                tvNoRecords.text = "Error loading records. Please check connection."
                tvNoRecords.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    private fun saveAttendance() {
        val dateToSave = etAttendanceDate.text.toString()

        if (dateToSave.isEmpty() || subjectCode.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Petsa o Subject Code ay kulang.", Toast.LENGTH_LONG).show()
            return
        }

        val (attendanceMap, recitationMap) = attendanceAdapter.getAttendanceAndRecitationMaps()

        // Optional: Check for unmarked students
        val unmarkedStudents = currentStudentList.filter { student ->
            student.id?.let { studentId ->
                attendanceMap[studentId].isNullOrEmpty()
            } ?: true
        }

        if (unmarkedStudents.isNotEmpty()) {
            val count = unmarkedStudents.size
            val dialog = AlertDialog.Builder(this)
                .setTitle("May Hindi Pa Na-markahang Estudyante")
                .setMessage("May $count estudyante na walang attendance status. Gusto mo pa ring i-save?")
                .setPositiveButton("Oo, I-save Pa Rin") { dialog, _ ->
                    performSaveToDatabase(dateToSave, attendanceMap, recitationMap)
                    dialog.dismiss()
                }
                .setNegativeButton("Hindi, Ayusin Muna") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            return
        }

        performSaveToDatabase(dateToSave, attendanceMap, recitationMap)
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

                        isDataModified = false
                        isExistingRecordLoaded = true
                        updateSaveButtonState()

                        Toast.makeText(this, "✅ Class Session $sessionNum successfully saved!", Toast.LENGTH_LONG).show()
                        updateUIForDate(dateToSave)
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

                            isDataModified = false
                            isExistingRecordLoaded = true
                            updateSaveButtonState()

                            loadExistingAttendance()
                        } else {
                            currentSessionNumber = 1
                            loadExistingAttendance()
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

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val newDate = dateFormat.format(selectedDate.time)

                etAttendanceDate.setText(newDate)
                updateUIForDate(newDate)

                isDataModified = false
                isExistingRecordLoaded = false
                updateSaveButtonState()

                loadExistingAttendance()
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
        try {
            unregisterReceiver(timerFinishedReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
        }
    }
}
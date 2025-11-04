package com.example.datadomeapp.teacher

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldPath
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

// Model na ginagamit sa RecyclerView Adapter
data class StudentMonitoringData(
    val studentUid: String,
    var studentName: String,
    var status: String, // E.g., IN_PROGRESS, COMPLETED, TIME_EXPIRED, UNATTEMPTED_TIME_EXPIRED, ACCESS_REVOKED
    var score: Int,
    var cheatCount: Int,
    var lastUpdate: Long
)

class QuizMonitoringViewModel(
    private val quizId: String,
    private val assignmentNo: String,
    private val quizEndTime: Long // Quiz End Time
) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _monitoringData = MutableLiveData<List<StudentMonitoringData>>()
    val monitoringData: LiveData<List<StudentMonitoringData>> = _monitoringData

    private val studentList = mutableMapOf<String, StudentMonitoringData>()

    private var quizResultListener: ListenerRegistration? = null
    private var isStudentNamesLoaded = false

    private val authUidToStudentId = mutableMapOf<String, String>()

    init {
        loadEnrolledStudents()
    }

    override fun onCleared() {
        super.onCleared()
        quizResultListener?.remove()
    }

    // --- STEP 1: LOAD ENROLLED STUDENT IDS (DDS-xxxx) ---
    private fun loadEnrolledStudents() {
        // ... (Keep the original loadEnrolledStudents logic here)
        firestore.collectionGroup("subjects")
            .whereEqualTo("assignmentNo", assignmentNo)
            .get()
            .addOnSuccessListener { subjectsSnapshot ->

                val enrolledStudentIds = mutableSetOf<String>()

                for (doc in subjectsSnapshot.documents) {
                    val studentRef = doc.reference.parent?.parent
                    studentRef?.id?.let {
                        if (studentRef.parent?.id == "students") {
                            enrolledStudentIds.add(it) // Ito ang DDS-xxxx
                        }
                    }
                }

                val finalStudentIds = enrolledStudentIds.toList()

                if (finalStudentIds.isEmpty()) {
                    _monitoringData.value = emptyList()
                    return@addOnSuccessListener
                }

                // I-fetch ang Auth UIDs at Pangalan gamit ang DDS-xxxx
                fetchStudentAuthUids(finalStudentIds)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("QuizMonitorVM", "Failed to load enrolled students: $e")
                _monitoringData.value = emptyList()
            }
    }

    // --- STEP 2: FETCH AUTH UID AT PROFILE USING DDS-xxxx ---
    private fun fetchStudentAuthUids(studentIds: List<String>) {
        // ... (Keep the original fetchStudentAuthUids logic here)
        if (studentIds.isEmpty()) return

        val idChunks = studentIds.chunked(10)
        val fetchJobs = mutableListOf<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>>()

        idChunks.forEach { chunk ->
            val query = firestore.collection("students")
                .whereIn(FieldPath.documentId(), chunk) // Query by DDS-xxxx
            fetchJobs.add(query.get())
        }

        Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(fetchJobs)
            .addOnSuccessListener { snapshots ->
                val authUids = mutableListOf<String>()
                val studentProfiles = mutableMapOf<String, String>()

                snapshots.forEach { querySnapshot ->
                    querySnapshot.documents.forEach { userDoc ->
                        val studentIdDds = userDoc.id
                        val authUid = userDoc.getString("userUid")

                        if (authUid != null) {
                            authUids.add(authUid)
                            authUidToStudentId[authUid] = studentIdDds

                            val firstName = userDoc.getString("firstName") ?: ""
                            val middleName = userDoc.getString("middleName") ?: ""
                            val lastName = userDoc.getString("lastName") ?: ""
                            val middleInitial = if (middleName.isNotEmpty()) "${middleName.first()}. " else ""
                            val fullName = "$lastName, $firstName $middleInitial".trim()

                            studentProfiles[authUid] = fullName.ifEmpty { "Student ID: $studentIdDds" }
                        }
                    }
                }

                initializeMonitoringWithAuthUids(authUids, studentProfiles)

            }
            .addOnFailureListener { e ->
                android.util.Log.e("QuizMonitorVM", "Error fetching student profiles: ${e.message}")
                initializeMonitoringWithAuthUids(emptyList(), emptyMap())
            }
    }

    // --- STEP 3: INITIALIZE MONITORING WITH AUTH UID (With Time Check) ---
    private fun initializeMonitoringWithAuthUids(authUids: List<String>, studentProfiles: Map<String, String>) {

        val currentTime = System.currentTimeMillis()
        val isQuizPeriodFinished = quizEndTime > 0L && currentTime > quizEndTime

        val initialStatus = if (isQuizPeriodFinished) {
            "UNATTEMPTED_TIME_EXPIRED"
        } else {
            "NOT_STARTED"
        }

        authUids.forEach { uid ->
            val fullName = studentProfiles[uid] ?: "Unknown Student"
            studentList[uid] = StudentMonitoringData(
                studentUid = uid,
                studentName = fullName,
                status = initialStatus,
                score = 0,
                cheatCount = 0,
                lastUpdate = 0L
            )
        }

        isStudentNamesLoaded = true
        updateMonitoringData()
        setupQuizResultsListener()
    }


    /**
     * Sineset up ang real-time listener para sa quiz results ng bawat student.
     */
    private fun setupQuizResultsListener() {
        val currentTime = System.currentTimeMillis()
        val isQuizPeriodFinished = quizEndTime > 0L && currentTime > quizEndTime

        quizResultListener = firestore.collection("quizResults")
            .whereEqualTo("quizId", quizId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val monitoredStudentUid = doc.getString("studentId") ?: continue

                        if (!studentList.containsKey(monitoredStudentUid)) continue

                        val data = studentList[monitoredStudentUid]!!

                        var newStatus = doc.getString("status") ?: "N/A"

                        // CRITICAL FIX: Kung ang oras ay tapos na, huwag hayaang maging NOT_STARTED!
                        if (isQuizPeriodFinished && newStatus == "NOT_STARTED") {
                            newStatus = "UNATTEMPTED_TIME_EXPIRED"
                        }

                        data.status = newStatus
                        data.score = doc.getLong("score")?.toInt() ?: 0
                        data.cheatCount = doc.getLong("cheatCount")?.toInt() ?: 0
                        data.lastUpdate = doc.getLong("timestamp") ?: System.currentTimeMillis()

                        studentList[monitoredStudentUid] = data
                    }

                    updateMonitoringData()
                }
            }
    }

    /**
     * Ina-update ang LiveData na ginagamit ng Activity/Adapter.
     */
    private fun updateMonitoringData() {
        val sortedList = studentList.values.toMutableList().sortedWith(
            compareByDescending<StudentMonitoringData> { getStatusPriority(it.status) }
                .thenByDescending { it.lastUpdate }
                .thenBy { it.studentName }
        )
        _monitoringData.value = sortedList
    }

    // UPDATED STATUS PRIORITY
    private fun getStatusPriority(status: String): Int {
        return when (status) {
            "CHEATING" -> 6 // Highest priority
            "IN_PROGRESS" -> 5
            "COMPLETED" -> 4
            "TIME_EXPIRED", "UNATTEMPTED_TIME_EXPIRED" -> 3
            "RETAKE_GRANTED" -> 2
            "ACCESS_REVOKED" -> 1
            else -> 0 // NOT_STARTED
        }
    }

    /**
     * Performs a bulk action (RETAKE, REOPEN, REVOKE) on selected students.
     */
    fun performBulkAction(action: String, studentUids: List<String>, startTime: Long, endTime: Long) {
        if (studentUids.isEmpty()) return

        studentUids.forEach { studentUid ->
            val documentRef = firestore.collection("quizResults").document("${quizId}_$studentUid")

            val updateData = when (action) {
                "RETAKE", "REOPEN" -> mapOf(
                    "status" to "RETAKE_GRANTED",
                    "timestamp" to System.currentTimeMillis(),
                    "score" to 0, // Reset score
                    "cheatCount" to 0, // Reset cheat count
                    "retakeDeadline" to endTime // Gamitin ang endTime ng access window
                )
                "REVOKE" -> mapOf(
                    "status" to "ACCESS_REVOKED", // Bagong status para sa pag-block
                    "timestamp" to System.currentTimeMillis(),
                    "retakeDeadline" to 0L // Alisin ang retake deadline
                )
                else -> return
            }

            documentRef.update(updateData)
                .addOnSuccessListener {
                    android.util.Log.d("QuizMonitorVM", "$action command sent for $studentUid until $endTime")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("QuizMonitorVM", "Error performing $action: ${e.message}")
                }
        }
    }

    fun getDetailedCheatLog(studentUid: String, callback: (List<String>) -> Unit) {
        firestore.collection("quizResults").document("${quizId}_$studentUid")
            .get()
            .addOnSuccessListener { doc ->
                val log = doc.get("cheatLog") as? List<String> ?: emptyList()
                callback(log)
            }
            .addOnFailureListener {
                callback(listOf("Error fetching detailed cheat log: ${it.message}"))
            }
    }
}

class QuizMonitoringViewModelFactory(
    private val quizId: String,
    private val assignmentId: String,
    private val quizEndTime: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuizMonitoringViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuizMonitoringViewModel(quizId, assignmentId, quizEndTime) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
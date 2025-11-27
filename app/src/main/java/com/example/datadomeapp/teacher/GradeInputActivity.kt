package com.example.datadomeapp.teacher

import android.app.ProgressDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student
import com.example.datadomeapp.utils.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- DATA CLASSES ---
data class ActivityScoreData(
    val title: String,
    val score50Base: Double,
    val rawScore: Double,
    val maxPoints: Double,
    val activityId: String,
    val activityType: String
)

data class DetailedScores(
    val attendanceDetails: Pair<Int, Int>,
    val recitationDetails: Int,
    val quizScores: List<ActivityScoreData>,
    val examScores: List<ActivityScoreData>,
    val assignmentScores: List<ActivityScoreData>
)

data class ActivityMetadata(
    val type: String,
    val maxPoints: Double,
    val title: String
)

interface OnStudentClickListener {
    fun onStudentClicked(student: Student)
}

class GradeInputActivity : AppCompatActivity(), OnStudentClickListener {

    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance()

    private var studentUidToDocIdMap: Map<String, String> = emptyMap()
    private var activityMetadata: Map<String, ActivityMetadata> = emptyMap()

    private lateinit var tvGradeTitle: TextView
    private lateinit var tvLoadingStatus: TextView
    private lateinit var recyclerViewGrades: RecyclerView
    private lateinit var btnPublishGrades: Button
    private lateinit var gradeAdapter: GradeInputAdapter

    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null
    private var gradingPeriod: String? = null
    private var areGradesPublished: Boolean = false

    // Enhanced caching system
    private val metadataCache = MetadataCache()
    private val studentScoresCache = StudentScoresCache()

    // Batch operation helper
    private suspend fun <T> processInBatches(
        items: List<T>,
        batchSize: Int = 10,
        processor: suspend (List<T>) -> Unit
    ) {
        val batches = items.chunked(batchSize)
        for (batch in batches) {
            processor(batch)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_grade_input)

        // --- Intent Data Retrieval ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")
        className = intent.getStringExtra("CLASS_NAME")
        gradingPeriod = intent.getStringExtra("GRADING_PERIOD")

        Log.d("GradeDebug", "Activity Started. AssignmentID: $assignmentId, Period: $gradingPeriod")

        tvGradeTitle = findViewById(R.id.tvGradeTitle)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        recyclerViewGrades = findViewById(R.id.recyclerViewGrades)
        btnPublishGrades = findViewById(R.id.btnSaveGrades)
        btnPublishGrades.text = "Publish Grades"
        btnPublishGrades.visibility = View.GONE

        recyclerViewGrades.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Log.e("GradeDebug", "Missing Intent Data! Cannot proceed.")
            Toast.makeText(this, "Error: Missing grade context.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvGradeTitle.text = "$gradingPeriod Grades\n$className ($subjectCode)"

        lifecycleScope.launch {
            checkIfGradesPublished()
        }

        btnPublishGrades.setOnClickListener {
            if (areGradesPublished) {
                Toast.makeText(this, "Grades are already published and cannot be modified.", Toast.LENGTH_SHORT).show()
            } else {
                showPublishConfirmationDialog()
            }
        }

        // Pre-load metadata for faster access
        lifecycleScope.launch {
            loadActivityMetadata()
            loadGradingData()
        }
    }

    private suspend fun checkIfGradesPublished() {
        try {
            // Check if any grade exists with isPublished = true for this class
            val publishedGradesQuery = firestore.collection("finalStudentGrades")
                .whereEqualTo("assignmentId", assignmentId!!)
                .whereEqualTo("gradingPeriod", gradingPeriod!!)
                .whereEqualTo("isPublished", true)
                .limit(1)
                .get()
                .await()

            areGradesPublished = !publishedGradesQuery.isEmpty
            Log.d("GradeDebug", "Grades published status: $areGradesPublished")

            if (areGradesPublished) {
                runOnUiThread {
                    btnPublishGrades.text = "Grades Published"
                    btnPublishGrades.isEnabled = false
                    btnPublishGrades.alpha = 0.6f
                    Toast.makeText(this, "Grades are already published and locked.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error checking published status: ${e.message}")
        }
    }

    private fun showPublishConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Publish Grades")
            .setMessage("Are you sure you want to publish these grades? Once published, grades cannot be edited.")
            .setPositiveButton("Publish") { dialog, _ ->
                publishFinalGrades()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // OPTIMIZED: Faster metadata loading with caching
    private suspend fun loadActivityMetadata(): Map<String, ActivityMetadata> {
        // Use cache if valid
        if (metadataCache.isCacheValid()) {
            Log.d("GradeDebug", "Using cached activity metadata")
            return metadataCache.getCache()
        }

        return try {
            val metadata = fetchActivityMetadata()
            metadataCache.updateCache(metadata)
            Log.d("GradeDebug", "Loaded ${metadata.size} activity metadata items")
            metadata
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error loading metadata: ${e.message}")
            emptyMap()
        }
    }

    // OPTIMIZED: Parallel metadata fetching
    private suspend fun fetchActivityMetadata(): Map<String, ActivityMetadata> {
        val metadata = mutableMapOf<String, ActivityMetadata>()

        // Fetch quizzes and assignments in parallel
        val quizzesDeferred = lifecycleScope.async { fetchQuizMetadata() }
        val assignmentsDeferred = lifecycleScope.async { fetchAssignmentMetadata() }

        val quizzes = quizzesDeferred.await()
        val assignments = assignmentsDeferred.await()

        metadata.putAll(quizzes)
        metadata.putAll(assignments)

        return metadata
    }

    private suspend fun fetchQuizMetadata(): Map<String, ActivityMetadata> {
        return try {
            val quizzesSnapshot = realtimeDb.reference.child("quizzes")
                .orderByChild("assignmentId").equalTo(assignmentId!!).get().await()

            quizzesSnapshot.children.mapNotNull { snapshot ->
                val id = snapshot.key ?: return@mapNotNull null
                val map = snapshot.value as? Map<*, *> ?: return@mapNotNull null

                val activityPeriod = map["academicTerm"] as? String
                if (activityPeriod != gradingPeriod) return@mapNotNull null

                val title = map["title"] as? String ?: "Quiz/Exam ($id)"
                val rawType = map["quizType"] as? String ?: "Quiz"
                val type = when (rawType.lowercase()) {
                    "exam" -> "Exam"
                    else -> "Quiz"
                }

                val questionsCount = calculateQuestionsCount(map["questions"])
                val maxPoints = questionsCount.toDouble()

                if (maxPoints > 0) id to ActivityMetadata(type, maxPoints, title) else null
            }.toMap()
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error fetching quiz metadata: ${e.message}")
            emptyMap()
        }
    }

    private suspend fun fetchAssignmentMetadata(): Map<String, ActivityMetadata> {
        return try {
            val assignmentsSnapshot = firestore.collection("assignments")
                .whereEqualTo("classId", assignmentId!!)
                .whereEqualTo("academicTerm", gradingPeriod!!)
                .get().await()

            assignmentsSnapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val title = doc.getString("title") ?: "Assignment ($id)"
                val rawType = doc.getString("type") ?: "assignment"
                val maxPoints = doc.getDouble("maxPoints") ?: doc.getDouble("totalPoints") ?: 100.0

                val type = when (rawType.lowercase()) {
                    "assignment", "project", "homework", "activity" -> "Assignment"
                    else -> ""
                }

                if (type.isNotBlank() && maxPoints > 0.0) id to ActivityMetadata(type, maxPoints, title) else null
            }.toMap()
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error fetching assignment metadata: ${e.message}")
            emptyMap()
        }
    }

    private fun calculateQuestionsCount(rawQuestions: Any?): Int {
        var count = 0
        when (rawQuestions) {
            is List<*> -> {
                rawQuestions.forEach { question ->
                    if (question is Map<*, *>) {
                        count += when (question["type"] as? String) {
                            "MATCHING" -> (question["options"] as? List<*>)?.size ?: 0
                            else -> 1
                        }
                    }
                }
            }
            is Map<*, *> -> {
                count = rawQuestions.keys.count { key -> key.toString().toIntOrNull() != null }
                // Additional logic for matching type
                rawQuestions.values.forEach { question ->
                    if (question is Map<*, *> && question["type"] == "MATCHING") {
                        val matchingOptions = question["options"] as? List<*>
                        count += (matchingOptions?.size ?: 1) - 1
                    }
                }
            }
        }
        return count
    }

    // OPTIMIZED: Show dialog with cached data
    override fun onStudentClicked(student: Student) {
        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Cannot fetch details due to missing context.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check cache first for instant loading
        studentScoresCache.cleanupExpired()
        val cachedScores = studentScoresCache.get(student.id)
        if (cachedScores != null) {
            showEditableScoreDialogWithFixedTotal(student, cachedScores)
        } else {
            showDetailedScoreDialog(student)
        }
    }

    private fun showDetailedScoreDialog(student: Student) {
        val dialog = ProgressDialog(this).apply {
            setMessage("Fetching detailed scores for ${student.lastName}...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val detailedScores = fetchDetailedScores(student.id)
                studentScoresCache.put(student.id, detailedScores)
                dialog.dismiss()
                showEditableScoreDialogWithFixedTotal(student, detailedScores)
            } catch (e: Exception) {
                dialog.dismiss()
                Log.e("ScoreDetail", "Error fetching detailed scores: ${e.message}", e)
                Toast.makeText(this@GradeInputActivity, "Error fetching details: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // OPTIMIZED: Faster detailed scores fetch using pre-loaded metadata
    private suspend fun fetchDetailedScores(studentDocId: String): DetailedScores {
        activityMetadata = metadataCache.getCache()

        val studentUserUid = studentUidToDocIdMap.entries.firstOrNull { it.value == studentDocId }?.key
            ?: return DetailedScores(Pair(0,0), 0, emptyList(), emptyList(), emptyList())

        // CHECK FOR MANUAL OVERRIDES FIRST
        val attendanceDetails = try {
            val overrideDoc = firestore.collection("manualGradeOverrides")
                .document("${studentDocId}_${assignmentId!!}_${gradingPeriod!!}")
                .get().await()

            if (overrideDoc.exists()) {
                // Use manual override values
                val present = overrideDoc.getLong("attendancePresent")?.toInt() ?: 0
                val total = overrideDoc.getLong("attendanceTotal")?.toInt() ?: 1
                Pair(present, total)
            } else {
                // Fetch from original attendance records
                fetchOriginalAttendance(studentDocId)
            }
        } catch (e: Exception) {
            fetchOriginalAttendance(studentDocId)
        }

        val recitationDetails = try {
            val overrideDoc = firestore.collection("manualGradeOverrides")
                .document("${studentDocId}_${assignmentId!!}_${gradingPeriod!!}")
                .get().await()

            if (overrideDoc.exists()) {
                overrideDoc.getLong("recitationPoints")?.toInt() ?: 0
            } else {
                fetchOriginalRecitation(studentDocId)
            }
        } catch (e: Exception) {
            fetchOriginalRecitation(studentDocId)
        }

        // Fetch activity scores
        val studentBestActivityScores = fetchStudentActivityScoresWithOverrides(studentUserUid, studentDocId)

        // Categorize scores
        val quizList = studentBestActivityScores.filter { it.activityType == "Quiz" }
        val examList = studentBestActivityScores.filter { it.activityType == "Exam" }
        val assignmentList = studentBestActivityScores.filter { it.activityType == "Assignment" }

        return DetailedScores(
            attendanceDetails = attendanceDetails,
            recitationDetails = recitationDetails,
            quizScores = quizList,
            examScores = examList,
            assignmentScores = assignmentList
        )
    }

    private suspend fun fetchOriginalAttendance(studentDocId: String): Pair<Int, Int> {
        var presentCount = 0
        var totalClassSessions = 0

        val termDoc = firestore.document("systemSettings/currentTerm").get().await()
        val semester = termDoc.getString("semester") ?: ""

        val attendanceSnapshot = firestore.collection("dailyAttendanceRecords")
            .whereEqualTo("assignmentId", assignmentId!!)
            .whereEqualTo("academicTerm", gradingPeriod!!)
            .whereEqualTo("semester", semester)
            .get().await()

        attendanceSnapshot.documents.forEach { doc ->
            totalClassSessions++
            val statuses = doc.get("statuses") as? Map<String, String> ?: emptyMap()

            // 🟢 FIX: Directly use studentDocId - NO NEED FOR UID CONVERSION
            if (statuses[studentDocId] == "PRESENT") presentCount++
        }

        return Pair(presentCount, totalClassSessions)
    }

    private suspend fun fetchOriginalRecitation(studentDocId: String): Int {
        var totalRecitationPoints = 0

        val termDoc = firestore.document("systemSettings/currentTerm").get().await()
        val semester = termDoc.getString("semester") ?: ""

        val attendanceSnapshot = firestore.collection("dailyAttendanceRecords")
            .whereEqualTo("assignmentId", assignmentId!!)
            .whereEqualTo("academicTerm", gradingPeriod!!)
            .whereEqualTo("semester", semester)
            .get().await()

        attendanceSnapshot.documents.forEach { doc ->
            // 🟢 FIX: Directly use studentDocId for recitation points
            val recitationLong = doc.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
            val recitationPoints = recitationLong.mapValues { it.value.toInt() }
            totalRecitationPoints += recitationPoints[studentDocId] ?: 0
        }

        return totalRecitationPoints
    }

    // OPTIMIZED: Faster student scores fetching with batching
    private suspend fun fetchStudentActivityScoresWithOverrides(
        studentUserUid: String,
        studentDocId: String
    ): List<ActivityScoreData> {
        val studentScores = mutableListOf<ActivityScoreData>()

        // Group activity IDs by type for batch querying
        val quizIds = activityMetadata.filter { it.value.type == "Quiz" || it.value.type == "Exam" }.keys.toList()
        val assignmentIds = activityMetadata.filter { it.value.type == "Assignment" }.keys.toList()

        // Fetch scores in parallel
        val quizScoresDeferred = lifecycleScope.async {
            if (quizIds.isNotEmpty()) {
                fetchQuizScoresInBatches(quizIds, studentUserUid)
            } else {
                emptyList()
            }
        }

        val assignmentScoresDeferred = lifecycleScope.async {
            if (assignmentIds.isNotEmpty()) {
                fetchAssignmentScoresInBatches(assignmentIds, studentUserUid)
            } else {
                emptyList()
            }
        }

        studentScores.addAll(quizScoresDeferred.await())
        studentScores.addAll(assignmentScoresDeferred.await())

        // Add missing activities with 50% base score
        activityMetadata.forEach { (activityId, metadata) ->
            val exists = studentScores.any { it.activityId == activityId }
            if (!exists) {
                studentScores.add(
                    ActivityScoreData(
                        title = metadata.title,
                        score50Base = 50.0,
                        rawScore = 0.0,
                        maxPoints = metadata.maxPoints,
                        activityId = activityId,
                        activityType = metadata.type
                    )
                )
            }
        }

        return studentScores
    }

    private suspend fun fetchQuizScoresInBatches(quizIds: List<String>, studentUserUid: String): List<ActivityScoreData> {
        val allScores = mutableListOf<ActivityScoreData>()

        processInBatches(quizIds, 10) { batch ->
            try {
                val scores = firestore.collection("quizResults")
                    .whereIn("quizId", batch)
                    .whereEqualTo("studentId", studentUserUid)
                    .get().await()
                allScores.addAll(processQuizScores(scores.documents))
            } catch (e: Exception) {
                Log.e("GradeDebug", "Error fetching quiz batch: ${e.message}")
            }
        }

        return allScores
    }

    private suspend fun fetchAssignmentScoresInBatches(assignmentIds: List<String>, studentUserUid: String): List<ActivityScoreData> {
        val allScores = mutableListOf<ActivityScoreData>()

        processInBatches(assignmentIds, 10) { batch ->
            try {
                val scores = firestore.collection("submissions")
                    .whereIn("assignmentId", batch)
                    .whereEqualTo("studentId", studentUserUid)
                    .get().await()
                allScores.addAll(processAssignmentScores(scores.documents))
            } catch (e: Exception) {
                Log.e("GradeDebug", "Error fetching assignment batch: ${e.message}")
            }
        }

        return allScores
    }

    private fun processQuizScores(docs: List<DocumentSnapshot>): List<ActivityScoreData> {
        return docs.mapNotNull { doc ->
            val activityId = doc.getString("quizId") ?: return@mapNotNull null
            val metadata = activityMetadata[activityId] ?: return@mapNotNull null

            val rawScore = extractScore(doc)
            val percentage = rawScore.safePercentage(metadata.maxPoints)
            val score50Base = percentage.safeMax(50.0).roundToTwoDecimals()

            ActivityScoreData(
                title = metadata.title,
                score50Base = score50Base,
                rawScore = rawScore,
                maxPoints = metadata.maxPoints,
                activityId = activityId,
                activityType = metadata.type
            )
        }
    }

    private fun processAssignmentScores(docs: List<DocumentSnapshot>): List<ActivityScoreData> {
        return docs.mapNotNull { doc ->
            val activityId = doc.getString("assignmentId") ?: return@mapNotNull null
            val metadata = activityMetadata[activityId] ?: return@mapNotNull null

            val rawScore = extractScore(doc)
            val percentage = rawScore.safePercentage(metadata.maxPoints)
            val score50Base = percentage.safeMax(50.0).roundToTwoDecimals()

            ActivityScoreData(
                title = metadata.title,
                score50Base = score50Base,
                rawScore = rawScore,
                maxPoints = metadata.maxPoints,
                activityId = activityId,
                activityType = metadata.type
            )
        }
    }

    private fun extractScore(doc: DocumentSnapshot): Double {
        return (doc.get("grade") as? Double) ?:
        (doc.get("grade") as? Long)?.toDouble() ?:
        (doc.get("grade") as? String)?.toDoubleOrNull() ?:
        (doc.get("score") as? Double) ?:
        (doc.get("score") as? Long)?.toDouble() ?:
        (doc.get("score") as? String)?.toDoubleOrNull() ?:
        (doc.get("rawScore") as? Double) ?:
        (doc.get("rawScore") as? Long)?.toDouble() ?:
        (doc.get("rawScore") as? String)?.toDoubleOrNull() ?: 0.0
    }

    // FIXED FUNCTION WITH PROPER EMPTY FIELD HANDLING
    private fun showEditableScoreDialogWithFixedTotal(student: Student, detailedScores: DetailedScores) {
        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_editable_score_details, null)

        val tvDialogTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val btnEdit = view.findViewById<Button>(R.id.btnEdit)
        val etAttendancePresent = view.findViewById<EditText>(R.id.etAttendancePresent)
        val etAttendanceTotal = view.findViewById<EditText>(R.id.etAttendanceTotal)
        val tvAttendancePercentage = view.findViewById<TextView>(R.id.tvAttendancePercentage)
        val etRecitationPoints = view.findViewById<EditText>(R.id.etRecitationPoints)
        val tvRecitationPercentage = view.findViewById<TextView>(R.id.tvRecitationPercentage)

        // Get references to RecyclerViews
        val rvQuizScores = view.findViewById<RecyclerView>(R.id.rvQuizScores)
        val rvExamScores = view.findViewById<RecyclerView>(R.id.rvExamScores)
        val rvAssignmentScores = view.findViewById<RecyclerView>(R.id.rvAssignmentScores)

        // Store original values for validation
        val originalAttendancePresent = detailedScores.attendanceDetails.first
        val originalAttendanceTotal = detailedScores.attendanceDetails.second
        val originalRecitationPoints = detailedScores.recitationDetails

        // FIXED: Show empty for zero values, not "1"
        etAttendancePresent.setText(if (originalAttendancePresent == 0) "" else originalAttendancePresent.toString())
        etAttendanceTotal.setText(originalAttendanceTotal.toString())
        etAttendanceTotal.isEnabled = false
        etAttendanceTotal.alpha = 0.5f

        etRecitationPoints.setText(if (originalRecitationPoints == 0) "" else originalRecitationPoints.toString())

        // Update percentages
        updateAttendancePercentage(etAttendancePresent, etAttendanceTotal, tvAttendancePercentage)
        updateRecitationPercentage(
            etRecitationPoints.text.toString().toIntOrNull() ?: 0,
            tvRecitationPercentage
        )

        // Set title based on published state
        if (areGradesPublished) {
            tvDialogTitle.text = "View Scores (Published): ${student.lastName}, ${student.firstName}"
            btnEdit.visibility = View.GONE
        } else {
            tvDialogTitle.text = "View Scores: ${student.lastName}, ${student.firstName}"
            btnEdit.visibility = View.VISIBLE
        }

        // Setup RecyclerViews in view mode initially
        setupEditableActivityRecyclerView(
            rvQuizScores,
            detailedScores.quizScores,
            student.id,
            "Quiz",
            isEditable = false
        )
        setupEditableActivityRecyclerView(
            rvExamScores,
            detailedScores.examScores,
            student.id,
            "Exam",
            isEditable = false
        )
        setupEditableActivityRecyclerView(
            rvAssignmentScores,
            detailedScores.assignmentScores,
            student.id,
            "Assignment",
            isEditable = false
        )

        // Initially set to view mode (not editable)
        setViewModeEnabledWithFixedTotal(
            enabled = false,
            etAttendancePresent = etAttendancePresent,
            etAttendanceTotal = etAttendanceTotal,
            etRecitationPoints = etRecitationPoints,
            tvAttendancePercentage = tvAttendancePercentage,
            tvRecitationPercentage = tvRecitationPercentage,
            rvQuizScores = rvQuizScores,
            rvExamScores = rvExamScores,
            rvAssignmentScores = rvAssignmentScores
        )

        // Edit button click listener
        btnEdit.setOnClickListener {
            val isCurrentlyEditable = etAttendancePresent.isEnabled

            if (isCurrentlyEditable) {
                // Switch back to view mode
                setViewModeEnabledWithFixedTotal(
                    enabled = false,
                    etAttendancePresent = etAttendancePresent,
                    etAttendanceTotal = etAttendanceTotal,
                    etRecitationPoints = etRecitationPoints,
                    tvAttendancePercentage = tvAttendancePercentage,
                    tvRecitationPercentage = tvRecitationPercentage,
                    rvQuizScores = rvQuizScores,
                    rvExamScores = rvExamScores,
                    rvAssignmentScores = rvAssignmentScores
                )
                btnEdit.text = "Edit"
                tvDialogTitle.text = "View Scores: ${student.lastName}, ${student.firstName}"
            } else {
                // Switch to edit mode
                setViewModeEnabledWithFixedTotal(
                    enabled = true,
                    etAttendancePresent = etAttendancePresent,
                    etAttendanceTotal = etAttendanceTotal,
                    etRecitationPoints = etRecitationPoints,
                    tvAttendancePercentage = tvAttendancePercentage,
                    tvRecitationPercentage = tvRecitationPercentage,
                    rvQuizScores = rvQuizScores,
                    rvExamScores = rvExamScores,
                    rvAssignmentScores = rvAssignmentScores
                )
                btnEdit.text = "View"
                tvDialogTitle.text = "Edit Scores: ${student.lastName}, ${student.firstName}"
            }
        }

        builder.setView(view)

        // Only show Save Changes button when not published
        if (!areGradesPublished) {
            builder.setPositiveButton("Save Changes") { dialog, _ ->
                // VALIDATE ATTENDANCE AND RECITATION BEFORE SAVING
                val newPresent = etAttendancePresent.text.toString().toIntOrNull() ?: 0
                val total = originalAttendanceTotal
                val newRecitation = etRecitationPoints.text.toString().toIntOrNull() ?: 0

                if (!isValidAttendanceWithFixedTotal(newPresent, originalAttendanceTotal, originalAttendancePresent) ||
                    !isValidRecitation(newRecitation, originalRecitationPoints)) {
                    Toast.makeText(this@GradeInputActivity,
                        "Scores must be between 0 and maximum allowed!",
                        Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                // SAVE BOTH ACTIVITY SCORES AND ATTENDANCE/RECITATION
                saveModifiedAttendanceRecitation(
                    student.id,
                    newPresent,
                    total,
                    newRecitation
                )
                saveModifiedScores(student.id)
                dialog.dismiss()
            }
        }

        builder.setNegativeButton(if (areGradesPublished) "Close" else "Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun setViewModeEnabledWithFixedTotal(
        enabled: Boolean,
        etAttendancePresent: EditText,
        etAttendanceTotal: EditText,
        etRecitationPoints: EditText,
        tvAttendancePercentage: TextView,
        tvRecitationPercentage: TextView,
        rvQuizScores: RecyclerView,
        rvExamScores: RecyclerView,
        rvAssignmentScores: RecyclerView
    ) {
        // Set attendance fields - ONLY PRESENT CAN BE ENABLED
        etAttendancePresent.isEnabled = enabled
        etAttendanceTotal.isEnabled = false
        etRecitationPoints.isEnabled = enabled

        val alphaEnabled = if (enabled) 1.0f else 0.6f
        val alphaDisabled = 0.5f

        etAttendancePresent.alpha = alphaEnabled
        etAttendanceTotal.alpha = alphaDisabled
        etRecitationPoints.alpha = alphaEnabled

        // Set RecyclerView adapters to editable mode
        (rvQuizScores.adapter as? EditableActivityScoreAdapter)?.setEditable(enabled)
        (rvExamScores.adapter as? EditableActivityScoreAdapter)?.setEditable(false)
        (rvAssignmentScores.adapter as? EditableActivityScoreAdapter)?.setEditable(enabled)

        // Add/remove text watchers based on mode
        if (enabled) {
            etAttendancePresent.addTextChangedListener(createAttendanceWatcher(etAttendancePresent, etAttendanceTotal, tvAttendancePercentage))
            etRecitationPoints.addTextChangedListener(createRecitationWatcher(etRecitationPoints, tvRecitationPercentage))
        } else {
            etAttendancePresent.removeTextChangedListener(createAttendanceWatcher(etAttendancePresent, etAttendanceTotal, tvAttendancePercentage))
            etRecitationPoints.removeTextChangedListener(createRecitationWatcher(etRecitationPoints, tvRecitationPercentage))
        }
    }

    private fun isValidAttendanceWithFixedTotal(newPresent: Int, fixedTotal: Int, originalPresent: Int): Boolean {
        //if (newPresent < originalPresent) {
        //Toast.makeText(this, "Cannot decrease present days from $originalPresent to $newPresent", Toast.LENGTH_LONG).show()
        //return false
        //}

        if (newPresent < 0) {
            Toast.makeText(this, "Present days cannot be negative", Toast.LENGTH_LONG).show()
            return false
        }

        if (newPresent > fixedTotal) {
            Toast.makeText(this, "Present days ($newPresent) cannot exceed total days ($fixedTotal)", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun isValidRecitation(newRecitation: Int, originalRecitation: Int): Boolean {
        return newRecitation >= 0
    }

    private fun setupEditableActivityRecyclerView(
        recyclerView: RecyclerView,
        scores: List<ActivityScoreData>,
        studentId: String,
        category: String,
        isEditable: Boolean = false
    ) {
        recyclerView.layoutManager = LinearLayoutManager(this)

        val shouldBeEditable = when (category) {
            "Exam" -> false
            else -> isEditable && !areGradesPublished
        }

        recyclerView.adapter = EditableActivityScoreAdapter(
            scores = scores,
            studentId = studentId,
            category = category,
            isEditable = shouldBeEditable,
            isPublished = areGradesPublished,
            onScoreUpdate = { updatedScore ->
                if (!areGradesPublished && category != "Exam") {
                    updateCachedScore(studentId, updatedScore, category)
                    recalculateStudentGrade(studentId)
                }
            }
        )
        recyclerView.visibility = if (scores.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateCachedScore(studentId: String, updatedScore: ActivityScoreData, category: String) {
        val cachedScores = studentScoresCache.get(studentId) ?: return

        val updatedScores = when (category) {
            "Quiz" -> cachedScores.copy(quizScores = cachedScores.quizScores.map {
                if (it.activityId == updatedScore.activityId) updatedScore else it
            })
            "Exam" -> cachedScores.copy(examScores = cachedScores.examScores.map {
                if (it.activityId == updatedScore.activityId) updatedScore else it
            })
            "Assignment" -> cachedScores.copy(assignmentScores = cachedScores.assignmentScores.map {
                if (it.activityId == updatedScore.activityId) updatedScore else it
            })
            else -> cachedScores
        }

        studentScoresCache.put(studentId, updatedScores)
        Log.d("ScoreEdit", "Updated cached score for $studentId: ${updatedScore.title} = ${updatedScore.rawScore}")
    }

    private fun publishFinalGrades() {
        if (!::gradeAdapter.isInitialized) {
            Toast.makeText(this, "Grades not loaded yet. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        if (areGradesPublished) {
            Toast.makeText(this, "Grades are already published.", Toast.LENGTH_SHORT).show()
            return
        }

        tvLoadingStatus.text = "Publishing final grades..."

        lifecycleScope.launch {
            try {
                val gradesToSave = gradeAdapter.getCurrentGrades()
                val gradingPeriodClean = gradingPeriod!!.replace(" ", "")
                val subjectCodeClean = subjectCode!!.replace(" ", "")

                val saveJobs = gradesToSave.map { (studentId, gradeData) ->
                    async {
                        val documentId = "${studentId}_${subjectCodeClean}_${gradingPeriodClean}"

                        val data = hashMapOf(
                            "studentId" to studentId,
                            "assignmentId" to assignmentId!!,
                            "subjectCode" to subjectCode!!,
                            "gradingPeriod" to gradingPeriod!!,
                            "attendanceScore" to gradeData.attendance,
                            "recitationScore" to gradeData.recitation,
                            "quizScore" to gradeData.quiz,
                            "examScore" to gradeData.exam,
                            "assignmentScore" to gradeData.assignment,
                            "finalGrade" to gradeData.finalGrade,
                            "isPublished" to true,
                            "publishedAt" to System.currentTimeMillis(),
                            "timestamp" to System.currentTimeMillis()
                        )

                        firestore.collection("finalStudentGrades")
                            .document(documentId)
                            .set(data, SetOptions.merge())
                            .await()
                    }
                }

                saveJobs.awaitAll()

                // UPDATE LOCAL STATE
                areGradesPublished = true
                runOnUiThread {
                    btnPublishGrades.text = "Grades Published"
                    btnPublishGrades.isEnabled = false
                    btnPublishGrades.alpha = 0.6f

                    // Update adapter state
                    gradeAdapter.setPublishedState(true)
                }

                tvLoadingStatus.text = "✅ Grades successfully published for ${gradesToSave.size} students."
                Toast.makeText(this@GradeInputActivity, "All grades published successfully! Grades are now locked.", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("GradeInput", "Error publishing grades: ${e.message}", e)
                tvLoadingStatus.text = "Error publishing grades."
                Toast.makeText(this@GradeInputActivity, "Publishing error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // FIXED: Percentage calculation methods with NaN protection
    private fun updateAttendancePercentage(etPresent: EditText, etTotal: EditText, tvPercentage: TextView) {
        val present = etPresent.text.toString().toIntOrNull() ?: 0
        val total = etTotal.text.toString().toIntOrNull() ?: 1
        val percentage = if (total > 0) (present.toDouble() / total) * 100 else 0.0
        val percentage50Base = percentage.safeMax(50.0)
        tvPercentage.text = "Percentage: ${"%.2f".format(percentage50Base)}% (50% base)"
    }

    private fun createAttendanceWatcher(etPresent: EditText, etTotal: EditText, tvPercentage: TextView): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateAttendancePercentage(etPresent, etTotal, tvPercentage)
            }
        }
    }

    private fun createRecitationWatcher(etPoints: EditText, tvPercentage: TextView): TextWatcher {
        return object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""

                if (input == previousText) return

                val normalizedText = normalizeNumericInput(input, 10)

                if (normalizedText != input) {
                    etPoints.removeTextChangedListener(this)
                    etPoints.setText(normalizedText)
                    etPoints.setSelection(normalizedText.length)
                    etPoints.addTextChangedListener(this)
                    return
                }

                val points = normalizedText.toIntOrNull() ?: 0
                updateRecitationPercentage(points, tvPercentage)
            }
        }
    }

    private fun normalizeNumericInput(input: String, maxValue: Int = Int.MAX_VALUE): String {
        if (input.isEmpty()) return input

        var normalized = input.trim()

        if (normalized.length > 1 && normalized.startsWith("0")) {
            normalized = normalized.replaceFirst("^0+".toRegex(), "")
            if (normalized.isEmpty()) {
                normalized = "0"
            }
        }

        val value = normalized.toIntOrNull() ?: 0
        if (value > maxValue) {
            return maxValue.toString()
        }

        return normalized
    }

    private fun updateRecitationPercentage(points: Int, tvPercentage: TextView) {
        val percentage = if (points >= 5) 100.0 else (points.toDouble() / 5.0) * 100.0
        val percentage50Base = percentage.safeMax(50.0)
        tvPercentage.text = "Percentage: ${"%.2f".format(percentage50Base)}% (50% base)"
    }

    private fun saveModifiedAttendanceRecitation(studentId: String, present: Int, total: Int, recitationPoints: Int) {
        val cachedScores = studentScoresCache.get(studentId) ?: return

        // Update the cache with new values
        studentScoresCache.put(studentId, cachedScores.copy(
            attendanceDetails = Pair(present, total),
            recitationDetails = recitationPoints
        ))

        // Recalculate the main grade
        recalculateStudentGrade(studentId)
    }

    private fun recalculateStudentGrade(studentId: String) {
        val cachedScores = studentScoresCache.get(studentId) ?: return

        // Recalculate category averages based on modified scores with safe average
        val quizAverage = cachedScores.quizScores.map { it.score50Base }.safeAverage()
        val examAverage = cachedScores.examScores.map { it.score50Base }.safeAverage()
        val assignmentAverage = cachedScores.assignmentScores.map { it.score50Base }.safeAverage()

        // RECALCULATE ATTENDANCE AND RECITATION
        val (present, total) = cachedScores.attendanceDetails
        val attendanceScoreRaw = if (total > 0) (present.toDouble() / total) * 100 else 50.0
        val attendanceAverage = attendanceScoreRaw.safeMax(50.0)

        val recitationPoints = cachedScores.recitationDetails
        val recitationScore = if (recitationPoints >= 5) 100.0 else (recitationPoints.toDouble() / 5.0) * 100.0
        val recitationAverage = recitationScore.safeMax(50.0)

        // Update the main adapter
        if (::gradeAdapter.isInitialized) {
            gradeAdapter.updateStudentGrade(
                studentId,
                quizAverage.roundToTwoDecimals(),
                examAverage.roundToTwoDecimals(),
                assignmentAverage.roundToTwoDecimals()
            )
            updateAttendanceRecitationInAdapter(studentId, attendanceAverage, recitationAverage)
        }
    }

    private fun updateAttendanceRecitationInAdapter(studentId: String, attendance: Double, recitation: Double) {
        if (::gradeAdapter.isInitialized) {
            gradeAdapter.updateStudentAttendanceRecitation(
                studentId,
                attendance.roundToTwoDecimals(),
                recitation.roundToTwoDecimals()
            )
        }
    }

    private fun saveModifiedScores(studentId: String) {
        val cachedScores = studentScoresCache.get(studentId) ?: return

        lifecycleScope.launch {
            try {
                saveModifiedActivityScores(studentId, cachedScores)
                saveAttendanceRecitationOverrides(studentId, cachedScores)
                Toast.makeText(this@GradeInputActivity, "Scores updated successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ScoreEdit", "Error saving modified scores: ${e.message}", e)
                Toast.makeText(this@GradeInputActivity, "Error saving scores: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // OPTIMIZED: Batch save operations
    private suspend fun saveModifiedActivityScores(studentId: String, scores: DetailedScores) {
        val studentUserUid = studentUidToDocIdMap.entries.firstOrNull { it.value == studentId }?.key ?: return

        val allScores = scores.quizScores + scores.examScores + scores.assignmentScores

        // Process in batches for better performance
        processInBatches(allScores, 5) { batch ->
            batch.forEach { scoreData ->
                when (scoreData.activityType) {
                    "Quiz", "Exam" -> {
                        val quizResultData = hashMapOf(
                            "quizId" to scoreData.activityId,
                            "studentId" to studentUserUid,
                            "grade" to scoreData.rawScore,
                            "score" to scoreData.rawScore,
                            "rawScore" to scoreData.rawScore,
                            "modified" to true,
                            "modifiedAt" to System.currentTimeMillis(),
                            "createdAt" to System.currentTimeMillis()
                        )

                        val docId = "${scoreData.activityId}_${studentUserUid}"
                        firestore.collection("quizResults")
                            .document(docId)
                            .set(quizResultData, SetOptions.merge())
                    }
                    "Assignment" -> {
                        val submissionData = hashMapOf(
                            "assignmentId" to scoreData.activityId,
                            "studentId" to studentUserUid,
                            "grade" to scoreData.rawScore,
                            "score" to scoreData.rawScore,
                            "rawScore" to scoreData.rawScore,
                            "status" to "submitted",
                            "modified" to true,
                            "modifiedAt" to System.currentTimeMillis(),
                            "submittedAt" to System.currentTimeMillis(),
                            "createdAt" to System.currentTimeMillis()
                        )

                        val docId = "${scoreData.activityId}_${studentUserUid}"
                        firestore.collection("submissions")
                            .document(docId)
                            .set(submissionData, SetOptions.merge())
                    }
                }
            }
        }

        // Invalidate cache for this student
        studentScoresCache.remove(studentId)
    }

    private suspend fun saveAttendanceRecitationOverrides(studentId: String, scores: DetailedScores) {
        val attendanceDetails = scores.attendanceDetails
        val present = attendanceDetails.first
        val total = attendanceDetails.second
        val recitationPoints = scores.recitationDetails

        val overrideData = hashMapOf(
            "studentId" to studentId,
            "assignmentId" to assignmentId!!,
            "subjectCode" to subjectCode!!,
            "gradingPeriod" to gradingPeriod!!,
            "attendancePresent" to present,
            "attendanceTotal" to total,
            "recitationPoints" to recitationPoints,
            "modified" to true,
            "modifiedAt" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis()
        )

        val docId = "${studentId}_${assignmentId!!}_${gradingPeriod!!}"
        firestore.collection("manualGradeOverrides")
            .document(docId)
            .set(overrideData, SetOptions.merge())
            .await()

        Log.d("ScoreEdit", "Saved attendance/recitation override: $docId - Present: $present/$total, Recitation: $recitationPoints")
    }

    // OPTIMIZED: Main data loading with performance improvements
    private fun loadGradingData() {
        tvLoadingStatus.text = "Fetching enrolled students..."

        lifecycleScope.launch {
            Log.d("GradeDebug", "Starting loadGradingData coroutine.")
            try {
                // 1. Get class info
                val classDoc = firestore.collection("classAssignments").document(assignmentId!!).get().await()
                val yearLevel = classDoc.getString("yearLevel") ?: ""
                val semester = classDoc.getString("semester") ?: ""
                val subjectId = classDoc.getString("subjectId") ?: ""
                val sectionId = className?.split(" - ")?.lastOrNull() ?: ""

                if (yearLevel.isEmpty() || semester.isEmpty() || sectionId.isEmpty()) {
                    tvLoadingStatus.text = "Error: Missing class details."
                    return@launch
                }

                // 2. Fetch students
                val studentsSnapshot = firestore.collection("students")
                    .whereEqualTo("sectionId", sectionId)
                    .whereEqualTo("yearLevel", yearLevel)
                    .get().await()

                val studentIds = studentsSnapshot.documents.map { it.id }
                if (studentIds.isEmpty()) {
                    tvLoadingStatus.text = "No admitted students found."
                    return@launch
                }

                // CREATE THE MAPPING (Firebase UID -> Student Document ID)
                studentUidToDocIdMap = studentsSnapshot.documents.mapNotNull { doc ->
                    val studentDocId = doc.id
                    val studentUid = doc.getString("userUid")
                    if (studentUid != null) studentUid to studentDocId else null
                }.toMap()
                Log.d("GradeDebug", "Student UID mapping created: ${studentUidToDocIdMap.size} students mapped.")

                // 3. Check enrollment
                val enrollmentDocId = "${yearLevel.replace(" ", "")}_${semester.replace(" ", "")}_${subjectCode}"
                val studentMap = studentsSnapshot.documents.mapNotNull { it.toObject(Student::class.java)?.copy(id = it.id) }.associateBy { it.id }
                val enrolledStudents = mutableListOf<Student>()

                val enrollmentChecks = studentIds.map { studentId ->
                    async {
                        firestore.collection("students").document(studentId).collection("subjects").document(enrollmentDocId)
                            .get().await().let { if (it.exists()) studentId else null }
                    }
                }
                enrollmentChecks.awaitAll().filterNotNull().forEach { id ->
                    studentMap[id]?.let { enrolledStudents.add(it) }
                }

                if (enrolledStudents.isEmpty()) {
                    tvLoadingStatus.text = "No students officially enrolled."
                    return@launch
                }

                // 🟢 ADD: Debug student mapping
                debugStudentMapping(enrolledStudents)

                val studentGradesData = mutableMapOf<String, GradeInputAdapter.GradeData>()
                enrolledStudents.forEach { student ->
                    studentGradesData[student.id] = GradeInputAdapter.GradeData(
                        studentDocId = student.id,
                        firstName = student.firstName,
                        lastName = student.lastName,
                        subjectId = subjectId,
                        gradingPeriod = gradingPeriod!!
                    )
                }

                // 4. Fetch and Calculate Scores CONCURRENTLY
                tvLoadingStatus.text = "Fetching all grades concurrently..."

                // 🟢 ADD: Debug attendance data first
                debugAttendanceData()

                val attendanceJob = async {
                    fetchAttendanceAndRecitationScores(enrolledStudents, studentGradesData)
                }
                val quizJob = async {
                    fetchQuizExamAndAssignmentScores(enrolledStudents, studentGradesData)
                }

                awaitAll(attendanceJob, quizJob)

                Log.d("GradeDebug", "All score fetching jobs completed.")

                // 5. Initialize Adapter
                tvLoadingStatus.text = "Loading data into grade sheet..."

                val gradesList = studentGradesData.values.toList()

                gradeAdapter = GradeInputAdapter(
                    gradeDataList = gradesList,
                    listener = this@GradeInputActivity,
                    isPublished = areGradesPublished
                )
                recyclerViewGrades.adapter = gradeAdapter
                tvLoadingStatus.text = "✅ ${enrolledStudents.size} students loaded. Grades are ready."
                btnPublishGrades.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("GradeInput", "Error fetching students or scores: ${e.message}", e)
                tvLoadingStatus.text = "Error loading students."
                Toast.makeText(this@GradeInputActivity, "Loading error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun debugAttendanceData() {
        val termDoc = firestore.document("systemSettings/currentTerm").get().await()
        val semester = termDoc.getString("semester") ?: ""

        Log.d("GradeDebug", "🔍 DEBUG ATTENDANCE DATA:")
        Log.d("GradeDebug", "   - Assignment ID: $assignmentId")
        Log.d("GradeDebug", "   - Grading Period: $gradingPeriod")
        Log.d("GradeDebug", "   - Semester: $semester")

        try {
            val attendanceSnapshot = firestore.collection("dailyAttendanceRecords")
                .whereEqualTo("assignmentId", assignmentId!!)
                .whereEqualTo("academicTerm", gradingPeriod!!)
                .whereEqualTo("semester", semester)
                .get().await()

            Log.d("GradeDebug", "📊 Found ${attendanceSnapshot.documents.size} attendance records")

            attendanceSnapshot.documents.forEachIndexed { index, doc ->
                Log.d("GradeDebug", "   📄 Record ${index + 1}:")
                Log.d("GradeDebug", "      - ID: ${doc.id}")
                Log.d("GradeDebug", "      - Date: ${doc.getString("date")}")

                val statuses = doc.get("statuses") as? Map<String, String> ?: emptyMap()
                Log.d("GradeDebug", "      - Statuses count: ${statuses.size}")

                // Show ALL statuses to see what student IDs are being used
                statuses.forEach { (studentId, status) ->
                    Log.d("GradeDebug", "         👤 $studentId: $status")
                }

                val recitationPoints = doc.get("recitationPoints") as? Map<String, Any> ?: emptyMap()
                Log.d("GradeDebug", "      - Recitation points count: ${recitationPoints.size}")

                recitationPoints.forEach { (studentId, points) ->
                    Log.d("GradeDebug", "         🎤 $studentId: $points points")
                }
            }

            if (attendanceSnapshot.isEmpty) {
                Log.d("GradeDebug", "❌ NO ATTENDANCE RECORDS FOUND!")
                Log.d("GradeDebug", "   Check if records exist with:")
                Log.d("GradeDebug", "   - assignmentId: $assignmentId")
                Log.d("GradeDebug", "   - academicTerm: $gradingPeriod")
                Log.d("GradeDebug", "   - semester: $semester")
            }

        } catch (e: Exception) {
            Log.e("GradeDebug", "❌ Error debugging attendance: ${e.message}")
        }
    }

    private suspend fun debugStudentMapping(enrolledStudents: List<Student>) {
        Log.d("GradeDebug", "🔍 DEBUG STUDENT MAPPING:")
        Log.d("GradeDebug", "   - Total enrolled students: ${enrolledStudents.size}")

        enrolledStudents.forEach { student ->
            Log.d("GradeDebug", "   👤 Student: ${student.id} - ${student.firstName} ${student.lastName}")
            Log.d("GradeDebug", "      - Year Level: ${student.yearLevel}")
            Log.d("GradeDebug", "      - userUid: ${student.userUid}")
        }

        Log.d("GradeDebug", "   - Student UID to DocID mapping: ${studentUidToDocIdMap.size} entries")
        studentUidToDocIdMap.forEach { (uid, docId) ->
            Log.d("GradeDebug", "      🔄 $uid -> $docId")
        }
    }

    // OPTIMIZED: Attendance and recitation calculation WITH DEBUG AND MANUAL OVERRIDES
    private suspend fun fetchAttendanceAndRecitationScores(
        enrolledStudents: List<Student>,
        studentGradesData: MutableMap<String, GradeInputAdapter.GradeData>
    ) {
        val termDoc = firestore.document("systemSettings/currentTerm").get().await()
        val academicTerm = termDoc.getString("academicTerm") ?: ""
        val semester = termDoc.getString("semester") ?: ""

        if (academicTerm.isEmpty() || semester.isEmpty()) {
            Log.e("GradeInput", "Missing current term or semester info.")
            return
        }

        Log.d("GradeDebug", "📅 Fetching attendance for:")
        Log.d("GradeDebug", "   - Assignment: $assignmentId")
        Log.d("GradeDebug", "   - Grading Period: $gradingPeriod")
        Log.d("GradeDebug", "   - Semester: $semester")

        // 🟢 NEW: Fetch manual overrides first
        val manualOverrides = fetchManualGradeOverrides(enrolledStudents)

        val attendanceSnapshot = firestore.collection("dailyAttendanceRecords")
            .whereEqualTo("assignmentId", assignmentId!!)
            .whereEqualTo("academicTerm", gradingPeriod!!)
            .whereEqualTo("semester", semester)
            .get().await()

        Log.d("GradeDebug", "📊 Found ${attendanceSnapshot.documents.size} attendance records")

        // 🟢 SIMPLER APPROACH: Create a map of enrolled student IDs for quick lookup
        val enrolledStudentIds = enrolledStudents.map { it.id }.toSet()
        Log.d("GradeDebug", "📋 Enrolled Student IDs: $enrolledStudentIds")

        val studentAttendanceCounts = mutableMapOf<String, Int>()
        var totalClassSessions = 0
        val studentRecitationPoints = mutableMapOf<String, Int>()

        // Initialize counters ONLY for enrolled students
        enrolledStudents.forEach { student ->
            studentAttendanceCounts[student.id] = 0
            studentRecitationPoints[student.id] = 0
        }

        attendanceSnapshot.documents.forEach { doc ->
            val statuses = doc.get("statuses") as? Map<String, String> ?: emptyMap()
            totalClassSessions++

            Log.d("GradeDebug", "   📆 Session $totalClassSessions - Statuses: ${statuses.size}")

            // 🟢 ADD DETAILED DEBUG HERE
            Log.d("GradeDebug", "   🔍 PROCESSING STATUSES:")
            statuses.forEach { (studentDocId, status) ->
                Log.d("GradeDebug", "      Checking: $studentDocId -> $status")

                // Check if this student is enrolled using the set
                if (enrolledStudentIds.contains(studentDocId)) {
                    Log.d("GradeDebug", "      ✅ $studentDocId is enrolled!")
                    if (status.equals("Present", ignoreCase = true) || status == "PRESENT") {
                        studentAttendanceCounts[studentDocId] = studentAttendanceCounts.getOrDefault(studentDocId, 0) + 1
                        Log.d("GradeDebug", "      ✅✅✅ $studentDocId marked Present - Count: ${studentAttendanceCounts[studentDocId]}")
                    } else {
                        Log.d("GradeDebug", "      ℹ️ $studentDocId status: $status (not Present)")
                    }
                } else {
                    Log.w("GradeDebug", "      ⚠️ Student $studentDocId not in enrolled list - skipping")
                }
            }

            // Same for recitation points
            val recitationData = doc.get("recitationPoints")
            when (recitationData) {
                is Map<*, *> -> {
                    recitationData.forEach { (key, value) ->
                        val studentDocId = key.toString()
                        if (enrolledStudentIds.contains(studentDocId)) {
                            val points = when (value) {
                                is Long -> value.toInt()
                                is Int -> value
                                is Double -> value.toInt()
                                is String -> value.toIntOrNull() ?: 0
                                else -> 0
                            }
                            if (points > 0) {
                                studentRecitationPoints[studentDocId] = studentRecitationPoints.getOrDefault(studentDocId, 0) + points
                                Log.d("GradeDebug", "      🎤 $studentDocId +$points recitation points")
                            }
                        }
                    }
                }
                else -> {
                    Log.d("GradeDebug", "      ℹ️ No recitation points in this session")
                }
            }
        }

        Log.d("GradeDebug", "🏫 Total class sessions: $totalClassSessions")

        // 🟢 ADD: Debug attendance counts before calculation
        Log.d("GradeDebug", "🎯 ATTENDANCE COUNTS VERIFICATION:")
        enrolledStudents.forEach { student ->
            val count = studentAttendanceCounts[student.id] ?: 0
            Log.d("GradeDebug", "   ${student.lastName} (${student.id}): $count/$totalClassSessions present")
        }

        // 🟢 ADD: Debug manual overrides
        Log.d("GradeDebug", "📝 MANUAL OVERRIDES VERIFICATION:")
        manualOverrides.forEach { (studentId, overrideData) ->
            Log.d("GradeDebug", "   $studentId: Present=${overrideData.present}/${overrideData.total}, Recitation=${overrideData.recitationPoints}")
        }

        // Calculate final scores WITH MANUAL OVERRIDES
        enrolledStudents.forEach { student ->
            val studentDocId = student.id
            val grades = studentGradesData[studentDocId] ?: GradeInputAdapter.GradeData()

            // 🟢 CHECK FOR MANUAL OVERRIDE FIRST
            val manualOverride = manualOverrides[studentDocId]

            val finalAttendance: Double
            val finalRecitation: Double

            if (manualOverride != null) {
                Log.d("GradeDebug", "🎯 USING MANUAL OVERRIDE for $studentDocId")

                // Calculate attendance from manual override
                val presentCount = manualOverride.present
                val totalSessions = manualOverride.total
                val attendanceScoreRaw = if (totalSessions > 0) {
                    val rawPercentage = (presentCount.toDouble() / totalSessions.toDouble()) * 100.0
                    rawPercentage.coerceIn(0.0, 100.0) // LIMIT TO 100%
                } else {
                    50.0
                }
                finalAttendance = attendanceScoreRaw.coerceAtLeast(50.0).roundToTwoDecimals()

                // Calculate recitation from manual override
                val totalRecitationPoints = manualOverride.recitationPoints
                val recitationScore = if (totalRecitationPoints >= 5) {
                    100.0
                } else {
                    (totalRecitationPoints.toDouble() / 5.0) * 100.0
                }
                finalRecitation = recitationScore.safeMax(50.0).roundToTwoDecimals()

                Log.d("GradeDebug", "👤 Student ${student.lastName} (OVERRIDE): $presentCount/$totalSessions present, $totalRecitationPoints recitation points")

            } else {
                Log.d("GradeDebug", "📊 USING ORIGINAL DATA for $studentDocId")

                // Use original calculation
                val presentCount = studentAttendanceCounts[studentDocId] ?: 0
                Log.d("GradeDebug", "👤 Student ${student.lastName} ($studentDocId): $presentCount/$totalClassSessions present")

                val attendanceScoreRaw = if (totalClassSessions > 0) {
                    (presentCount.toDouble() / totalClassSessions.toDouble()) * 100.0
                } else {
                    Log.d("GradeDebug", "      ⚠️ No class sessions - using default 50%")
                    50.0
                }
                finalAttendance = attendanceScoreRaw.safeMax(50.0).roundToTwoDecimals()

                val totalRecitationPoints = studentRecitationPoints[studentDocId] ?: 0
                Log.d("GradeDebug", "🎤 Student ${student.lastName}: $totalRecitationPoints total recitation points")

                val recitationScore = if (totalRecitationPoints >= 5) {
                    100.0
                } else {
                    (totalRecitationPoints.toDouble() / 5.0) * 100.0
                }
                finalRecitation = recitationScore.safeMax(50.0).roundToTwoDecimals()
            }

            grades.attendance = finalAttendance
            grades.recitation = finalRecitation

            studentGradesData[studentDocId] = grades
            Log.d("GradeDebug", "✅ FINAL: ${student.lastName} - Att=$finalAttendance, Rec=$finalRecitation")
        }

        // 🟢 ADD: Summary log
        Log.d("GradeDebug", "📈 ATTENDANCE SUMMARY:")
        enrolledStudents.forEach { student ->
            val grades = studentGradesData[student.id]!!
            Log.d("GradeDebug", "   ${student.lastName}: ${grades.attendance}% attendance, ${grades.recitation}% recitation")
        }
    }

    // 🟢 NEW: Function to fetch manual grade overrides
    private suspend fun fetchManualGradeOverrides(enrolledStudents: List<Student>): Map<String, ManualOverrideData> {
        val overridesMap = mutableMapOf<String, ManualOverrideData>()

        try {
            // Create document IDs for all enrolled students
            val overrideDocIds = enrolledStudents.map { student ->
                "${student.id}_${assignmentId!!}_${gradingPeriod!!}"
            }

            Log.d("GradeDebug", "🔍 Fetching manual overrides for ${overrideDocIds.size} students")

            // Fetch overrides in batches to avoid too many queries
            val batchSize = 10
            for (i in overrideDocIds.indices step batchSize) {
                val batch = overrideDocIds.subList(i, minOf(i + batchSize, overrideDocIds.size))

                val batchOverrides = firestore.collection("manualGradeOverrides")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                    .get()
                    .await()

                batchOverrides.documents.forEach { doc ->
                    val studentId = doc.getString("studentId") ?: return@forEach
                    val present = doc.getLong("attendancePresent")?.toInt() ?: 0
                    val total = doc.getLong("attendanceTotal")?.toInt() ?: 1
                    val recitationPoints = doc.getLong("recitationPoints")?.toInt() ?: 0

                    overridesMap[studentId] = ManualOverrideData(present, total, recitationPoints)
                    Log.d("GradeDebug", "   ✅ Found override for $studentId: $present/$total present, $recitationPoints recitation")
                }
            }

            Log.d("GradeDebug", "📋 Total manual overrides found: ${overridesMap.size}")

        } catch (e: Exception) {
            Log.e("GradeDebug", "❌ Error fetching manual overrides: ${e.message}")
        }

        return overridesMap
    }

    // 🟢 NEW: Data class for manual override data
    private data class ManualOverrideData(
        val present: Int,
        val total: Int,
        val recitationPoints: Int
    )

    // Add this to your utils or in the same file
    private fun List<Double>.safeAverage(): Double {
        return if (this.isEmpty()) 50.0 else this.average()
    }

    // OPTIMIZED: Quiz and assignment scores with better performance
    private suspend fun fetchQuizExamAndAssignmentScores(
        enrolledStudents: List<Student>,
        studentGradesData: MutableMap<String, GradeInputAdapter.GradeData>
    ) {
        val activityMetadata = metadataCache.getCache()

        if (activityMetadata.isEmpty()) {
            Log.d("GradeDebug", "No valid activities found. Cannot proceed to fetch scores.")
            // Set default scores for all students
            enrolledStudents.forEach { student ->
                val grades = studentGradesData[student.id] ?: GradeInputAdapter.GradeData()
                grades.quiz = 50.0
                grades.exam = 50.0
                grades.assignment = 50.0
                studentGradesData[student.id] = grades
            }
            return
        }

        val fetchJobs = mutableListOf<Deferred<List<DocumentSnapshot>>>()
        val allScoresDocuments = mutableListOf<DocumentSnapshot>()

        val quizIds = activityMetadata.filter { it.value.type == "Quiz" || it.value.type == "Exam" }.keys.toList()
        val assignmentIds = activityMetadata.filter { it.value.type == "Assignment" }.keys.toList()

        // Fetch Quiz Scores (Concurrent Chunking)
        if (quizIds.isNotEmpty()) {
            val quizChunks = quizIds.chunked(10)
            for (chunk in quizChunks) {
                val job = lifecycleScope.async<List<DocumentSnapshot>> {
                    try {
                        firestore.collection("quizResults").whereIn("quizId", chunk).get().await().documents
                    } catch (e: Exception) {
                        Log.e("GradeDebug", "Error in Quiz/Exam chunk query: ${e.message}")
                        emptyList()
                    }
                }
                fetchJobs.add(job)
            }
        }

        // Fetch Assignment Scores (Concurrent Chunking)
        if (assignmentIds.isNotEmpty()) {
            val assignmentChunks = assignmentIds.chunked(10)
            for (chunk in assignmentChunks) {
                val job = lifecycleScope.async<List<DocumentSnapshot>> {
                    try {
                        firestore.collection("submissions").whereIn("assignmentId", chunk).get().await().documents
                    } catch (e: Exception) {
                        Log.e("submission", "Error in Assignment chunk query: ${e.message}")
                        emptyList()
                    }
                }
                fetchJobs.add(job)
            }
        }

        // Wait for all jobs and combine results
        if (fetchJobs.isNotEmpty()) {
            val allResults = fetchJobs.awaitAll()
            allResults.forEach { docs -> allScoresDocuments.addAll(docs) }
        }

        Log.d("GradeDebug", "Fetched ${allScoresDocuments.size} score documents in total.")

        // Group raw score documents by Student Document ID
        val studentSubmissions = allScoresDocuments
            .mapNotNull { doc ->
                val studentDocId = studentUidToDocIdMap[doc.getString("studentId")]
                if (studentDocId != null) studentDocId to doc else null
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }

        studentGradesData.keys.forEach { studentId ->
            val studentBestActivityScores = mutableMapOf<String, Double>()

            val submissionsForStudent = studentSubmissions[studentId] ?: emptyList()

            // Process all submissions to find the best score per activity
            submissionsForStudent.forEach { doc ->
                val activityId = doc.getString("quizId") ?: doc.getString("assignmentId") ?: return@forEach
                val metadata = activityMetadata[activityId] ?: return@forEach
                val maxPoints = metadata.maxPoints

                val score = extractScore(doc)
                val rawPercentage = score.safePercentage(maxPoints)
                val finalPercentage = rawPercentage.safeMax(50.0)

                val currentBest = studentBestActivityScores[activityId] ?: 0.0
                if (finalPercentage > currentBest) {
                    studentBestActivityScores[activityId] = finalPercentage
                }
            }

            // Variables for final category averaging
            val categoryTotalScores = mutableMapOf("Quiz" to 0.0, "Exam" to 0.0, "Assignment" to 0.0)
            val categoryActivityCount = mutableMapOf("Quiz" to 0, "Exam" to 0, "Assignment" to 0)

            // Iterate through ALL activities and tally scores
            activityMetadata.forEach { (activityId, metadata) ->
                val type = metadata.type
                val finalScoreForActivity = studentBestActivityScores[activityId] ?: 50.0

                if (categoryTotalScores.containsKey(type)) {
                    categoryTotalScores[type] = categoryTotalScores[type]!! + finalScoreForActivity
                    categoryActivityCount[type] = categoryActivityCount[type]!! + 1
                }
            }

            // Calculate Final Average for each category
            val grades = studentGradesData[studentId] ?: GradeInputAdapter.GradeData()

            categoryTotalScores.keys.forEach { type ->
                val totalScore = categoryTotalScores[type]!!
                val count = categoryActivityCount[type]!!

                val finalCalculatedScore = if (count > 0) {
                    totalScore / count
                } else {
                    50.0
                }

                when (type) {
                    "Quiz" -> grades.quiz = finalCalculatedScore.roundToTwoDecimals()
                    "Exam" -> grades.exam = finalCalculatedScore.roundToTwoDecimals()
                    "Assignment" -> grades.assignment = finalCalculatedScore.roundToTwoDecimals()
                }
            }

            studentGradesData[studentId] = grades
            Log.d("GradeDebug", "Final Scores for $studentId: Q=${grades.quiz} E=${grades.exam} A=${grades.assignment}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up caches to prevent memory leaks
        metadataCache.clear()
        studentScoresCache.clear()
    }
}
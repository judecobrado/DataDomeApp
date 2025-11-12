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
import com.google.firebase.database.DataSnapshot
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
// --- END DATA CLASSES ---

class GradeInputActivity : AppCompatActivity(), OnStudentClickListener {

    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance()

    private var studentUidToDocIdMap: Map<String, String> = emptyMap()
    private var activityMetadata: Map<String, ActivityMetadata> = emptyMap()
    private var cachedActivityMetadata: Map<String, ActivityMetadata> = emptyMap()

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

    private val detailedScoresCache = mutableMapOf<String, DetailedScores>()

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
            val gradingPeriodClean = gradingPeriod!!.replace(" ", "")
            val subjectCodeClean = subjectCode!!.replace(" ", "")

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

    // NEW: Show confirmation dialog before publishing
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

    // --- OPTIMIZED: Pre-load activity metadata ---
    private suspend fun loadActivityMetadata() {
        try {
            cachedActivityMetadata = fetchActivityMetadata()
            Log.d("GradeDebug", "Pre-loaded ${cachedActivityMetadata.size} activity metadata")
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error pre-loading metadata: ${e.message}")
        }
    }

    private suspend fun fetchActivityMetadata(): Map<String, ActivityMetadata> {
        val metadata = mutableMapOf<String, ActivityMetadata>()

        // Fetch quizzes from Realtime DB
        try {
            val quizzesSnapshot = realtimeDb.reference.child("quizzes")
                .orderByChild("assignmentId").equalTo(assignmentId!!).get().await()

            quizzesSnapshot.children.forEach { snapshot ->
                val id = snapshot.key ?: return@forEach
                val map = snapshot.value as? Map<*, *> ?: return@forEach

                val activityPeriod = map["academicTerm"] as? String
                if (activityPeriod != gradingPeriod) return@forEach

                val title = map["title"] as? String ?: "Quiz/Exam ($id)"
                val rawType = map["quizType"] as? String ?: "Quiz"
                val type = when (rawType.lowercase()) {
                    "exam" -> "Exam"
                    else -> "Quiz"
                }

                val rawQuestions = map["questions"]
                var questionsCount = 0
                if (rawQuestions is List<*>) questionsCount = rawQuestions.size
                else if (rawQuestions is Map<*, *>) {
                    questionsCount = rawQuestions.keys.count { key -> key.toString().toIntOrNull() != null }
                }

                val maxPoints = questionsCount.toDouble()
                if (maxPoints > 0) {
                    metadata[id] = ActivityMetadata(type, maxPoints, title)
                }
            }
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error fetching quiz metadata: ${e.message}")
        }

        // Fetch assignments from Firestore
        try {
            val assignmentsSnapshot = firestore.collection("assignments")
                .whereEqualTo("classId", assignmentId!!)
                .whereEqualTo("academicTerm", gradingPeriod!!)
                .get().await()

            assignmentsSnapshot.documents.forEach { doc ->
                val id = doc.id
                val title = doc.getString("title") ?: "Assignment ($id)"
                val rawType = doc.getString("type") ?: "assignment"
                val maxPoints = doc.getDouble("maxPoints") ?: doc.getDouble("totalPoints") ?: 100.0

                val type = when (rawType.lowercase()) {
                    "assignment", "project", "homework", "activity" -> "Assignment"
                    else -> ""
                }

                if (type.isNotBlank() && maxPoints > 0.0) {
                    metadata[id] = ActivityMetadata(type, maxPoints, title)
                }
            }
        } catch (e: Exception) {
            Log.e("GradeDebug", "Error fetching assignment metadata: ${e.message}")
        }

        return metadata
    }

    // --- IMPLEMENTATION OF OnStudentClickListener ---
    override fun onStudentClicked(student: Student) {
        if (areGradesPublished) {
            Toast.makeText(this, "Grades are published and cannot be edited.", Toast.LENGTH_SHORT).show()
            return
        }

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Cannot fetch details due to missing context.", Toast.LENGTH_SHORT).show()
            return
        }

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Cannot fetch details due to missing context.", Toast.LENGTH_SHORT).show()
            return
        }


        // Check cache first for instant loading
        val cachedScores = detailedScoresCache[student.id]
        if (cachedScores != null) {
            showEditableScoreDialog(student, cachedScores)
        } else {
            showDetailedScoreDialog(student)
        }
    }

    // --- Function to Fetch and Display Detailed Scores ---
    private fun showDetailedScoreDialog(student: Student) {
        val dialog = ProgressDialog(this).apply {
            setMessage("Fetching detailed scores for ${student.lastName}...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val detailedScores = fetchDetailedScores(student.id)
                detailedScoresCache[student.id] = detailedScores
                dialog.dismiss()
                showEditableScoreDialog(student, detailedScores)
            } catch (e: Exception) {
                dialog.dismiss()
                Log.e("ScoreDetail", "Error fetching detailed scores: ${e.message}", e)
                Toast.makeText(this@GradeInputActivity, "Error fetching details: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- OPTIMIZED: Faster detailed scores fetch using pre-loaded metadata ---
    private suspend fun fetchDetailedScores(studentDocId: String): DetailedScores {
        activityMetadata = cachedActivityMetadata

        val studentUserUid = studentUidToDocIdMap.entries.firstOrNull { it.value == studentDocId }?.key
            ?: return DetailedScores(Pair(0,0), 0, emptyList(), emptyList(), emptyList())

        // CHECK FOR MANUAL OVERRIDES FIRST
        var attendanceDetails = try {
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

        var recitationDetails = try {
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

    // ADD THESE HELPER FUNCTIONS FOR ATTENDANCE/RECITATION
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
            if (statuses[studentDocId] == "Present") presentCount++
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
            val recitationLong = doc.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
            val recitationPoints = recitationLong.mapValues { it.value.toInt() }
            totalRecitationPoints += recitationPoints[studentDocId] ?: 0
        }

        return totalRecitationPoints
    }

    // UPDATE THIS FUNCTION TO CHECK FOR OVERRIDES
    private suspend fun fetchStudentActivityScoresWithOverrides(studentUserUid: String, studentDocId: String): List<ActivityScoreData> {
        val studentScores = mutableListOf<ActivityScoreData>()

        // Group activity IDs by type for batch querying
        val quizIds = activityMetadata.filter { it.value.type == "Quiz" || it.value.type == "Exam" }.keys.toList()
        val assignmentIds = activityMetadata.filter { it.value.type == "Assignment" }.keys.toList()

        // Fetch quiz scores
        if (quizIds.isNotEmpty()) {
            val quizChunks = quizIds.chunked(10)
            for (chunk in quizChunks) {
                val scores = firestore.collection("quizResults")
                    .whereIn("quizId", chunk)
                    .whereEqualTo("studentId", studentUserUid)
                    .get().await()
                studentScores.addAll(processQuizScores(scores.documents))
            }
        }

        // Fetch assignment scores
        if (assignmentIds.isNotEmpty()) {
            val assignmentChunks = assignmentIds.chunked(10)
            for (chunk in assignmentChunks) {
                val scores = firestore.collection("submissions")
                    .whereIn("assignmentId", chunk)
                    .whereEqualTo("studentId", studentUserUid)
                    .get().await()
                studentScores.addAll(processAssignmentScores(scores.documents))
            }
        }

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

    private fun processQuizScores(docs: List<DocumentSnapshot>): List<ActivityScoreData> {
        return docs.mapNotNull { doc ->
            val activityId = doc.getString("quizId") ?: return@mapNotNull null
            val metadata = activityMetadata[activityId] ?: return@mapNotNull null

            val rawScore = extractScore(doc)
            val percentage = if (metadata.maxPoints > 0) (rawScore / metadata.maxPoints) * 100.0 else 0.0
            val score50Base = maxOf(50.0, percentage).roundToTwoDecimals()

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
            val percentage = if (metadata.maxPoints > 0) (rawScore / metadata.maxPoints) * 100.0 else 0.0
            val score50Base = maxOf(50.0, percentage).roundToTwoDecimals()

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

    // --- NEW: Editable dialog with score modification ---
    private fun showEditableScoreDialog(student: Student, detailedScores: DetailedScores) {
        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_editable_score_details, null)

        view.findViewById<TextView>(R.id.tvDialogTitle).text =
            if (areGradesPublished) "View Scores (Published): ${student.lastName}, ${student.firstName}"
            else "Edit Scores: ${student.lastName}, ${student.firstName}"

        val etAttendancePresent = view.findViewById<EditText>(R.id.etAttendancePresent)
        val etAttendanceTotal = view.findViewById<EditText>(R.id.etAttendanceTotal)
        val tvAttendancePercentage = view.findViewById<TextView>(R.id.tvAttendancePercentage)

        val etRecitationPoints = view.findViewById<EditText>(R.id.etRecitationPoints)
        val tvRecitationPercentage = view.findViewById<TextView>(R.id.tvRecitationPercentage)

        // SET DEFAULT VALUES IF ZERO (NO EXISTING DATA)
        val defaultPresent = if (detailedScores.attendanceDetails.first == 0) 1 else detailedScores.attendanceDetails.first
        val defaultTotal = if (detailedScores.attendanceDetails.second == 0) 1 else detailedScores.attendanceDetails.second
        val defaultRecitation = if (detailedScores.recitationDetails == 0) 1 else detailedScores.recitationDetails

        // Set current values
        etAttendancePresent.setText(defaultPresent.toString())
        etAttendanceTotal.setText(defaultTotal.toString())
        updateAttendancePercentage(etAttendancePresent, etAttendanceTotal, tvAttendancePercentage)

        etRecitationPoints.setText(defaultRecitation.toString())
        updateRecitationPercentage(etRecitationPoints.text.toString().toIntOrNull() ?: 0, tvRecitationPercentage)

        // DISABLE EDITING IF PUBLISHED
        if (areGradesPublished) {
            etAttendancePresent.isEnabled = false
            etAttendanceTotal.isEnabled = false
            etRecitationPoints.isEnabled = false
            etAttendancePresent.alpha = 0.4f
            etAttendanceTotal.alpha = 0.4f
            etRecitationPoints.alpha = 0.4f
        } else {
            // ADD TEXT WATCHERS FOR REAL-TIME UPDATES ONLY IF NOT PUBLISHED
            etAttendancePresent.addTextChangedListener(createAttendanceWatcher(etAttendancePresent, etAttendanceTotal, tvAttendancePercentage))
            etAttendanceTotal.addTextChangedListener(createAttendanceWatcher(etAttendancePresent, etAttendanceTotal, tvAttendancePercentage))
            etRecitationPoints.addTextChangedListener(createRecitationWatcher(etRecitationPoints, tvRecitationPercentage))
        }

        // Setup editable recycler views with published state
        setupEditableActivityRecyclerView(
            view.findViewById(R.id.rvQuizScores),
            detailedScores.quizScores,
            student.id,
            "Quiz",
            isEditable = !areGradesPublished // Disable if published
        )
        setupEditableActivityRecyclerView(
            view.findViewById(R.id.rvExamScores),
            detailedScores.examScores,
            student.id,
            "Exam",
            isEditable = false // Exams are always not editable
        )
        setupEditableActivityRecyclerView(
            view.findViewById(R.id.rvAssignmentScores),
            detailedScores.assignmentScores,
            student.id,
            "Assignment",
            isEditable = !areGradesPublished // Disable if published
        )

        builder.setView(view)

        if (!areGradesPublished) {
            builder.setPositiveButton("Save Changes") { dialog, _ ->
                // SAVE BOTH ACTIVITY SCORES AND ATTENDANCE/RECITATION
                saveModifiedAttendanceRecitation(
                    student.id,
                    etAttendancePresent.text.toString().toIntOrNull() ?: 0,
                    etAttendanceTotal.text.toString().toIntOrNull() ?: 1,
                    etRecitationPoints.text.toString().toIntOrNull() ?: 0
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

    private fun setupEditableActivityRecyclerView(
        recyclerView: RecyclerView,
        scores: List<ActivityScoreData>,
        studentId: String,
        category: String,
        isEditable: Boolean = true
    ) {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = EditableActivityScoreAdapter(
            scores,
            studentId,
            category,
            isEditable,
            areGradesPublished, // NEW: Pass published state
            onScoreUpdate = { updatedScore ->
                if (!areGradesPublished) {
                    updateCachedScore(studentId, updatedScore, category)
                    recalculateStudentGrade(studentId)
                }
            }
        )
        recyclerView.visibility = if (scores.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateCachedScore(studentId: String, updatedScore: ActivityScoreData, category: String) {
        val cachedScores = detailedScoresCache[studentId] ?: return

        when (category) {
            "Quiz" -> {
                val updatedList = cachedScores.quizScores.map {
                    if (it.activityId == updatedScore.activityId) updatedScore else it
                }
                detailedScoresCache[studentId] = cachedScores.copy(quizScores = updatedList)
            }
            "Exam" -> {
                val updatedList = cachedScores.examScores.map {
                    if (it.activityId == updatedScore.activityId) updatedScore else it
                }
                detailedScoresCache[studentId] = cachedScores.copy(examScores = updatedList)
            }
            "Assignment" -> {
                val updatedList = cachedScores.assignmentScores.map {
                    if (it.activityId == updatedScore.activityId) updatedScore else it
                }
                detailedScoresCache[studentId] = cachedScores.copy(assignmentScores = updatedList)
            }
        }

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
                            "isPublished" to true, // NEW: Mark as published
                            "publishedAt" to System.currentTimeMillis(), // NEW: Publication timestamp
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

    private fun updateAttendancePercentage(etPresent: EditText, etTotal: EditText, tvPercentage: TextView) {
        val present = etPresent.text.toString().toIntOrNull() ?: 0
        val total = etTotal.text.toString().toIntOrNull() ?: 1
        val percentage = if (total > 0) (present.toDouble() / total) * 100 else 0.0
        val percentage50Base = maxOf(50.0, percentage)
        tvPercentage.text = "Percentage: ${"%.2f".format(percentage50Base)}% (50% base)"
    }

    private fun updateRecitationPercentage(points: Int, tvPercentage: TextView) {
        val percentage = if (points >= 5) 100.0 else (points.toDouble() / 5.0) * 100.0
        val percentage50Base = maxOf(50.0, percentage)
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val points = etPoints.text.toString().toIntOrNull() ?: 0
                updateRecitationPercentage(points, tvPercentage)
            }
        }
    }

    private fun saveModifiedAttendanceRecitation(studentId: String, present: Int, total: Int, recitationPoints: Int) {
        val cachedScores = detailedScoresCache[studentId] ?: return

        // Update the cache with new values
        detailedScoresCache[studentId] = cachedScores.copy(
            attendanceDetails = Pair(present, total),
            recitationDetails = recitationPoints
        )

        // Recalculate the main grade
        recalculateStudentGrade(studentId)
    }

    private fun recalculateStudentGrade(studentId: String) {
        val cachedScores = detailedScoresCache[studentId] ?: return

        // Recalculate category averages based on modified scores
        val quizAverage = cachedScores.quizScores.map { it.score50Base }.average()
        val examAverage = cachedScores.examScores.map { it.score50Base }.average()
        val assignmentAverage = cachedScores.assignmentScores.map { it.score50Base }.average()

        // RECALCULATE ATTENDANCE AND RECITATION
        val (present, total) = cachedScores.attendanceDetails
        val attendanceScoreRaw = if (total > 0) (present.toDouble() / total) * 100 else 50.0
        val attendanceAverage = maxOf(50.0, attendanceScoreRaw)

        val recitationPoints = cachedScores.recitationDetails
        val recitationScore = if (recitationPoints >= 5) 100.0 else (recitationPoints.toDouble() / 5.0) * 100.0
        val recitationAverage = maxOf(50.0, recitationScore)

        // Update the main adapter
        if (::gradeAdapter.isInitialized) {
            gradeAdapter.updateStudentGrade(
                studentId,
                quizAverage.roundToTwoDecimals(),
                examAverage.roundToTwoDecimals(),
                assignmentAverage.roundToTwoDecimals()
            )
            // UPDATE ATTENDANCE AND RECITATION IN THE MAIN ADAPTER
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
        val cachedScores = detailedScoresCache[studentId] ?: return

        lifecycleScope.launch {
            try {
                saveModifiedActivityScores(studentId, cachedScores)
                saveAttendanceRecitationOverrides(studentId, cachedScores) // ADD THIS
                Toast.makeText(this@GradeInputActivity, "Scores updated successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ScoreEdit", "Error saving modified scores: ${e.message}", e)
                Toast.makeText(this@GradeInputActivity, "Error saving scores: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // FIXED: AUTOMATICALLY CREATE RECORDS IF NOT EXISTS
    private suspend fun saveModifiedActivityScores(studentId: String, scores: DetailedScores) {
        val studentUserUid = studentUidToDocIdMap.entries.firstOrNull { it.value == studentId }?.key ?: return

        // Save all modified scores - CREATE NEW RECORDS IF NOT EXISTS
        val allScores = scores.quizScores + scores.examScores + scores.assignmentScores

        allScores.forEach { scoreData ->
            when (scoreData.activityType) {
                "Quiz", "Exam" -> {
                    // ALWAYS CREATE OR UPDATE - USE COMPOSITE ID FOR CONSISTENCY
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

                    // Use composite document ID to avoid duplicates
                    val docId = "${scoreData.activityId}_${studentUserUid}"
                    firestore.collection("quizResults")
                        .document(docId)
                        .set(quizResultData, SetOptions.merge())
                        .await()

                    Log.d("ScoreEdit", "Saved quiz result: $docId with score ${scoreData.rawScore}")
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
                        .await()

                    Log.d("ScoreEdit", "Saved assignment submission: $docId with score ${scoreData.rawScore}")
                }
            }
        }
    }

    // ADD THIS NEW FUNCTION FOR ATTENDANCE/RECITATION OVERRIDES
    private suspend fun saveAttendanceRecitationOverrides(studentId: String, scores: DetailedScores) {
        val (present, total) = scores.attendanceDetails
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

        // Use composite ID for consistency
        val docId = "${studentId}_${assignmentId!!}_${gradingPeriod!!}"
        firestore.collection("manualGradeOverrides")
            .document(docId)
            .set(overrideData, SetOptions.merge())
            .await()

        Log.d("ScoreEdit", "Saved attendance/recitation override: $docId - Present: $present/$total, Recitation: $recitationPoints")
    }

    // --- KEEP EXISTING METHODS (with fixes) ---

    private fun Double.roundToTwoDecimals(): Double {
        return String.format("%.2f", this).toDouble()
    }

    // --- fetchAttendanceAndRecitationScores (A/R calculation) ---
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

        val attendanceSnapshot = firestore.collection("dailyAttendanceRecords")
            .whereEqualTo("assignmentId", assignmentId!!)
            .whereEqualTo("academicTerm", gradingPeriod!!)
            .whereEqualTo("semester", semester)
            .get().await()

        if (attendanceSnapshot.isEmpty) {
            Log.d("GradeDebug", "No daily attendance records found for this term.")
        } else {
            Log.d("GradeDebug", "Fetched ${attendanceSnapshot.size()} attendance records.")
        }

        val studentAttendanceCounts = mutableMapOf<String, Int>()
        var totalClassSessions = 0
        val studentRecitationPoints = mutableMapOf<String, Int>()

        enrolledStudents.forEach { student ->
            studentAttendanceCounts[student.id] = 0
            studentRecitationPoints[student.id] = 0
        }

        attendanceSnapshot.documents.forEach { doc ->
            val statuses = doc.get("statuses") as? Map<String, String> ?: emptyMap()
            totalClassSessions++

            statuses.forEach { (studentId, status) -> // studentId here is Student Document ID
                if (status == "Present") {
                    if (studentAttendanceCounts.containsKey(studentId)) {
                        studentAttendanceCounts[studentId] = studentAttendanceCounts[studentId]!! + 1
                    }
                }
            }

            val recitationLong = doc.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
            val recitationPoints = recitationLong.mapValues { it.value.toInt() }

            recitationPoints.forEach { (studentUid, points) -> // studentUid here is Student Document ID
                if (studentRecitationPoints.containsKey(studentUid)) {
                    studentRecitationPoints[studentUid] = studentRecitationPoints[studentUid]!! + points
                }
            }
        }

        enrolledStudents.forEach { student ->
            val studentDocId = student.id
            val grades = studentGradesData[studentDocId] ?: GradeInputAdapter.GradeData()

            val presentCount = studentAttendanceCounts[studentDocId] ?: 0
            val attendanceScoreRaw = if (totalClassSessions > 0) {
                (presentCount.toDouble() / totalClassSessions.toDouble()) * 100.0
            } else { 50.0 } // Default 50.0 kung walang sessions

            // 2. APPLY 50% BASE
            grades.attendance = maxOf(50.0, attendanceScoreRaw).roundToTwoDecimals()

            val totalRecitationPoints = studentRecitationPoints[studentDocId] ?: 0

            val recitationScore = if (totalRecitationPoints >= 5) {
                100.0
            } else {
                (totalRecitationPoints.toDouble() / 5.0) * 100.0
            }

            // 3. APPLY 50% BASE
            grades.recitation = maxOf(50.0, recitationScore).roundToTwoDecimals()

            studentGradesData[studentDocId] = grades
            Log.d("GradeDebug", "A/R Calculated for ${student.lastName} (${student.id}): Att=${grades.attendance}, Rec=${grades.recitation}")
        }
    }

    // --- REVISED: fetchQuizExamAndAssignmentScores ---
    private suspend fun fetchQuizExamAndAssignmentScores(
        enrolledStudents: List<Student>,
        studentGradesData: MutableMap<String, GradeInputAdapter.GradeData>
    ) {
        val activityMetadata = mutableMapOf<String, Pair<String, Double>>()
        val fetchJobs = mutableListOf<Deferred<List<DocumentSnapshot>>>()
        val allScoresDocuments = mutableListOf<DocumentSnapshot>()

        // FIX: Declare variables outside try block for correct scoping
        var quizMetadataDocuments: List<DataSnapshot> = emptyList()
        var assignmentsSnapshot: List<DocumentSnapshot> = emptyList()

        try {
            // --- HAKBANG 1 & 2: FETCH & PROCESS METADATA ---
            Log.d("GradeDebug", "Querying activity metadata...")

            val quizzesTask = realtimeDb.reference.child("quizzes").orderByChild("assignmentId").equalTo(assignmentId!!).get()
            // Assign result to outside variable
            quizMetadataDocuments = quizzesTask.await().children.toList()

            // Assign result to outside variable
            assignmentsSnapshot = firestore.collection("assignments")
                .whereEqualTo("classId", assignmentId!!)
                .whereEqualTo("academicTerm", gradingPeriod!!)
                .get().await().documents

            // Processing logic for Quizzes/Exams metadata
            quizMetadataDocuments.forEach { dataSnapshot: DataSnapshot ->
                val docId = dataSnapshot.key ?: return@forEach
                val map = dataSnapshot.value as? Map<*, *> ?: return@forEach
                val activityPeriod = map["academicTerm"] as? String // ⬅️ Check the field name here
                if (activityPeriod != gradingPeriod) return@forEach
                val activityId = docId

                var questionsCount = 0
                val rawQuestions = map["questions"]
                if (rawQuestions is List<*>) { questionsCount = rawQuestions.size }
                else if (rawQuestions is Map<*, *>) { questionsCount = rawQuestions.keys.count { key -> key.toString().toIntOrNull() != null } }

                val maxPoints = questionsCount.toDouble()
                val rawType = map["quizType"] as? String ?: "Quiz"
                val type = rawType.lowercase().let {
                    when (it) { "quiz" -> "Quiz"; "exam" -> "Exam"; else -> "Quiz" }
                }
                if (activityId.isNotBlank() && maxPoints > 0.0) {
                    activityMetadata[activityId] = Pair(type, maxPoints)
                }
            }

            // Processing logic for Assignments metadata
            assignmentsSnapshot.forEach { doc ->
                val activityId = doc.id
                val rawType = doc.getString("type") ?: "assignment"
                val maxPoints = doc.getDouble("maxPoints") ?: doc.getDouble("totalPoints") ?: 100.0
                val type = when (rawType.lowercase()) {
                    "assignment", "project", "homework", "activity" -> "Assignment"
                    else -> ""
                }
                if (type.isNotBlank() && maxPoints > 0.0) {
                    activityMetadata[activityId] = Pair(type, maxPoints)
                }
            }

            Log.d("GradeDebug", "Total valid activities fetched after filtering: ${activityMetadata.size}")

        } catch (e: Exception) {
            Log.e("GradeDebug", "Error fetching activity metadata: ${e.message}")
            return
        }

        if (activityMetadata.isEmpty()) {
            Log.d("GradeDebug", "No valid activities found. Cannot proceed to fetch scores.")
            return
        }

        // --- HAKBANG 3: FETCH SCORES MULA SA FIRESTORE CONCURRENTLY ---

        val quizIds = activityMetadata.filter { it.value.first == "Quiz" || it.value.first == "Exam" }.keys.toList()
        val assignmentIds = activityMetadata.filter { it.value.first == "Assignment" }.keys.toList()

        // Fetch Quiz Scores (Concurrent Chunking)
        if (quizIds.isNotEmpty()) {
            val quizChunks = quizIds.chunked(10)
            Log.d("GradeDebug", "Fetching Quiz/Exam scores concurrently in ${quizChunks.size} chunks from 'quizResults'.")
            for (chunk in quizChunks) {
                val job = lifecycleScope.async<List<DocumentSnapshot>> {
                    try {
                        return@async firestore.collection("quizResults").whereIn("quizId", chunk).get().await().documents
                    }
                    catch (e: Exception) {
                        Log.e("GradeDebug", "Error in Quiz/Exam chunk query: ${e.message}");
                        return@async emptyList<DocumentSnapshot>()
                    }
                }
                fetchJobs.add(job)
            }
        }

        // Fetch Assignment Scores (Concurrent Chunking)
        if (assignmentIds.isNotEmpty()) {
            val assignmentChunks = assignmentIds.chunked(10)
            Log.d("GradeDebug", "Fetching Assignment scores concurrently in ${assignmentChunks.size} chunks from 'submissions'.")
            for (chunk in assignmentChunks) {
                val job = lifecycleScope.async<List<DocumentSnapshot>> {
                    try {
                        val documents = firestore.collection("submissions").whereIn("assignmentId", chunk).get().await().documents
                        return@async documents
                    }
                    catch (e: Exception) {
                        Log.e("submission", "Error in Assignment chunk query: ${e.message}");
                        return@async emptyList<DocumentSnapshot>()
                    }
                }
                fetchJobs.add(job)
            }
        }

        // HINTAYIN ANG LAHAT NG JOB AT PAGSAMAHIN ANG MGA RESULTA
        if (fetchJobs.isNotEmpty()) {
            val allResults = fetchJobs.awaitAll()
            allResults.forEach { docs -> allScoresDocuments.addAll(docs) }
        }

        Log.d("GradeDebug", "Fetched ${allScoresDocuments.size} score documents in total.")

        // --- HAKBANG 4: REVISED AGGREGATION & MISSING SCORE INJECTION ---

        // Group raw score documents by Student Document ID
        val studentSubmissions = allScoresDocuments
            .mapNotNull { doc ->
                // FIX: CORRECT MAPPING USAGE. Map the fetched User UID (doc.getString("studentId")) to the Student Document ID.
                val studentDocId = studentUidToDocIdMap[doc.getString("studentId")]
                if (studentDocId != null) studentDocId to doc else null
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }

        studentGradesData.keys.forEach { studentId ->

            // Map to store the best 50%-based percentage score for each activity for this student.
            val studentBestActivityScores = mutableMapOf<String, Double>()

            val submissionsForStudent = studentSubmissions[studentId] ?: emptyList()

            // 1. Process all submissions to find the single best 50%-based score per activity
            submissionsForStudent.forEach { doc ->
                val activityId = doc.getString("quizId") ?: doc.getString("assignmentId") ?: return@forEach
                val metadata = activityMetadata[activityId] ?: return@forEach
                val maxPoints = metadata.second

                // Extract score (using the robust fallback logic)
                var score = 0.0
                score = (doc.get("grade") as? Double) ?: (doc.get("grade") as? Long)?.toDouble() ?: (doc.get("grade") as? String)?.toDoubleOrNull() ?: score
                if (score <= 0.0) score = (doc.get("score") as? Double) ?: (doc.get("score") as? Long)?.toDouble() ?: (doc.get("score") as? String)?.toDoubleOrNull() ?: score
                if (score <= 0.0) score = (doc.get("rawScore") as? Double) ?: (doc.get("rawScore") as? Long)?.toDouble() ?: (doc.get("rawScore") as? String)?.toDoubleOrNull() ?: score

                val rawPercentage = if (maxPoints > 0.0) (score / maxPoints) * 100.0 else 0.0

                // Apply 50% base to the INDIVIDUAL activity score
                val finalPercentage = maxOf(50.0, rawPercentage)

                // Store the highest 50%-based score found so far for this activity
                val currentBest = studentBestActivityScores[activityId] ?: 0.0
                if (finalPercentage > currentBest) {
                    studentBestActivityScores[activityId] = finalPercentage
                }
            }

            // Variables for final category averaging
            val categoryTotalScores = mutableMapOf("Quiz" to 0.0, "Exam" to 0.0, "Assignment" to 0.0)
            val categoryActivityCount = mutableMapOf("Quiz" to 0, "Exam" to 0, "Assignment" to 0)

            // 2. Iterate through ALL activities (present and missing) and tally scores
            activityMetadata.forEach { (activityId, metadata) ->
                val type = metadata.first

                // 3. Inject Missing Score (50%) or use the calculated Best Score
                val finalScoreForActivity = studentBestActivityScores[activityId] ?: 50.0

                // Tally the results
                if (categoryTotalScores.containsKey(type)) {
                    categoryTotalScores[type] = categoryTotalScores[type]!! + finalScoreForActivity
                    categoryActivityCount[type] = categoryActivityCount[type]!! + 1
                }
            }

            // 4. Calculate Final Average for each category
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
            Log.d("GradeDebug", "Final Scores (Revised) for $studentId: Q=${grades.quiz} E=${grades.exam} A=${grades.assignment}")
        }
    }

    // 🚨 loadGradingData (FIXED AND COMPLETED)
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
                    .whereEqualTo("status", "Admitted")
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

    // FINAL GRADE SAVING LOGIC (No changes needed)
    private fun saveFinalGrades() {
        if (!::gradeAdapter.isInitialized) {
            Toast.makeText(this, "Grades not loaded yet. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        tvLoadingStatus.text = "Saving final grades..."

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
                            "isSubmitted" to true,
                            "timestamp" to System.currentTimeMillis()
                        )

                        firestore.collection("finalStudentGrades")
                            .document(documentId)
                            .set(data, SetOptions.merge())
                            .await()
                    }
                }

                saveJobs.awaitAll()

                tvLoadingStatus.text = "✅ Grades successfully saved for ${gradesToSave.size} students."
                Toast.makeText(this@GradeInputActivity, "All grades saved successfully!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("GradeInput", "Error saving grades: ${e.message}", e)
                tvLoadingStatus.text = "Error saving grades."
                Toast.makeText(this@GradeInputActivity, "Saving error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
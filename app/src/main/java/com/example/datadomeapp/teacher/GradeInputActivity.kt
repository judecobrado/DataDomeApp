package com.example.datadomeapp.teacher

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
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

// --- NEW DATA CLASS FOR DETAILED DISPLAY ---
data class ActivityScoreData(
    val title: String,
    val score50Base: Double,
    val rawScore: Double,
    val maxPoints: Double
)
// --- END NEW DATA CLASS ---

// --- INTERFACE AND DATA CLASSES ---
interface OnStudentClickListener {
    fun onStudentClicked(student: Student)
}

data class DetailedScores(
    val attendanceDetails: Pair<Int, Int>, // (Present Count, Total Sessions)
    val recitationDetails: Int, // Total Recitation Points
    val quizScores: List<ActivityScoreData>,
    val examScores: List<ActivityScoreData>,
    val assignmentScores: List<ActivityScoreData>
)
// --- END INTERFACE AND DATA CLASSES ---

class GradeInputActivity : AppCompatActivity(), OnStudentClickListener {

    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance()

    // Map: Firebase UID -> Student Document ID (Key is UID, Value is Doc ID)
    private var studentUidToDocIdMap: Map<String, String> = emptyMap()

    private lateinit var tvGradeTitle: TextView
    private lateinit var tvLoadingStatus: TextView
    private lateinit var recyclerViewGrades: RecyclerView
    private lateinit var btnSaveGrades: Button
    private lateinit var gradeAdapter: GradeInputAdapter

    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null
    private var gradingPeriod: String? = null

    // Function to safely round Double values
    private fun Double.roundToTwoDecimals(): Double {
        return String.format("%.2f", this).toDouble()
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
        btnSaveGrades = findViewById(R.id.btnSaveGrades)
        btnSaveGrades.visibility = View.GONE

        recyclerViewGrades.layoutManager = LinearLayoutManager(this)

        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Log.e("GradeDebug", "Missing Intent Data! Cannot proceed.")
            Toast.makeText(this, "Error: Missing grade context.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvGradeTitle.text = "$gradingPeriod Grades\n$className ($subjectCode)"

        btnSaveGrades.setOnClickListener {
            saveFinalGrades()
        }

        loadGradingData()
    }

    // --- IMPLEMENTATION OF OnStudentClickListener ---
    override fun onStudentClicked(student: Student) {
        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || gradingPeriod.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Cannot fetch details due to missing context.", Toast.LENGTH_SHORT).show()
            return
        }
        showDetailedScoreDialog(student)
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
                // Pass the student's Firestore Document ID
                val detailedScores = fetchDetailedScores(student.id)

                dialog.dismiss()

                displayScoreDetails(student, detailedScores)

            } catch (e: Exception) {
                dialog.dismiss()
                Log.e("ScoreDetail", "Error fetching detailed scores: ${e.message}", e)
                Toast.makeText(this@GradeInputActivity, "Error fetching details: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Function to Fetch Detailed Scores (REVISED for ActivityScoreData) ---
    private suspend fun fetchDetailedScores(studentDocId: String): DetailedScores {
        // A. CONTEXT & METADATA
        val termDoc = firestore.document("systemSettings/currentTerm").get().await()
        val academicTerm = termDoc.getString("academicTerm") ?: ""
        val semester = termDoc.getString("semester") ?: ""

        // Triple: ID -> (Type, MaxPoints, Title)
        val activityMetadata = mutableMapOf<String, Triple<String, Double, String>>()

        // 1. Fetch Quiz/Exam Metadata (Realtime DB)
        val quizzesTask = realtimeDb.reference.child("quizzes").orderByChild("assignmentId").equalTo(assignmentId!!).get()
        quizzesTask.await().children.forEach { dataSnapshot ->
            val id = dataSnapshot.key ?: return@forEach
            val map = dataSnapshot.value as? Map<*, *> ?: return@forEach
            val title = map["title"] as? String ?: "Quiz/Exam ($id)"

            val rawQuestions = map["questions"]
            var questionsCount = 0
            if (rawQuestions is List<*>) { questionsCount = rawQuestions.size }
            else if (rawQuestions is Map<*, *>) { questionsCount = rawQuestions.keys.count { key -> key.toString().toIntOrNull() != null } }

            val maxPoints = questionsCount.toDouble()
            val rawType = map["quizType"] as? String ?: "Quiz"
            val type = rawType.lowercase().let { if (it == "exam") "Exam" else "Quiz" }

            if (maxPoints > 0.0) { activityMetadata[id] = Triple(type, maxPoints, title) }
        }

        // 2. Fetch Assignment Metadata (Firestore)
        val assignmentsSnapshot = firestore.collection("assignments").whereEqualTo("classId", assignmentId!!).get().await()
        assignmentsSnapshot.documents.forEach { doc ->
            val id = doc.id
            val title = doc.getString("title") ?: "Assignment ($id)"
            val rawType = doc.getString("type") ?: "assignment"
            val maxPoints = doc.getDouble("maxPoints") ?: doc.getDouble("totalPoints") ?: 100.0
            val type = when (rawType.lowercase()) {
                "assignment", "project", "homework", "activity" -> "Assignment"
                else -> ""
            }
            if (type.isNotBlank() && maxPoints > 0.0) { activityMetadata[id] = Triple(type, maxPoints, title) }
        }

        // B. ATTENDANCE & RECITATION
        var presentCount = 0
        var totalClassSessions = 0
        var totalRecitationPoints = 0

        val attendanceSnapshot = firestore.collection("dailyAttendanceRecords")
            .whereEqualTo("assignmentId", assignmentId!!)
            .whereEqualTo("academicTerm", academicTerm)
            .whereEqualTo("semester", semester)
            .get().await()

        attendanceSnapshot.documents.forEach { doc ->
            totalClassSessions++
            val statuses = doc.get("statuses") as? Map<String, String> ?: emptyMap()
            // studentDocId used here
            if (statuses[studentDocId] == "Present") { presentCount++ }

            val recitationLong = doc.get("recitationPoints") as? Map<String, Long> ?: emptyMap()
            val recitationPoints = recitationLong.mapValues { it.value.toInt() }
            // studentDocId used here
            totalRecitationPoints += recitationPoints[studentDocId] ?: 0
        }

        // C. QUIZ, EXAM, ASSIGNMENT SCORES
        // FIX: Look up the User UID using the studentDocId (Needed for querying quizResults/submissions)
        val studentUserUid = studentUidToDocIdMap.entries.firstOrNull { it.value == studentDocId }?.key
            ?: return DetailedScores(Pair(0,0), 0, emptyList(), emptyList(), emptyList())


        val studentBestActivityScores = mutableMapOf<String, ActivityScoreData>() // ActivityId -> Best ActivityScoreData
        val allScoreDocs = mutableListOf<DocumentSnapshot>()

        val quizIds = activityMetadata.filter { it.value.first == "Quiz" || it.value.first == "Exam" }.keys.toList()
        val assignmentIds = activityMetadata.filter { it.value.first == "Assignment" }.keys.toList()

        // Fetch Quiz/Exam Scores (Targeted by User UID)
        if (quizIds.isNotEmpty()) {
            val quizJobs = quizIds.chunked(10).map { chunk ->
                lifecycleScope.async<List<DocumentSnapshot>> {
                    firestore.collection("quizResults")
                        .whereIn("quizId", chunk)
                        .whereEqualTo("studentId", studentUserUid) // Filter by User UID
                        .get().await().documents
                }
            }
            quizJobs.awaitAll().forEach { allScoreDocs.addAll(it) }
        }

        // Fetch Assignment Scores (Targeted by User UID)
        if (assignmentIds.isNotEmpty()) {
            val assignmentJobs = assignmentIds.chunked(10).map { chunk ->
                lifecycleScope.async<List<DocumentSnapshot>> {
                    firestore.collection("submissions")
                        .whereIn("assignmentId", chunk)
                        .whereEqualTo("studentId", studentUserUid) // Filter by User UID
                        .get().await().documents
                }
            }
            assignmentJobs.awaitAll().forEach { allScoreDocs.addAll(it) }
        }

        // Process all score documents to find the best 50%-based score and raw data per activity
        allScoreDocs.forEach { doc ->
            val activityId = doc.getString("quizId") ?: doc.getString("assignmentId") ?: return@forEach
            val (type, maxPoints, title) = activityMetadata[activityId] ?: return@forEach

            var rawScore = 0.0
            // Robust score extraction logic
            rawScore = (doc.get("grade") as? Double) ?: (doc.get("grade") as? Long)?.toDouble() ?: (doc.get("grade") as? String)?.toDoubleOrNull() ?: rawScore
            if (rawScore <= 0.0) rawScore = (doc.get("score") as? Double) ?: (doc.get("score") as? Long)?.toDouble() ?: (doc.get("score") as? String)?.toDoubleOrNull() ?: rawScore
            if (rawScore <= 0.0) rawScore = (doc.get("rawScore") as? Double) ?: (doc.get("rawScore") as? Long)?.toDouble() ?: (doc.get("rawScore") as? String)?.toDoubleOrNull() ?: rawScore

            val rawPercentage = if (maxPoints > 0.0) (rawScore / maxPoints) * 100.0 else 0.0
            val score50Base = maxOf(50.0, rawPercentage).roundToTwoDecimals()

            val newScoreData = ActivityScoreData(
                title = title,
                score50Base = score50Base,
                rawScore = rawScore,
                maxPoints = maxPoints
            )

            // Store the highest 50%-based score found so far for this activity
            val currentBestScore50Base = studentBestActivityScores[activityId]?.score50Base ?: 0.0
            if (newScoreData.score50Base > currentBestScore50Base) {
                studentBestActivityScores[activityId] = newScoreData
            }
        }

        // Compile final list of scores (including 50% for missing)
        activityMetadata.forEach { (activityId, metadata) ->
            if (!studentBestActivityScores.containsKey(activityId)) {
                // Inject 50% base for missing submissions, keeping raw score/max points at 0/MaxPoints
                val (type, maxPoints, title) = metadata
                val missingScore = ActivityScoreData(
                    title = title,
                    score50Base = 50.0,
                    rawScore = 0.0,
                    maxPoints = maxPoints
                )
                studentBestActivityScores[activityId] = missingScore
            }
        }

        val allFinalScores = studentBestActivityScores.values.toList()

        // Filter scores based on the type stored in metadata
        val quizList = allFinalScores.filter { scoreData ->
            activityMetadata.values.any { it.third == scoreData.title && it.first == "Quiz" }
        }
        val examList = allFinalScores.filter { scoreData ->
            activityMetadata.values.any { it.third == scoreData.title && it.first == "Exam" }
        }
        val assignmentList = allFinalScores.filter { scoreData ->
            activityMetadata.values.any { it.third == scoreData.title && it.first == "Assignment" }
        }


        return DetailedScores(
            attendanceDetails = Pair(presentCount, totalClassSessions),
            recitationDetails = totalRecitationPoints,
            quizScores = quizList,
            examScores = examList,
            assignmentScores = assignmentList
        )
    }

    // --- Function to Display Details (REVISED for ActivityScoreData) ---
    private fun displayScoreDetails(student: Student, detailedScores: DetailedScores) {
        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_score_details, null)

        view.findViewById<TextView>(R.id.tvDialogTitle).text = "Detailed Scores: ${student.lastName}, ${student.firstName}"

        // Attendance
        val (presentCount, totalSessions) = detailedScores.attendanceDetails
        val attendanceText = if (totalSessions > 0) "$presentCount / $totalSessions Sessions (${(presentCount.toDouble() / totalSessions.toDouble() * 100.0).roundToTwoDecimals()}%)" else "No Sessions Recorded"
        view.findViewById<TextView>(R.id.tvAttendanceDetail).text = attendanceText

        // Recitation (Assuming 5 points max for 100%)
        val totalRecitationPoints = detailedScores.recitationDetails
        // Recalculate 50% base logic for display
        val recitationPercentage = maxOf(50.0, (totalRecitationPoints.toDouble() / 5.0) * 100.0).roundToTwoDecimals()
        view.findViewById<TextView>(R.id.tvRecitationDetail).text = "$totalRecitationPoints Points (50% base: $recitationPercentage%)"

        // Recycler Views for Activities (NOW USES ActivityScoreAdapter)
        setupActivityRecyclerView(view.findViewById(R.id.rvQuizScores), detailedScores.quizScores)
        setupActivityRecyclerView(view.findViewById(R.id.rvExamScores), detailedScores.examScores)
        setupActivityRecyclerView(view.findViewById(R.id.rvAssignmentScores), detailedScores.assignmentScores)

        builder.setView(view)
        builder.setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    // --- Helper for setting up activity lists (REVISED for ActivityScoreData) ---
    private fun setupActivityRecyclerView(recyclerView: RecyclerView, scores: List<ActivityScoreData>) {
        recyclerView.layoutManager = LinearLayoutManager(this)
        // Ensure ActivityScoreAdapter is updated to accept List<ActivityScoreData>
        recyclerView.adapter = ActivityScoreAdapter(scores)
        // Hide the RecyclerView if no activities were found for that category
        recyclerView.visibility = if (scores.isNotEmpty()) View.VISIBLE else View.GONE
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
            .whereEqualTo("academicTerm", academicTerm)
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


    // --- REVISED: fetchQuizExamAndAssignmentScores (The core issue fix) ---
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
                .whereEqualTo("classId", assignmentId!!).get().await().documents

            // Processing logic for Quizzes/Exams metadata
            quizMetadataDocuments.forEach { dataSnapshot: DataSnapshot ->
                val docId = dataSnapshot.key ?: return@forEach
                val map = dataSnapshot.value as? Map<*, *> ?: return@forEach
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

        // --- HAKBANG 3: FETCH SCORES MULA SA FIRESTORE CONCURRENTLY (Using chunking and relying on UID filter in HAKBANG 4) ---

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

        // --- HAKBANG 4: REVISED AGGREGATION & MISSING SCORE INJECTION (Client-side filtering by Student Doc ID) ---

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
                val sectionId = className?.split(" - ")?.lastOrNull() ?: ""

                if (yearLevel.isEmpty() || semester.isEmpty() || sectionId.isEmpty()) { tvLoadingStatus.text = "Error: Missing class details." ; return@launch }

                // 2. Fetch students
                val studentsSnapshot = firestore.collection("students")
                    .whereEqualTo("sectionId", sectionId)
                    .whereEqualTo("yearLevel", yearLevel)
                    .whereEqualTo("status", "Admitted")
                    .get().await()

                val studentIds = studentsSnapshot.documents.map { it.id }
                if (studentIds.isEmpty()) { tvLoadingStatus.text = "No admitted students found." ; return@launch }

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

                // Completed enrollment check logic
                val enrollmentChecks = studentIds.map { studentId ->
                    async {
                        firestore.collection("students").document(studentId).collection("subjects").document(enrollmentDocId)
                            .get().await().let { if (it.exists()) studentId else null }
                    }
                }
                enrollmentChecks.awaitAll().filterNotNull().forEach { id -> studentMap[id]?.let { enrolledStudents.add(it) } }

                if (enrolledStudents.isEmpty()) { tvLoadingStatus.text = "No students officially enrolled." ; return@launch }

                val studentGradesData = mutableMapOf<String, GradeInputAdapter.GradeData>()
                enrolledStudents.forEach { student -> studentGradesData[student.id] = GradeInputAdapter.GradeData() }


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

                // Adapter initialization
                gradeAdapter = GradeInputAdapter(
                    students = enrolledStudents,
                    gradingPeriod = gradingPeriod!!,
                    initialGrades = studentGradesData,
                    listener = this@GradeInputActivity
                )
                recyclerViewGrades.adapter = gradeAdapter
                tvLoadingStatus.text = "✅ ${enrolledStudents.size} students loaded. Grades are ready."
                btnSaveGrades.visibility = View.VISIBLE

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
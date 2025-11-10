package com.example.datadomeapp.teacher

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GradeInputActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance()

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

    // --- fetchAttendanceAndRecitationScores (A/R calculation - NO CHANGE) ---
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
            grades.attendance = maxOf(50.0, attendanceScoreRaw).let { "%.2f".format(it).toDouble() }

            val totalRecitationPoints = studentRecitationPoints[studentDocId] ?: 0

            val recitationScore = if (totalRecitationPoints >= 5) {
                100.0
            } else {
                (totalRecitationPoints.toDouble() / 5.0) * 100.0
            }

            // 3. APPLY 50% BASE
            grades.recitation = maxOf(50.0, recitationScore).let { "%.2f".format(it).toDouble() }

            studentGradesData[studentDocId] = grades
            Log.d("GradeDebug", "A/R Calculated for ${student.lastName} (${student.id}): Att=${grades.attendance}, Rec=${grades.recitation}")
        }
    }

    // --- REVISED: fetchQuizExamAndAssignmentScores (Base 50% Per Activity) ---
    private suspend fun fetchQuizExamAndAssignmentScores(
        enrolledStudents: List<Student>,
        studentGradesData: MutableMap<String, GradeInputAdapter.GradeData>
    ) {
        val activityMetadata = mutableMapOf<String, Pair<String, Double>>()
        val fetchJobs = mutableListOf<kotlinx.coroutines.Deferred<List<com.google.firebase.firestore.DocumentSnapshot>>>()
        val allScoresDocuments = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()

        try {
            // --- HAKBANG 1 & 2: FETCH & PROCESS METADATA ---
            Log.d("GradeDebug", "Querying QUIZ/EXAM metadata from REALTIME DATABASE (quizzes). AssignmentId: $assignmentId")

            val quizzesTask = realtimeDb.reference.child("quizzes")
                .orderByChild("assignmentId")
                .equalTo(assignmentId!!)
                .get()
            val quizMetadataDocuments = quizzesTask.await().children.toList()

            Log.d("GradeDebug", "Querying ASSIGNMENT metadata from FIRESTORE (assignments).")
            val assignmentsSnapshot = firestore.collection("assignments")
                .whereEqualTo("classId", assignmentId!!)
                .get().await()

            // Processing logic for Quizzes/Exams metadata
            quizMetadataDocuments.forEach { dataSnapshot: DataSnapshot ->
                val docId = dataSnapshot.key ?: return@forEach
                val map = dataSnapshot.value as? Map<*, *> ?: return@forEach
                val activityId = docId
                val rawQuestions = map["questions"]
                var questionsCount = 0
                val questionsMap: Map<*, *>?
                if (rawQuestions is List<*>) { questionsCount = rawQuestions.size; questionsMap = null }
                else if (rawQuestions is Map<*, *>) { questionsMap = rawQuestions; questionsMap.keys.forEach { key -> if (key.toString().toIntOrNull() != null) { questionsCount++ } } }
                else { questionsMap = null }

                val maxPoints = questionsCount.toDouble()
                val rawType = map["quizType"] as? String ?: "Quiz"
                val type = rawType.lowercase().let {
                    when (it) {
                        "quiz" -> "Quiz"
                        "exam" -> "Exam"
                        else -> "Quiz"
                    }
                }
                if (activityId.isNotBlank() && maxPoints > 0.0) {
                    activityMetadata[activityId] = Pair(type, maxPoints)
                }
            }

            // Processing logic for Assignments metadata
            assignmentsSnapshot.documents.forEach { doc ->
                val activityId = doc.id
                val rawType = doc.getString("type") ?: "assignment"
                val maxPoints = doc.getDouble("maxPoints")
                    ?: doc.getDouble("totalPoints")
                    ?: 100.0
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
                val job = lifecycleScope.async {
                    try { return@async firestore.collection("quizResults").whereIn("quizId", chunk).get().await().documents }
                    catch (e: Exception) { Log.e("GradeDebug", "Error in Quiz/Exam chunk query: ${e.message}"); return@async emptyList() }
                }
                fetchJobs.add(job)
            }
        }

        // Fetch Assignment Scores (Concurrent Chunking)
        if (assignmentIds.isNotEmpty()) {
            val assignmentChunks = assignmentIds.chunked(10)
            Log.d("GradeDebug", "Fetching Assignment scores concurrently in ${assignmentChunks.size} chunks from 'submissions'.")
            for (chunk in assignmentChunks) {
                val job = lifecycleScope.async {
                    try {
                        val documents = firestore.collection("submissions").whereIn("assignmentId", chunk).get().await().documents
                        return@async documents
                    }
                    catch (e: Exception) { Log.e("submission", "Error in Assignment chunk query: ${e.message}"); return@async emptyList() }
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
                val studentDocId = studentUidToDocIdMap[doc.getString("studentId")]
                if (studentDocId != null) studentDocId to doc else null
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }

        studentGradesData.keys.forEach { studentId ->

            // Map to store the best 50%-based percentage score for each activity for this student.
            // Map<ActivityId, Best50PercentScore>
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

                // 🚨 Apply 50% base to the INDIVIDUAL activity score
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
                // If the activity ID is NOT in the studentBestActivityScores map, it means they didn't submit (or got 0 points), so we assign 50.0.
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
                    // Average the 50%-based scores.
                    totalScore / count
                } else {
                    50.0 // Default 50.0 if no activities were found at all for the subject
                }

                when (type) {
                    "Quiz" -> grades.quiz = finalCalculatedScore.let { "%.2f".format(it).toDouble() }
                    "Exam" -> grades.exam = finalCalculatedScore.let { "%.2f".format(it).toDouble() }
                    "Assignment" -> grades.assignment = finalCalculatedScore.let { "%.2f".format(it).toDouble() }
                }
            }

            studentGradesData[studentId] = grades
            Log.d("GradeDebug", "Final Scores (Revised) for $studentId: Q=${grades.quiz} E=${grades.exam} A=${grades.assignment}")
        }
    }


    // 🚨 loadGradingData (NO CHANGE)
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

                val enrollmentChecks = studentIds.map { studentId -> async { firestore.collection("students").document(studentId).collection("subjects").document(enrollmentDocId).get().await().let { if (it.exists()) studentId else null } } }
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

                gradeAdapter = GradeInputAdapter(
                    students = enrolledStudents,
                    gradingPeriod = gradingPeriod!!,
                    initialGrades = studentGradesData
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
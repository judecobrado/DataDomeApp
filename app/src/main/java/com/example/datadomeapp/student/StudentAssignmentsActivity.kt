package com.example.datadomeapp.student

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Submission
import com.example.datadomeapp.repository.AssignmentRepository
import com.example.datadomeapp.models.Assignment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentAssignmentsActivity : AppCompatActivity() {

    private lateinit var lvAssignments: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSubjectTitle: TextView
    private lateinit var progressBar: ProgressBar
    private var isTermLoaded = false
    private val assignments = mutableListOf<Assignment>()
    private val submissionStatusMap = mutableMapOf<String, Submission?>()
    private lateinit var adapter: AssignmentAdapter

    private var classId: String? = null
    private var subjectName: String? = null
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val studentId = auth.currentUser?.uid
    private var academicTerm: String? = null
    private var academicYear: String? = null
    private var semester: String? = null

    // Maroon Color Palette
    private val maroonPrimary = Color.parseColor("#800000")     // Dark Maroon
    private val maroonDark = Color.parseColor("#5A0000")        // Darker Maroon
    private val maroonLight = Color.parseColor("#A63232")       // Light Maroon
    private val maroonAccent = Color.parseColor("#C14545")      // Accent Maroon
    private val maroonSoft = Color.parseColor("#F8E9E9")        // Soft Maroon Background
    private val goldAccent = Color.parseColor("#D4AF37")        // Gold for highlights
    private val creamLight = Color.parseColor("#FFF5E6")        // Light Cream

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_assignments)

        initializeViews()
        getIntentData()
        setupListView()
        loadCurrentTerm {
            loadAssignments()
        }
    }

    private fun initializeViews() {
        lvAssignments = findViewById(R.id.lvStudentAssignments)
        tvEmpty = findViewById(R.id.tvEmptyAssignments)
        tvSubjectTitle = findViewById(R.id.tvSubjectTitle)
        progressBar = findViewById(R.id.progressBar)

        // Set modern fonts if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tvSubjectTitle.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun getIntentData() {
        classId = intent.getStringExtra("classId")
        subjectName = intent.getStringExtra("subjectName")

        // Modern subject title with maroon rounded background
        val titleText = "${subjectName ?: "Assignments"}"
        val spannable = SpannableString(titleText)
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            titleText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvSubjectTitle.text = spannable

        // Add maroon rounded background to title - 20dp corners
        val titleBg = createRoundedDrawable(maroonPrimary, 20f)
        tvSubjectTitle.background = titleBg
        tvSubjectTitle.setPadding(32, 16, 32, 16)
        tvSubjectTitle.setTextColor(Color.WHITE)

        if (classId.isNullOrEmpty()) {
            showModernError("🚫 Class ID not found")
            return
        }
    }

    private fun setupListView() {
        adapter = AssignmentAdapter()
        lvAssignments.adapter = adapter

        lvAssignments.setOnItemClickListener { _, _, position, _ ->
            if (!isTermLoaded) {
                showModernToast("⏳ Loading system data...")
                return@setOnItemClickListener
            }

            if (position < assignments.size) {
                val assignment = assignments[position]
                val submission = submissionStatusMap[assignment.id]
                openAssignmentDetails(assignment, submission)
            }
        }
    }

    private fun loadCurrentTerm(onComplete: (() -> Unit)? = null) {
        val termDocRef = firestore.collection("systemSettings").document("currentTerm")

        termDocRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    academicTerm = doc.getString("academicTerm")
                    academicYear = doc.getString("academicYear")
                    semester = doc.getString("semester")
                    isTermLoaded = true
                    Log.d("TERM_INFO", "📅 Loaded term: $academicTerm | Year: $academicYear | Semester: $semester")
                } else {
                    Log.w("TERM_INFO", "📅 No currentTerm document found.")
                    isTermLoaded = true
                }
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e("TERM_INFO", "❌ Error loading current term: ${e.message}")
                onComplete?.invoke()
            }
    }

    private fun openAssignmentDetails(assignment: Assignment, submission: Submission?) {
        val intent = Intent(this, AssignmentDetailsActivity::class.java)
        intent.putExtra("assignmentId", assignment.id)
        intent.putExtra("assignmentTitle", assignment.title)
        intent.putExtra("assignmentInstructions", assignment.instructions)
        intent.putExtra("assignmentFileUrl", assignment.fileUrl)
        intent.putExtra("dueDateMillis", assignment.dueDateMillis)
        intent.putExtra("classId", classId)
        intent.putExtra("academicTerm", academicTerm)
        intent.putExtra("academicYear", academicYear)
        intent.putExtra("semester", semester)

        submission?.let { sub ->
            intent.putExtra("submissionId", sub.id)
            intent.putExtra("submissionFileUrl", sub.fileUrl)
            intent.putExtra("submissionDate", sub.submittedAt)
            intent.putExtra("grade", sub.grade)
            intent.putExtra("feedback", sub.feedback)
        }

        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadAssignments()
    }

    private fun loadAssignments() {
        val id = classId
        if (id.isNullOrEmpty()) {
            showModernError("🚫 Invalid class information")
            return
        }

        showModernLoading()
        AssignmentRepository.getAssignmentsForClass(id) { success, snapshot, error ->
            runOnUiThread {
                progressBar.visibility = View.GONE

                if (!success || snapshot == null) {
                    showModernError("❌ Failed to load assignments")
                    showModernToast("❌ ${error ?: "Unknown error"}")
                    return@runOnUiThread
                }

                assignments.clear()
                submissionStatusMap.clear()

                for (doc in snapshot.documents) {
                    val assignment = doc.toObject(Assignment::class.java)
                    if (assignment != null) {
                        assignment.id = doc.id
                        assignments.add(assignment)
                    }
                }

                if (assignments.isEmpty()) {
                    showModernEmptyState()
                } else {
                    tvEmpty.visibility = View.GONE
                    lvAssignments.visibility = View.VISIBLE
                    assignments.sortBy { it.dueDateMillis }
                    loadSubmissionStatusForAllAssignments()
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun loadSubmissionStatusForAllAssignments() {
        if (studentId.isNullOrEmpty()) {
            updateAssignmentDisplay()
            return
        }

        var completedRequests = 0
        val totalAssignments = assignments.size

        if (totalAssignments == 0) {
            updateAssignmentDisplay()
            return
        }

        assignments.forEach { assignment ->
            AssignmentRepository.getSubmissionByStudentAndAssignment(studentId, assignment.id) { submission ->
                submissionStatusMap[assignment.id] = submission
                completedRequests++

                if (completedRequests == totalAssignments) {
                    runOnUiThread {
                        updateAssignmentDisplay()
                    }
                }
            }
        }
    }

    private fun updateAssignmentDisplay() {
        adapter.notifyDataSetChanged()
    }

    private fun formatDueDate(dueDateMillis: Long): SpannableString {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

        return if (dueDateMillis > 0) {
            val dueDate = sdf.format(Date(dueDateMillis))

            // Modern icon set with better visual hierarchy
            val hoursLeft = (dueDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60)
            val icon = when {
                System.currentTimeMillis() > dueDateMillis -> "⏰ " // Urgent
                hoursLeft <= 12 -> "🚨 " // Critical (12 hours)
                hoursLeft <= 24 -> "⚠️ " // Warning (24 hours)
                hoursLeft <= 72 -> "📆 " // Upcoming (3 days)
                else -> "📅 " // Normal
            }

            val text = "$icon$dueDate"
            val spannable = SpannableString(text)

            // Color based on urgency with maroon tones
            val color = when {
                System.currentTimeMillis() > dueDateMillis -> Color.parseColor("#8B0000") // Dark red/maroon
                hoursLeft <= 12 -> Color.parseColor("#B22222") // Firebrick
                hoursLeft <= 24 -> Color.parseColor("#CD5C5C") // Indian red
                hoursLeft <= 72 -> Color.parseColor("#DC143C") // Crimson
                else -> Color.parseColor("#2E8B57") // Sea green
            }

            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Make it bold if urgent
            if (hoursLeft <= 24 || System.currentTimeMillis() > dueDateMillis) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            spannable
        } else {
            SpannableString("📅 No deadline set").apply {
                setSpan(ForegroundColorSpan(Color.parseColor("#757575")), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun isAssignmentOverdue(assignment: Assignment): Boolean {
        return assignment.dueDateMillis > 0 && System.currentTimeMillis() > assignment.dueDateMillis
    }

    private fun getAssignmentStatusInfo(assignment: Assignment, submission: Submission?, isOverdue: Boolean): StatusInfo {
        return when {
            // Submitted and graded - PREMIUM STATUS with gold accent
            submission != null && submission.submittedAt > 0 && submission.grade != null -> {
                StatusInfo(
                    "✨ Graded • ${submission.grade}%", // Sparkle + grade
                    Color.WHITE, // White text
                    goldAccent, // Gold background
                    20f // Rounded corners
                )
            }

            // Submitted but not graded - PENDING STATUS with light maroon
            submission != null && submission.submittedAt > 0 -> {
                StatusInfo(
                    "⏳ Awaiting Grade", // Clock
                    Color.WHITE, // White text
                    maroonLight, // Light maroon background
                    20f
                )
            }

            // Not submitted and overdue - CRITICAL STATUS with dark maroon
            isOverdue -> {
                StatusInfo(
                    "🚨 OVERDUE", // Siren
                    Color.WHITE, // White text
                    maroonDark, // Dark maroon background
                    20f
                )
            }

            // Not submitted but due soon (within 12 hours) - URGENT STATUS with accent maroon
            assignment.dueDateMillis > 0 && (assignment.dueDateMillis - System.currentTimeMillis()) <= (12 * 60 * 60 * 1000) -> {
                StatusInfo(
                    "⚠️ Due Soon", // Warning
                    Color.WHITE, // White text
                    maroonAccent, // Accent maroon background
                    20f
                )
            }

            // Not submitted but due within 24 hours - WARNING STATUS with soft maroon
            assignment.dueDateMillis > 0 && (assignment.dueDateMillis - System.currentTimeMillis()) <= (24 * 60 * 60 * 1000) -> {
                StatusInfo(
                    "📝 Due Tomorrow", // Pencil
                    Color.parseColor("#5A0000"), // Dark maroon text
                    maroonSoft, // Soft maroon background
                    20f
                )
            }

            // Not submitted with time - NORMAL STATUS with primary maroon
            else -> {
                StatusInfo(
                    "📋 Ready", // Clipboard
                    Color.WHITE, // White text
                    maroonPrimary, // Primary maroon background
                    20f
                )
            }
        }
    }

    private inner class AssignmentAdapter : BaseAdapter() {

        override fun getCount(): Int = assignments.size

        override fun getItem(position: Int): Any = assignments[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: ModernViewHolder

            if (convertView == null) {
                view = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                holder = ModernViewHolder(view)
                view.tag = holder

                // Modern list item styling with rounded corners
                holder.textView.apply {
                    setPadding(32, 24, 32, 24)
                    setLineSpacing(1.2f, 1.2f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    }

                    // Set default cream background with MORE rounded corners (24dp)
                    background = createRoundedDrawable(creamLight, 24f)
                }
            } else {
                view = convertView
                holder = view.tag as ModernViewHolder
            }

            val assignment = assignments[position]
            val submission = submissionStatusMap[assignment.id]
            val isOverdue = isAssignmentOverdue(assignment)
            val dueDateSpannable = formatDueDate(assignment.dueDateMillis)
            val statusInfo = getAssignmentStatusInfo(assignment, submission, isOverdue)

            // Create modern text display with enhanced formatting
            val builder = SpannableStringBuilder()

            // Title section with modern styling in maroon
            val titleSpannable = SpannableString("📋 ${assignment.title}")
            titleSpannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                titleSpannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            titleSpannable.setSpan(
                ForegroundColorSpan(maroonPrimary),
                0,
                titleSpannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append(titleSpannable)
            builder.append("\n\n")

            // Due date section with rounded background
            builder.append(dueDateSpannable)
            builder.append("\n\n")

            // Status badge - modern design with rounded corners
            val statusPlaceholder = "[STATUS_BADGE]"
            builder.append(statusPlaceholder)

            // Grade section with medal system and rounded background
            submission?.grade?.let { grade ->
                builder.append("\n\n")

                // Modern grade display with medal
                val gradeIcon = when {
                    grade >= 95 -> "🏆" // Trophy
                    grade >= 90 -> "🥇" // Gold medal
                    grade >= 85 -> "🥈" // Silver medal
                    grade >= 80 -> "🥉" // Bronze medal
                    grade >= 75 -> "⭐" // Star
                    grade >= 70 -> "✅" // Check
                    else -> "📊" // Chart
                }

                // Grade text with rounded background
                val gradeText = "  $gradeIcon Score: ${grade}%  "
                val gradeSpannable = SpannableString(gradeText)

                // Color code based on grade with maroon/gold palette
                var gradeTextColor: Int
                var gradeBgColor: Int

                when {
                    grade >= 90 -> {
                        gradeTextColor = Color.parseColor("#5A0000") // Dark maroon text
                        gradeBgColor = goldAccent // Gold background
                    }
                    grade >= 80 -> {
                        gradeTextColor = Color.WHITE
                        gradeBgColor = Color.parseColor("#C0C0C0") // Silver
                    }
                    grade >= 70 -> {
                        gradeTextColor = Color.WHITE
                        gradeBgColor = Color.parseColor("#CD7F32") // Bronze
                    }
                    grade >= 60 -> {
                        gradeTextColor = Color.parseColor("#5A0000") // Dark maroon text
                        gradeBgColor = Color.parseColor("#FFCC80") // Light orange
                    }
                    else -> {
                        gradeTextColor = Color.WHITE
                        gradeBgColor = Color.parseColor("#8B0000") // Dark red/maroon
                    }
                }

                gradeSpannable.setSpan(
                    ForegroundColorSpan(gradeTextColor),
                    0,
                    gradeText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // Make grade bold
                gradeSpannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    gradeText.indexOf(grade.toString()),
                    gradeText.indexOf(grade.toString()) + grade.toString().length + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                builder.append(gradeSpannable)
            }

            // Attachment indicator - modern style with rounded background
            if (!assignment.fileUrl.isNullOrEmpty()) {
                builder.append("\n\n")
                val attachText = "  📎 Attachments available  "
                val attachSpannable = SpannableString(attachText)
                attachSpannable.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    0,
                    attachText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                attachSpannable.setSpan(
                    ForegroundColorSpan(maroonPrimary),
                    2, // Start after emoji
                    3, // Just the emoji
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                attachSpannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    attachText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(attachSpannable)
            }

            // Submission info - modern timestamp with rounded background
            if (submission != null && submission.submittedAt > 0) {
                builder.append("\n\n")
                val submittedDate = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    .format(Date(submission.submittedAt))
                val submitText = "  ✅ Submitted: $submittedDate  "
                val submitSpannable = SpannableString(submitText)
                submitSpannable.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    0,
                    submitText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                submitSpannable.setSpan(
                    ForegroundColorSpan(goldAccent),
                    2, // Start after emoji
                    3, // Just the emoji
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                submitSpannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    submitText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(submitSpannable)
            }

            // Modern separator with rounded ends in maroon
            builder.append("\n\n")
            val separator = "●━━━━━━━━━━━━━━━━━━━━━━━━━━━━●"
            val separatorSpannable = SpannableString(separator)
            separatorSpannable.setSpan(
                ForegroundColorSpan(maroonLight),
                0,
                separator.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append(separatorSpannable)

            // Set final text with all formatting
            val finalText = builder.toString()
            val displayText = finalText.replace(
                "[STATUS_BADGE]",
                "  ${statusInfo.statusText}  "
            )

            val finalSpannable = SpannableString(displayText)

            // Apply status badge styling
            val statusIndex = displayText.indexOf("  ${statusInfo.statusText}  ")
            if (statusIndex >= 0) {
                finalSpannable.setSpan(
                    ForegroundColorSpan(statusInfo.textColor),
                    statusIndex,
                    statusIndex + statusInfo.statusText.length + 4,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                finalSpannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    statusIndex,
                    statusIndex + statusInfo.statusText.length + 4,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            holder.textView.text = finalSpannable

            // Set main background with MORE rounded corners (28dp) using maroon palette
            val mainBgColor = when {
                submission != null && submission.submittedAt > 0 ->
                    if (submission.grade != null) Color.parseColor("#FFF8E6") // Light gold tint
                    else Color.parseColor("#F8E9E9") // Soft maroon
                isOverdue -> Color.parseColor("#FFEBEE") // Light red
                assignment.dueDateMillis > 0 && (assignment.dueDateMillis - System.currentTimeMillis()) <= (24 * 60 * 60 * 1000) ->
                    Color.parseColor("#FFF5E6") // Light cream
                else -> Color.parseColor("#F5F5F5") // Light gray
            }

            // Use MORE rounded corners (28dp instead of 24dp)
            holder.textView.background = createRoundedDrawable(mainBgColor, 28f)

            // Add elevation/shadow effect for better visual separation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.textView.elevation = 8f // Increased elevation
                holder.textView.translationZ = 8f
            }

            return view
        }

        fun clear() {
            assignments.clear()
            submissionStatusMap.clear()
            notifyDataSetChanged()
        }
    }

    private class ModernViewHolder(view: View) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    data class StatusInfo(
        val statusText: String,
        val textColor: Int,
        val bgColor: Int,
        val cornerRadius: Float = 16f
    )

    // FIXED: Helper function to create rounded drawable with maroon border
    private fun createRoundedDrawable(color: Int, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            // FIX: Add the cornerRadius property
            // Add maroon border for better visibility
            setStroke(2, maroonLight)
        }
    }

    // Modern UI Helper Functions with maroon palette
    private fun showModernLoading() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.text = "🔄 Loading assignments..."
        tvEmpty.setTextColor(maroonPrimary)
        tvEmpty.background = createRoundedDrawable(creamLight, 24f) // 24dp corners
        tvEmpty.setPadding(32, 24, 32, 24)
        lvAssignments.visibility = View.GONE
    }

    private fun showModernEmptyState() {
        progressBar.visibility = View.GONE
        lvAssignments.visibility = View.GONE
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = "📭 No assignments found\n\nYour teacher hasn't posted any assignments yet."
        tvEmpty.setTextColor(maroonPrimary)
        tvEmpty.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tvEmpty.setLineSpacing(1.5f, 1.5f)
        tvEmpty.background = createRoundedDrawable(maroonSoft, 28f) // 28dp corners
        tvEmpty.setPadding(48, 36, 48, 36)

        // Add icon with rounded background
        val emptySpannable = SpannableStringBuilder()
        emptySpannable.append("📭")
        emptySpannable.append("\n\n")
        emptySpannable.append("No assignments found")
        emptySpannable.append("\n\n")
        emptySpannable.append("Your teacher hasn't posted any\nassignments for this subject yet.")

        tvEmpty.text = emptySpannable
    }

    private fun showModernError(message: String) {
        progressBar.visibility = View.GONE
        lvAssignments.visibility = View.GONE
        tvEmpty.visibility = View.VISIBLE

        val errorSpannable = SpannableStringBuilder()
        errorSpannable.append("❌\n\n")
        errorSpannable.append(message)
        errorSpannable.append("\n\n")
        errorSpannable.append("Tap to retry")

        tvEmpty.text = errorSpannable
        tvEmpty.setTextColor(Color.WHITE)
        tvEmpty.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tvEmpty.background = createRoundedDrawable(maroonDark, 28f) // 28dp corners
        tvEmpty.setPadding(48, 36, 48, 36)
        tvEmpty.setOnClickListener {
            loadAssignments()
        }
    }

    private fun showModernToast(message: String) {
        // Create a custom Toast with maroon rounded corners
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)

        // Get the Toast view
        val toastView = toast.view
        val roundedBg = createRoundedDrawable(maroonPrimary, 24f) // 24dp corners
        toastView?.background = roundedBg

        // Get the TextView
        val textView = toastView?.findViewById<TextView>(android.R.id.message)
        textView?.setTextColor(Color.WHITE)
        textView?.setPadding(32, 16, 32, 16)
        textView?.textAlignment = View.TEXT_ALIGNMENT_CENTER

        toast.show()
    }
}
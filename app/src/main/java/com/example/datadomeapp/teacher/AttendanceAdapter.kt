package com.example.datadomeapp.teacher

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student

class AttendanceAdapter(
    private val studentList: List<Student>,
    private val assignmentId: String,
    private var isEditable: Boolean = true,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    companion object {
        private const val TAG = "AttendanceAdapter"
        private const val MAX_RECITATION_POINTS = 10
    }

    private val attendanceStatus = mutableMapOf<String, String>()
    private val recitationPoints = mutableMapOf<String, Int>()

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvAttStudentName)
        val tvStudentId: TextView = view.findViewById(R.id.tvAttStudentId)

        // Attendance Controls (RadioButtons)
        val rgAttendanceStatus: RadioGroup = view.findViewById(R.id.rgAttendanceStatus)
        val rbPresent: RadioButton = view.findViewById(R.id.rbPresent)
        val rbLate: RadioButton = view.findViewById(R.id.rbLate)
        val rbExcused: RadioButton = view.findViewById(R.id.rbExcused)
        val rbAbsent: RadioButton = view.findViewById(R.id.rbAbsent)

        // Recitation Controls
        val llRecitationControls: LinearLayout = view.findViewById(R.id.llRecitationControls)
        val btnReciteMinus: Button = view.findViewById(R.id.btnReciteMinus)
        val tvReciteScore: TextView = view.findViewById(R.id.tvReciteScore)
        val btnRecitePlus: Button = view.findViewById(R.id.btnRecitePlus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.teacher_item_attendance_student, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val currentItem = studentList[position]
        val studentId = currentItem.id ?: return

        val currentStatus = attendanceStatus[studentId] ?: ""
        val currentPoints = recitationPoints[studentId] ?: 0

        // FIXED: Show BOTH attendance status AND recitation points
        val cleanName = "${currentItem.lastName}, ${currentItem.firstName}"

        // Build display text with both status and points
        val displayText = buildString {
            append(cleanName)
            if (currentStatus.isNotEmpty()) {
                append(" ($currentStatus")
                if (currentPoints > 0) {
                    append(" • $currentPoints pts")
                }
                append(")")
            } else if (currentPoints > 0) {
                append(" ($currentPoints pts)")
            }
        }

        holder.tvStudentName.text = displayText
        holder.tvStudentId.text = "ID: $studentId"
        holder.tvReciteScore.text = currentPoints.toString()

        // FIXED: Show BOTH attendance radio buttons AND recitation controls
        holder.rgAttendanceStatus.visibility = View.VISIBLE
        holder.llRecitationControls.visibility = View.VISIBLE

        // --- Attendance Radio Buttons ---
        // Remove listener temporarily to avoid infinite loops
        holder.rgAttendanceStatus.setOnCheckedChangeListener(null)

        // Set the correct radio button based on status
        when (currentStatus.uppercase()) {
            "PRESENT" -> holder.rbPresent.isChecked = true
            "LATE" -> holder.rbLate.isChecked = true
            "EXCUSED" -> holder.rbExcused.isChecked = true
            "ABSENT" -> holder.rbAbsent.isChecked = true
            else -> holder.rgAttendanceStatus.clearCheck()
        }

        // Enable/disable radio buttons
        listOf(holder.rbPresent, holder.rbLate, holder.rbExcused, holder.rbAbsent).forEach {
            it.isEnabled = isEditable
        }

        // Set up radio group listener
        if (isEditable) {
            holder.rgAttendanceStatus.setOnCheckedChangeListener { _, checkedId ->
                val selectedStatus = when (checkedId) {
                    holder.rbPresent.id -> "PRESENT"
                    holder.rbLate.id -> "LATE"
                    holder.rbExcused.id -> "EXCUSED"
                    holder.rbAbsent.id -> "ABSENT"
                    else -> "ABSENT"
                }

                attendanceStatus[studentId] = selectedStatus

                // Update display with new status
                val newDisplayText = buildString {
                    append(cleanName)
                    append(" ($selectedStatus")
                    val points = recitationPoints[studentId] ?: 0
                    if (points > 0) {
                        append(" • $points pts")
                    }
                    append(")")
                }
                holder.tvStudentName.text = newDisplayText

                Log.i(TAG, "Student $studentId attendance updated to $selectedStatus")
                onDataChanged()
            }
        }

        // --- Recitation Controls ---
        // Enable recitation controls only if student is PRESENT or LATE
        val isAttended = currentStatus == "PRESENT" || currentStatus == "LATE"
        val controlsEnabled = isEditable && isAttended

        // Update recitation controls
        holder.btnRecitePlus.isEnabled = controlsEnabled && currentPoints < MAX_RECITATION_POINTS
        holder.btnReciteMinus.isEnabled = controlsEnabled && currentPoints > 0

        // Visual feedback
        holder.llRecitationControls.alpha = if (controlsEnabled) 1.0f else 0.5f

        // Set recitation button listeners
        holder.btnRecitePlus.setOnClickListener {
            if (controlsEnabled && currentPoints < MAX_RECITATION_POINTS) {
                val newPoints = currentPoints + 1
                recitationPoints[studentId] = newPoints

                // Update UI immediately
                holder.tvReciteScore.text = newPoints.toString()
                val updatedDisplayText = buildString {
                    append(cleanName)
                    if (currentStatus.isNotEmpty()) {
                        append(" ($currentStatus • $newPoints pts)")
                    } else {
                        append(" ($newPoints pts)")
                    }
                }
                holder.tvStudentName.text = updatedDisplayText

                Log.i(TAG, "Student $studentId recitation points: $newPoints")
                onDataChanged()
            }
        }

        holder.btnReciteMinus.setOnClickListener {
            if (controlsEnabled && currentPoints > 0) {
                val newPoints = currentPoints - 1
                recitationPoints[studentId] = newPoints

                // Update UI immediately
                holder.tvReciteScore.text = newPoints.toString()
                val updatedDisplayText = buildString {
                    append(cleanName)
                    if (currentStatus.isNotEmpty()) {
                        if (newPoints > 0) {
                            append(" ($currentStatus • $newPoints pts)")
                        } else {
                            append(" ($currentStatus)")
                        }
                    } else if (newPoints > 0) {
                        append(" ($newPoints pts)")
                    } else {
                        append(cleanName)
                    }
                }
                holder.tvStudentName.text = updatedDisplayText

                Log.i(TAG, "Student $studentId recitation points: $newPoints")
                onDataChanged()
            }
        }

        // Set overall item opacity
        holder.itemView.alpha = if (isEditable) 1.0f else 0.8f
    }

    override fun getItemCount() = studentList.size

    fun getAttendanceAndRecitationMaps(): Pair<Map<String, String>, Map<String, Int>> {
        return Pair(attendanceStatus.toMap(), recitationPoints.toMap())
    }

    fun updateStatuses(existingAttendance: Map<String, String>, existingRecitation: Map<String, Int>) {
        attendanceStatus.clear()
        recitationPoints.clear()

        existingAttendance.forEach { (studentId, status) ->
            attendanceStatus[studentId] = status.uppercase()
        }

        existingRecitation.forEach { (studentId, points) ->
            recitationPoints[studentId] = points
        }

        notifyDataSetChanged()
    }

    fun setEditable(editable: Boolean) {
        if (isEditable != editable) {
            isEditable = editable
            notifyDataSetChanged()
        }
    }

    // Clear all data for fresh start
    fun clearAllData() {
        attendanceStatus.clear()
        recitationPoints.clear()
        notifyDataSetChanged()
        Log.d(TAG, "All attendance and recitation data cleared")
    }

    // Methods for ID tapping to update specific students
    fun updateStudentStatus(studentId: String, status: String) {
        attendanceStatus[studentId] = status
        val position = studentList.indexOfFirst { it.id == studentId }
        if (position != -1) {
            notifyItemChanged(position)
        }
        onDataChanged()
    }

    fun updateStudentRecitation(studentId: String, points: Int) {
        recitationPoints[studentId] = points
        val position = studentList.indexOfFirst { it.id == studentId }
        if (position != -1) {
            notifyItemChanged(position)
        }
        onDataChanged()
    }

    // Get current points for a student
    fun getStudentRecitationPoints(studentId: String): Int {
        return recitationPoints[studentId] ?: 0
    }

    // Get current status for a student
    fun getStudentAttendanceStatus(studentId: String): String {
        return attendanceStatus[studentId] ?: ""
    }

    // NEW: Process student tap - handles both attendance and recitation
    fun processStudentTap(studentId: String, isWithinLateThreshold: Boolean): Boolean {
        val currentStatus = getStudentAttendanceStatus(studentId)
        val currentPoints = getStudentRecitationPoints(studentId)

        return if (currentStatus.isEmpty()) {
            // First tap - set attendance status
            val status = if (isWithinLateThreshold) "PRESENT" else "LATE"
            updateStudentStatus(studentId, status)
            true
        } else if (currentStatus == "PRESENT" || currentStatus == "LATE") {
            // Subsequent taps - add recitation points
            if (currentPoints < MAX_RECITATION_POINTS) {
                updateStudentRecitation(studentId, currentPoints + 1)
                true
            } else {
                false // Max points reached
            }
        } else {
            false // Student is ABSENT or EXCUSED
        }
    }
}
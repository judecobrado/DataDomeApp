package com.example.datadomeapp.teacher

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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

        val cleanName = "${currentItem.lastName}, ${currentItem.firstName}"

        // Build display text with name and status only
        val displayText = buildString {
            append(cleanName)
            if (currentStatus.isNotEmpty()) {
                append(" ($currentStatus)")
            }
        }

        holder.tvStudentName.text = displayText
        holder.tvStudentId.text = "ID: $studentId"

        // Show points on the right side - update the recitation score text
        holder.tvReciteScore.text = if (currentPoints > 0) "$currentPoints pts" else "0 pts"

        // Show attendance radio buttons but HIDE recitation controls
        holder.rgAttendanceStatus.visibility = View.VISIBLE
        holder.llRecitationControls.visibility = View.GONE // HIDE recitation controls

        // --- Update Radio Button Colors ---
        updateRadioButtonColors(holder, currentStatus)

        // --- Attendance Radio Buttons ---
        // Remove all listeners first
        holder.rgAttendanceStatus.setOnCheckedChangeListener(null)
        holder.rbExcused.setOnClickListener(null)

        // Set the correct radio button based on status
        when (currentStatus.uppercase()) {
            "PRESENT" -> holder.rbPresent.isChecked = true
            "LATE" -> holder.rbLate.isChecked = true
            "EXCUSED" -> holder.rbExcused.isChecked = true
            "ABSENT" -> holder.rbAbsent.isChecked = true
            else -> holder.rgAttendanceStatus.clearCheck()
        }

        // ONLY enable the EXCUSE button, disable all others
        holder.rbPresent.isEnabled = false
        holder.rbLate.isEnabled = false
        holder.rbAbsent.isEnabled = false
        holder.rbExcused.isEnabled = isEditable // Only excuse can be manually set

        // Make Present, Late, and Absent buttons non-clickable and change appearance
        holder.rbPresent.isClickable = false
        holder.rbLate.isClickable = false
        holder.rbAbsent.isClickable = false

        // Visual feedback - make disabled buttons look more disabled
        holder.rbPresent.alpha = if (currentStatus == "PRESENT") 1.0f else 0.6f
        holder.rbLate.alpha = if (currentStatus == "LATE") 1.0f else 0.6f
        holder.rbAbsent.alpha = if (currentStatus == "ABSENT") 1.0f else 0.6f
        holder.rbExcused.alpha = if (isEditable) 1.0f else 0.6f

        // --- Excuse Button Toggle Logic ---
        if (isEditable) {
            holder.rbExcused.setOnClickListener {
                val currentExcuseStatus = attendanceStatus[studentId] ?: ""

                if (currentExcuseStatus == "EXCUSED") {
                    // If already excused, remove the status (undo)
                    attendanceStatus.remove(studentId)

                    // Update display to show no status
                    holder.tvStudentName.text = cleanName

                    // Uncheck the radio button
                    holder.rbExcused.isChecked = false

                    // Update colors
                    updateRadioButtonColors(holder, "")

                    Log.i(TAG, "Student $studentId excuse removed")
                } else {
                    // Set to excused
                    attendanceStatus[studentId] = "EXCUSED"

                    // Update display
                    val newDisplayText = buildString {
                        append(cleanName)
                        append(" (EXCUSED)")
                    }
                    holder.tvStudentName.text = newDisplayText

                    // Check the radio button
                    holder.rbExcused.isChecked = true

                    // Update colors
                    updateRadioButtonColors(holder, "EXCUSED")

                    Log.i(TAG, "Student $studentId manually excused")
                }
                onDataChanged()
            }

            // Still set up radio group listener to handle any other interactions
            holder.rgAttendanceStatus.setOnCheckedChangeListener { _, checkedId ->
                // Only process if it's NOT the excuse button (since we handle that separately)
                if (checkedId != holder.rbExcused.id && checkedId != -1) {
                    // If any other button is somehow checked, uncheck it
                    holder.rgAttendanceStatus.setOnCheckedChangeListener(null)
                    when (currentStatus.uppercase()) {
                        "PRESENT" -> holder.rbPresent.isChecked = true
                        "LATE" -> holder.rbLate.isChecked = true
                        "EXCUSED" -> holder.rbExcused.isChecked = true
                        "ABSENT" -> holder.rbAbsent.isChecked = true
                        else -> holder.rgAttendanceStatus.clearCheck()
                    }
                    holder.rgAttendanceStatus.setOnCheckedChangeListener { _, _ ->
                        // Re-set the listener
                    }
                }
            }
        } else {
            // If not editable, disable the entire radio group
            holder.rgAttendanceStatus.isEnabled = false
        }

        // --- Recitation Controls ---
        // Completely disable recitation button listeners since controls are hidden
        holder.btnRecitePlus.setOnClickListener(null)
        holder.btnReciteMinus.setOnClickListener(null)
        holder.btnRecitePlus.isEnabled = false
        holder.btnReciteMinus.isEnabled = false

        // Set overall item opacity
        holder.itemView.alpha = if (isEditable) 1.0f else 0.8f
    }

    private fun updateRadioButtonColors(holder: AttendanceViewHolder, currentStatus: String) {
        val context = holder.itemView.context

        // Reset all buttons to default first
        holder.rbPresent.setBackgroundColor(Color.TRANSPARENT)
        holder.rbLate.setBackgroundColor(Color.TRANSPARENT)
        holder.rbAbsent.setBackgroundColor(Color.TRANSPARENT)
        holder.rbExcused.setBackgroundColor(Color.TRANSPARENT)

        // Set colors based on current status
        when (currentStatus.uppercase()) {
            "PRESENT" -> {
                holder.rbPresent.setBackgroundColor(ContextCompat.getColor(context, R.color.status_present))
                holder.rbPresent.setTextColor(ContextCompat.getColor(context, R.color.status_present_text))
            }
            "LATE" -> {
                holder.rbLate.setBackgroundColor(ContextCompat.getColor(context, R.color.status_late))
                holder.rbLate.setTextColor(ContextCompat.getColor(context, R.color.status_late_text))
            }
            "ABSENT" -> {
                holder.rbAbsent.setBackgroundColor(ContextCompat.getColor(context, R.color.status_absent))
                holder.rbAbsent.setTextColor(ContextCompat.getColor(context, R.color.status_absent_text))
            }
            "EXCUSED" -> {
                holder.rbExcused.setBackgroundColor(ContextCompat.getColor(context, R.color.status_excused))
                holder.rbExcused.setTextColor(ContextCompat.getColor(context, R.color.status_excused_text))
            }
            else -> {
                // No status selected - reset all text colors
                holder.rbPresent.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                holder.rbLate.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                holder.rbAbsent.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                holder.rbExcused.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }
        }
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
        // Only allow RFID to set PRESENT or LATE, manual can only set EXCUSED
        if (status == "PRESENT" || status == "LATE" || status == "ABSENT") {
            attendanceStatus[studentId] = status
            val position = studentList.indexOfFirst { it.id == studentId }
            if (position != -1) {
                notifyItemChanged(position)
            }
            onDataChanged()
        }
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

    // Process student tap - handles both attendance and recitation via RFID only
    fun processStudentTap(studentId: String, isWithinLateThreshold: Boolean): Boolean {
        val currentStatus = getStudentAttendanceStatus(studentId)
        val currentPoints = getStudentRecitationPoints(studentId)

        // If student is already excused, don't allow RFID tapping
        if (currentStatus == "EXCUSED") {
            return false
        }

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
            false // Student is ABSENT
        }
    }
}
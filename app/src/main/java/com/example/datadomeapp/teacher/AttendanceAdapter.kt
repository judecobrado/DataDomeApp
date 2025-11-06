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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student

/**
 * RecyclerView Adapter para sa pag-manage ng attendance at recitation status ng mga estudyante.
 * Ngayon ay sumusuporta sa Multiple Recitation Points.
 */
class AttendanceAdapter(
    private val studentList: List<Student>,
    private val assignmentId: String,
    private var isEditable: Boolean = true,
    private var currentMode: Mode = Mode.ATTENDANCE,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    companion object {
        private const val TAG = "AttendanceAdapter"
        private const val MAX_RECITATION_POINTS = 10 // Limitahan ang recitation points
    }

    enum class Mode {
        ATTENDANCE,
        RECITATION
    }

    private val attendanceStatus = mutableMapOf<String, String>()
    // Pinalitan ang Int status (0/1) ng Int points (0, 1, 2, 3, ...)
    private val recitationStatus = mutableMapOf<String, Int>()

    init {
        Log.d(TAG, "Adapter initialized. Assignment: $assignmentId")
    }

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvAttStudentName)
        val tvStudentId: TextView = view.findViewById(R.id.tvAttStudentId)

        // Attendance Controls
        val rgAttendanceStatus: RadioGroup = view.findViewById(R.id.rgAttendanceStatus)
        val rbPresent: RadioButton = view.findViewById(R.id.rbPresent)
        val rbLate: RadioButton = view.findViewById(R.id.rbLate)
        val rbExcused: RadioButton = view.findViewById(R.id.rbExcused)
        val rbAbsent: RadioButton = view.findViewById(R.id.rbAbsent)

        // 🟢 NEW: Recitation Controls
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

        val isAttendanceMode = currentMode == Mode.ATTENDANCE
        val attStatus = attendanceStatus[studentId] ?: ""
        val recitationPoints = recitationStatus[studentId] ?: 0

        // I-toggle ang visibility base sa mode
        holder.rgAttendanceStatus.visibility = if (isAttendanceMode) View.VISIBLE else View.GONE
        holder.llRecitationControls.visibility = if (isAttendanceMode) View.GONE else View.VISIBLE // NEW LINE

        holder.tvStudentId.text = "ID: $studentId"
        holder.tvStudentName.text = "${currentItem.lastName}, ${currentItem.firstName}"

        // I-display ang points sa recitation mode
        if (!isAttendanceMode) {
            holder.tvStudentName.text = "${holder.tvStudentName.text} (Recite: $recitationPoints)"
        }


        // --- Attendance Mode Logic ---
        if (isAttendanceMode) {
            holder.rgAttendanceStatus.setOnCheckedChangeListener(null)
            holder.rgAttendanceStatus.clearCheck()

            when (attStatus) {
                "Present" -> holder.rbPresent.isChecked = true
                "Late" -> holder.rbLate.isChecked = true
                "Excused" -> holder.rbExcused.isChecked = true
                "Absent" -> holder.rbAbsent.isChecked = true
            }

            listOf(holder.rbPresent, holder.rbLate, holder.rbExcused, holder.rbAbsent).forEach {
                it.isEnabled = isEditable
            }

            if (isEditable) {
                holder.rgAttendanceStatus.setOnCheckedChangeListener { _, checkedId ->
                    val selectedStatus = when (checkedId) {
                        holder.rbPresent.id -> "Present"
                        holder.rbLate.id -> "Late"
                        holder.rbExcused.id -> "Excused"
                        holder.rbAbsent.id -> "Absent"
                        else -> "Absent"
                    }

                    if (selectedStatus == "Absent" || selectedStatus == "Excused") {
                        val currentPoints = recitationStatus[studentId] ?: 0
                        if (currentPoints > 0) {
                            recitationStatus[studentId] = 0 // I-set sa zero!
                            Log.i(TAG, "Student $studentId attendance is $selectedStatus. Recitation reset to 0.")
                        }
                    }

                    attendanceStatus[studentId] = selectedStatus
                    Log.i(TAG, "Student $studentId attendance updated to $selectedStatus")
                    onDataChanged()
                }
            }
        }

        // --- Recitation Mode Logic (Multiple Points) ---
        else {
            val isAttended = attStatus == "Present" || attStatus == "Late"

            holder.tvReciteScore.text = recitationPoints.toString()

            // I-disable ang buong control group kung hindi present/late
            holder.llRecitationControls.alpha = if (isAttended) 1.0f else 0.5f
            val controlsEnabled = isEditable && isAttended

            holder.btnRecitePlus.isEnabled = controlsEnabled && recitationPoints < MAX_RECITATION_POINTS
            holder.btnReciteMinus.isEnabled = controlsEnabled && recitationPoints > 0

            if (controlsEnabled) {
                // I-increment ang points
                holder.btnRecitePlus.setOnClickListener {
                    if (recitationPoints < MAX_RECITATION_POINTS) {
                        recitationStatus[studentId] = recitationPoints + 1
                        notifyItemChanged(position)
                        Log.i(TAG, "Student $studentId recitation points: ${recitationPoints + 1}")
                        onDataChanged()
                    }
                }

                // I-decrement ang points
                holder.btnReciteMinus.setOnClickListener {
                    if (recitationPoints > 0) {
                        recitationStatus[studentId] = recitationPoints - 1
                        notifyItemChanged(position)
                        Log.i(TAG, "Student $studentId recitation points: ${recitationPoints - 1}")
                        onDataChanged()
                    }
                }
            } else {
                // Clear listeners kung hindi enabled
                holder.btnRecitePlus.setOnClickListener(null)
                holder.btnReciteMinus.setOnClickListener(null)
            }
        }
        if (isEditable) {
            // Full opacity kung editable
            holder.itemView.alpha = 1.0f
        } else {
            // I-dim ang buong item kung read-only (e.g., nakaraang araw)
            holder.itemView.alpha = 0.8f
        }
    }

    override fun getItemCount() = studentList.size

    fun getAttendanceAndRecitationMaps(): Pair<Map<String, String>, Map<String, Int>> {
        return Pair(attendanceStatus, recitationStatus)
    }

    fun updateStatuses(existingAttendance: Map<String, String>, existingRecitation: Map<String, Int>) {
        attendanceStatus.clear()
        recitationStatus.clear()

        // 🟢 CRITICAL FIX: Populate the internal maps
        attendanceStatus.putAll(existingAttendance)
        recitationStatus.putAll(existingRecitation)

        notifyDataSetChanged()
    }

    fun setMode(mode: Mode) {
        if (currentMode != mode) {
            this.currentMode = mode
            notifyDataSetChanged()
        }
    }

    fun setEditable(editable: Boolean) {
        if (isEditable != editable) {
            isEditable = editable
            notifyDataSetChanged()
        }
    }
}
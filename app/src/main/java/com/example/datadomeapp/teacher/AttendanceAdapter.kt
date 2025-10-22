package com.example.datadomeapp.teacher

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student

class AttendanceAdapter(
    private val studentList: List<Student>,
    private val assignmentId: String,
    private var isEditable: Boolean = true
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    companion object {
        private const val TAG = "AttendanceAdapter"
    }

    // Map: studentId -> Status ("Present", "Late", "Absent", "Excused")
    private val attendanceStatus = mutableMapOf<String, String>()

    init {
        Log.d(TAG, "Initialized for assignment: $assignmentId with ${studentList.size} students.")
    }

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvAttStudentName)
        val tvStudentId: TextView = view.findViewById(R.id.tvAttStudentId)
        val rgAttendanceStatus: RadioGroup = view.findViewById(R.id.rgAttendanceStatus)
        val rbPresent: RadioButton = view.findViewById(R.id.rbPresent)
        val rbLate: RadioButton = view.findViewById(R.id.rbLate)
        val rbExcused: RadioButton = view.findViewById(R.id.rbExcused)
        val rbAbsent: RadioButton = view.findViewById(R.id.rbAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.teacher_item_attendance_student, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val currentItem = studentList[position]
        val studentId = currentItem.id

        if (studentId == null) {
            Log.e(TAG, "Student at position $position has null ID — skipping bind.")
            return
        }

        holder.tvStudentName.text = "${currentItem.lastName}, ${currentItem.firstName}"
        holder.tvStudentId.text = "ID: $studentId"

        Log.d(TAG, "Binding $studentId (pos $position), editable=$isEditable")

        // 1️⃣ Disable listener + clear previous selection
        holder.rgAttendanceStatus.setOnCheckedChangeListener(null)
        holder.rgAttendanceStatus.clearCheck()

        // 2️⃣ Restore stored status
        when (attendanceStatus[studentId]) {
            "Present" -> holder.rbPresent.isChecked = true
            "Late" -> holder.rbLate.isChecked = true
            "Excused" -> holder.rbExcused.isChecked = true
            "Absent" -> holder.rbAbsent.isChecked = true
        }

        // 3️⃣ Enable/disable all radio buttons based on editable state
        listOf(holder.rbPresent, holder.rbLate, holder.rbExcused, holder.rbAbsent).forEach {
            it.isEnabled = isEditable
        }

        // 4️⃣ Attach listener if editable
        if (isEditable) {
            holder.rgAttendanceStatus.setOnCheckedChangeListener { group, checkedId ->
                if (checkedId == -1) {
                    Log.w(TAG, "Ignored clearCheck() event for $studentId")
                    return@setOnCheckedChangeListener
                }

                // ✅ FIX: Gumamit ng 'when' statement para i-determine ang status base sa ID
                val selectedStatus = when (checkedId) {
                    holder.rbPresent.id -> "Present"
                    holder.rbLate.id -> "Late"
                    holder.rbExcused.id -> "Excused"
                    holder.rbAbsent.id -> "Absent"
                    else -> "Absent"
                }

                // ❌ TANGGALIN ITO: Ang logic na ito ay umaasa sa tag, na hindi nakikita
                // val selected = group.findViewById<RadioButton>(checkedId)
                // selected?.let {
                //     attendanceStatus[studentId] = it.tag.toString()
                //     Log.i(TAG, "Student $studentId updated to ${it.tag}")
                // }

                // ✅ Gamitin ang mas malinis na logic
                attendanceStatus[studentId] = selectedStatus
                Log.i(TAG, "Student $studentId updated to $selectedStatus")
            }
        }
    }

    override fun getItemCount() = studentList.size

    // ✅ Retrieve final attendance map
    fun getAttendanceMap(): Map<String, String> = attendanceStatus

    // ✅ Pre-fill statuses from Firestore records
    fun updateStatuses(existingStatuses: Map<String, String>) {
        Log.d(TAG, "Updating from Firestore: ${existingStatuses.size} entries")

        attendanceStatus.clear()

        // Ilagay ang mga bago at existing na records
        existingStatuses.forEach { (studentId, status) ->
            attendanceStatus[studentId] = status
        }
        notifyDataSetChanged()
    }

    // ✅ Toggle edit/view mode
    fun setEditable(editable: Boolean) {
        if (isEditable != editable) {
            isEditable = editable
            Log.w(TAG, "Editable state changed to $editable")
            notifyDataSetChanged()
        }
    }
}

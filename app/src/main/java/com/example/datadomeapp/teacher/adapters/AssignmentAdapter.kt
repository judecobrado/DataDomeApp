package com.example.datadomeapp.teacher.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Assignment
import java.text.SimpleDateFormat
import java.util.*

class AssignmentAdapter(context: Context, private var assignments: MutableList<Assignment>) :
    ArrayAdapter<Assignment>(context, 0, assignments) {

    fun updateData(newAssignments: List<Assignment>) {
        assignments.clear()
        assignments.addAll(newAssignments)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_assignment_student, parent, false)

        val assignment = assignments[position]

        val titleText: TextView = view.findViewById(R.id.textAssignmentTitle)
        val descText: TextView = view.findViewById(R.id.textAssignmentDescription)
        val dueText: TextView = view.findViewById(R.id.textAssignmentDueDate)

        // 🧩 Title
        titleText.text = assignment.title.ifEmpty { "Untitled" }

        // 🧩 Description + Submission count
        val submissionText = "Submissions: ${assignment.submissionCount ?: 0}"
        descText.text = "${assignment.instructions.ifEmpty { "No instructions" }}\n$submissionText"

        // 🧩 Due Date
        if (assignment.dueDateMillis > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            dueText.text = "Due: ${sdf.format(Date(assignment.dueDateMillis))}"
        } else {
            dueText.text = "No due date"
        }

        return view
    }
}

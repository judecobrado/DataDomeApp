package com.example.datadomeapp.student

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Assignment
import java.text.SimpleDateFormat
import java.util.*

class StudentAssignmentsAdapter(
    private val context: Context,
    private val assignments: List<Assignment>
) : BaseAdapter() {

    override fun getCount(): Int = assignments.size
    override fun getItem(position: Int): Any = assignments[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(R.layout.item_assignment_student, parent, false)

        val assignment = assignments[position]
        val tvTitle = view.findViewById<TextView>(R.id.tvAssignmentTitle)
        val tvDue = view.findViewById<TextView>(R.id.tvAssignmentDue)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitAssignment)

        tvTitle.text = assignment.title
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        tvDue.text = "Due: ${sdf.format(Date(assignment.dueDateMillis))}"

        btnSubmit.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Submit Assignment")
                .setMessage("Would you like to mark '${assignment.title}' as submitted?")
                .setPositiveButton("Submit") { _, _ ->
                    Toast.makeText(context, "Assignment submitted!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return view
    }
}

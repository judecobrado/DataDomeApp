package com.example.datadomeapp.teacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.example.datadomeapp.models.Student

class ClassStudentAdapter(
    private val studentList: List<Student>
) : RecyclerView.Adapter<ClassStudentAdapter.StudentViewHolder>() {

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentId: TextView = view.findViewById(R.id.tvStudentId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.teacher_item_student_in_class, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val currentItem = studentList[position]

        holder.tvStudentName.text = "${currentItem.lastName}, ${currentItem.firstName}"
        holder.tvStudentId.text = "ID: ${currentItem.id} | Course: ${currentItem.courseCode}"

        // Optional: Magdagdag ng click listener kung gusto mong makita ang student profile
        // holder.itemView.setOnClickListener { /* Navigate to Student Profile */ }
    }

    override fun getItemCount() = studentList.size
}
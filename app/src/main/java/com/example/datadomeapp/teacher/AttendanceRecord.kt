package com.example.datadomeapp.teacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.Timestamp

data class AttendanceRecord(
    val studentId: String = "",
    val assignmentId: String = "",
    val subjectCode: String = "",
    val date: String = "", // E.g., "YYYY-MM-DD"
    val status: String = "Absent", // "Present", "Late", "Absent"
    val timestamp: Timestamp = Timestamp.now()
)

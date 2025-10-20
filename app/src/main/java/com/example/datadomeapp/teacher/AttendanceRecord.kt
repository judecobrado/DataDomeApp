// File: com.example.datadomeapp.models/AttendanceRecord.kt
package com.example.datadomeapp.teacher

import com.google.firebase.Timestamp

data class AttendanceRecord(
    val studentId: String = "",
    val assignmentId: String = "",
    val subjectCode: String = "",
    val date: String = "", // E.g., "YYYY-MM-DD"
    val status: String = "Absent", // "Present", "Late", "Absent"
    val timestamp: Timestamp = Timestamp.now()
)
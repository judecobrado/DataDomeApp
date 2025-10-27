package com.example.datadomeapp.student

data class StudentSubjectAttendance(
    val subjectCode: String,
    val subjectTitle: String,
    val totalClasses: Int,
    val totalPresent: Int,
    val totalAbsent: Int,
    val totalLate: Int,
    val totalExcused: Int,
    val attendancePercentage: Double,
    val assignmentId: String
)

package com.example.datadomeapp.models

data class ClassAssignment(
    val assignmentId: String = "", // Document ID
    val courseCode: String = "",
    val yearLevel: String = "", // e.g., "1st Year"
    val sectionBlock: String = "", // e.g., "A"
    val sectionName: String = "", // e.g., "BSIT-1-A"
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val day: String = "", // e.g., "Mon"
    val startTime: String = "", // e.g., "08:00" (HH:mm 24-hour)
    val endTime: String = "", // e.g., "10:00" (HH:mm 24-hour)
    val roomNumber: String = "",
    val maxCapacity: Int = 0,
    val enrolledCount: Int = 0,
    val onlineClassLink: String = ""
)

data class Room(
    val id: String = "",       // Hal: "RM-305"
)
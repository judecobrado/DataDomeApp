package com.example.datadomeapp.models

// Ginagamit para sa Subject Assignment sa ilalim ng Student record
data class StudentSubject(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val assignmentNo: String = "",
    val sectionBlock: String = "",
    val sectionName: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val schedule: String = "",
    val roomNumber: String = "",
    val onlineClassLink: String? = null,
)

data class OnlineClassAssignment(
    // Identifiers
    val assignmentId: String = "",
    val courseCode: String = "",
    val sectionName: String = "",
    val roomNumber: String = "",

    val subjectTitle: String = "",
    val teacherName: String = "",
    val subjectCode: String,
    // Schedule Fields
    val day: String = "", // e.g., "Fri"
    val startTime: String = "", // e.g., "7:00 AM"
    val endTime: String = "", // e.g., "9:30 AM"

    // Online Link
    val onlineClassLink: String? = null,

    // Iba pang fields
    val enrolledCount: Int = 0,
    val maxCapacity: Int = 0,
    val sectionBlock: String = ""
)

// Curriculum Model (para sa required subjects)
data class Curriculum(
    val courseCode: String = "",
    val yearLevel: String = "",
    val requiredSubjects: List<SubjectEntry> = emptyList()
)

data class SubjectEntry(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val units: Int = 3,
    val credits: Int = 3
)

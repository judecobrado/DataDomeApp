package com.example.datadomeapp.models

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
    val semester: String? = null,
    val onlineClassLink: String? = null,
    val onlineLink: String? = null,

    // 🛑 CRITICAL FIX: Changed from Double? to String? to match Firestore data 🛑
    val gwa: String? = null,
    val final: String? = null,
    val prelim: String? = null,
    val midterm: String? = null,

    // Kept as original types (assuming they match the database)
    val academicYear: String? = null,
    val credits: Int? = null,
    val yearLevel: String? = null,
)

data class ClassSchedule(
    val day: String,
    val startTime: String,
    val endTime: String,
    val room: String
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
    val allSchedules: List<ClassSchedule> = emptyList(),

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

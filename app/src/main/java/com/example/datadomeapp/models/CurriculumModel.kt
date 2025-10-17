package com.example.datadomeapp.models

// Data Model na ginamit sa iyong ManageEnrollmentsActivity.kt
data class Enrollment(
    val id: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val course: String = "",
    val yearLevel: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val status: String = "pending"
)

// Ginagamit para sa Subject Assignment sa ilalim ng Student record
data class StudentSubject(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val sectionName: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val schedule: String = ""
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
    val units: Int = 3
)

// Class Assignment Model (Schedule/Section Load) - Ito ang may Capacity Limit
data class ClassAssignment(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val courseCode: String = "",
    val yearLevel: String = "",
    val sectionName: String = "",       // e.g., BSIT-1A-MATH101
    val assignedTeacherId: String = "",
    val assignedTeacherName: String = "",
    val schedule: String = "",
    val maxCapacity: Int = 50,
    val currentlyEnrolled: Int = 0
)
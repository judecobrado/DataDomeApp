package com.example.datadomeapp.admin

// Data Model para sa pending enrollment forms
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
    val status: String = "pending" // Idinagdag para sa tracking
)

// Data Model para sa assigned subjects ng student
data class StudentSubject(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val sectionName: String = "", // e.g., 'ITEP111-A'
    val teacherId: String = "",   // ID ng guro
    val teacherName: String = "", // Pangalan ng guro
    val schedule: String = ""     // e.g., 'MW 8:00AM - 10:00AM'
)
package com.example.datadomeapp.admin

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
    val guardianRelationship: String = "",
    // Father information
    val fatherFirstName: String = "",
    val fatherMiddleName: String = "",
    val fatherLastName: String = "",
    val fatherDOB: String = "",
    val fatherPhone: String = "",
    val fatherOccupation: String = "",
    // Mother information
    val motherFirstName: String = "",
    val motherMiddleName: String = "",
    val motherLastName: String = "",
    val motherDOB: String = "",
    val motherPhone: String = "",
    val motherOccupation: String = "",
    val status: String = "pending",
    val data: Map<String, Any> = emptyMap()
)

data class StudentSubjectss(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val sectionName: String = "", // e.g., 'ITEP111-A'
    val teacherId: String = "",   // ID ng guro
    val teacherName: String = "", // Pangalan ng guro
    val schedule: String = ""     // e.g., 'MW 8:00AM - 10:00AM'
)
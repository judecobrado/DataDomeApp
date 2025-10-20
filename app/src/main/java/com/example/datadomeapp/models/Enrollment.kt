package com.example.datadomeapp.models

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
    val status: String = "pending",
    // 🛑 BAGONG FIELD: Kailangan ito para mabasa ang applicationType at timestamp
    val data: Map<String, Any> = emptyMap()
)

// I-aalis ang CourseSections (Single Document)
// Ibalik ang SectionBlock (Multi-Document)
data class SectionBlock(
    val courseCode: String = "",
    val blockName: String = "" // e.g., "A", "B", "C"
)
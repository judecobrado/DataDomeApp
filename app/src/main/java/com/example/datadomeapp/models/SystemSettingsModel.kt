package com.example.datadomeapp.models

// Para sa nested field: academicTerm
data class AcademicTerm(
    val academicYear: String = "",
    val semester: String = ""
)

// Ang pangunahing document structure
data class SystemSettings(
    val currentTerm: String = "",
    val academicTerm: AcademicTerm = AcademicTerm()
)
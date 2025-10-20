package com.example.datadomeapp.models

/**
 * Data model para sa isang klase sa schedule ng estudyante.
 * Ginagamit ito para sa pagkuha at pag-store ng data sa database (hal. Firestore).
 */
data class ScheduleItem(
    // Identifiers
    val subjectCode: String = "",       // Hal: "CS 101"
    val subjectTitle: String = "",      // Hal: "Programming Fundamentals"
    val sectionId: String = "",         // Hal: "BSIT-1A" (Para sa filtering)

    // Time and Location
    val startTime: String = "",         // Hal: "08:00 AM" (String para sa display)
    val endTime: String = "",           // Hal: "09:30 AM"
    val dayOfWeek: String = "",         // Hal: "Monday" (Para sa daily schedule check)
    val venue: String = "",             // Hal: "Rm 305" or "Online via Google Meet"

    // Optional Details
    val instructor: String = "",        // Hal: "Prof. Cruz"
    val semester: String = "2025-2026 1st Sem" // Hal: Kasalukuyang academic period
)
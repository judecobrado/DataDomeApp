package com.example.datadomeapp.models

// 1. AppUser: Para sa records sa 'users' collection (Authentication/Authorization)
data class AppUser(
    val uid: String = "",       // Ang Document ID ay karaniwang ang Auth UID
    val email: String = "",
    val role: String = "student", // student, teacher, admin
    val studentId: String? = null // Para sa students
)

// 2. Teacher: Para sa records sa 'teachers' collection (Profile Data)
// Ito ang gagamitin ng ManageSchedulesActivity.
data class Teacher(
    val uid: String = "",       // Foreign key sa AppUser (UID)
    val teacherId: String = "", // Internal Teacher ID (e.g., T-001)
    val name: String = "",
    val phone: String = ""
    // Maaari ring magdagdag ng ibang fields (e.g., specialization, department)
)

// ... (Iba pang models: Enrollment, ClassAssignment, Curriculum, SubjectEntry, StudentSubject)
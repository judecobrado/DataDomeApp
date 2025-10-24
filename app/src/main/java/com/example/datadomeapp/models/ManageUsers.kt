package com.example.datadomeapp.models

import com.google.firebase.Timestamp // Don't forget this import!

// 🧑‍🎓 Student model
data class Student(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val birthday: String = "",
    val section: String = "",
    val assignedSchedules: List<String> = emptyList(),
    val courseCode: String = "",

    val address: String? = null,
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val guardianRelationship: String? = null,
    val academicYear: String? = null,
    val guardianName: String? = null,
    val yearLevel: String? = null,
    val phone: String? = null,
    val guardianPhone: String? = null,
    val dateEnrolled: Timestamp? = null, // 🌟 FIX: Use Timestamp to match Firestore data type    val semester: String? = null,
    val middleName: String? = null,
    val semester: String? = null,
    val isEnrolled: Boolean = false,
    val enrollmentType: String? = null,
    val userUid: String? = null,
    val status: String? = null
)

// 👩‍🏫 Teacher model
data class AdminTeacher(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val subject: String = "",
    val sectionAssigned: String = "",
    val department: String = ""
)

// 👩‍🍳 Canteen Staff model
data class CanteenStaff(
    var email: String = "",
    var role: String = "",
    var canteenName: String = "",
    var firstName: String = "",
    var middleName: String = "",
    var lastName: String = "",
    var uid: String? = null,
    var storeImageUrl: String = "",
    var canteenStaffId: String = "",
    var firestoreId: String? = null    // <-- Add this
)


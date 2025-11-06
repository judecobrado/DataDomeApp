package com.example.datadomeapp.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Student(
    var id: String = "", // ✅ Change from val to var
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
    val dateEnrolled: Timestamp? = null,
    val semester: String? = null,
    val middleName: String? = null,
    val isEnrolled: Boolean = false,
    val enrollmentType: String? = null,
    val userUid: String? = null,
    val status: String? = null
) : Parcelable // ✅ Don't forget to make it Parcelable

@Parcelize
data class AdminTeacher(
    var id: String = "", // ✅ Also make this mutable
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val subject: String = "",
    val sectionAssigned: String = "",
    val department: String = ""
) : Parcelable

@Parcelize
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
    var firestoreId: String? = null
) : Parcelable
package com.example.datadomeapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Assignment(
    var id: String = "",
    var teacherId: String = "",
    var title: String = "",
    var instructions: String = "",
    var fileUrl: String? = null,
    var dueDateMillis: Long = 0L,
    var classId: String = "",
    var submissionCount: Int? = 0,
    var createdAt: Long = System.currentTimeMillis(),
    val academicTerm: String? = null,
    val academicYear: String? = null,
    val semester: String? = null,
    // 🆕 Individual student extensions
    var studentExtensions: Map<String, StudentExtension> = emptyMap()
) : Parcelable

@Parcelize
data class StudentExtension(
    val studentId: String = "",
    val studentName: String = "",
    val extendedDueDate: Long = 0L,
    val reason: String = "",
    val grantedAt: Long = System.currentTimeMillis(),
    val grantedBy: String = "" // teacher ID
) : Parcelable
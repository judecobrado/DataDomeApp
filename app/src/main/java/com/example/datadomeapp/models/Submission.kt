package com.example.datadomeapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Submission(
    var id: String = "",
    var assignmentId: String = "",
    var studentId: String = "",
    var fileUrl: String? = null,
    var imageUrl: String? = null,
    var submittedAt: Long = 0L,
    var grade: Double? = null,
    var feedback: String? = null,
    var gradedAt: Long? = null,
    var classId: String = "",
    // 🆕 Submission status
    var status: String = "pending", // pending, submitted, late, excused
    var isResubmitted: Boolean = false
) : Parcelable
package com.example.datadomeapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class ScheduleItem(
    val subjectCode: String,
    val sectionName: String,
    val startTime: String,
    val endTime: String,
    val venue: String,
    val day: String
) : Parcelable


data class ScheduleBlock(
    val subjectCode: String,
    val venue: String,
    val startTime: String,
    val endTime: String,
    val day: String,
    val startRow: Int,
    val rowSpan: Int
)
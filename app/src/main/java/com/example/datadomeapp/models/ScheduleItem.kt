package com.example.datadomeapp.models

data class ScheduleBlock(
    val subjectCode: String,
    val venue: String,
    val startTime: String,
    val endTime: String,
    val day: String,
    val startRow: Int,
    val rowSpan: Int
)
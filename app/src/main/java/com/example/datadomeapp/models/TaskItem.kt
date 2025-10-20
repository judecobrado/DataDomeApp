package com.example.datadomeapp.models

data class TaskItem(
    val taskId: String = "",
    val title: String = "",
    val details: String = "",
    val date: String = "",
    val time: String = "",
    val done: Boolean = false,
    val timestamp: Long = 0L
)
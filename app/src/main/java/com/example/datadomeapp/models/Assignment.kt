package com.example.datadomeapp.models

data class Assignment(
    var id: String = "",
    var teacherId: String = "",
    var title: String = "",
    var instructions: String = "",
    var fileUrl: String? = null,
    var dueDateMillis: Long = 0L,
    var classId: String = "", // which class / section
    var createdAt: Long = System.currentTimeMillis()
)

package com.example.datadomeapp.models

data class RfidHistory(
    val studentId: String,
    val userUid: String,
    val oldRfidTag: String?,
    val newRfidTag: String?,
    val action: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val adminUid: String? = null
)
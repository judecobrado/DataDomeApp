package com.example.datadomeapp.enrollment

interface EmailSendCallback {
    fun onSending()
    fun onSuccess()
    fun onComplete(success: Boolean)
}
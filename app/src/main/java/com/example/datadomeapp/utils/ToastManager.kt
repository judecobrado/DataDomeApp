package com.example.utils

import android.content.Context
import android.widget.Toast

object ToastManager {
    private var currentToast: Toast? = null

    fun showToast(context: Context, message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    fun showLongToast(context: Context, message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        currentToast?.show()
    }

    fun cancelCurrent() {
        currentToast?.cancel()
        currentToast = null
    }
}
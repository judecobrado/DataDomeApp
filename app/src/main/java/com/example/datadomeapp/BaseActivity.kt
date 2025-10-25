package com.example.datadomeapp

import androidx.appcompat.app.AppCompatActivity

import android.widget.ProgressBar
import android.view.View

open class BaseActivity : AppCompatActivity() {
    // I-override ito sa bawat activity
    protected open fun getLoadingProgressBar(): ProgressBar? = null

    fun showLoading() {
        getLoadingProgressBar()?.visibility = View.VISIBLE
    }

    fun hideLoading() {
        getLoadingProgressBar()?.visibility = View.GONE
    }
}
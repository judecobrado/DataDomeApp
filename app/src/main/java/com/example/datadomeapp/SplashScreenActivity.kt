package com.example.datadomeapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import kotlin.random.Random

class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoIcon = findViewById<ImageView>(R.id.logoIcon)
        val appName = findViewById<TextView>(R.id.appName)
        val appTagline = findViewById<TextView>(R.id.appTagline)
        val loadingProgress = findViewById<ProgressBar>(R.id.loadingProgress)

        // Hide elements initially
        logoIcon.alpha = 0f
        appName.alpha = 0f
        appTagline.alpha = 0f
        loadingProgress.alpha = 0f

        // Start animation sequence
        startSplashAnimation(logoIcon, appName, appTagline, loadingProgress)
    }

    private fun startSplashAnimation(
        logoIcon: ImageView,
        appName: TextView,
        appTagline: TextView,
        loadingProgress: ProgressBar
    ) {
        // Sequence of animations
        val handler = Handler(Looper.getMainLooper())

        // 1. Logo entrance
        handler.postDelayed({
            animateLogoEntrance(logoIcon)
        }, 300)

        // 2. App name entrance
        handler.postDelayed({
            animateTextEntrance(appName)
        }, 600)

        // 3. Tagline entrance
        handler.postDelayed({
            animateTextEntrance(appTagline)
        }, 900)

        // 4. Progress bar entrance and loading simulation
        handler.postDelayed({
            animateProgressLoading(loadingProgress)
        }, 1200)

        // 5. Navigate to main activity
        handler.postDelayed({
            navigateToMainActivity()
        }, 3500)
    }

    private fun animateLogoEntrance(logoIcon: ImageView) {
        // Scale up animation
        val scaleX = ObjectAnimator.ofFloat(logoIcon, View.SCALE_X, 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(logoIcon, View.SCALE_Y, 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(logoIcon, View.ALPHA, 0f, 1f)

        scaleX.duration = 600
        scaleY.duration = 600
        alpha.duration = 600

        scaleX.interpolator = DecelerateInterpolator()
        scaleY.interpolator = DecelerateInterpolator()

        scaleX.start()
        scaleY.start()
        alpha.start()

        // Add subtle floating animation
        startFloatingAnimation(logoIcon)
    }

    private fun animateTextEntrance(textView: TextView) {
        val slideUp = ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 50f, 0f)
        val alpha = ObjectAnimator.ofFloat(textView, View.ALPHA, 0f, 1f)

        slideUp.duration = 500
        alpha.duration = 500

        slideUp.interpolator = DecelerateInterpolator()

        slideUp.start()
        alpha.start()
    }

    private fun animateProgressLoading(progressBar: ProgressBar) {
        // Fade in
        val fadeIn = ObjectAnimator.ofFloat(progressBar, View.ALPHA, 0f, 1f)
        fadeIn.duration = 400
        fadeIn.start()

        // Simulate loading progress
        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 2000
        progressAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            progressBar.progress = progress
        }
        progressAnimator.start()
    }

    private fun startFloatingAnimation(view: View) {
        val floatAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -10f, 0f)
        floatAnimator.duration = 2000
        floatAnimator.repeatCount = ObjectAnimator.INFINITE
        floatAnimator.repeatMode = ObjectAnimator.REVERSE
        floatAnimator.interpolator = AccelerateDecelerateInterpolator()
        floatAnimator.start()
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // Optional: Add background color transition for more visual interest
    private fun animateBackgroundTransition() {
        val rootView = findViewById<View>(android.R.id.content)
        val colorAnimation = ValueAnimator.ofArgb(
            Color.parseColor("#2196F3"),
            Color.parseColor("#1976D2"),
            Color.parseColor("#2196F3")
        )
        colorAnimation.duration = 4000
        colorAnimation.addUpdateListener { animator ->
            rootView.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.repeatCount = ValueAnimator.INFINITE
        colorAnimation.repeatMode = ValueAnimator.REVERSE
        colorAnimation.start()
    }
}
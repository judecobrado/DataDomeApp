package com.example.datadomeapp

import android.animation.*
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.MainActivity // Assuming MainActivity exists
import kotlin.random.Random

class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.imgLogo)
        val particleContainer = findViewById<FrameLayout>(R.id.particleContainer)
        val lightStreak = findViewById<View>(R.id.lightStreak)
        val doorLeft = findViewById<View>(R.id.doorLeft)
        val doorRight = findViewById<View>(R.id.doorRight)

        // -------------------------
        // Particles and Sparkle Trails
        // -------------------------
        repeat(20) { addParticle(particleContainer) }
        repeat(15) { addSparkleTrail(particleContainer) }

        // -------------------------
        // Door Swing Animation with slight overshoot
        // -------------------------
        val doorLeftSwing = ObjectAnimator.ofFloat(doorLeft, View.ROTATION_Y, 0f, -95f, -90f)
        val doorRightSwing = ObjectAnimator.ofFloat(doorRight, View.ROTATION_Y, 0f, 95f, 90f)
        doorLeftSwing.duration = 1200
        doorRightSwing.duration = 1200
        doorLeftSwing.interpolator = AccelerateDecelerateInterpolator()
        doorRightSwing.interpolator = AccelerateDecelerateInterpolator()

        val doorsSet = AnimatorSet()
        doorsSet.playTogether(doorLeftSwing, doorRightSwing)
        doorsSet.start()

        // -------------------------
        // Logo Reveal + Springy Bounce
        // -------------------------
        doorsSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val fadeIn = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f)
                val scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.5f, 1.3f, 1f)
                val scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.5f, 1.3f, 1f)
                val rotate = ObjectAnimator.ofFloat(logo, View.ROTATION, -5f, 5f, 0f)

                val logoSet = AnimatorSet()
                logoSet.playTogether(fadeIn, scaleX, scaleY, rotate)
                logoSet.duration = 2000
                logoSet.interpolator = OvershootInterpolator(2f) // springy effect
                logoSet.start()

                // -------------------------
                // Light streak animation
                // -------------------------
                val streakX = ObjectAnimator.ofFloat(lightStreak, View.TRANSLATION_X, -250f, 250f)
                val streakAlpha = ObjectAnimator.ofFloat(lightStreak, View.ALPHA, 0f, 0.7f, 0f)
                streakX.duration = 1500
                streakAlpha.duration = 1500
                streakX.repeatCount = 1
                streakAlpha.repeatCount = 1
                streakX.repeatMode = ValueAnimator.RESTART
                streakAlpha.repeatMode = ValueAnimator.RESTART

                val streakSet = AnimatorSet()
                streakSet.playTogether(streakX, streakAlpha)
                streakSet.startDelay = 500
                streakSet.start()

                // Navigate to MainActivity after animation
                logoSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        startActivity(Intent(this@SplashScreenActivity, MainActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                })
            }
        })
    }

    // -------------------------
    // Particle Functions
    // -------------------------
    private fun addParticle(container: FrameLayout) {
        val particle = View(this).apply {
            setBackgroundResource(R.drawable.particle_glow) // Make sure you have this drawable
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(6, 6)
        }
        container.addView(particle)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val startX = Random.nextInt(screenWidth)
        val startY = Random.nextInt(screenHeight / 2, screenHeight / 2 + 50)
        particle.translationX = startX.toFloat()
        particle.translationY = startY.toFloat()

        val deltaX = Random.nextInt(-50, 50)
        val deltaY = Random.nextInt(-100, -50)

        // 🟢 INAYOS NA: Gumamit ng "translationX" String
        val moveX = ObjectAnimator.ofFloat(particle, "translationX", startX.toFloat(), startX + deltaX.toFloat())
        val moveY = ObjectAnimator.ofFloat(particle, "translationY", startY.toFloat(), startY + deltaY.toFloat())

        val fade = ObjectAnimator.ofFloat(particle, View.ALPHA, 0f, 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(particle, View.SCALE_X, 0.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(particle, View.SCALE_Y, 0.5f, 1f)

        val set = AnimatorSet()
        set.playTogether(moveX, moveY, fade, scaleX, scaleY)
        set.duration = Random.nextLong(1500, 2500)
        set.interpolator = LinearInterpolator()
        set.startDelay = Random.nextLong(0, 500)
        set.start()

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                container.removeView(particle)
                addParticle(container)
            }
        })
    }

    private fun addSparkleTrail(container: FrameLayout) {
        val sparkle = View(this).apply {
            setBackgroundResource(R.drawable.sparkle) // Make sure you have this drawable
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(4, 4)
        }
        container.addView(sparkle)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val startX = Random.nextInt(screenWidth)
        val startY = Random.nextInt(screenHeight)
        sparkle.translationX = startX.toFloat()
        sparkle.translationY = startY.toFloat()

        val endX = startX + Random.nextInt(-100, 100)
        val endY = startY - Random.nextInt(100, 200)

        // 🟢 INAYOS NA: Gumamit ng "translationX" String
        val moveX = ObjectAnimator.ofFloat(sparkle, "translationX", startX.toFloat(), endX.toFloat())
        val moveY = ObjectAnimator.ofFloat(sparkle, "translationY", startY.toFloat(), endY.toFloat())

        val fade = ObjectAnimator.ofFloat(sparkle, View.ALPHA, 0f, 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(sparkle, View.SCALE_X, 0.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(sparkle, View.SCALE_Y, 0.3f, 1f)

        val set = AnimatorSet()
        set.playTogether(moveX, moveY, fade, scaleX, scaleY)
        set.duration = Random.nextLong(2000, 3500)
        set.interpolator = LinearInterpolator()
        set.startDelay = Random.nextLong(0, 500)
        set.start()

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                container.removeView(sparkle)
                addSparkleTrail(container)
            }
        })
    }
}
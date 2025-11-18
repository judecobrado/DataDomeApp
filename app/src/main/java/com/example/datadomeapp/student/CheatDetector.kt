package com.example.datadomeapp.student

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class CheatDetector(private val context: Context, private val quizId: String) {

    private val _cheatEvent = MutableLiveData<CheatEvent>()
    val cheatEvent: LiveData<CheatEvent> = _cheatEvent

    var isMonitoring = false
    private var homeButtonPressed = false
    private var lastScreenOffTime = 0L
    private var lastCheatTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    // Cooldown para iwas multiple triggers
    private val CHEAT_COOLDOWN = 1000L // 3 seconds between cheat detections

    // Broadcast Receivers
    private lateinit var screenStateReceiver: BroadcastReceiver
    private lateinit var closeSystemDialogReceiver: BroadcastReceiver

    // Cheat types
    enum class CheatType {
        HOME_BUTTON,
        RECENT_APPS,
        POWER_BUTTON,
        NOTIFICATION_PANEL,
        MULTI_WINDOW,
        ORIENTATION_CHANGE,
        SCREEN_OFF,
        APP_SWITCH,
        LOCK_SCREEN,
        UNKNOWN
    }

    data class CheatEvent(
        val type: CheatType,
        val reason: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        registerReceivers()
        startPeriodicChecks()

        Log.d("CheatDetector", "Cheat monitoring started for quiz: $quizId")
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        unregisterReceivers()
        handler.removeCallbacksAndMessages(null)

        Log.d("CheatDetector", "Cheat monitoring stopped for quiz: $quizId")
    }

    fun setHomeButtonPressed(pressed: Boolean) {
        homeButtonPressed = pressed
    }

    private fun registerReceivers() {
        // Screen State Receiver (Power Button)
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        lastScreenOffTime = System.currentTimeMillis()
                        triggerCheat(CheatType.POWER_BUTTON, "Screen turned off")
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Check if screen was off for suspicious time
                        val offDuration = System.currentTimeMillis() - lastScreenOffTime
                        if (offDuration > 2000) { // More than 2 seconds
                            triggerCheat(CheatType.SCREEN_OFF, "Screen off for ${offDuration}ms")
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // Device unlocked
                        triggerCheat(CheatType.LOCK_SCREEN, "Device was locked/unlocked")
                    }
                }
            }
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenStateReceiver, screenFilter)

        // Close System Dialogs Receiver (Home/Recent Apps)
        closeSystemDialogReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    val reason = intent.getStringExtra("reason")
                    when (reason) {
                        "homekey" -> {
                            homeButtonPressed = true
                            triggerCheat(CheatType.HOME_BUTTON, "Home button pressed")
                        }
                        "recentapps" -> {
                            triggerCheat(CheatType.RECENT_APPS, "Recent apps button pressed")
                        }
                        "assist" -> {
                            triggerCheat(CheatType.APP_SWITCH, "Assist/Google Now pressed")
                        }
                    }
                }
            }
        }

        val closeSystemFilter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        ContextCompat.registerReceiver(
            context,
            closeSystemDialogReceiver,
            closeSystemFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterReceivers() {
        try {
            context.unregisterReceiver(screenStateReceiver)
            context.unregisterReceiver(closeSystemDialogReceiver)
        } catch (e: Exception) {
            Log.e("CheatDetector", "Error unregistering receivers", e)
        }
    }

    private fun startPeriodicChecks() {
        handler.postDelayed({
            if (isMonitoring) {
                // REMOVED: checkNotificationPanel() - Ito ang cause ng false positives
                checkMultiWindowMode()
                startPeriodicChecks()
            }
        }, 2000) // Increased to 2 seconds (less aggressive)
    }

    // REMOVED: checkNotificationPanel() function entirely
    // Ito ang nag-cause ng multiple cheat counts kapag nag-click ng buttons

    private fun checkMultiWindowMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val activity = context as? Activity
            activity?.let {
                if (it.isInMultiWindowMode) {
                    triggerCheat(CheatType.MULTI_WINDOW, "Multi-window mode detected")
                }
            }
        }
    }

    // REMOVED: isNotificationPanelExpanded() function
    // This was causing false positives with button clicks

    fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            triggerCheat(CheatType.ORIENTATION_CHANGE, "Orientation changed to landscape")
        }
    }

    fun onUserLeaveHint() {
        // Called when user presses Home button
        homeButtonPressed = true
    }

    fun onTrimMemory(level: Int) {
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                if (!homeButtonPressed) {
                    triggerCheat(CheatType.RECENT_APPS, "App backgrounded - possible recent apps")
                }
            }
        }
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus && !homeButtonPressed) {
            // Window lost focus but home button wasn't pressed - possible recent apps or other dialog
            handler.postDelayed({
                if (!hasFocus) {
                    triggerCheat(CheatType.RECENT_APPS, "Window focus lost - possible recent apps")
                }
            }, 500)
        } else if (hasFocus) {
            homeButtonPressed = false
        }
    }

    // FIXED: Added cooldown mechanism to prevent multiple triggers
    private fun triggerCheat(type: CheatType, reason: String) {
        if (!isMonitoring) return

        val currentTime = System.currentTimeMillis()

        // Check cooldown - prevent multiple triggers in short time
        if (currentTime - lastCheatTime < CHEAT_COOLDOWN) {
            Log.d("CheatDetector", "Cheat cooldown active, skipping: $reason")
            return
        }

        lastCheatTime = currentTime
        Log.w("CheatDetector", "Cheat detected: $type - $reason")
        _cheatEvent.postValue(CheatEvent(type, reason))

        // Auto-reset home button flag after short delay
        if (type == CheatType.HOME_BUTTON) {
            handler.postDelayed({
                homeButtonPressed = false
            }, 2000)
        }
    }

    fun isKeyguardLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    fun getDetectionSummary(): Map<CheatType, Int> {
        // This would track counts of each cheat type
        // Implementation depends on your tracking needs
        return emptyMap()
    }
}
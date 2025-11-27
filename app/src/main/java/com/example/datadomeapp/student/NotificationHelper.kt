package com.example.datadomeapp.student

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.datadomeapp.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "TASK_REMINDERS_CHANNEL"
        const val CHANNEL_NAME = "Academic Task Reminders"
    }

    fun showTaskNotification(task: TaskItem, notificationType: String) {
        createNotificationChannel()

        val notificationId = task.taskId.hashCode()

        // DIFFERENT NOTIFICATION TYPES
        val (title, message, color) = when (notificationType) {
            "DUE_SOON" -> {
                Triple(
                    "⏰ Due Soon: ${task.title}",
                    "Due in ${task.hoursUntilDue()} hours: ${task.date} at ${task.time}",
                    Color.parseColor("#FF9800") // Orange
                )
            }
            "OVERDUE" -> {
                Triple(
                    "📚 Overdue: ${task.title}",
                    "Reminder! This task in To-Do list is past due!.",
                    Color.RED
                )
            }
            else -> { // Normal reminder
                Triple(
                    "📝 Reminder: ${task.title}",
                    "Due: ${task.date} at ${task.time}",
                    Color.parseColor("#2E7D32") // Green
                )
            }
        }

        val intent = Intent(context, StudentToDoListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("scrollToTask", task.taskId) // Optional: scroll to specific task
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            task.taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // BUILD NOTIFICATION
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_school_white)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${task.details}\n\n$message"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(color)
            .setLights(color, 1000, 1000)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights

        // SPECIAL SETTINGS FOR DUE SOON
        if (notificationType == "DUE_SOON") {
            notificationBuilder
                .setTimeoutAfter(2 * 60 * 60 * 1000) // 2 hours timeout for due soon
                .setNumber(task.hoursUntilDue().toInt()) // Show hours count in badge
        }

        // SPECIAL SETTINGS FOR OVERDUE - More urgent
        if (notificationType == "OVERDUE") {
            notificationBuilder
                .setOngoing(true) // Sticky notification - user needs to dismiss
                .setFullScreenIntent(pendingIntent, false) // High priority
        }

        // Add large icon
        getLargeIconFromRaw()?.let { bitmap ->
            notificationBuilder.setLargeIcon(bitmap)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d("NotificationHelper", "Showed $notificationType notification for: ${task.title}")
    }

    private fun getLargeIconFromRaw(): Bitmap? {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.dd_logo)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error loading image from raw: ${e.message}")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for academic deadlines and assignment reminders"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun cancelNotification(taskId: String) {
        val notificationId = taskId.hashCode()
        notificationManager.cancel(notificationId)
    }
}
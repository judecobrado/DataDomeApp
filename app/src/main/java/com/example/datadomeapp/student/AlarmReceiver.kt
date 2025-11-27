package com.example.datadomeapp.student

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Date

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "TASK_REMINDER" -> {
                val taskId = intent.getStringExtra("taskId")
                val title = intent.getStringExtra("title")
                val details = intent.getStringExtra("details")
                val date = intent.getStringExtra("date")
                val time = intent.getStringExtra("time")
                val notificationType = intent.getStringExtra("notificationType") ?: "REMINDER"

                if (taskId != null && title != null) {
                    val task = TaskItem(
                        taskId = taskId,
                        title = title,
                        details = details ?: "",
                        date = date ?: "",
                        time = time ?: "",
                        done = false,
                        dueDateTime = intent.getLongExtra("dueDateTime", 0)
                    )

                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.showTaskNotification(task, notificationType)

                    Log.d("AlarmReceiver", "Showing $notificationType for: $title")
                }
            }
        }
    }

    companion object {
        fun scheduleTaskNotification(
            context: Context,
            task: TaskItem,
            triggerTime: Long,
            notificationType: String = "REMINDER"
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "TASK_REMINDER"
                putExtra("taskId", task.taskId)
                putExtra("title", task.title)
                putExtra("details", task.details)
                putExtra("date", task.date)
                putExtra("time", task.time)
                putExtra("dueDateTime", task.dueDateTime)
                putExtra("notificationType", notificationType)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (task.taskId + notificationType).hashCode(), // Unique ID per notification type
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            Log.d("AlarmScheduler", "Scheduled $notificationType for: ${task.title} at ${
                Date(
                    triggerTime
                )
            }")
        }

        // Schedule multiple reminders for a task
        fun scheduleAllTaskReminders(context: Context, task: TaskItem) {
            val dueTime = task.dueDateTime
            val now = System.currentTimeMillis()

            // Only schedule if task is not done and not yet due
            if (task.done || dueTime <= now) return

            // 1. Due Soon Notification (24 hours before)
            val dueSoonTime = dueTime - (24 * 60 * 60 * 1000)
            if (dueSoonTime > now) {
                scheduleTaskNotification(context, task, dueSoonTime, "DUE_SOON")
            }

            // 2. 1 Hour Before Notification
            val oneHourBefore = dueTime - (60 * 60 * 1000)
            if (oneHourBefore > now) {
                scheduleTaskNotification(context, task, oneHourBefore, "REMINDER")
            }

            // 3. Due Time Notification
            scheduleTaskNotification(context, task, dueTime, "REMINDER")

            // 4. Overdue Notification (1 hour after due time)
            val overdueTime = dueTime + (60 * 60 * 1000)
            scheduleTaskNotification(context, task, overdueTime, "OVERDUE")

            Log.d("AlarmScheduler", "Scheduled all reminders for: ${task.title}")
        }

        fun cancelAllTaskReminders(context: Context, taskId: String) {
            val notificationTypes = arrayOf("REMINDER", "DUE_SOON", "OVERDUE")

            notificationTypes.forEach { type ->
                cancelScheduledNotification(context, taskId, type)
            }

            Log.d("AlarmScheduler", "Cancelled all reminders for: $taskId")
        }

        fun cancelScheduledNotification(context: Context, taskId: String, notificationType: String = "REMINDER") {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "TASK_REMINDER"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (taskId + notificationType).hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d("AlarmScheduler", "Cancelled $notificationType for: $taskId")
            }
        }
    }
}
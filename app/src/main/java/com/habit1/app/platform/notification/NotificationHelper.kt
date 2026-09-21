package com.habit1.app.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.habit1.app.domain.model.Habit
import com.habit1.app.ui.MainActivity

/**
 * Platform helper managing habit reminder notifications and channels.
 * Adheres to privacy principles: no telemetry, clean titles, no sensitive habit data leak.
 */
open class NotificationHelper(
    private val context: Context
) {

    companion object {
        const val CHANNEL_ID_REMINDERS = "habit_reminders"
        private const val CHANNEL_NAME = "Habit Reminders"
        private const val CHANNEL_DESC = "Reminders for scheduled habits"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Initializes the notification channel on Android 8.0+ (API 26+).
     */
    open fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_REMINDERS,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Determines whether notifications are currently allowed for this application.
     */
    open fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Shows a reminder notification for [habit].
     * Tapping the notification opens the Today screen via MainActivity.
     * Does NOT mark the habit complete or record any habit activity.
     */
    open fun showReminderNotification(habit: Habit) {
        if (!areNotificationsEnabled()) {
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            habit.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messageText = habit.description?.takeIf { it.isNotBlank() } ?: "Time for your scheduled habit."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(habit.name)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(habit.id.hashCode(), notification)
    }

    /**
     * Dismisses any active notification in the status bar for [habitId].
     */
    open fun cancelNotification(habitId: String) {
        notificationManager.cancel(habitId.hashCode())
    }
}

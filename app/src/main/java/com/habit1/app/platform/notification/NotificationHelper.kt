package com.habit1.app.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.habit1.app.R
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.platform.receiver.HabitActionReceiver
import com.habit1.app.ui.MainActivity
import java.time.LocalDate
import java.util.Objects

/**
 * Platform helper managing habit reminder notifications and channels.
 * Adheres to privacy principles: no telemetry, clean titles, no sensitive habit data leak.
 * Supports actionable reminders: YES/NO for Boolean, DONE/OPEN for Quantitative.
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
     * Overload for backwards compatibility and simple reminder invocations.
     */
    open fun showReminderNotification(habit: Habit) {
        showReminderNotification(habit, DateTimeUtils.formatDate(LocalDate.now()))
    }

    /**
     * Shows an actionable reminder notification for [habit] tied to explicit [targetDate].
     *
     * Actions:
     * - BooleanChoice: "YES" and "NO" direct actions
     * - Quantitative (Count, Duration, Quantity): "DONE" direct completion and "OPEN" app shortcut
     */
    open fun showReminderNotification(habit: Habit, targetDate: String) {
        if (!areNotificationsEnabled()) {
            android.util.Log.w("Habit1Notify", "Notifications disabled for com.habit1.app!")
            return
        }
        createNotificationChannel()
        android.util.Log.d("Habit1Notify", "Posting notification for habit: ${habit.name} on $targetDate")

        val notificationId = habit.id.hashCode()

        // Content intent: tapping notification body opens TodayScreen
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("habit1://reminder_open/${habit.id}/$targetDate")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            Objects.hash(habit.id, targetDate, "CONTENT"),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.name)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        when (val m = habit.measurement) {
            is MeasurementType.BooleanChoice -> {
                val promptText = if (!habit.description.isNullOrBlank() && habit.description.endsWith("?")) {
                    habit.description
                } else {
                    "Did you complete ${habit.name} today?"
                }
                builder.setContentText(promptText)

                // YES action
                val yesIntent = Intent(context, HabitActionReceiver::class.java).apply {
                    action = HabitActionReceiver.ACTION_HABIT_RECORD_YES
                    data = Uri.parse("habit1://action/${habit.id}/$targetDate/YES")
                    putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habit.id)
                    putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, targetDate)
                    putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val yesPendingIntent = PendingIntent.getBroadcast(
                    context,
                    Objects.hash(habit.id, targetDate, "YES"),
                    yesIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // NO action
                val noIntent = Intent(context, HabitActionReceiver::class.java).apply {
                    action = HabitActionReceiver.ACTION_HABIT_RECORD_NO
                    data = Uri.parse("habit1://action/${habit.id}/$targetDate/NO")
                    putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habit.id)
                    putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, targetDate)
                    putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val noPendingIntent = PendingIntent.getBroadcast(
                    context,
                    Objects.hash(habit.id, targetDate, "NO"),
                    noIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(0, "YES", yesPendingIntent)
                builder.addAction(0, "NO", noPendingIntent)
            }

            is MeasurementType.Count -> {
                val promptText = "Complete your ${m.target} ${m.unit} goal today?"
                builder.setContentText(promptText)
                addQuantitativeActions(builder, habit, targetDate, notificationId)
            }

            is MeasurementType.Duration -> {
                val promptText = "Complete your ${m.targetMinutes} min goal today?"
                builder.setContentText(promptText)
                addQuantitativeActions(builder, habit, targetDate, notificationId)
            }

            is MeasurementType.Quantity -> {
                val promptText = "Did you reach your ${m.target} ${m.unit} goal today?"
                builder.setContentText(promptText)
                addQuantitativeActions(builder, habit, targetDate, notificationId)
            }
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun addQuantitativeActions(
        builder: NotificationCompat.Builder,
        habit: Habit,
        targetDate: String,
        notificationId: Int
    ) {
        // DONE action
        val doneIntent = Intent(context, HabitActionReceiver::class.java).apply {
            action = HabitActionReceiver.ACTION_HABIT_RECORD_DONE
            data = Uri.parse("habit1://action/${habit.id}/$targetDate/DONE")
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habit.id)
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, targetDate)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            Objects.hash(habit.id, targetDate, "DONE"),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // OPEN action
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("habit1://open_habit/${habit.id}/$targetDate")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            Objects.hash(habit.id, targetDate, "OPEN"),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(0, "DONE", donePendingIntent)
        builder.addAction(0, "OPEN", openPendingIntent)
    }

    /**
     * Updates an active notification to show a confirmation state after an action is taken.
     * Removes actionable buttons, presents feedback, and auto-dismisses after a timeout.
     */
    open fun updateNotificationAfterAction(
        habit: Habit,
        targetDate: String,
        confirmationMessage: String,
        notificationId: Int = habit.id.hashCode()
    ) {
        if (!areNotificationsEnabled()) {
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("habit1://confirmed/${habit.id}/$targetDate")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            Objects.hash(habit.id, targetDate, "CONFIRMED"),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✓ ${habit.name}")
            .setContentText(confirmationMessage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setTimeoutAfter(8000)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Dismisses any active notification in the status bar by its integer [notificationId].
     */
    open fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * Dismisses any active notification in the status bar for [habitId].
     */
    open fun cancelNotification(habitId: String) {
        notificationManager.cancel(habitId.hashCode())
    }

    /**
     * Shows a reminder notification for a daily goal.
     */
    open fun showGoalReminderNotification(goalTitle: String, goalId: String, notes: String? = null) {
        if (!areNotificationsEnabled()) return
        createNotificationChannel()
        val notificationId = ("goal_$goalId").hashCode()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("habit1://goal_open/$goalId")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            Objects.hash("goal", goalId),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Goal: $goalTitle")
            .setContentText(notes ?: "Reminder for today's goal")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    open fun cancelGoalNotification(goalId: String) {
        notificationManager.cancel(("goal_$goalId").hashCode())
    }
}

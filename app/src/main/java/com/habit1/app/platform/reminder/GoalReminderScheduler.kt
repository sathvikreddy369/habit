package com.habit1.app.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.habit1.app.platform.receiver.AlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

interface GoalReminderScheduler {
    fun scheduleGoalReminder(
        goalId: String,
        title: String,
        notes: String?,
        targetDate: LocalDate,
        reminderTime: LocalTime
    )
    fun cancelGoalReminder(goalId: String)
}

class AlarmManagerGoalReminderScheduler(
    private val context: Context
) : GoalReminderScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleGoalReminder(
        goalId: String,
        title: String,
        notes: String?,
        targetDate: LocalDate,
        reminderTime: LocalTime
    ) {
        cancelGoalReminder(goalId)

        val triggerDateTime = LocalDateTime.of(targetDate, reminderTime)
        val zonedDateTime = triggerDateTime.atZone(ZoneId.systemDefault())
        val triggerEpochMillis = zonedDateTime.toInstant().toEpochMilli()

        if (triggerEpochMillis <= System.currentTimeMillis()) {
            // Cannot schedule reminder in the past
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_GOAL_REMINDER
            putExtra(AlarmReceiver.EXTRA_GOAL_ID, goalId)
            putExtra(AlarmReceiver.EXTRA_GOAL_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_GOAL_NOTES, notes)
            data = Uri.parse("habit1://goal_reminder/$goalId")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("goal_$goalId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerEpochMillis,
                pendingIntent
            )
        }
    }

    override fun cancelGoalReminder(goalId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_GOAL_REMINDER
            data = Uri.parse("habit1://goal_reminder/$goalId")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("goal_$goalId").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

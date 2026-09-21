package com.habit1.app.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.reminder.HabitReminderCalculator
import com.habit1.app.platform.receiver.AlarmReceiver
import java.time.Instant
import java.time.ZoneId

/**
 * Contract for scheduling and canceling device habit reminder alarms.
 */
interface HabitReminderScheduler {
    fun scheduleNextReminder(habit: Habit, fromInstant: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault())
    fun cancelReminder(habitId: String)
}

/**
 * Android AlarmManager implementation of [HabitReminderScheduler].
 *
 * Adheres to Phase 8 specifications:
 * - The actual PendingIntent identity is the Habit UUID encoded in the intent data URI ("habit1://reminder/{habitId}").
 * - Integer request code is a deterministic implementation detail.
 * - Checks canScheduleExactAlarms() on Android 12+ (API 31+); uses setExactAndAllowWhileIdle() if permitted,
 *   otherwise falls back to setAndAllowWhileIdle().
 * - Completely dormant when idle; wakes up only at the exact trigger time.
 */
class AlarmManagerHabitReminderScheduler(
    private val context: Context,
    private val calculator: HabitReminderCalculator = HabitReminderCalculator()
) : HabitReminderScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {
        // Cancel any existing alarm for this habit before scheduling a new one
        cancelReminder(habit.id)

        val nextTriggerZoned = calculator.calculateNextReminder(habit, fromInstant, zoneId) ?: return
        val triggerEpochMillis = nextTriggerZoned.toInstant().toEpochMilli()

        val intent = createAlarmIntent(habit.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habit.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(triggerEpochMillis, pendingIntent)
    }

    override fun cancelReminder(habitId: String) {
        val intent = createAlarmIntent(habitId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun scheduleAlarm(triggerEpochMillis: Long, pendingIntent: PendingIntent) {
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

    private fun createAlarmIntent(habitId: String): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }
    }
}

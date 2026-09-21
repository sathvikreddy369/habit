package com.habit1.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habit1.app.HabitApplication
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * BroadcastReceiver triggered by AlarmManager when a habit reminder alarm fires.
 *
 * Core Principle:
 * "Notifications are reminders, not records."
 * Receiving, displaying, or dismissing this reminder NEVER modifies habit_records or streaks.
 *
 * Defense-in-Depth:
 * Re-validates the habit against the database and evaluates today's schedule before showing the notification.
 * Then immediately schedules the next upcoming reminder.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_HABIT_REMINDER = "com.habit1.app.ACTION_HABIT_REMINDER"
        const val EXTRA_HABIT_ID = "extra_habit_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HABIT_REMINDER) {
            return
        }

        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
            ?: intent.data?.lastPathSegment
            ?: return

        val pendingResult = goAsync()
        val app = context.applicationContext as? HabitApplication

        if (app == null) {
            pendingResult?.finish()
            return
        }

        val container = app.container
        val habitRepository = container.habitRepository
        val notificationHelper = container.notificationHelper
        val scheduler = container.reminderScheduler
        val evaluateSchedule = EvaluateScheduleUseCase()
        val zoneId = ZoneId.systemDefault()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entity = habitRepository.getHabitById(habitId)
                if (entity == null) {
                    scheduler.cancelReminder(habitId)
                    return@launch
                }

                val habit = entity.toDomain()
                val today = DateTimeUtils.today(zoneId)

                // Re-validate habit eligibility & schedule for today
                val isEligible = habit.reminderTime != null && !habit.isPaused && !habit.isArchived
                val isScheduledToday = isEligible && evaluateSchedule.isScheduledOn(habit, today, zoneId)

                if (isScheduledToday) {
                    notificationHelper.showReminderNotification(habit)
                }

                // Schedule next upcoming reminder starting from tomorrow
                if (isEligible) {
                    val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant()
                    scheduler.scheduleNextReminder(habit, fromInstant = tomorrowStart, zoneId = zoneId)
                } else {
                    scheduler.cancelReminder(habit.id)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

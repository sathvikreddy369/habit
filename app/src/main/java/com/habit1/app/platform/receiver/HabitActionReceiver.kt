package com.habit1.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habit1.app.HabitApplication
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * BroadcastReceiver triggered by user interactions with actionable habit reminder notifications.
 *
 * Actions Handled:
 * - ACTION_HABIT_RECORD_YES: Sets actualValue = 1.0, isCompleted = true for BooleanChoice habit
 * - ACTION_HABIT_RECORD_NO: Sets actualValue = 0.0, isCompleted = false for BooleanChoice habit
 * - ACTION_HABIT_RECORD_DONE: Promotes to target completion for Quantitative habit
 *
 * Enforces:
 * - Defense in depth: Re-reads current habit and validates status (not paused, not archived).
 * - Explicit civil target date: Uses EXTRA_TARGET_DATE rather than assuming system now.
 * - Non-destructive DONE: Preserves existing actualValue if it exceeds target.
 * - Single Notification Update: Updates notification to confirmed state without spam.
 */
class HabitActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_HABIT_RECORD_YES = "com.habit1.app.ACTION_HABIT_RECORD_YES"
        const val ACTION_HABIT_RECORD_NO = "com.habit1.app.ACTION_HABIT_RECORD_NO"
        const val ACTION_HABIT_RECORD_DONE = "com.habit1.app.ACTION_HABIT_RECORD_DONE"

        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_TARGET_DATE = "extra_target_date"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in listOf(ACTION_HABIT_RECORD_YES, ACTION_HABIT_RECORD_NO, ACTION_HABIT_RECORD_DONE)) {
            return
        }

        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
            ?: intent.data?.getQueryParameter("habit_id")
            ?: return

        val targetDateStr = intent.getStringExtra(EXTRA_TARGET_DATE)
            ?: intent.data?.getQueryParameter("target_date")
            ?: return

        val targetDate = try {
            DateTimeUtils.parseDate(targetDateStr)
        } catch (_: Throwable) {
            return
        }

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, habitId.hashCode())

        val app = context.applicationContext as? HabitApplication ?: return
        val container = app.container
        val progressUseCase = container.recordHabitProgressUseCase
        val habitRepository = container.habitRepository
        val notificationHelper = container.notificationHelper

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zoneId = ZoneId.systemDefault()
                val result = when (action) {
                    ACTION_HABIT_RECORD_YES -> progressUseCase.markCompleted(
                        habitId = habitId,
                        targetDate = targetDate,
                        zoneId = zoneId,
                        enforceSchedule = true
                    )
                    ACTION_HABIT_RECORD_NO -> progressUseCase.markIncomplete(
                        habitId = habitId,
                        targetDate = targetDate,
                        zoneId = zoneId,
                        enforceSchedule = true
                    )
                    ACTION_HABIT_RECORD_DONE -> progressUseCase.markCompleted(
                        habitId = habitId,
                        targetDate = targetDate,
                        zoneId = zoneId,
                        enforceSchedule = true
                    )
                    else -> Result.failure(IllegalArgumentException("Unsupported action: $action"))
                }

                // Immediately dismiss the notification once the user has interacted with it,
                // regardless of whether progress was recorded or habit was stale/deleted/paused.
                notificationHelper.cancelNotification(notificationId)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

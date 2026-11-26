package com.habit1.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.habit1.app.HabitApplication
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.reminder.SnoozeCalculator
import com.habit1.app.domain.reminder.SnoozeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * BroadcastReceiver triggered by user interactions with actionable habit reminder notifications.
 *
 * Actions Handled:
 * - ACTION_HABIT_RECORD_DONE / ACTION_HABIT_RECORD_YES: Completes habit to target for today.
 * - ACTION_HABIT_RECORD_SKIP: Explicitly sets status to SKIPPED for today (neutral streak freeze).
 * - ACTION_HABIT_SNOOZE: Reschedules reminder within midnight-bounded snooze rules.
 *
 * Enforces:
 * - Defense in depth: Re-reads current habit and record, validating status (not paused, not archived).
 * - Action safety: Never overwrites existing completions, notes, or partial volumes accidentally.
 * - Swiping away notification: Dismisses only, never persists a MISSED record.
 */
class HabitActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_HABIT_RECORD_DONE = "com.habit1.app.ACTION_HABIT_RECORD_DONE"
        const val ACTION_HABIT_RECORD_YES = "com.habit1.app.ACTION_HABIT_RECORD_YES" // Backwards compatibility
        const val ACTION_HABIT_RECORD_SKIP = "com.habit1.app.ACTION_HABIT_RECORD_SKIP"
        const val ACTION_HABIT_SNOOZE = "com.habit1.app.ACTION_HABIT_SNOOZE"

        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_TARGET_DATE = "extra_target_date"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    private val snoozeCalculator = SnoozeCalculator()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in listOf(ACTION_HABIT_RECORD_DONE, ACTION_HABIT_RECORD_YES, ACTION_HABIT_RECORD_SKIP, ACTION_HABIT_SNOOZE)) {
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
        val habitRecordRepository = container.habitRecordRepository
        val notificationHelper = container.notificationHelper
        val scheduler = container.reminderScheduler

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zoneId = ZoneId.systemDefault()
                val habitEntity = habitRepository.getHabitById(habitId)

                if (habitEntity == null || habitEntity.isArchived || habitEntity.isPaused) {
                    notificationHelper.cancelNotification(notificationId)
                    return@launch
                }

                when (action) {
                    ACTION_HABIT_RECORD_DONE,
                    ACTION_HABIT_RECORD_YES -> {
                        progressUseCase.markCompleted(
                            habitId = habitId,
                            targetDate = targetDate,
                            zoneId = zoneId,
                            enforceSchedule = true
                        )
                        notificationHelper.cancelNotification(notificationId)
                    }

                    ACTION_HABIT_RECORD_SKIP -> {
                        val existingRecord = habitRecordRepository.getRecord(habitId, targetDateStr)
                        // Safety: Do not overwrite an already completed record with SKIP
                        if (existingRecord != null && existingRecord.isCompleted) {
                            notificationHelper.cancelNotification(notificationId)
                            return@launch
                        }

                        progressUseCase.markSkipped(
                            habitId = habitId,
                            targetDate = targetDate,
                            notes = existingRecord?.notes,
                            zoneId = zoneId
                        )
                        notificationHelper.cancelNotification(notificationId)
                    }

                    ACTION_HABIT_SNOOZE -> {
                        val now = LocalDateTime.now(zoneId)
                        when (val snoozeResult = snoozeCalculator.calculateSnooze(now, zoneId)) {
                            is SnoozeResult.Scheduled -> {
                                scheduler.scheduleSnooze(habitId, snoozeResult.triggerEpochMillis)
                                notificationHelper.cancelNotification(notificationId)
                            }
                            is SnoozeResult.DisallowedPastBoundary -> {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Cannot snooze past 23:50 today.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

package com.habit1.app.domain.reminder

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.EveningDigestScheduler
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Coordinates habit reminder scheduling with persistence lifecycle events.
 *
 * Guarantees:
 * 1. Reminders are scheduled/canceled ONLY after database persistence operations succeed.
 * 2. Eagerly suppresses and cancels alarms when a habit is completed for today.
 * 3. Reconciles all eligible habits and evening digest on boot, time changes, or resume.
 */
class HabitReminderCoordinator(
    private val habitRepository: HabitRepository,
    private val scheduler: HabitReminderScheduler,
    private val notificationHelper: NotificationHelper,
    private val habitRecordRepository: HabitRecordRepository? = null,
    private val eveningDigestScheduler: EveningDigestScheduler? = null,
    private val userPreferences: UserPreferencesDataStore? = null
) {

    /**
     * Called after a habit is successfully created and persisted.
     */
    fun onHabitCreated(habit: Habit, zoneId: ZoneId = ZoneId.systemDefault()) {
        if (habit.reminderTime != null && !habit.isPaused && !habit.isArchived && notificationHelper.areNotificationsEnabled()) {
            scheduler.scheduleNextReminder(habit, fromInstant = Instant.now(), zoneId = zoneId)
        }
    }

    /**
     * Called after a habit is successfully updated and persisted.
     */
    fun onHabitUpdated(habit: Habit, zoneId: ZoneId = ZoneId.systemDefault()) {
        if (habit.reminderTime != null && !habit.isPaused && !habit.isArchived && notificationHelper.areNotificationsEnabled()) {
            scheduler.scheduleNextReminder(habit, fromInstant = Instant.now(), zoneId = zoneId)
        } else {
            scheduler.cancelReminder(habit.id)
            notificationHelper.cancelNotification(habit.id)
        }
    }

    /**
     * Eagerly cancels today's pending reminder and notification when a habit is marked DONE today.
     * Immediately reschedules for the next scheduled occurrence starting from tomorrow.
     */
    suspend fun onHabitCompletedToday(habitId: String, zoneId: ZoneId = ZoneId.systemDefault()) {
        scheduler.cancelReminder(habitId)
        notificationHelper.cancelNotification(habitId)

        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain(zoneId)
        if (habit.reminderTime != null && !habit.isPaused && !habit.isArchived && notificationHelper.areNotificationsEnabled()) {
            val today = DateTimeUtils.today(zoneId)
            val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant()
            scheduler.scheduleNextReminder(habit, fromInstant = tomorrowStart, zoneId = zoneId)
        }
    }

    /**
     * Called after a habit is successfully paused in the database.
     */
    fun onHabitPaused(habitId: String) {
        scheduler.cancelReminder(habitId)
        notificationHelper.cancelNotification(habitId)
    }

    /**
     * Called after a habit is successfully resumed in the database.
     */
    suspend fun onHabitResumed(habitId: String, zoneId: ZoneId = ZoneId.systemDefault()) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain(zoneId)
        if (habit.reminderTime != null && !habit.isArchived && notificationHelper.areNotificationsEnabled()) {
            scheduler.scheduleNextReminder(habit, fromInstant = Instant.now(), zoneId = zoneId)
        }
    }

    /**
     * Called after a habit is successfully archived in the database.
     */
    fun onHabitArchived(habitId: String) {
        scheduler.cancelReminder(habitId)
        notificationHelper.cancelNotification(habitId)
    }

    /**
     * Called after a habit is successfully unarchived in the database.
     */
    suspend fun onHabitUnarchived(habitId: String, zoneId: ZoneId = ZoneId.systemDefault()) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain(zoneId)
        if (habit.reminderTime != null && !habit.isPaused && notificationHelper.areNotificationsEnabled()) {
            scheduler.scheduleNextReminder(habit, fromInstant = Instant.now(), zoneId = zoneId)
        }
    }

    /**
     * Called after a habit is successfully deleted from the database.
     */
    fun onHabitDeleted(habitId: String) {
        scheduler.cancelReminder(habitId)
        notificationHelper.cancelNotification(habitId)
    }

    /**
     * Reconciles all active reminders across the database and the evening digest.
     * Invoked on:
     * - Notification permission granted / app resume
     * - System boot (BOOT_COMPLETED)
     * - App update (MY_PACKAGE_REPLACED)
     * - Clock change (TIME_SET)
     * - Timezone change (TIMEZONE_CHANGED)
     */
    suspend fun reconcileAllReminders(zoneId: ZoneId = ZoneId.systemDefault()) {
        val notificationsAllowed = notificationHelper.areNotificationsEnabled()
        val activeHabits = habitRepository.getActiveHabitsList().map { it.toDomain(zoneId) }
        val today = DateTimeUtils.today(zoneId)
        val todayStr = DateTimeUtils.formatDate(today)

        for (habit in activeHabits) {
            if (notificationsAllowed && habit.reminderTime != null && !habit.isPaused) {
                val record = habitRecordRepository?.getRecord(habit.id, todayStr)
                val isDoneToday = record != null && (record.isCompleted || record.status == HabitRecordStatus.DONE.name)
                val fromInstant = if (isDoneToday) {
                    today.plusDays(1).atStartOfDay(zoneId).toInstant()
                } else {
                    Instant.now()
                }
                scheduler.scheduleNextReminder(habit, fromInstant = fromInstant, zoneId = zoneId)
            } else {
                scheduler.cancelReminder(habit.id)
            }
        }

        // Reconcile Evening Digest
        if (eveningDigestScheduler != null && userPreferences != null) {
            val prefs = userPreferences.userPreferencesFlow.firstOrNull()
            if (prefs != null && prefs.dailyReviewEnabled && notificationsAllowed) {
                val digestTime = try {
                    LocalTime.parse(prefs.dailyReviewTime)
                } catch (_: Exception) {
                    LocalTime.of(21, 0)
                }
                eveningDigestScheduler.scheduleDigest(digestTime, zoneId)
            } else {
                eveningDigestScheduler.cancelDigest()
            }
        }
    }
}

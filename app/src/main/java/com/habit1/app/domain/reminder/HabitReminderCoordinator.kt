package com.habit1.app.domain.reminder

import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Habit
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import java.time.Instant
import java.time.ZoneId

/**
 * Coordinates habit reminder scheduling with persistence lifecycle events.
 *
 * Guarantees:
 * 1. Reminders are scheduled/canceled ONLY after database persistence operations succeed.
 * 2. ViewModels do not directly manipulate alarms.
 * 3. Reconciles all eligible habits when notification permission becomes available, on device reboot,
 *    or when the system clock/timezone changes.
 */
class HabitReminderCoordinator(
    private val habitRepository: HabitRepository,
    private val scheduler: HabitReminderScheduler,
    private val notificationHelper: NotificationHelper
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
        }
    }

    /**
     * Called after a habit is successfully paused in the database.
     */
    fun onHabitPaused(habitId: String) {
        scheduler.cancelReminder(habitId)
    }

    /**
     * Called after a habit is successfully resumed in the database.
     */
    suspend fun onHabitResumed(habitId: String, zoneId: ZoneId = ZoneId.systemDefault()) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain()
        if (habit.reminderTime != null && !habit.isArchived && notificationHelper.areNotificationsEnabled()) {
            scheduler.scheduleNextReminder(habit, fromInstant = Instant.now(), zoneId = zoneId)
        }
    }

    /**
     * Called after a habit is successfully archived in the database.
     */
    fun onHabitArchived(habitId: String) {
        scheduler.cancelReminder(habitId)
    }

    /**
     * Called after a habit is successfully unarchived in the database.
     */
    suspend fun onHabitUnarchived(habitId: String, zoneId: ZoneId = ZoneId.systemDefault()) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain()
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
     * Reconciles all active reminders across the database.
     * Invoked on:
     * - Notification permission granted / app resume
     * - System boot (BOOT_COMPLETED)
     * - App update (MY_PACKAGE_REPLACED)
     * - Clock change (TIME_SET)
     * - Timezone change (TIMEZONE_CHANGED)
     */
    suspend fun reconcileAllReminders(zoneId: ZoneId = ZoneId.systemDefault()) {
        val notificationsAllowed = notificationHelper.areNotificationsEnabled()
        val activeHabits = habitRepository.getActiveHabitsList().map { it.toDomain() }

        for (habit in activeHabits) {
            if (notificationsAllowed && habit.reminderTime != null && !habit.isPaused) {
                scheduler.scheduleNextReminder(habit, fromInstant = Instant.now(), zoneId = zoneId)
            } else {
                scheduler.cancelReminder(habit.id)
            }
        }
    }
}

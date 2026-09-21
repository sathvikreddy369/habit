package com.habit1.app.di

import android.content.Context
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.DailyReviewRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dependency container interface.
 * Defines dependencies available to ViewModels and services.
 * Implemented manually without reflection or code generation per ADR-01.
 */
interface AppContainer {
    val context: Context
    val ioDispatcher: CoroutineDispatcher
    val defaultDispatcher: CoroutineDispatcher
    val mainDispatcher: CoroutineDispatcher

    val database: AppDatabase
    val userPreferences: UserPreferencesDataStore
    val habitRepository: HabitRepository
    val habitRecordRepository: HabitRecordRepository
    val dailyGoalRepository: DailyGoalRepository
    val dailyReviewRepository: DailyReviewRepository

    val notificationHelper: com.habit1.app.platform.notification.NotificationHelper
    val reminderScheduler: com.habit1.app.platform.reminder.HabitReminderScheduler
    val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator
    val backupRepository: com.habit1.app.data.repository.BackupRepository
    val recordHabitProgressUseCase: com.habit1.app.domain.usecase.RecordHabitProgressUseCase
    val computeHabitAnalyticsUseCase: com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
}

/**
 * Default implementation of AppContainer.
 * Repositories and database are instantiated lazily upon first access.
 */
class DefaultAppContainer(
    override val context: Context,
    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    override val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.buildDatabase(context)
    }

    override val userPreferences: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    override val habitRepository: HabitRepository by lazy {
        HabitRepositoryImpl(database.habitDao(), ioDispatcher)
    }

    override val habitRecordRepository: HabitRecordRepository by lazy {
        HabitRecordRepositoryImpl(database.habitRecordDao(), ioDispatcher)
    }

    override val dailyGoalRepository: DailyGoalRepository by lazy {
        DailyGoalRepositoryImpl(database.dailyGoalDao(), ioDispatcher)
    }

    override val dailyReviewRepository: DailyReviewRepository by lazy {
        DailyReviewRepositoryImpl(database.dailyReviewDao(), ioDispatcher)
    }

    override val notificationHelper: com.habit1.app.platform.notification.NotificationHelper by lazy {
        com.habit1.app.platform.notification.NotificationHelper(context)
    }

    override val reminderScheduler: com.habit1.app.platform.reminder.HabitReminderScheduler by lazy {
        com.habit1.app.platform.reminder.AlarmManagerHabitReminderScheduler(context)
    }

    override val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator by lazy {
        com.habit1.app.domain.reminder.HabitReminderCoordinator(
            habitRepository = habitRepository,
            scheduler = reminderScheduler,
            notificationHelper = notificationHelper
        )
    }

    override val backupRepository: com.habit1.app.data.repository.BackupRepository by lazy {
        val exporter = com.habit1.app.data.backup.BackupExporter(database)
        val importer = com.habit1.app.data.backup.BackupImporter(
            database = database,
            reminderCoordinator = reminderCoordinator,
            reminderScheduler = reminderScheduler,
            notificationHelper = notificationHelper
        )
        com.habit1.app.data.repository.BackupRepositoryImpl(
            context = context,
            database = database,
            exporter = exporter,
            importer = importer,
            ioDispatcher = ioDispatcher
        )
    }

    override val recordHabitProgressUseCase: com.habit1.app.domain.usecase.RecordHabitProgressUseCase by lazy {
        com.habit1.app.domain.usecase.RecordHabitProgressUseCase(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository
        )
    }

    override val computeHabitAnalyticsUseCase: com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase by lazy {
        com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase()
    }
}

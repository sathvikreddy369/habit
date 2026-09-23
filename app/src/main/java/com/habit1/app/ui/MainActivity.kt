package com.habit1.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.habit1.app.HabitApplication
import com.habit1.app.ui.habits.form.HabitFormScreen
import com.habit1.app.ui.habits.form.HabitFormViewModel
import com.habit1.app.ui.habits.list.HabitListScreen
import com.habit1.app.ui.habits.list.HabitListViewModel
import com.habit1.app.ui.navigation.Screen
import com.habit1.app.ui.theme.HabitTheme
import com.habit1.app.ui.today.TodayScreen
import com.habit1.app.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {

    private val pendingDestination = mutableStateOf<Screen?>(null)

    private val todayViewModel: TodayViewModel by viewModels {
        val app = application as HabitApplication
        TodayViewModel.Factory(
            habitRepository = app.container.habitRepository,
            habitRecordRepository = app.container.habitRecordRepository,
            dailyGoalRepository = app.container.dailyGoalRepository,
            dailyReviewRepository = app.container.dailyReviewRepository
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDestination(intent)?.let { destination ->
            pendingDestination.value = destination
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as? HabitApplication ?: return
        lifecycleScope.launch {
            app.container.reminderCoordinator.reconcileAllReminders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as HabitApplication
            val userPrefs by app.container.userPreferences.userPreferencesFlow.collectAsStateWithLifecycle(initialValue = null)
            val isDarkTheme = when (userPrefs?.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            HabitTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val initialDestination = remember { parseDestination(intent) }
                    val backstack = remember {
                        mutableStateListOf<Screen>().apply {
                            add(Screen.Today)
                            if (initialDestination != null) {
                                add(initialDestination)
                            }
                        }
                    }
                    val currentScreen = backstack.lastOrNull() ?: Screen.Today

                    val newDest = pendingDestination.value
                    LaunchedEffect(newDest) {
                        if (newDest != null) {
                            if (backstack.lastOrNull() != newDest) {
                                backstack.add(newDest)
                            }
                            pendingDestination.value = null
                        }
                    }

                    BackHandler(enabled = backstack.size > 1) {
                        backstack.removeAt(backstack.size - 1)
                    }

                    when (currentScreen) {
                        is Screen.Today -> {
                            TodayScreen(
                                viewModel = todayViewModel,
                                onNavigateToHabits = {
                                    backstack.add(Screen.HabitList)
                                },
                                onNavigateToHistory = {
                                    backstack.add(Screen.History)
                                },
                                onNavigateToSettings = {
                                    backstack.add(Screen.Settings)
                                }
                            )
                        }

                        is Screen.HabitList -> {
                            val habitListViewModel: HabitListViewModel = viewModel(
                                factory = HabitListViewModel.Factory(
                                    habitRepository = app.container.habitRepository,
                                    reminderCoordinator = app.container.reminderCoordinator
                                )
                            )
                            HabitListScreen(
                                viewModel = habitListViewModel,
                                onCreateHabit = {
                                    backstack.add(Screen.HabitForm(null))
                                },
                                onEditHabit = { habitId ->
                                    backstack.add(Screen.HabitForm(habitId))
                                },
                                onInspectHabit = { habitId ->
                                    backstack.add(Screen.HabitHistory(habitId))
                                },
                                onNavigateToTemplates = {
                                    backstack.add(Screen.HabitTemplates)
                                },
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                }
                            )
                        }

                        is Screen.HabitTemplates -> {
                            com.habit1.app.ui.habits.templates.HabitTemplatesScreen(
                                onSelectTemplate = { templateId ->
                                    backstack.add(Screen.HabitForm(habitId = null, templateId = templateId))
                                },
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                }
                            )
                        }

                        is Screen.HabitForm -> {
                            val formKey = currentScreen.habitId ?: ("new_habit_" + (currentScreen.templateId ?: "empty"))
                            val habitFormViewModel: HabitFormViewModel = viewModel(
                                key = formKey,
                                factory = HabitFormViewModel.Factory(
                                    habitRepository = app.container.habitRepository,
                                    reminderCoordinator = app.container.reminderCoordinator,
                                    habitId = currentScreen.habitId,
                                    templateId = currentScreen.templateId
                                )
                            )
                            HabitFormScreen(
                                viewModel = habitFormViewModel,
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                }
                            )
                        }

                        is Screen.History -> {
                            val historyViewModel: com.habit1.app.ui.history.HistoryViewModel = viewModel(
                                factory = com.habit1.app.ui.history.HistoryViewModel.Factory(
                                    habitRepository = app.container.habitRepository,
                                    habitRecordRepository = app.container.habitRecordRepository,
                                    dailyGoalRepository = app.container.dailyGoalRepository,
                                    dailyReviewRepository = app.container.dailyReviewRepository
                                )
                            )

                            com.habit1.app.ui.history.HistoryScreen(
                                viewModel = historyViewModel,
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                },
                                onInspectHabit = { habitId ->
                                    backstack.add(Screen.HabitHistory(habitId))
                                }
                            )
                        }

                        is Screen.HabitHistory -> {
                            val habitHistoryViewModel: com.habit1.app.ui.history.HabitHistoryViewModel = viewModel(
                                key = "habit_history_${currentScreen.habitId}",
                                factory = com.habit1.app.ui.history.HabitHistoryViewModel.Factory(
                                    habitId = currentScreen.habitId,
                                    habitRepository = app.container.habitRepository,
                                    habitRecordRepository = app.container.habitRecordRepository,
                                    computeHabitAnalytics = app.container.computeHabitAnalyticsUseCase
                                )
                            )
                            com.habit1.app.ui.history.HabitHistoryScreen(
                                viewModel = habitHistoryViewModel,
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                }
                            )
                        }

                        is Screen.Settings -> {
                            val settingsViewModel: com.habit1.app.ui.settings.SettingsViewModel = viewModel(
                                factory = com.habit1.app.ui.settings.SettingsViewModel.Factory(
                                    backupRepository = app.container.backupRepository,
                                    userPreferences = app.container.userPreferences
                                )
                            )
                            com.habit1.app.ui.settings.SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun parseDestination(intent: Intent?): Screen? {
            val uri = intent?.data ?: return null
            if (uri.scheme != "habit1") return null
            val segments = uri.pathSegments
            val habitId = when {
                uri.host == "open_habit" -> segments.firstOrNull()
                segments.firstOrNull() == "open_habit" -> segments.getOrNull(1)
                else -> null
            }
            return if (!habitId.isNullOrBlank()) {
                Screen.HabitHistory(habitId)
            } else {
                null
            }
        }
    }
}

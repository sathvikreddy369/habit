package com.habit1.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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

    private val todayViewModel: TodayViewModel by viewModels {
        val app = application as HabitApplication
        TodayViewModel.Factory(
            habitRepository = app.container.habitRepository,
            habitRecordRepository = app.container.habitRecordRepository,
            dailyGoalRepository = app.container.dailyGoalRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as HabitApplication
                    val backstack = remember { mutableStateListOf<Screen>(Screen.Today) }
                    val currentScreen = backstack.lastOrNull() ?: Screen.Today

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
                                }
                            )
                        }

                        is Screen.HabitList -> {
                            val habitListViewModel: HabitListViewModel = viewModel(
                                factory = HabitListViewModel.Factory(app.container.habitRepository)
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
                                onNavigateBack = {
                                    if (backstack.size > 1) {
                                        backstack.removeAt(backstack.size - 1)
                                    }
                                }
                            )
                        }

                        is Screen.HabitForm -> {
                            val habitFormViewModel: HabitFormViewModel = viewModel(
                                key = currentScreen.habitId ?: "new_habit",
                                factory = HabitFormViewModel.Factory(
                                    habitRepository = app.container.habitRepository,
                                    habitId = currentScreen.habitId
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
                                    dailyGoalRepository = app.container.dailyGoalRepository
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
                                    habitRecordRepository = app.container.habitRecordRepository
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
                    }
                }
            }
        }
    }
}

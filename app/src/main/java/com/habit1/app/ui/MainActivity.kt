package com.habit1.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
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
import com.habit1.app.ui.security.LockScreen
import com.habit1.app.ui.theme.HabitTheme
import com.habit1.app.ui.today.TodayScreen
import com.habit1.app.ui.today.TodayViewModel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.habit1.app.ui.analytics.GlobalAnalyticsScreen
import com.habit1.app.ui.analytics.GlobalAnalyticsViewModel

class MainActivity : FragmentActivity() {

    private val pendingDestination = mutableStateOf<Screen?>(null)

    private val todayViewModel: TodayViewModel by viewModels {
        val app = application as HabitApplication
        TodayViewModel.Factory(
            habitRepository = app.container.habitRepository,
            habitRecordRepository = app.container.habitRecordRepository,
            dailyGoalRepository = app.container.dailyGoalRepository,
            dailyReviewRepository = app.container.dailyReviewRepository,
            goalReminderScheduler = app.container.goalReminderScheduler,
            categoryRepository = app.container.categoryRepository,
            userPreferences = app.container.userPreferences
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
                "DARK", "AMOLED" -> true
                else -> isSystemInDarkTheme()
            }
            val isAmoled = userPrefs?.themeMode == "AMOLED"

            HabitTheme(darkTheme = isDarkTheme, isAmoled = isAmoled) {
                val isLocked by app.container.appLockManager.isLocked.collectAsStateWithLifecycle()
                val isAppLockEnabled by app.container.appLockManager.isAppLockEnabled.collectAsStateWithLifecycle()

                // Dynamic FLAG_SECURE protection (Decision 7.2)
                LaunchedEffect(isAppLockEnabled) {
                    if (isAppLockEnabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked) {
                        var authError by remember { mutableStateOf<String?>(null) }
                        LockScreen(
                            onTriggerAuth = {
                                authError = null
                                app.container.biometricAuthManager.authenticate(
                                    activity = this@MainActivity,
                                    title = "Unlock Habit1",
                                    subtitle = "Authenticate to access your habits and history",
                                    onSuccess = {
                                        authError = null
                                        app.container.appLockManager.unlock()
                                    },
                                    onError = { errorCode, errString ->
                                        if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                            errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                            errorCode != androidx.biometric.BiometricPrompt.ERROR_CANCELED) {
                                            authError = errString.toString()
                                        }
                                    },
                                    onFailed = {
                                        authError = "Authentication failed. Please try again."
                                    }
                                )
                            },
                            errorMessage = authError
                        )
                    } else {
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

                    val isRootDestination = currentScreen is Screen.Today ||
                            currentScreen is Screen.HabitList ||
                            currentScreen is Screen.History

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (isRootDestination) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = currentScreen is Screen.Today,
                                        onClick = {
                                            if (currentScreen !is Screen.Today) {
                                                backstack.clear()
                                                backstack.add(Screen.Today)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Today"
                                            )
                                        },
                                        label = { Text("Today") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen is Screen.HabitList,
                                        onClick = {
                                            if (currentScreen !is Screen.HabitList) {
                                                backstack.clear()
                                                backstack.add(Screen.Today)
                                                backstack.add(Screen.HabitList)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = "Habits"
                                            )
                                        },
                                        label = { Text("Habits") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen is Screen.History,
                                        onClick = {
                                            if (currentScreen !is Screen.History) {
                                                backstack.clear()
                                                backstack.add(Screen.Today)
                                                backstack.add(Screen.History)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "History"
                                            )
                                        },
                                        label = { Text("History") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen is Screen.Analytics,
                                        onClick = {
                                            if (currentScreen !is Screen.Analytics) {
                                                backstack.clear()
                                                backstack.add(Screen.Today)
                                                backstack.add(Screen.Analytics)
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Assessment,
                                                contentDescription = "Analytics"
                                            )
                                        },
                                        label = { Text("Analytics") }
                                    )
                                }
                            }
                        }
                    ) { scaffoldPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = scaffoldPadding.calculateBottomPadding())
                        ) {
                            when (currentScreen) {
                                is Screen.Today -> {
                                    TodayScreen(
                                        viewModel = todayViewModel,
                                        onCreateHabit = {
                                            backstack.add(Screen.HabitForm(null))
                                        },
                                        onInspectHabit = { habitId ->
                                            backstack.add(Screen.HabitHistory(habitId))
                                        },
                                        onNavigateToHabits = {
                                            backstack.clear()
                                            backstack.add(Screen.Today)
                                            backstack.add(Screen.HabitList)
                                        },
                                        onNavigateToHistory = {
                                            backstack.clear()
                                            backstack.add(Screen.Today)
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
                                            categoryRepository = app.container.categoryRepository,
                                            habitRecordRepository = app.container.habitRecordRepository,
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
                                        onNavigateToSettings = {
                                            backstack.add(Screen.Settings)
                                        },
                                        onNavigateBack = null
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
                                            categoryRepository = app.container.categoryRepository,
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

                                is Screen.Analytics -> {
                                    val analyticsViewModel: GlobalAnalyticsViewModel = viewModel(
                                        factory = GlobalAnalyticsViewModel.Factory(
                                            habitRepository = app.container.habitRepository,
                                            habitRecordRepository = app.container.habitRecordRepository,
                                            computeGlobalAnalyticsUseCase = app.container.computeGlobalAnalyticsUseCase
                                        )
                                    )
                                    GlobalAnalyticsScreen(
                                        viewModel = analyticsViewModel,
                                        onInspectHabit = { habitId ->
                                            backstack.add(Screen.HabitHistory(habitId))
                                        },
                                        onNavigateToSettings = {
                                            backstack.add(Screen.Settings)
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
                                        onNavigateBack = null,
                                        onInspectHabit = { habitId ->
                                            backstack.add(Screen.HabitHistory(habitId))
                                        },
                                        onNavigateToSettings = {
                                            backstack.add(Screen.Settings)
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
                                        },
                                        onEditHabit = { habitId ->
                                            backstack.add(Screen.HabitForm(habitId))
                                        }
                                    )
                                }

                                is Screen.Settings -> {
                                    val settingsViewModel: com.habit1.app.ui.settings.SettingsViewModel = viewModel(
                                        factory = com.habit1.app.ui.settings.SettingsViewModel.Factory(
                                            backupRepository = app.container.backupRepository,
                                            userPreferences = app.container.userPreferences,
                                            habitRepository = app.container.habitRepository,
                                            habitRecordRepository = app.container.habitRecordRepository,
                                            categoryRepository = app.container.categoryRepository,
                                            eveningDigestScheduler = app.container.eveningDigestScheduler,
                                            biometricAuthManager = app.container.biometricAuthManager,
                                            appLockManager = app.container.appLockManager,
                                            appContext = applicationContext
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

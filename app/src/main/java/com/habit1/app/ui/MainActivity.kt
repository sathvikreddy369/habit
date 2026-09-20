package com.habit1.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.habit1.app.HabitApplication
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
                    TodayScreen(viewModel = todayViewModel)
                }
            }
        }
    }
}


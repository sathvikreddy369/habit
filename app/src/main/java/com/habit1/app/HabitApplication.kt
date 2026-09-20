package com.habit1.app

import android.app.Application
import com.habit1.app.di.AppContainer
import com.habit1.app.di.DefaultAppContainer

/**
 * Main application class.
 * Owns the central manual dependency injection container (AppContainer).
 */
class HabitApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

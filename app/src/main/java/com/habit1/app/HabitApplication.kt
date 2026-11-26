package com.habit1.app

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.habit1.app.di.AppContainer
import com.habit1.app.di.DefaultAppContainer

/**
 * Main application class.
 * Owns the central manual dependency injection container (AppContainer).
 * Registers lifecycle callbacks for App Lock and Screen Off detection (Decision 7.1).
 */
class HabitApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                container.appLockManager.onScreenOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        container.notificationHelper.createNotificationChannel()

        // Register Screen Off receiver for App Lock
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        // Register Activity Lifecycle Callbacks for App Lock
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                container.appLockManager.onActivityStarted(activity)
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                container.appLockManager.onActivityStopped(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

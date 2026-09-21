package com.habit1.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habit1.app.HabitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * System BroadcastReceiver responsible for rescheduling reminders after:
 * - Device boot (ACTION_BOOT_COMPLETED)
 * - Application update (ACTION_MY_PACKAGE_REPLACED)
 * - Timezone changes (ACTION_TIMEZONE_CHANGED)
 * - System clock changes (ACTION_TIME_SET)
 *
 * Guaranteed to execute promptly and exit.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val pendingResult = goAsync()
                val app = context.applicationContext as? HabitApplication

                if (app == null) {
                    pendingResult?.finish()
                    return
                }

                val coordinator = app.container.reminderCoordinator
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        coordinator.reconcileAllReminders(ZoneId.systemDefault())
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
        }
    }
}

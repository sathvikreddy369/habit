package com.habit1.app.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.habit1.app.platform.receiver.AlarmReceiver
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

interface EveningDigestScheduler {
    fun scheduleDigest(time: LocalTime, zoneId: ZoneId = ZoneId.systemDefault())
    fun cancelDigest()
}

class AlarmManagerEveningDigestScheduler(
    private val context: Context
) : EveningDigestScheduler {

    companion object {
        const val DIGEST_REQUEST_CODE = 999999
        val DIGEST_URI: Uri = Uri.parse("habit1://digest")
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleDigest(time: LocalTime, zoneId: ZoneId) {
        cancelDigest()

        val now = LocalDateTime.now(zoneId)
        var triggerDateTime = LocalDateTime.of(now.toLocalDate(), time)
        if (!triggerDateTime.isAfter(now)) {
            triggerDateTime = triggerDateTime.plusDays(1)
        }

        val triggerEpochMillis = triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EVENING_DIGEST
            data = DIGEST_URI
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerEpochMillis,
                pendingIntent
            )
        }
    }

    override fun cancelDigest() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EVENING_DIGEST
            data = DIGEST_URI
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

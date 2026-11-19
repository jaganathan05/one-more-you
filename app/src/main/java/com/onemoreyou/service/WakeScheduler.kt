package com.onemoreyou.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.schedule.WakeupPlanner
import java.time.ZonedDateTime

object WakeScheduler {
    fun schedule(context: Context, config: BlockerConfig, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = WakeupPlanner.next(config, now) ?: return
        val triggerAt = next.toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis() + 1_000L) return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BoundaryAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val REQUEST_CODE = 4101
}

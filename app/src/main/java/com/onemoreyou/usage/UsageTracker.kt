package com.onemoreyou.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.onemoreyou.domain.DayKeys
import com.onemoreyou.permissions.PermissionGateway
import java.time.ZonedDateTime

class UsageTracker(context: Context) {
    private val appContext = context.applicationContext
    private val permissions = PermissionGateway(appContext)
    private val stats = appContext.getSystemService(UsageStatsManager::class.java)

    fun foregroundMillisToday(packageName: String, now: ZonedDateTime = ZonedDateTime.now()): Long {
        if (!permissions.usageAccessGranted()) return 0L
        val start = DayKeys.startOfDayMillis(now)
        val end = now.toInstant().toEpochMilli()
        val transitions = readTransitions(start, end)
        return UsageCalculator.foregroundMillis(transitions, packageName, start, end)
    }

    fun currentForeground(now: ZonedDateTime = ZonedDateTime.now()): String? {
        if (!permissions.usageAccessGranted()) return null
        val end = now.toInstant().toEpochMilli()
        val start = end - LOOKBACK_MILLIS
        return UsageCalculator.foregroundPackageAt(readTransitions(start, end), end)
    }

    private fun readTransitions(start: Long, end: Long): List<UsageTransition> {
        val manager = stats ?: return emptyList()
        val events = runCatching { manager.queryEvents(start, end) }.getOrNull() ?: return emptyList()
        val event = UsageEvents.Event()
        val transitions = ArrayList<UsageTransition>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            UsageEventMapper.toTransition(event.eventType, packageName, event.timeStamp)?.let(transitions::add)
        }
        return transitions
    }

    private companion object {
        const val LOOKBACK_MILLIS = 12L * 60L * 60L * 1000L
    }
}

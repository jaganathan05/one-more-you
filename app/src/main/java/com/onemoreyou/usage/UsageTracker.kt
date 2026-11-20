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
        val recent = recentUsagePackage(end)
        if (recent != null) return recent
        val transitions = readTransitions(end - LOOKBACK_MILLIS, end)
        val open = UsageCalculator.foregroundPackageAt(transitions, end) ?: return null
        val lastForOpen = transitions.lastOrNull { it.packageName == open } ?: return null
        if (lastForOpen.type != TransitionType.ENTER) return null
        if (end - lastForOpen.atMillis > EVENT_FRESH_MILLIS) return null
        val newer = transitions.any { it.atMillis > lastForOpen.atMillis && it.packageName != open }
        if (newer) return null
        return open
    }

    private fun recentUsagePackage(end: Long): String? {
        val manager = stats ?: return null
        val rows = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, end - LOOKBACK_MILLIS, end)
        }.getOrNull().orEmpty()
        val best = rows.maxByOrNull { it.lastTimeUsed } ?: return null
        val name = best.packageName
        if (name.isNullOrBlank()) return null
        if (end - best.lastTimeUsed > RECENT_WINDOW_MILLIS) return null
        return name
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
        const val RECENT_WINDOW_MILLIS = 20_000L
        const val EVENT_FRESH_MILLIS = 60_000L
    }
}

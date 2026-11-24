package com.onemoreyou.usage

enum class TransitionType { ENTER, EXIT }

data class UsageTransition(
    val packageName: String,
    val atMillis: Long,
    val type: TransitionType,
)

object UsageEventMapper {
    const val MOVE_TO_FOREGROUND = 1
    const val MOVE_TO_BACKGROUND = 2
    const val CONTINUE_PREVIOUS_DAY = 4
    const val ACTIVITY_RESUMED = 23

    fun toTransition(eventType: Int, packageName: String, timeMillis: Long): UsageTransition? {
        if (packageName.isBlank()) return null
        val type = when (eventType) {
            MOVE_TO_FOREGROUND, CONTINUE_PREVIOUS_DAY -> TransitionType.ENTER
            MOVE_TO_BACKGROUND -> TransitionType.EXIT
            else -> return null
        }
        return UsageTransition(packageName, timeMillis, type)
    }
}

object UsageCalculator {
    fun foregroundMillis(
        transitions: List<UsageTransition>,
        packageName: String,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): Long {
        if (rangeEndMillis <= rangeStartMillis) return 0L
        val ordered = transitions
            .asSequence()
            .filter { it.packageName == packageName && it.atMillis <= rangeEndMillis }
            .sortedWith(compareBy<UsageTransition> { it.atMillis }.thenBy { if (it.type == TransitionType.EXIT) 0 else 1 })
            .toList()

        var openFrom: Long? = null
        var total = 0L
        for (transition in ordered) {
            if (transition.atMillis < rangeStartMillis) {
                openFrom = if (transition.type == TransitionType.ENTER) transition.atMillis else null
                continue
            }
            if (transition.type == TransitionType.ENTER) {
                if (openFrom == null) openFrom = transition.atMillis
            } else if (openFrom != null) {
                val begin = maxOf(openFrom, rangeStartMillis)
                if (transition.atMillis > begin) total += transition.atMillis - begin
                openFrom = null
            }
        }
        if (openFrom != null) {
            val begin = maxOf(openFrom, rangeStartMillis)
            if (rangeEndMillis > begin) total += rangeEndMillis - begin
        }
        return total.coerceAtMost(rangeEndMillis - rangeStartMillis)
    }

    fun foregroundPackageAt(transitions: List<UsageTransition>, atMillis: Long): String? {
        val ordered = transitions.sortedWith(
            compareBy<UsageTransition> { it.atMillis }.thenBy { if (it.type == TransitionType.EXIT) 0 else 1 },
        )
        var current: String? = null
        for (transition in ordered) {
            if (transition.atMillis > atMillis) break
            when (transition.type) {
                TransitionType.ENTER -> current = transition.packageName
                TransitionType.EXIT -> if (current == transition.packageName) current = null
            }
        }
        return current
    }
}

object UsageSession {
    /**
     * Phone usage logs often omit the still-open visit until the app is left.
     * Count that visit from the moment we saw it, without adding it twice once the log catches up.
     */
    fun millisToday(
        loggedMillis: Long,
        logShowsThisAppOpen: Boolean,
        seenOpenAtMillis: Long?,
        nowMillis: Long,
    ): Long {
        if (logShowsThisAppOpen || seenOpenAtMillis == null) return loggedMillis
        return loggedMillis + (nowMillis - seenOpenAtMillis).coerceAtLeast(0L)
    }
}

package com.onemoreyou.blocking

import com.onemoreyou.domain.DayKeys
import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.domain.LearningPassSnapshot
import java.time.ZonedDateTime

data class PassStatus(
    val active: Boolean,
    val activeUntilEpochMillis: Long?,
    val remainingPasses: Int,
    val passesPerDay: Int,
    val durationMinutes: Int,
    val passesConfigured: Boolean,
)

sealed interface GrantOutcome {
    data class Granted(val state: LearningPassSnapshot) : GrantOutcome
    data object NoPassesLeft : GrantOutcome
    data object Disabled : GrantOutcome
}

object PassRules {
    fun status(
        allowance: LearningPassAllowance?,
        state: LearningPassSnapshot?,
        now: ZonedDateTime,
    ): PassStatus {
        val today = DayKeys.key(now)
        val nowMillis = now.toInstant().toEpochMilli()
        val usedToday = if (state != null && state.dayKey == today) state.passesUsed else 0
        val perDay = (allowance?.passesPerDay ?: 0).coerceAtLeast(0)
        val duration = (allowance?.durationMinutes ?: LearningPassAllowance.DEFAULT_PASS_MINUTES).coerceAtLeast(1)
        val rawUntil = state?.activeUntilEpochMillis
        val remaining = if (rawUntil == null) Long.MAX_VALUE else rawUntil - nowMillis
        val coversOnlyThisPass = remaining <= duration * 60_000L + GRANT_GRACE_MILLIS
        val activeUntil = rawUntil?.takeIf { it > nowMillis && coversOnlyThisPass }
        return PassStatus(
            active = activeUntil != null,
            activeUntilEpochMillis = activeUntil,
            remainingPasses = (perDay - usedToday).coerceAtLeast(0),
            passesPerDay = perDay,
            durationMinutes = duration,
            passesConfigured = allowance != null && perDay > 0,
        )
    }

    fun grant(
        allowance: LearningPassAllowance?,
        state: LearningPassSnapshot?,
        now: ZonedDateTime,
    ): GrantOutcome {
        if (allowance == null || allowance.passesPerDay <= 0) return GrantOutcome.Disabled
        val current = status(allowance, state, now)
        if (current.remainingPasses <= 0) return GrantOutcome.NoPassesLeft
        val today = DayKeys.key(now)
        val usedToday = if (state != null && state.dayKey == today) state.passesUsed else 0
        val duration = allowance.durationMinutes.coerceAtLeast(1)
        val until = now.toInstant().toEpochMilli() + duration * 60_000L
        return GrantOutcome.Granted(
            LearningPassSnapshot(
                packageName = allowance.packageName,
                dayKey = today,
                passesUsed = usedToday + 1,
                activeUntilEpochMillis = until,
            ),
        )
    }

    fun remainingMillis(activeUntilEpochMillis: Long?, nowMillis: Long): Long {
        val until = activeUntilEpochMillis ?: return 0L
        return (until - nowMillis).coerceAtLeast(0L)
    }

    private const val GRANT_GRACE_MILLIS = 2_000L
}

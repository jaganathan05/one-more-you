package com.onemoreyou.blocking

import com.onemoreyou.domain.AppSchedule
import com.onemoreyou.domain.AppUsageLimit
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.domain.LearningPassSnapshot
import com.onemoreyou.domain.Weekday
import com.onemoreyou.schedule.ScheduleRules
import java.time.ZonedDateTime

data class BlockInput(
    val packageName: String,
    val now: ZonedDateTime,
    val monitored: Boolean,
    val schedule: AppSchedule?,
    val usageLimit: AppUsageLimit?,
    val usedMillis: Long,
    val allowance: LearningPassAllowance?,
    val passState: LearningPassSnapshot?,
)

data class BlockDecision(
    val blocked: Boolean,
    val scheduleActive: Boolean,
    val scheduleEndsAt: ZonedDateTime?,
    val limitReached: Boolean,
    val limitMillis: Long?,
    val usedMillis: Long,
    val passActive: Boolean,
    val passEndsAtEpochMillis: Long?,
    val remainingPasses: Int,
    val passDurationMinutes: Int,
    val passesConfigured: Boolean,
)

object BlockingEngine {
    fun evaluate(input: BlockInput): BlockDecision {
        val pass = PassRules.status(input.allowance, input.passState, input.now)
        val scheduleHit = input.schedule?.let { ScheduleRules.activeHit(it, input.now) }
        val scheduleActive = scheduleHit != null
        val day = Weekday.from(input.now.dayOfWeek)
        val limitMinutes = input.usageLimit?.limitMinutes(day)
        val limitMillis = limitMinutes?.times(60_000L)
        val limitReached = limitMillis != null && input.usedMillis >= limitMillis
        val covered = input.monitored || input.usageLimit?.enabled == true
        val blocked = covered && !pass.active && (scheduleActive || limitReached)
        return BlockDecision(
            blocked = blocked,
            scheduleActive = scheduleActive,
            scheduleEndsAt = scheduleHit?.endsAt,
            limitReached = limitReached,
            limitMillis = limitMillis,
            usedMillis = input.usedMillis,
            passActive = pass.active,
            passEndsAtEpochMillis = pass.activeUntilEpochMillis,
            remainingPasses = pass.remainingPasses,
            passDurationMinutes = pass.durationMinutes,
            passesConfigured = pass.passesConfigured,
        )
    }

    fun evaluatePackage(
        config: BlockerConfig,
        packageName: String,
        usedMillis: Long,
        now: ZonedDateTime,
    ): BlockDecision = evaluate(
        BlockInput(
            packageName = packageName,
            now = now,
            monitored = config.isMonitored(packageName),
            schedule = if (config.isMonitored(packageName) && config.sharedSchedule.enabled) {
                config.sharedSchedule.copy(packageName = packageName)
            } else {
                null
            },
            usageLimit = config.limitFor(packageName),
            usedMillis = usedMillis,
            allowance = config.allowanceFor(packageName),
            passState = config.passStateFor(packageName),
        ),
    )
}

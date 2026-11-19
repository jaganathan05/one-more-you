package com.onemoreyou.domain

import java.time.DayOfWeek
import kotlinx.serialization.Serializable

@Serializable
enum class Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
    ;

    fun toJava(): DayOfWeek = DayOfWeek.valueOf(name)

    companion object {
        fun from(day: DayOfWeek): Weekday = valueOf(day.name)

        val displayOrder: List<Weekday> = entries
    }
}

@Serializable
data class MonitoredApp(
    val packageName: String,
    val label: String,
)

/**
 * A same-day window blocks [startMinuteOfDay, endMinuteOfDay).
 * When the end is earlier than the start, the window runs overnight and finishes
 * at [endMinuteOfDay] on the next calendar day. Equal times block nothing.
 */
@Serializable
data class DayWindow(
    val enabled: Boolean = false,
    val startMinuteOfDay: Int = 9 * 60,
    val endMinuteOfDay: Int = 19 * 60,
) {
    val overnight: Boolean get() = startMinuteOfDay > endMinuteOfDay

    fun isValidRange(): Boolean = startMinuteOfDay in 0..1439 && endMinuteOfDay in 0..1439
}

@Serializable
data class AppSchedule(
    val packageName: String,
    val enabled: Boolean = false,
    val days: Map<Weekday, DayWindow> = emptyMap(),
) {
    companion object {
        const val SHARED_PACKAGE = "shared"

        fun sharedDefault(): AppSchedule {
            val open = DayWindow(enabled = true, startMinuteOfDay = 9 * 60, endMinuteOfDay = 19 * 60)
            val off = DayWindow(enabled = false, startMinuteOfDay = 9 * 60, endMinuteOfDay = 19 * 60)
            val days = Weekday.entries.associateWith { day ->
                if (day == Weekday.SATURDAY || day == Weekday.SUNDAY) off else open
            }
            return AppSchedule(SHARED_PACKAGE, enabled = true, days = days)
        }
    }
}

@Serializable
data class AppUsageLimit(
    val packageName: String,
    val enabled: Boolean = false,
    val minutesByDay: Map<Weekday, Int> = emptyMap(),
    val uniformMinutes: Int? = null,
    val perDay: Boolean = false,
) {
    fun limitMinutes(day: Weekday): Int? {
        if (!enabled) return null
        val value = when {
            perDay -> minutesByDay[day]
            uniformMinutes != null -> uniformMinutes
            else -> minutesByDay[day]
        }
        return value?.takeIf { it >= 0 }
    }
}

@Serializable
data class LearningPassAllowance(
    val packageName: String,
    val passesPerDay: Int = 0,
    val durationMinutes: Int = DEFAULT_PASS_MINUTES,
) {
    companion object {
        const val DEFAULT_PASS_MINUTES = 10
        const val MAX_PASSES_PER_DAY = 2
        const val MAX_PASS_MINUTES = 15
    }
}

@Serializable
data class LearningPassSnapshot(
    val packageName: String,
    val dayKey: String,
    val passesUsed: Int = 0,
    val activeUntilEpochMillis: Long? = null,
)

@Serializable
data class BlockerConfig(
    val monitored: List<MonitoredApp> = emptyList(),
    val schedules: List<AppSchedule> = emptyList(),
    val sharedSchedule: AppSchedule = AppSchedule.sharedDefault(),
    val limits: List<AppUsageLimit> = emptyList(),
    val allowances: List<LearningPassAllowance> = emptyList(),
    val passStates: List<LearningPassSnapshot> = emptyList(),
    val names: List<MonitoredApp> = emptyList(),
    val learningPassesPerDay: Int = 0,
    val learningPassMinutes: Int = LearningPassAllowance.DEFAULT_PASS_MINUTES,
) {
    fun isMonitored(packageName: String): Boolean = monitored.any { it.packageName == packageName }

    fun labelOf(packageName: String): String =
        monitored.firstOrNull { it.packageName == packageName }?.label
            ?: names.firstOrNull { it.packageName == packageName }?.label
            ?: packageName

    fun scheduleFor(packageName: String): AppSchedule? = schedules.find { it.packageName == packageName }

    fun limitFor(packageName: String): AppUsageLimit? = limits.find { it.packageName == packageName }

    fun allowanceFor(packageName: String): LearningPassAllowance? =
        allowances.find { it.packageName == packageName }

    fun passStateFor(packageName: String): LearningPassSnapshot? =
        passStates.find { it.packageName == packageName }

    fun withSchedule(schedule: AppSchedule): BlockerConfig = copy(
        schedules = schedules.filter { it.packageName != schedule.packageName } + schedule,
    )

    fun withSharedSchedule(schedule: AppSchedule): BlockerConfig = copy(
        sharedSchedule = schedule.copy(packageName = AppSchedule.SHARED_PACKAGE, enabled = schedule.days.any { it.value.enabled }),
    )

    fun withName(app: MonitoredApp): BlockerConfig = copy(
        names = names.filter { it.packageName != app.packageName } + app,
    )

    fun withLimit(limit: AppUsageLimit): BlockerConfig = copy(
        limits = limits.filter { it.packageName != limit.packageName } + limit,
    )

    fun withoutLimit(packageName: String): BlockerConfig = copy(
        limits = limits.filter { it.packageName != packageName },
    )

    fun withAllowance(allowance: LearningPassAllowance): BlockerConfig = copy(
        allowances = allowances.filter { it.packageName != allowance.packageName } + allowance,
    )

    fun withLearningPass(passesPerDay: Int, durationMinutes: Int): BlockerConfig {
        val count = passesPerDay.coerceIn(0, LearningPassAllowance.MAX_PASSES_PER_DAY)
        val minutes = durationMinutes.coerceIn(1, LearningPassAllowance.MAX_PASS_MINUTES)
        return copy(
            learningPassesPerDay = count,
            learningPassMinutes = minutes,
            allowances = allowances.map { it.copy(passesPerDay = count, durationMinutes = minutes) },
        )
    }

    fun withPassState(state: LearningPassSnapshot): BlockerConfig = copy(
        passStates = passStates.filter { it.packageName != state.packageName } + state,
    )

    fun withMonitored(apps: List<MonitoredApp>): BlockerConfig = copy(
        monitored = apps.distinctBy { it.packageName },
    )
}

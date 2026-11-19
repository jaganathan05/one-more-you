package com.onemoreyou.schedule

import com.onemoreyou.domain.AppSchedule
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.domain.DayWindow
import com.onemoreyou.domain.Weekday
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class ScheduleHit(
    val endsAt: ZonedDateTime,
)

object ScheduleRules {
    fun activeHit(schedule: AppSchedule, now: ZonedDateTime): ScheduleHit? {
        if (!schedule.enabled) return null
        val minute = minuteOfDay(now)
        val today = Weekday.from(now.dayOfWeek)
        val yesterday = Weekday.from(now.dayOfWeek.minus(1))
        val date = now.toLocalDate()

        val todayWindow = schedule.days[today]
        if (todayWindow.coversSameDayOrEvening(minute)) {
            val endDate = if (todayWindow!!.overnight) date.plusDays(1) else date
            return ScheduleHit(atEnd(endDate, todayWindow, now))
        }

        val yesterdayWindow = schedule.days[yesterday]
        if (yesterdayWindow.coversMorningSpill(minute)) {
            return ScheduleHit(atEnd(date, yesterdayWindow!!, now))
        }
        return null
    }

    fun windowFor(schedule: AppSchedule?, day: Weekday): DayWindow? = schedule?.days?.get(day)

    private fun DayWindow?.coversSameDayOrEvening(minute: Int): Boolean {
        val window = this ?: return false
        if (!window.enabled || !window.isValidRange() || window.startMinuteOfDay == window.endMinuteOfDay) {
            return false
        }
        return if (window.overnight) {
            minute >= window.startMinuteOfDay
        } else {
            minute >= window.startMinuteOfDay && minute < window.endMinuteOfDay
        }
    }

    private fun DayWindow?.coversMorningSpill(minute: Int): Boolean {
        val window = this ?: return false
        if (!window.enabled || !window.isValidRange() || !window.overnight) return false
        return minute < window.endMinuteOfDay
    }

    private fun atEnd(date: LocalDate, window: DayWindow, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(window.endMinuteOfDay / 60, window.endMinuteOfDay % 60)
        return date.atTime(time).atZone(now.zone)
    }

    private fun minuteOfDay(now: ZonedDateTime): Int = now.hour * 60 + now.minute
}

object WakeupPlanner {
    /**
     * Next moment the blocking result can change: a window edge, local midnight, or pass expiry.
     */
    fun next(config: BlockerConfig, now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        val candidates = ArrayList<ZonedDateTime>()
        candidates += now.toLocalDate().plusDays(1).atStartOfDay(zone)
        for (schedule in config.schedules) {
            if (!schedule.enabled) continue
            for (offset in 0..7) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                val window = schedule.days[Weekday.from(date.dayOfWeek)] ?: continue
                if (!window.enabled || !window.isValidRange() || window.startMinuteOfDay == window.endMinuteOfDay) {
                    continue
                }
                val start = date.atTime(LocalTime.of(window.startMinuteOfDay / 60, window.startMinuteOfDay % 60)).atZone(zone)
                val endDate = if (window.overnight) date.plusDays(1) else date
                val end = endDate.atTime(LocalTime.of(window.endMinuteOfDay / 60, window.endMinuteOfDay % 60)).atZone(zone)
                if (start.isAfter(now)) candidates += start
                if (end.isAfter(now)) candidates += end
            }
        }
        for (state in config.passStates) {
            val until = state.activeUntilEpochMillis ?: continue
            val expiry = java.time.Instant.ofEpochMilli(until).atZone(zone)
            if (expiry.isAfter(now)) candidates += expiry
        }
        return candidates.filter { it.isAfter(now) }.minOrNull()
    }
}

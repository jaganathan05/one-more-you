package com.onemoreyou.schedule

import com.onemoreyou.domain.AppSchedule
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.domain.DayWindow
import com.onemoreyou.domain.LearningPassSnapshot
import com.onemoreyou.domain.Weekday
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRulesTest {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun sameDayWindowBlocksFromStartUntilEnd() {
        val schedule = schedule(
            Weekday.MONDAY to DayWindow(enabled = true, startMinuteOfDay = 9 * 60, endMinuteOfDay = 18 * 60),
        )
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 16, 8, 59)))
        val start = ScheduleRules.activeHit(schedule, at(2026, 11, 16, 9, 0))
        assertEquals(at(2026, 11, 16, 18, 0), start?.endsAt)
        assertEquals(at(2026, 11, 16, 18, 0), ScheduleRules.activeHit(schedule, at(2026, 11, 16, 17, 59))?.endsAt)
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 16, 18, 0)))
    }

    @Test
    fun weekdaysUseIndependentWindows() {
        val schedule = schedule(
            Weekday.MONDAY to DayWindow(true, 9 * 60, 18 * 60),
            Weekday.TUESDAY to DayWindow(true, 12 * 60, 13 * 60),
            Weekday.SATURDAY to DayWindow(enabled = false, startMinuteOfDay = 9 * 60, endMinuteOfDay = 18 * 60),
        )
        assertTrue(ScheduleRules.activeHit(schedule, at(2026, 11, 16, 10, 0)) != null)
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 17, 10, 0)))
        assertTrue(ScheduleRules.activeHit(schedule, at(2026, 11, 17, 12, 30)) != null)
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 21, 10, 0)))
    }

    @Test
    fun overnightWindowContinuesIntoTheNextMorning() {
        val schedule = schedule(
            Weekday.MONDAY to DayWindow(true, 22 * 60, 6 * 60),
            Weekday.TUESDAY to DayWindow(enabled = false, startMinuteOfDay = 22 * 60, endMinuteOfDay = 6 * 60),
        )
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 16, 21, 59)))
        assertEquals(at(2026, 11, 17, 6, 0), ScheduleRules.activeHit(schedule, at(2026, 11, 16, 22, 0))?.endsAt)
        assertEquals(at(2026, 11, 17, 6, 0), ScheduleRules.activeHit(schedule, at(2026, 11, 17, 5, 59))?.endsAt)
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 17, 6, 0)))
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 17, 22, 30)))
    }

    @Test
    fun consecutiveOvernightWindowsDoNotDoubleCountTheMorning() {
        val schedule = schedule(
            Weekday.MONDAY to DayWindow(true, 22 * 60, 6 * 60),
            Weekday.TUESDAY to DayWindow(true, 22 * 60, 6 * 60),
        )
        assertEquals(at(2026, 11, 17, 6, 0), ScheduleRules.activeHit(schedule, at(2026, 11, 17, 1, 0))?.endsAt)
        assertEquals(at(2026, 11, 18, 6, 0), ScheduleRules.activeHit(schedule, at(2026, 11, 17, 23, 0))?.endsAt)
    }

    @Test
    fun disabledScheduleAndEmptyWindowDoNotBlock() {
        val disabled = schedule(Weekday.MONDAY to DayWindow(true, 0, 12 * 60)).copy(enabled = false)
        assertNull(ScheduleRules.activeHit(disabled, at(2026, 11, 16, 10, 0)))
        val empty = schedule(Weekday.MONDAY to DayWindow(true, 10 * 60, 10 * 60))
        assertNull(ScheduleRules.activeHit(empty, at(2026, 11, 16, 10, 0)))
    }

    @Test
    fun untilMidnightUsesAnEndOfZero() {
        val schedule = schedule(Weekday.MONDAY to DayWindow(true, 9 * 60, 0))
        assertEquals(at(2026, 11, 17, 0, 0), ScheduleRules.activeHit(schedule, at(2026, 11, 16, 23, 30))?.endsAt)
        assertNull(ScheduleRules.activeHit(schedule, at(2026, 11, 17, 0, 1)))
    }

    @Test
    fun wakeupIncludesOvernightEndAndMidnight() {
        val schedule = schedule(Weekday.MONDAY to DayWindow(true, 22 * 60, 6 * 60))
        val now = at(2026, 11, 16, 23, 0)
        val next = WakeupPlanner.next(BlockerConfig(schedules = listOf(schedule)), now)
        assertEquals(at(2026, 11, 17, 0, 0), next)
    }

    @Test
    fun wakeupIncludesPassExpiryWhenItIsSoonerThanMidnight() {
        val now = at(2026, 11, 16, 22, 0)
        val expiry = now.plusMinutes(10).toInstant().toEpochMilli()
        val config = BlockerConfig(
            passStates = listOf(
                LearningPassSnapshot("com.instagram.android", "2026-11-16", 1, expiry),
            ),
        )
        assertEquals(now.plusMinutes(10), WakeupPlanner.next(config, now))
    }

    private fun schedule(vararg days: Pair<Weekday, DayWindow>) = AppSchedule(
        packageName = "com.instagram.android",
        enabled = true,
        days = days.toMap(),
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}

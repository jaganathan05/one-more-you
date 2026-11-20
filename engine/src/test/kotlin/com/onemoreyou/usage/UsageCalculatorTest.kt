package com.onemoreyou.usage

import com.onemoreyou.domain.DayKeys
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageCalculatorTest {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun sumsClosedSessionsForOnePackage() {
        val start = millis(2026, 11, 16, 0, 0)
        val end = millis(2026, 11, 16, 12, 0)
        val transitions = listOf(
            enter("com.instagram.android", millis(2026, 11, 16, 9, 0)),
            exit("com.instagram.android", millis(2026, 11, 16, 9, 30)),
            enter("com.google.android.youtube", millis(2026, 11, 16, 9, 30)),
            exit("com.google.android.youtube", millis(2026, 11, 16, 10, 0)),
            enter("com.instagram.android", millis(2026, 11, 16, 11, 0)),
            exit("com.instagram.android", millis(2026, 11, 16, 11, 10)),
        )
        assertEquals(40 * 60_000L, UsageCalculator.foregroundMillis(transitions, "com.instagram.android", start, end))
        assertEquals(30 * 60_000L, UsageCalculator.foregroundMillis(transitions, "com.google.android.youtube", start, end))
    }

    @Test
    fun clipsAnOpenSessionToTheQueryRange() {
        val start = millis(2026, 11, 16, 0, 0)
        val end = millis(2026, 11, 16, 10, 20)
        val transitions = listOf(
            enter("com.instagram.android", millis(2026, 11, 15, 23, 40)),
            exit("com.instagram.android", millis(2026, 11, 16, 0, 15)),
            enter("com.instagram.android", millis(2026, 11, 16, 10, 0)),
        )
        assertEquals(35 * 60_000L, UsageCalculator.foregroundMillis(transitions, "com.instagram.android", start, end))
    }

    @Test
    fun exitWithoutEnterCountsFromRangeStart() {
        val start = millis(2026, 11, 16, 10, 0)
        val end = millis(2026, 11, 16, 11, 0)
        val transitions = listOf(exit("com.instagram.android", millis(2026, 11, 16, 10, 15)))
        assertEquals(15 * 60_000L, UsageCalculator.foregroundMillis(transitions, "com.instagram.android", start, end))
    }

    @Test
    fun mapsPlatformEventsWithoutDoubleCountingAliases() {
        val mapped = listOf(
            UsageEventMapper.toTransition(UsageEventMapper.MOVE_TO_FOREGROUND, "com.instagram.android", 1_000),
            UsageEventMapper.toTransition(UsageEventMapper.MOVE_TO_BACKGROUND, "com.instagram.android", 11_000),
            UsageEventMapper.toTransition(UsageEventMapper.CONTINUE_PREVIOUS_DAY, "com.instagram.android", 0),
            UsageEventMapper.toTransition(99, "com.instagram.android", 50),
        )
        assertEquals(TransitionType.ENTER, mapped[0]?.type)
        assertEquals(TransitionType.EXIT, mapped[1]?.type)
        assertEquals(TransitionType.ENTER, mapped[2]?.type)
        assertNull(mapped[3])
        assertEquals(1, UsageEventMapper.MOVE_TO_FOREGROUND)
        assertEquals(2, UsageEventMapper.MOVE_TO_BACKGROUND)
    }

    @Test
    fun foregroundPackageFollowsTheLatestOpenApp() {
        val transitions = listOf(
            enter("com.instagram.android", 1_000),
            exit("com.instagram.android", 2_000),
            enter("com.google.android.youtube", 2_000),
        )
        assertEquals("com.instagram.android", UsageCalculator.foregroundPackageAt(transitions, 1_500))
        assertEquals("com.google.android.youtube", UsageCalculator.foregroundPackageAt(transitions, 2_000))
        assertNull(UsageCalculator.foregroundPackageAt(transitions, 500))
    }

    @Test
    fun openVisitCountsUntilTheUsageLogCatchesUp() {
        val seenAt = 1_000L
        val now = 61_000L
        assertEquals(70_000L, UsageSession.millisToday(10_000L, logShowsThisAppOpen = false, seenAt, now))
        assertEquals(10_000L, UsageSession.millisToday(10_000L, logShowsThisAppOpen = true, seenAt, now))
        assertEquals(10_000L, UsageSession.millisToday(10_000L, logShowsThisAppOpen = false, null, now))
    }

    @Test
    fun stoppedActivityClosesTheVisit() {
        val mapped = UsageEventMapper.toTransition(UsageEventMapper.ACTIVITY_STOPPED, "com.instagram.android", 5_000)
        assertEquals(TransitionType.EXIT, mapped?.type)
    }

    @Test
    fun localMidnightResetsTheUsageDayInIndia() {
        val late = ZonedDateTime.of(2026, 11, 16, 23, 30, 0, 0, zone)
        val early = ZonedDateTime.of(2026, 11, 17, 0, 10, 0, 0, zone)
        assertEquals("2026-11-16", DayKeys.key(late))
        assertEquals("2026-11-17", DayKeys.key(early))
        assertEquals(millis(2026, 11, 16, 0, 0), DayKeys.startOfDayMillis(late))
        assertEquals(millis(2026, 11, 17, 0, 0), DayKeys.startOfDayMillis(early))
        assertFalseSameDay(late, early)
    }

    private fun assertFalseSameDay(first: ZonedDateTime, second: ZonedDateTime) {
        org.junit.Assert.assertNotEquals(DayKeys.key(first), DayKeys.key(second))
    }

    private fun enter(packageName: String, at: Long) = UsageTransition(packageName, at, TransitionType.ENTER)

    private fun exit(packageName: String, at: Long) = UsageTransition(packageName, at, TransitionType.EXIT)

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}

package com.onemoreyou.blocking

import com.onemoreyou.domain.AppSchedule
import com.onemoreyou.domain.AppUsageLimit
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.domain.ConfigCodec
import com.onemoreyou.domain.DayWindow
import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.domain.LearningPassSnapshot
import com.onemoreyou.domain.MonitoredApp
import com.onemoreyou.domain.Weekday
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingEngineTest {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val facebook = "com.facebook.katana"
    private val snapchat = "com.snapchat.android"

    @Test
    fun scheduleAndLimitBlockIndependently() {
        val now = at(2026, 11, 16, 10, 0)
        val scheduleOnly = decision(
            packageName = instagram,
            now = now,
            schedule = weekdaySchedule(instagram),
            usedMillis = 5 * 60_000L,
            limit = limit(instagram, Weekday.MONDAY to 30),
        )
        assertTrue(scheduleOnly.blocked)
        assertTrue(scheduleOnly.scheduleActive)
        assertFalse(scheduleOnly.limitReached)

        val outsideSchedule = decision(
            packageName = youtube,
            now = at(2026, 11, 16, 20, 0),
            schedule = weekdaySchedule(youtube),
            usedMillis = 60 * 60_000L,
            limit = limit(youtube, Weekday.MONDAY to 45),
        )
        assertTrue(outsideSchedule.blocked)
        assertFalse(outsideSchedule.scheduleActive)
        assertTrue(outsideSchedule.limitReached)

        val allowed = decision(
            packageName = facebook,
            now = at(2026, 11, 16, 20, 0),
            schedule = weekdaySchedule(facebook),
            usedMillis = 10 * 60_000L,
            limit = limit(facebook, Weekday.MONDAY to 20),
        )
        assertFalse(allowed.blocked)
    }

    @Test
    fun limitsDifferByWeekdayAndByApp() {
        val instagramLimit = limit(
            instagram,
            Weekday.MONDAY to 30,
            Weekday.TUESDAY to 45,
        )
        val youtubeLimit = limit(
            youtube,
            Weekday.MONDAY to 45,
            Weekday.TUESDAY to 60,
        )
        val monday = at(2026, 11, 16, 20, 0)
        val tuesday = at(2026, 11, 17, 20, 0)
        val used = 40 * 60_000L
        assertTrue(decision(instagram, monday, usedMillis = used, limit = instagramLimit).limitReached)
        assertFalse(decision(instagram, tuesday, usedMillis = used, limit = instagramLimit).limitReached)
        assertFalse(decision(youtube, monday, usedMillis = used, limit = youtubeLimit).limitReached)
        assertFalse(decision(youtube, tuesday, usedMillis = used, limit = youtubeLimit).blocked)
    }

    @Test
    fun unmonitoredAppIsNotBlocked() {
        val decision = BlockingEngine.evaluate(
            input(instagram, at(2026, 11, 16, 10, 0)).copy(monitored = false),
        )
        assertFalse(decision.blocked)
    }

    @Test
    fun learningPassOverridesEitherBlockAndExpiresBackIntoIt() {
        val now = at(2026, 11, 16, 10, 0)
        val allowance = LearningPassAllowance(instagram, passesPerDay = 1, durationMinutes = 10)
        val active = LearningPassSnapshot(
            packageName = instagram,
            dayKey = "2026-11-16",
            passesUsed = 1,
            activeUntilEpochMillis = now.plusMinutes(10).toInstant().toEpochMilli(),
        )
        val during = decision(
            packageName = instagram,
            now = now.plusMinutes(9),
            schedule = weekdaySchedule(instagram),
            usedMillis = 40 * 60_000L,
            limit = limit(instagram, Weekday.MONDAY to 30),
            allowance = allowance,
            passState = active,
        )
        assertFalse(during.blocked)
        assertTrue(during.passActive)
        assertTrue(during.scheduleActive)
        assertTrue(during.limitReached)
        assertEquals(0, during.remainingPasses)

        val expired = decision(
            packageName = instagram,
            now = now.plusMinutes(10),
            schedule = weekdaySchedule(instagram),
            usedMillis = 40 * 60_000L,
            limit = limit(instagram, Weekday.MONDAY to 30),
            allowance = allowance,
            passState = active,
        )
        assertFalse(expired.passActive)
        assertTrue(expired.blocked)
        assertTrue(expired.scheduleActive)
        assertTrue(expired.limitReached)
    }

    @Test
    fun grantingAPassOnlyConsumesThatApp() {
        val now = at(2026, 11, 16, 12, 0)
        var config = BlockerConfig(
            monitored = listOf(
                MonitoredApp(instagram, "Instagram"),
                MonitoredApp(youtube, "YouTube"),
                MonitoredApp(facebook, "Facebook"),
                MonitoredApp(snapchat, "Snapchat"),
            ),
            allowances = listOf(
                LearningPassAllowance(instagram, 1, 10),
                LearningPassAllowance(youtube, 2, 10),
                LearningPassAllowance(facebook, 1, 10),
                LearningPassAllowance(snapchat, 0, 10),
            ),
        )
        val youtubeBefore = PassRules.status(config.allowanceFor(youtube), null, now)
        val granted = PassRules.grant(config.allowanceFor(instagram), null, now) as GrantOutcome.Granted
        config = config.withPassState(granted.state)
        assertEquals(1, granted.state.passesUsed)
        assertEquals(10 * 60_000L, granted.state.activeUntilEpochMillis!! - now.toInstant().toEpochMilli())
        assertEquals(0, PassRules.status(config.allowanceFor(instagram), config.passStateFor(instagram), now).remainingPasses)
        assertEquals(youtubeBefore.remainingPasses, PassRules.status(config.allowanceFor(youtube), config.passStateFor(youtube), now).remainingPasses)
        assertTrue(PassRules.grant(config.allowanceFor(snapchat), null, now) is GrantOutcome.Disabled)
        assertTrue(PassRules.grant(config.allowanceFor(instagram), config.passStateFor(instagram), now) is GrantOutcome.NoPassesLeft)
    }

    @Test
    fun unusedPassesResetAtLocalMidnightAndDoNotCarryForward() {
        val yesterday = LearningPassSnapshot(instagram, "2026-11-15", passesUsed = 0, activeUntilEpochMillis = null)
        val allowance = LearningPassAllowance(instagram, passesPerDay = 1, durationMinutes = 10)
        val today = PassRules.status(allowance, yesterday, at(2026, 11, 16, 0, 5))
        assertEquals(1, today.remainingPasses)
        assertFalse(today.active)
        val usedYesterday = yesterday.copy(passesUsed = 1)
        assertEquals(1, PassRules.status(allowance, usedYesterday, at(2026, 11, 16, 8, 0)).remainingPasses)
    }

    @Test
    fun activePassSurvivesRestartAndStillCountsDown() {
        val started = at(2026, 11, 16, 15, 0)
        val granted = PassRules.grant(
            LearningPassAllowance(youtube, passesPerDay = 2, durationMinutes = 10),
            null,
            started,
        ) as GrantOutcome.Granted
        val stored = ConfigCodec.decode(ConfigCodec.encode(BlockerConfig(passStates = listOf(granted.state))))
        val restored = stored.passStateFor(youtube)!!
        val later = started.plusMinutes(4)
        val status = PassRules.status(
            LearningPassAllowance(youtube, 2, 10),
            restored,
            later,
        )
        assertTrue(status.active)
        assertEquals(6 * 60_000L, PassRules.remainingMillis(status.activeUntilEpochMillis, later.toInstant().toEpochMilli()))
        assertEquals(1, status.remainingPasses)
        val afterRestartExpiry = started.plusMinutes(10)
        assertFalse(PassRules.status(LearningPassAllowance(youtube, 2, 10), restored, afterRestartExpiry).active)
    }

    @Test
    fun passLongerThanTheConfiguredDurationIsAlreadyOver() {
        val now = at(2026, 11, 16, 22, 0)
        val allowance = LearningPassAllowance(instagram, passesPerDay = 1, durationMinutes = 1)
        val stored = LearningPassSnapshot(
            packageName = instagram,
            dayKey = "2026-11-16",
            passesUsed = 1,
            activeUntilEpochMillis = now.plusMinutes(9).toInstant().toEpochMilli(),
        )
        val decision = decision(
            packageName = instagram,
            now = now,
            usedMillis = 40 * 60_000L,
            limit = limit(instagram, Weekday.MONDAY to 30),
            allowance = allowance,
            passState = stored,
        )
        assertFalse(decision.passActive)
        assertTrue(decision.blocked)
    }

    @Test
    fun zeroMinuteLimitBlocksImmediatelyAndMissingDayDoesNot() {
        val mondayLimit = limit(instagram, Weekday.MONDAY to 0)
        assertTrue(decision(instagram, at(2026, 11, 16, 8, 0), usedMillis = 0, limit = mondayLimit).blocked)
        assertFalse(decision(instagram, at(2026, 11, 17, 8, 0), usedMillis = 60_000L, limit = mondayLimit).blocked)
    }

    @Test
    fun sharedScheduleBlocksSelectedAppsAndALimitCanStandAlone() {
        val mondayNight = at(2026, 11, 16, 22, 0)
        val config = BlockerConfig(
            monitored = listOf(MonitoredApp(instagram, "Instagram")),
            sharedSchedule = AppSchedule.sharedDefault(),
            limits = listOf(limit(youtube, Weekday.MONDAY to 30)),
        )
        val scheduled = BlockingEngine.evaluatePackage(config, instagram, 0L, mondayNight)
        assertTrue(scheduled.blocked)
        assertTrue(scheduled.scheduleActive)
        val weekend = BlockingEngine.evaluatePackage(config, instagram, 0L, at(2026, 11, 21, 22, 0))
        assertFalse(weekend.scheduleActive)
        assertFalse(weekend.blocked)
        val limited = BlockingEngine.evaluatePackage(config, youtube, 40 * 60_000L, mondayNight)
        assertTrue(limited.limitReached)
        assertTrue(limited.blocked)
        assertFalse(limited.scheduleActive)
    }

    private fun decision(
        packageName: String,
        now: ZonedDateTime,
        schedule: AppSchedule? = null,
        usedMillis: Long = 0L,
        limit: AppUsageLimit? = null,
        allowance: LearningPassAllowance? = null,
        passState: LearningPassSnapshot? = null,
    ): BlockDecision = BlockingEngine.evaluate(
        input(packageName, now, schedule, usedMillis, limit, allowance, passState),
    )

    private fun input(
        packageName: String,
        now: ZonedDateTime,
        schedule: AppSchedule? = weekdaySchedule(packageName),
        usedMillis: Long = 0L,
        limit: AppUsageLimit? = null,
        allowance: LearningPassAllowance? = null,
        passState: LearningPassSnapshot? = null,
    ) = com.onemoreyou.blocking.BlockInput(
        packageName = packageName,
        now = now,
        monitored = true,
        schedule = schedule,
        usageLimit = limit,
        usedMillis = usedMillis,
        allowance = allowance,
        passState = passState,
    )

    private fun weekdaySchedule(packageName: String): AppSchedule {
        val window = DayWindow(true, 9 * 60, 18 * 60)
        val days = Weekday.entries.associateWith { day ->
            if (day == Weekday.SATURDAY || day == Weekday.SUNDAY) DayWindow(enabled = false) else window
        }
        return AppSchedule(packageName, enabled = true, days = days)
    }

    private fun limit(packageName: String, vararg minutes: Pair<Weekday, Int>) = AppUsageLimit(
        packageName = packageName,
        enabled = true,
        minutesByDay = minutes.toMap(),
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}

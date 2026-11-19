package com.onemoreyou.domain

import java.time.ZonedDateTime

object DayKeys {
    fun key(now: ZonedDateTime): String = now.toLocalDate().toString()

    fun startOfDayMillis(now: ZonedDateTime): Long =
        now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()

    fun nextMidnight(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
}

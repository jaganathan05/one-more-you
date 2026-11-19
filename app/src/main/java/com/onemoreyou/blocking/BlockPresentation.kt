package com.onemoreyou.blocking

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BlockScreenState(
    val appLabel: String,
    val reasons: List<String>,
    val details: List<String>,
    val remainingPasses: Int,
    val passDurationMinutes: Int,
    val passesConfigured: Boolean,
    val canRequestPass: Boolean,
    val limitBlocksPass: Boolean,
)

object BlockText {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun clock(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        return when {
            hours > 0L && minutes > 0L -> "$hours h $minutes min"
            hours > 0L -> "$hours h"
            minutes > 0L -> "$minutes min"
            totalSeconds == 0L -> "0 min"
            else -> "${totalSeconds} sec"
        }
    }

    fun minuteOfDay(minute: Int): String {
        val normalized = ((minute % (24 * 60)) + (24 * 60)) % (24 * 60)
        val hour24 = normalized / 60
        val mins = normalized % 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val value = hour24 % 12) {
            0 -> 12
            else -> value
        }
        return "%d:%02d %s".format(hour12, mins, suffix)
    }

    fun whenLabel(time: ZonedDateTime, now: ZonedDateTime): String {
        val clock = time.format(timeFormatter)
        return if (time.toLocalDate() == now.toLocalDate()) clock else "$clock tomorrow"
    }
}

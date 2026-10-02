package org.dergigi.ants

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val relativeDate = Regex("^(\\d+)([hdwmy])$", RegexOption.IGNORE_CASE)
private val absoluteDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")

internal fun searchDateTimestamp(value: String, keyword: String, now: Instant): Long {
    return runCatching {
        val relative = relativeDate.matchEntire(value)
        val date = if (relative == null) {
            require(absoluteDate.matches(value))
            LocalDate.parse(value)
        } else {
            val amount = relative.groupValues[1].toLong()
            val today = now.atOffset(ZoneOffset.UTC).toLocalDate()
            when (relative.groupValues[2].lowercase()) {
                "h" -> return now.minusSeconds(Math.multiplyExact(amount, 3600L)).epochSecond
                "d" -> today.minusDays(amount)
                "w" -> today.minusWeeks(amount)
                // Match the web app's JS UTC calendar arithmetic: overflow
                // days carry into the next month instead of being clamped.
                "m" -> today.withDayOfMonth(1).minusMonths(amount).plusDays(today.dayOfMonth - 1L)
                "y" -> today.withDayOfMonth(1).minusYears(amount).plusDays(today.dayOfMonth - 1L)
                else -> error("Unknown date unit")
            }
        }
        date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) + if (keyword == "until") 86399 else 0
    }.getOrElse { throw IllegalArgumentException("Use $keyword:YYYY-MM-DD or $keyword:2w (h, d, w, m, y).") }
}

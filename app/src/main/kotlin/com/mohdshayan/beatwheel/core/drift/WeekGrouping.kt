package com.mohdshayan.beatwheel.core.drift

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WeekGroup<T>(val label: String, val items: List<T>)

/** Groups items newest first under "This week", "Last week" or "Week of 8 September" (weeks start Monday). */
object WeekGrouping {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

    fun <T> group(items: List<T>, now: Instant, zone: ZoneId, timeOf: (T) -> Long): List<WeekGroup<T>> {
        val today = now.atZone(zone).toLocalDate()
        val thisWeek = monday(today)
        return items
            .sortedByDescending(timeOf)
            .groupBy { monday(Instant.ofEpochMilli(timeOf(it)).atZone(zone).toLocalDate()) }
            .map { (week, list) -> WeekGroup(label(week, thisWeek), list) }
    }

    private fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun label(week: LocalDate, thisWeek: LocalDate): String = when {
        week == thisWeek -> "This week"
        week == thisWeek.minusWeeks(1) -> "Last week"
        week.year == thisWeek.year -> "Week of " + DAY_MONTH.format(week)
        else -> "Week of " + DAY_MONTH_YEAR.format(week)
    }
}

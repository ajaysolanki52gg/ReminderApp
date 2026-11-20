package com.reminderapp.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * A WEEKLY reminder stores the days it repeats on in [Reminder.recurrenceValue] as a
 * comma-separated list, e.g. "MONDAY,WEDNESDAY,SUNDAY". A single day ("MONDAY") is what older
 * versions stored, so existing reminders keep working unchanged.
 */
object WeekDays {
    val WEEKDAYS: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val ALL: Set<DayOfWeek> = DayOfWeek.values().toSet()

    fun parse(value: String): Set<DayOfWeek> = value.split(',')
        .mapNotNull { name -> runCatching { DayOfWeek.valueOf(name.trim().uppercase()) }.getOrNull() }
        .toSet()

    fun format(days: Set<DayOfWeek>): String = days.sorted().joinToString(",") { it.name }

    /** "Weekdays", "Weekends", "Every day", "Mondays", or "Mon, Wed, Sun". */
    fun label(days: Set<DayOfWeek>): String = when {
        days.isEmpty() -> "Weekly"
        days == ALL -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == WEEKEND -> "Weekends"
        days.size == 1 -> days.first().getDisplayName(TextStyle.FULL, Locale.getDefault()) + "s"
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    /**
     * The first time at or after [start] (keeping its clock time) that falls on one of [days] and
     * is strictly after [now]. [days] empty means "the weekday of [start]".
     */
    fun firstOccurrence(start: LocalDateTime, days: Set<DayOfWeek>, now: LocalDateTime): LocalDateTime {
        val allowed = days.ifEmpty { setOf(start.dayOfWeek) }
        var candidate = start
        repeat(7 * 54) {
            if (candidate.dayOfWeek in allowed && candidate.isAfter(now)) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }
}

/** Short human label for a reminder's repeat rule, used on cards and sheets. */
fun Reminder.recurrenceLabel(): String = recurrenceLabel(recurrenceType, recurrenceValue)

fun recurrenceLabel(type: RecurrenceType, value: String): String = when (type) {
    RecurrenceType.NONE -> "Once"
    RecurrenceType.DAILY -> "Daily"
    RecurrenceType.WEEKLY -> WeekDays.label(WeekDays.parse(value))
    RecurrenceType.MONTHLY -> "Monthly"
    RecurrenceType.YEARLY -> "Yearly"
}

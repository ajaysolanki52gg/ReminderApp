package com.reminderapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.*
import java.time.LocalDateTime

class WeekDaysTest {

    @Test fun roundTripsStoredValue() {
        val days = setOf(SUNDAY, MONDAY, WEDNESDAY)
        assertEquals("MONDAY,WEDNESDAY,SUNDAY", WeekDays.format(days))
        assertEquals(days, WeekDays.parse("MONDAY,WEDNESDAY,SUNDAY"))
        // Older reminders stored a single day; empty/garbage parses to nothing.
        assertEquals(setOf(MONDAY), WeekDays.parse("MONDAY"))
        assertEquals(emptySet<java.time.DayOfWeek>(), WeekDays.parse(""))
    }

    @Test fun labels() {
        assertEquals("Weekdays", WeekDays.label(WeekDays.WEEKDAYS))
        assertEquals("Weekends", WeekDays.label(WeekDays.WEEKEND))
        assertEquals("Every day", WeekDays.label(WeekDays.ALL))
    }

    @Test fun nextSelectedDayKeepsClockTime() {
        // Friday 2026-11-20 08:00 just rang; weekdays-only -> Monday 08:00, not Saturday.
        val friday8 = LocalDateTime.of(2026, 11, 20, 8, 0)
        val next = WeekDays.firstOccurrence(friday8.plusDays(1), WeekDays.WEEKDAYS, friday8)
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 0), next)
    }
}

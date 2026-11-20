package com.reminderapp.domain.parser

import com.reminderapp.domain.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Run with: ./gradlew :app:testDebugUnitTest
 *
 * "Now" is fixed to Friday 2026-11-20 15:00 so every expectation is deterministic.
 */
class NaturalLanguageParserTest {

    private val parser = NaturalLanguageParser(SpokenTextNormalizer())
    private val now = LocalDateTime.of(2026, 11, 20, 15, 0)

    private fun parse(text: String) = parser.parse(text, now)
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, month, day, hour, minute)

    // ─── English ─────────────────────────────────────────────────────────────

    @Test fun tomorrowWithExplicitTime() {
        val r = parse("Call mom tomorrow at 5pm")
        assertEquals("Call mom", r.title)
        assertEquals(at(11, 21, 17), r.dateTime)
        assertEquals(1.0f, r.confidence)
    }

    @Test fun relativeMinutesFromNumberWords() {
        val r = parse("remind me in ten minutes to stretch")
        assertEquals("Stretch", r.title)
        assertEquals(at(11, 20, 15, 10), r.dateTime)
    }

    @Test fun relativeHours() {
        val r = parse("in 2 hours check oven")
        assertEquals("Check oven", r.title)
        assertEquals(at(11, 20, 17), r.dateTime)
    }

    @Test fun indianEnglishAfterMinutes() {
        val r = parse("after 10 minutes call back")
        assertEquals("Call back", r.title)
        assertEquals(at(11, 20, 15, 10), r.dateTime)
    }

    @Test fun bareHourOneToSixMeansPm() {
        val r = parse("call mom at 5")
        assertEquals(at(11, 20, 17), r.dateTime)
        assertTrue("ambiguous time should be flagged", r.confidence < 0.8f)
    }

    @Test fun bareMorningHourAlreadyPassedPicksEvening() {
        val r = parse("call mom at 9")
        assertEquals(at(11, 20, 21), r.dateTime)
    }

    @Test fun forIsKeptInsideTitle() {
        val r = parse("Buy gift for mom on saturday")
        assertEquals("Buy gift for mom", r.title)
        assertEquals(at(11, 21, 8), r.dateTime)
    }

    @Test fun dailyRecurring() {
        val r = parse("take medicine every day at 9pm")
        assertEquals("Take medicine", r.title)
        assertEquals(RecurrenceType.DAILY, r.recurrenceType)
        assertEquals(at(11, 20, 21), r.dateTime)
    }

    @Test fun everyWeekOnWeekday() {
        val r = parse("every week on friday at 5pm team sync")
        assertEquals("Team sync", r.title)
        assertEquals(RecurrenceType.WEEKLY, r.recurrenceType)
        assertEquals("FRIDAY", r.recurrenceValue)
        assertEquals(at(11, 20, 17), r.dateTime)
    }

    @Test fun monthlyOn31stClampsInsteadOfCrashing() {
        val r = parse("remind me to pay rent every month on 31st")
        assertEquals("Pay rent", r.title)
        assertEquals(RecurrenceType.MONTHLY, r.recurrenceType)
        assertEquals(at(11, 30, 8), r.dateTime)
    }

    @Test fun invalidDatesDoNotCrash() {
        assertNotNull(parse("doctor appointment june 31").dateTime)
        assertNotNull(parse("every year on february 30 party").dateTime)
    }

    @Test fun noDateNoTimeIsFlagged() {
        val r = parse("remind me to call mom")
        assertEquals("Call mom", r.title)
        assertTrue(r.dateTime!!.isAfter(now))
        assertTrue(r.confidence < 0.8f)
    }

    // ─── Hinglish ────────────────────────────────────────────────────────────

    @Test fun kalSubahBaje() {
        val r = parse("kal subah 9 baje doctor ko call karna")
        assertEquals("Doctor ko call karna", r.title)
        assertEquals(at(11, 21, 9), r.dateTime)
    }

    @Test fun parsoShaamWithYaadDilana() {
        val r = parse("mujhe parso shaam 6 baje yaad dilana ki dawai leni hai")
        assertEquals("Dawai leni", r.title)
        assertEquals(at(11, 22, 18), r.dateTime)
    }

    @Test fun minuteBaad() {
        val r = parse("10 minute baad chai banana")
        assertEquals("Chai banana", r.title)
        assertEquals(at(11, 20, 15, 10), r.dateTime)
    }

    @Test fun harSomvar() {
        val r = parse("har somvar subah 8 baje gym")
        assertEquals("Gym", r.title)
        assertEquals(RecurrenceType.WEEKLY, r.recurrenceType)
        assertEquals("MONDAY", r.recurrenceValue)
        assertEquals(at(11, 23, 8), r.dateTime)
    }

    @Test fun rozRaat() {
        val r = parse("roz raat 10 baje dawai")
        assertEquals("Dawai", r.title)
        assertEquals(RecurrenceType.DAILY, r.recurrenceType)
        assertEquals(at(11, 20, 22), r.dateTime)
    }

    @Test fun saadeBaje() {
        val r = parse("saade 7 baje meeting")
        assertEquals("Meeting", r.title)
        assertEquals(at(11, 20, 19, 30), r.dateTime)
    }

    @Test fun dopaharAlreadyPassedMovesToTomorrow() {
        val r = parse("dopahar 2 baje lunch")
        assertEquals("Lunch", r.title)
        assertEquals(at(11, 21, 14), r.dateTime)
    }

    // ─── Specific days of the week ──────────────────────────────────────────

    @Test fun everyWeekdaySkipsWeekend() {
        val r = parse("every weekday at 9am standup")
        assertEquals("Standup", r.title)
        assertEquals(RecurrenceType.WEEKLY, r.recurrenceType)
        assertEquals("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", r.recurrenceValue)
        assertEquals(at(11, 23, 9), r.dateTime) // Friday 9am already passed -> Monday
    }

    @Test fun weekendsOnly() {
        val r = parse("water plants on weekends")
        assertEquals("Water plants", r.title)
        assertEquals("SATURDAY,SUNDAY", r.recurrenceValue)
        assertEquals(at(11, 21, 8), r.dateTime)
    }

    @Test fun listOfDays() {
        val r = parse("every monday wednesday thursday and sunday at 7pm gym")
        assertEquals("Gym", r.title)
        assertEquals("MONDAY,WEDNESDAY,THURSDAY,SUNDAY", r.recurrenceValue)
        assertEquals(at(11, 22, 19), r.dateTime)
    }

    @Test fun hinglishListOfDays() {
        val r = parse("har somvar aur budhvar shaam 6 baje yoga")
        assertEquals("Yoga", r.title)
        assertEquals("MONDAY,WEDNESDAY", r.recurrenceValue)
        assertEquals(at(11, 23, 18), r.dateTime)
    }

    @Test fun singleOnDayIsStillOneTime() {
        val r = parse("on monday call bank")
        assertEquals(RecurrenceType.NONE, r.recurrenceType)
        assertEquals(at(11, 23, 8), r.dateTime)
    }

    @Test fun separatorDoesNotEatWords() {
        assertEquals("Orange juice", parse("every monday orange juice").title)
    }
}

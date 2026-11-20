package com.reminderapp.domain.parser

import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.domain.model.ReminderParseResult
import com.reminderapp.domain.model.ReminderType
import com.reminderapp.domain.model.WeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based, fully offline reminder parser. Understands English and romanised Hinglish
 * (the Hinglish words are rewritten to English by [SpokenTextNormalizer] first).
 *
 * [now] is a parameter so the result is deterministic and testable.
 */
@Singleton
class NaturalLanguageParser @Inject constructor(
    private val spokenTextNormalizer: SpokenTextNormalizer
) {

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ReminderParseResult {
        val prepared = spokenTextNormalizer.normalize(input)
        val normalized = prepared.lowercase()
        val clock = now.withSecond(0).withNano(0)

        val result = parseRecurring(normalized, prepared, clock)
            ?: parseRelative(normalized, prepared, now)
            ?: parseOneTime(normalized, prepared, clock)
        return result.copy(rawInput = input)
    }

    // ─── Relative Patterns ───────────────────────────────────────────────────

    private fun parseRelative(normalized: String, original: String, now: LocalDateTime): ReminderParseResult? {
        Regex("""\bin half(?: an)? hours?\b""").find(normalized)?.let { match ->
            return relativeResult(original, match.value, now.plusMinutes(30))
        }
        Regex("""\bin an? (minute|min|hour|hr|day|week)s?\b""").find(normalized)?.let { match ->
            return relativeResult(original, match.value, plusUnit(now, 1, match.groupValues[1]))
        }
        Regex("""\bin (\d+)\s*(minute|min|hour|hr|day|week)s?\b""").find(normalized)?.let { match ->
            val value = match.groupValues[1].toLongOrNull() ?: return null
            return relativeResult(original, match.value, plusUnit(now, value, match.groupValues[2]))
        }
        return null
    }

    private fun plusUnit(now: LocalDateTime, value: Long, unit: String): LocalDateTime = when {
        unit.startsWith("min") -> now.plusMinutes(value)
        unit.startsWith("h") -> now.plusHours(value)
        unit.startsWith("day") -> now.plusDays(value)
        else -> now.plusWeeks(value)
    }.withNano(0)

    private fun relativeResult(
        original: String,
        matched: String,
        dateTime: LocalDateTime
    ): ReminderParseResult = ReminderParseResult(
        title = extractTitle(matched, original),
        dateTime = dateTime,
        recurrenceType = RecurrenceType.NONE,
        reminderType = ReminderType.ONE_TIME,
        confidence = 1.0f,
        rawInput = original
    )

    // ─── Recurring Patterns ───────────────────────────────────────────────────

    private fun parseRecurring(normalized: String, original: String, now: LocalDateTime): ReminderParseResult? {
        val today = now.toLocalDate()

        // Every year on Month Day  e.g. "every year on june 10"
        Regex("""every year on ($MONTHS) (\d{1,2})(?:st|nd|rd|th)?""").find(normalized)?.let { match ->
            val month = parseMonth(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            if (day !in 1..month.maxLength()) return@let
            val (time, timeConf) = timeFor(normalized)
            var next = LocalDateTime.of(safeDate(today.year, month.value, day), time)
            if (!next.isAfter(now)) next = LocalDateTime.of(safeDate(today.year + 1, month.value, day), time)
            return recurringResult(
                original, match.value, next, RecurrenceType.YEARLY,
                "${month.name}_$day", ReminderType.YEARLY, timeConf
            )
        }

        // Every month on Nth  e.g. "every month on 5th"
        Regex("""every month on (?:the )?(\d{1,2})(?:st|nd|rd|th)?""").find(normalized)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            if (day !in 1..31) return@let
            val (time, timeConf) = timeFor(normalized)
            var next = LocalDateTime.of(safeDate(today.year, today.monthValue, day), time)
            if (!next.isAfter(now)) {
                val nextMonth = YearMonth.from(today).plusMonths(1)
                next = LocalDateTime.of(safeDate(nextMonth.year, nextMonth.monthValue, day), time)
            }
            return recurringResult(
                original, match.value, next, RecurrenceType.MONTHLY,
                day.toString(), ReminderType.MONTHLY, timeConf
            )
        }

        // Weekdays / weekends  e.g. "every weekday at 9", "on weekends", "har weekend"
        Regex("""\b(?:every|on|har|all)\s+(weekday|weekend)s?\b|\b(weekday|weekend)s\b""").find(normalized)?.let { match ->
            val kind = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val days = if (kind == "weekday") WeekDays.WEEKDAYS else WeekDays.WEEKEND
            return weeklyResult(normalized, original, match.value, days, now)
        }

        // One or more named days  e.g. "every monday", "every monday, wednesday and sunday",
        // "on mondays and thursdays"
        Regex("""\b(?:every|on)\s+(?:($DAY_PLURAL)(?:\s*(?:,|&|\band\b|\baur\b|\bor\b)\s*|\s+)?)+""").find(normalized)?.let { match ->
            val days = Regex("""\b($WEEKDAYS)""").findAll(match.value)
                .mapNotNull { parseDayOfWeek(it.groupValues[1]) }
                .toSet()
            // "on monday" (no "every", single day, not plural) is a one-time date, not a repeat.
            val isRepeat = match.value.startsWith("every") || days.size > 1 ||
                Regex("""\b($WEEKDAYS)s\b""").containsMatchIn(match.value)
            if (days.isNotEmpty() && isRepeat) {
                return weeklyResult(normalized, original, match.value.trim(), days, now)
            }
        }

        // Every week  e.g. "every week on friday at 5pm" is handled above; plain
        // "every week" repeats on the day the first occurrence lands on.
        Regex("""\bevery week\b""").find(normalized)?.let { match ->
            val (time, timeConf) = timeFor(normalized)
            val weekday = Regex("""\b($WEEKDAYS)\b""").find(normalized)
                ?.let { parseDayOfWeek(it.groupValues[1]) }
            val date = weekday?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
                ?: extractDate(normalized, today)
                ?: today
            var next = LocalDateTime.of(date, time)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            return recurringResult(
                original, match.value, next, RecurrenceType.WEEKLY,
                next.dayOfWeek.name, ReminderType.WEEKLY, timeConf
            )
        }

        // Every day
        Regex("""\b(every day|everyday|daily)\b""").find(normalized)?.let { match ->
            val (time, timeConf) = timeFor(normalized)
            var next = LocalDateTime.of(today, time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return recurringResult(
                original, match.value, next, RecurrenceType.DAILY,
                "", ReminderType.DAILY, timeConf
            )
        }

        return null
    }

    private fun weeklyResult(
        normalized: String,
        original: String,
        matched: String,
        days: Set<DayOfWeek>,
        now: LocalDateTime
    ): ReminderParseResult {
        val (time, timeConf) = timeFor(normalized)
        val next = WeekDays.firstOccurrence(LocalDateTime.of(now.toLocalDate(), time), days, now)
        return recurringResult(
            original, matched, next, RecurrenceType.WEEKLY,
            WeekDays.format(days), ReminderType.WEEKLY, timeConf
        )
    }

    private fun recurringResult(
        original: String,
        matched: String,
        dateTime: LocalDateTime,
        recurrence: RecurrenceType,
        recurrenceValue: String,
        type: ReminderType,
        confidence: Float
    ) = ReminderParseResult(
        title = extractTitle(matched, original),
        dateTime = dateTime,
        recurrenceType = recurrence,
        recurrenceValue = recurrenceValue,
        reminderType = type,
        confidence = confidence,
        rawInput = original
    )

    /** Recurring reminders default to 8am; confidence reflects how sure we are of the time. */
    private fun timeFor(normalized: String): Pair<LocalTime, Float> {
        val extracted = extractTime(normalized) ?: return LocalTime.of(8, 0) to 0.85f
        val confidence = when {
            extracted.ambiguous -> 0.75f
            extracted.explicitClock -> 1.0f
            else -> 0.75f
        }
        return extracted.time to confidence
    }

    // ─── One-Time Patterns ────────────────────────────────────────────────────

    private fun parseOneTime(normalized: String, original: String, now: LocalDateTime): ReminderParseResult {
        val today = now.toLocalDate()
        val date = extractDate(normalized, today)
        val extracted = extractTime(normalized)

        var dateTime: LocalDateTime = when {
            extracted == null -> LocalDateTime.of(date ?: today, LocalTime.of(8, 0))
            date != null -> LocalDateTime.of(date, extracted.time)
            else -> {
                // No date given: the next time that clock time comes around. For an ambiguous
                // "at 5" said at 3pm, that means 5pm today rather than 5am tomorrow.
                val candidates = buildList {
                    add(LocalDateTime.of(today, extracted.time))
                    if (extracted.ambiguous) add(LocalDateTime.of(today, extracted.time.plusHours(12)))
                }
                candidates.filter { it.isAfter(now) }.minOrNull()
                    ?: LocalDateTime.of(today.plusDays(1), extracted.time)
            }
        }

        // "remind me to call mom" (no time at all) on a date that's already past 8am: move to
        // the next day instead of producing an instant past-due reminder.
        if (extracted == null && date == null && !dateTime.isAfter(now)) {
            dateTime = dateTime.plusDays(1)
        }
        // "call mom today" said in the afternoon: 8am today is gone, so an hour from now.
        if (extracted == null && date == today && !dateTime.isAfter(now)) {
            dateTime = now.plusHours(1)
        }
        // A bare weekday that resolved to today but whose time already passed -> next week.
        if (!dateTime.isAfter(now) && date != null && isBareWeekday(normalized)) {
            dateTime = dateTime.plusWeeks(1)
        }

        val confidence = when {
            extracted == null && date == null -> 0.5f
            extracted?.ambiguous == true -> 0.75f
            date != null && extracted != null && extracted.explicitClock -> 1.0f
            date == null && extracted != null && extracted.explicitClock -> 0.9f
            else -> 0.75f
        }

        return ReminderParseResult(
            title = cleanupTitle(original, original),
            dateTime = dateTime,
            recurrenceType = RecurrenceType.NONE,
            reminderType = ReminderType.ONE_TIME,
            confidence = confidence,
            rawInput = original
        )
    }

    // ─── Date Extraction ──────────────────────────────────────────────────────

    private fun extractDate(normalized: String, today: LocalDate): LocalDate? {
        if (normalized.contains("day after tomorrow")) return today.plusDays(2)
        if (Regex("""\btoday\b""").containsMatchIn(normalized)) return today
        if (Regex("""\btomorrow\b""").containsMatchIn(normalized)) return today.plusDays(1)
        if (Regex("""\btonight\b""").containsMatchIn(normalized)) return today
        if (normalized.contains("next week")) return today.plusWeeks(1)

        Regex("""\bnext ($WEEKDAYS)\b""").find(normalized)?.let { match ->
            val dow = parseDayOfWeek(match.groupValues[1]) ?: return null
            return today.with(TemporalAdjusters.next(dow))
        }

        // "june 10", "march 5th", "10 june", "5th of march"
        val monthFirst = Regex("""\b($MONTHS) (\d{1,2})(?:st|nd|rd|th)?\b""").find(normalized)
        val dayFirst = Regex("""\b(\d{1,2})(?:st|nd|rd|th)? (?:of )?($MONTHS)\b""").find(normalized)
        val monthDay = when {
            monthFirst != null -> parseMonth(monthFirst.groupValues[1]) to monthFirst.groupValues[2].toIntOrNull()
            dayFirst != null -> parseMonth(dayFirst.groupValues[2]) to dayFirst.groupValues[1].toIntOrNull()
            else -> null
        }
        if (monthDay != null) {
            val (month, day) = monthDay
            if (month != null && day != null && day in 1..month.maxLength()) {
                val candidate = safeDate(today.year, month.value, day)
                return if (candidate.isBefore(today)) safeDate(today.year + 1, month.value, day) else candidate
            }
        }

        // dd/mm/yyyy (most locales) or mm/dd/yyyy (US) - the separator alone doesn't say
        // which order was meant, so fall back to the device locale's convention.
        Regex("""\b(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})\b""").find(normalized)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            val (month, day) = if (Locale.getDefault().country == "US") first to second else second to first
            return try {
                LocalDate.of(year, month, day)
            } catch (_: Exception) {
                null
            }
        }

        if (!normalized.contains("every ")) {
            Regex("""\b(?:on\s+|this\s+)?($WEEKDAYS)\b""").find(normalized)?.let { match ->
                val dow = parseDayOfWeek(match.groupValues[1]) ?: return null
                return today.with(TemporalAdjusters.nextOrSame(dow))
            }
        }

        return null
    }

    // ─── Time Extraction ──────────────────────────────────────────────────────

    private data class ExtractedTime(
        val time: LocalTime,
        /** An actual clock time was given ("5:30", "5pm", "noon"), not just "evening". */
        val explicitClock: Boolean,
        /** Hour 1-11 with no am/pm and no part-of-day word; AM/PM was guessed. */
        val ambiguous: Boolean = false
    )

    private fun extractTime(normalized: String): ExtractedTime? {
        if (Regex("""\bnoon\b""").containsMatchIn(normalized)) return ExtractedTime(LocalTime.NOON, true)
        if (Regex("""\bmidnight\b""").containsMatchIn(normalized)) return ExtractedTime(LocalTime.MIDNIGHT, true)

        Regex("""\b(\d{1,2}):(\d{2})\s*([ap])\.?m\.?""").find(normalized)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour in 1..12 && minute in 0..59) {
                return ExtractedTime(withMeridiem(hour, minute, match.groupValues[3]), true)
            }
        }

        Regex("""\b(\d{1,2})\s*([ap])\.?m\.?(?![a-z])""").find(normalized)?.let { match ->
            val hour = match.groupValues[1].toInt()
            if (hour in 1..12) return ExtractedTime(withMeridiem(hour, 0, match.groupValues[2]), true)
        }

        Regex("""\b(\d{1,2}):(\d{2})\b""").find(normalized)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour in 0..23 && minute in 0..59) {
                val base = LocalTime.of(hour, minute)
                if (hour !in 1..11) return ExtractedTime(base, true)
                return when (dayPart(normalized)) {
                    DayPart.MORNING -> ExtractedTime(base, true)
                    DayPart.AFTERNOON, DayPart.EVENING, DayPart.NIGHT ->
                        ExtractedTime(base.plusHours(12), true)
                    null -> {
                        // "at 5" with nothing else: 1-6 are almost always afternoon/evening,
                        // 7-11 almost always morning. Flagged as ambiguous so the one-time
                        // path can pick whichever is next and the UI asks the user to check.
                        val guess = if (hour <= 6) base.plusHours(12) else base
                        ExtractedTime(guess, true, ambiguous = true)
                    }
                }
            }
        }

        return when (dayPart(normalized)) {
            DayPart.NIGHT -> ExtractedTime(LocalTime.of(20, 0), false)
            DayPart.EVENING -> ExtractedTime(LocalTime.of(18, 0), false)
            DayPart.AFTERNOON -> ExtractedTime(LocalTime.of(15, 0), false)
            DayPart.MORNING -> ExtractedTime(LocalTime.of(9, 0), false)
            null -> null
        }
    }

    private enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

    private fun dayPart(normalized: String): DayPart? = when {
        Regex("""\b(tonight|night)\b""").containsMatchIn(normalized) -> DayPart.NIGHT
        Regex("""\bevening\b""").containsMatchIn(normalized) -> DayPart.EVENING
        Regex("""\bafternoon\b""").containsMatchIn(normalized) -> DayPart.AFTERNOON
        Regex("""\bmorning\b""").containsMatchIn(normalized) -> DayPart.MORNING
        else -> null
    }

    private fun withMeridiem(hour: Int, minute: Int, ampm: String): LocalTime {
        val h = when {
            ampm == "p" && hour != 12 -> hour + 12
            ampm == "a" && hour == 12 -> 0
            else -> hour
        }
        return LocalTime.of(h, minute)
    }

    private fun isBareWeekday(normalized: String): Boolean {
        if (Regex("""\b(today|tomorrow|tonight|next)\b""").containsMatchIn(normalized)) return false
        return Regex("""\b($WEEKDAYS)\b""").containsMatchIn(normalized)
    }

    // ─── Title Extraction ─────────────────────────────────────────────────────

    // Trigger phrases people actually say before the real task ("add a reminder to pick up
    // milk", "please remind me about the call", "mujhe yaad dilana"). Longer/more specific
    // phrases are listed before their shorter subsets so e.g. "remind me about" is consumed
    // whole instead of leaving a dangling "about".
    private val fillerPhrasePatterns = listOf(
        Regex("""\b(please\s+|pls\s+|plz\s+)?remind\s+me\s+(to|about|that)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(please\s+|pls\s+|plz\s+)?remind\s+me\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(set|add|create|make)\s+(a\s+|an\s+)?reminder\s+(for|to|that)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(set|add|create|make)\s+(a\s+|an\s+)?reminder\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi\s+(want|need|have)\s+to\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(don't|do not|dont)\s+forget\s+(to|about)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmake\s+sure\s+to\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnote\s+to\s+self\s*(that|to)?\b""", RegexOption.IGNORE_CASE),
        // Hinglish trigger phrases.
        Regex("""\b(mujhe|muje|mereko|mujko)\b""", RegexOption.IGNORE_CASE),
        Regex("""\byaad\s+(dila|dilana|dilaana|dila\s+dena|dila\s+do|dilado|karwana|karana|kara\s+dena|rakhna)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bremind\s+(karna|kar\s+dena|kar\s+do|karo|karwa\s+dena|kardena|kardo)\b""", RegexOption.IGNORE_CASE),
        Regex("""\breminder\s+(laga|lagao|laga\s+do|set\s+karo|set\s+kar\s+do|bana\s+do)\b""", RegexOption.IGNORE_CASE)
    )

    private val dateTimeFillerPatterns = listOf(
        Regex("""\b\d{1,2}:\d{2}\s*[ap]\.?m\.?(?![a-z])""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{1,2}:\d{2}\b"""),
        Regex("""\b\d{1,2}\s*[ap]\.?m\.?(?![a-z])""", RegexOption.IGNORE_CASE),
        Regex("""\b(noon|midnight)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bin \d+\s*(minute|min|hour|hr|day|week)s?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bin half(?: an)? hours?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bin an? (minute|min|hour|hr|day|week)s?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(this\s+)?(morning|afternoon|evening|night)\b""", RegexOption.IGNORE_CASE),
        Regex("""\btonight\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:next\s+|on\s+|this\s+)?($WEEKDAYS)s?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(weekday|weekend)s?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext week\b""", RegexOption.IGNORE_CASE),
        Regex("""\bday after tomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\btoday\b""", RegexOption.IGNORE_CASE),
        Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\b($MONTHS)\s+\d{1,2}(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{1,2}[/\-]\d{1,2}[/\-]\d{4}\b""")
    )

    private val genericConnectorPatterns = listOf(
        Regex("""\bat\b""", RegexOption.IGNORE_CASE),
        Regex("""\bon\b""", RegexOption.IGNORE_CASE)
    )

    // Connector words that only make sense mid-phrase; once everything else is stripped, any of
    // these left dangling at the very start/end of the title are leftovers, not part of the task
    // (e.g. "...to remind me about my books" -> "about my books" -> "my books"). Includes the
    // Hinglish postpositions/auxiliaries ("ko", "hai", "ke liye", ...).
    private val edgeFillerRegex = Regex(
        """^(for|at|on|to|about|that|then|and|aur|ko|ki|ke|ka|pe|par|se|hai|ke\s+liye)\s+|""" +
            """\s+(for|at|on|to|about|that|then|and|aur|ko|ki|ke|ka|pe|par|se|hai|please|pls|ke\s+liye)$""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanupTitle(text: String, fallback: String): String {
        var result = text
        (fillerPhrasePatterns + dateTimeFillerPatterns + genericConnectorPatterns).forEach { pattern ->
            result = pattern.replace(result, " ")
        }
        result = result.replace(Regex("""[,.!?]+(\s|$)"""), " ")
        result = result.trim().replace(Regex("""\s+"""), " ")

        // Repeatedly strip edge connector words: removing one can expose another
        // (e.g. "to about my books" -> "about my books" -> "my books").
        var previous: String
        do {
            previous = result
            result = edgeFillerRegex.replace(result, " ").trim()
        } while (result != previous)

        return result.replaceFirstChar { it.uppercase() }
            .ifEmpty { fallback.trim().replaceFirstChar { it.uppercase() } }
    }

    private fun extractTitle(patternFound: String, original: String): String {
        val withoutPattern = Regex(Regex.escape(patternFound), RegexOption.IGNORE_CASE)
            .replace(original, " ")
        return cleanupTitle(withoutPattern, original)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a date, clamping the day to the month's length (e.g. the 31st in April -> 30th). */
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
    }

    private fun parseDayOfWeek(name: String): DayOfWeek? = when (name.lowercase()) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseMonth(name: String): Month? = when (name.lowercase()) {
        "january", "jan" -> Month.JANUARY
        "february", "feb" -> Month.FEBRUARY
        "march", "mar" -> Month.MARCH
        "april", "apr" -> Month.APRIL
        "may" -> Month.MAY
        "june", "jun" -> Month.JUNE
        "july", "jul" -> Month.JULY
        "august", "aug" -> Month.AUGUST
        "september", "sep", "sept" -> Month.SEPTEMBER
        "october", "oct" -> Month.OCTOBER
        "november", "nov" -> Month.NOVEMBER
        "december", "dec" -> Month.DECEMBER
        else -> null
    }

    private companion object {
        const val WEEKDAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
        const val DAY_PLURAL = "mondays?|tuesdays?|wednesdays?|thursdays?|fridays?|saturdays?|sundays?"
        const val MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december|" +
            "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
    }
}

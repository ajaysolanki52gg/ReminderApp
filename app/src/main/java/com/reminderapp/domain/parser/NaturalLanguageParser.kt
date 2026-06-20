package com.reminderapp.domain.parser

import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.domain.model.ReminderParseResult
import com.reminderapp.domain.model.ReminderType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaturalLanguageParser @Inject constructor() {

    fun parse(input: String): ReminderParseResult {
        val normalized = input.trim().lowercase()

        // Try recurring patterns first
        parseRecurring(normalized, input)?.let { return it }

        // Try one-time patterns
        return parseOneTime(normalized, input)
    }

    // ─── Recurring Patterns ───────────────────────────────────────────────────

    private fun parseRecurring(normalized: String, original: String): ReminderParseResult? {
        // Every year on Month Day  e.g. "every year on june 10"
        val yearlyPattern = Regex("""every year on (\w+) (\d{1,2})""")
        yearlyPattern.find(normalized)?.let { match ->
            val month = parseMonth(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val title = extractTitle(normalized, match.value, original)
            val now = LocalDateTime.now()
            var nextOccurrence = LocalDateTime.of(now.year, month, day, 8, 0)
            if (nextOccurrence.isBefore(now)) nextOccurrence = nextOccurrence.plusYears(1)
            return ReminderParseResult(
                title = title,
                dateTime = nextOccurrence,
                recurrenceType = RecurrenceType.YEARLY,
                recurrenceValue = "${month.name}_$day",
                reminderType = ReminderType.YEARLY,
                rawInput = original
            )
        }

        // Every month on Nth  e.g. "every month on 5th"
        val monthlyPattern = Regex("""every month on (\d{1,2})(?:st|nd|rd|th)?""")
        monthlyPattern.find(normalized)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            if (day < 1 || day > 31) return@let
            val title = extractTitle(normalized, match.value, original)
            val now = LocalDateTime.now()
            var nextOccurrence = LocalDateTime.of(now.year, now.month, day, 8, 0)
            if (nextOccurrence.isBefore(now)) nextOccurrence = nextOccurrence.plusMonths(1)
            return ReminderParseResult(
                title = title,
                dateTime = nextOccurrence,
                recurrenceType = RecurrenceType.MONTHLY,
                recurrenceValue = day.toString(),
                reminderType = ReminderType.MONTHLY,
                rawInput = original
            )
        }

        // Every weekday  e.g. "every monday", "every sunday"
        val weekdayPattern = Regex("""every (monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        weekdayPattern.find(normalized)?.let { match ->
            val dayOfWeek = parseDayOfWeek(match.groupValues[1]) ?: return@let
            val title = extractTitle(normalized, match.value, original)
            val now = LocalDateTime.now()
            var nextDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dayOfWeek))
            if (nextDate == now.toLocalDate() && now.toLocalTime().isAfter(LocalTime.of(8, 0))) {
                nextDate = nextDate.with(TemporalAdjusters.next(dayOfWeek))
            }
            val time = extractTime(normalized) ?: LocalTime.of(8, 0)
            return ReminderParseResult(
                title = title,
                dateTime = LocalDateTime.of(nextDate, time),
                recurrenceType = RecurrenceType.WEEKLY,
                recurrenceValue = dayOfWeek.name,
                reminderType = ReminderType.WEEKLY,
                rawInput = original
            )
        }

        // Every day
        if (normalized.contains("every day") || normalized.contains("daily")) {
            val title = extractTitle(
                normalized,
                if (normalized.contains("every day")) "every day" else "daily",
                original
            )
            val time = extractTime(normalized) ?: LocalTime.of(8, 0)
            val now = LocalDateTime.now()
            var nextOccurrence = LocalDateTime.of(now.toLocalDate(), time)
            if (nextOccurrence.isBefore(now)) nextOccurrence = nextOccurrence.plusDays(1)
            return ReminderParseResult(
                title = title,
                dateTime = nextOccurrence,
                recurrenceType = RecurrenceType.DAILY,
                recurrenceValue = "",
                reminderType = ReminderType.DAILY,
                rawInput = original
            )
        }

        return null
    }

    // ─── One-Time Patterns ────────────────────────────────────────────────────

    private fun parseOneTime(normalized: String, original: String): ReminderParseResult {
        val date = extractDate(normalized)
        val time = extractTime(normalized)
        val now = LocalDateTime.now()
        
        val finalTime = time ?: LocalTime.of(8, 0)
        val finalDate = date ?: if (time != null && LocalDateTime.of(LocalDate.now(), time).isBefore(now)) {
            LocalDate.now().plusDays(1)
        } else {
            LocalDate.now()
        }

        val dateTime = LocalDateTime.of(finalDate, finalTime)

        // Strip date/time keywords and filler phrases from title
        val title = buildTitle(normalized, original)

        return ReminderParseResult(
            title = title,
            dateTime = dateTime,
            recurrenceType = RecurrenceType.NONE,
            reminderType = ReminderType.ONE_TIME,
            rawInput = original
        )
    }

    // ─── Date Extraction ──────────────────────────────────────────────────────

    private fun extractDate(normalized: String): LocalDate? {
        val today = LocalDate.now()

        return when {
            normalized.contains("today") -> today
            normalized.contains("tomorrow") -> today.plusDays(1)
            normalized.contains("next week") -> today.plusWeeks(1)

            // next <weekday>
            Regex("""next (monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
                .containsMatchIn(normalized) -> {
                val match = Regex("""next (monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
                    .find(normalized)!!
                val dow = parseDayOfWeek(match.groupValues[1])!!
                today.with(TemporalAdjusters.next(dow))
            }

            // specific date: month day(th/st/nd/rd)  e.g. "june 10", "march 5th"
            Regex("""(january|february|march|april|may|june|july|august|september|october|november|december) (\d{1,2})""")
                .containsMatchIn(normalized) -> {
                val match = Regex("""(january|february|march|april|may|june|july|august|september|october|november|december) (\d{1,2})""")
                    .find(normalized)!!
                val month = parseMonth(match.groupValues[1]) ?: return null
                val day = match.groupValues[2].toIntOrNull() ?: return null
                val candidate = LocalDate.of(today.year, month, day)
                if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }

            // dd/mm/yyyy or dd-mm-yyyy
            Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})""").containsMatchIn(normalized) -> {
                val match = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})""").find(normalized)!!
                try {
                    LocalDate.of(
                        match.groupValues[3].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[1].toInt()
                    )
                } catch (e: Exception) { null }
            }

            else -> null
        }
    }

    // ─── Time Extraction ──────────────────────────────────────────────────────

    private fun extractTime(normalized: String): LocalTime? {
        // noon / midnight
        if (normalized.contains("noon")) return LocalTime.of(12, 0)
        if (normalized.contains("midnight")) return LocalTime.of(0, 0)

        // HH:MM am/pm (flexible spaces and dots)
        val fullTimeRegex = Regex("""(\d{1,2}):(\d{2})\s*([ap]\.?m\.?)""", RegexOption.IGNORE_CASE)
        fullTimeRegex.find(normalized)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val ampm = match.groupValues[3].lowercase().replace(".", "")
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        // H am/pm (flexible spaces and dots)
        val shortTimeRegex = Regex("""(\d{1,2})\s*([ap]\.?m\.?)""", RegexOption.IGNORE_CASE)
        shortTimeRegex.find(normalized)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val ampm = match.groupValues[2].lowercase().replace(".", "")
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return LocalTime.of(hour.coerceIn(0, 23), 0)
        }

        return null
    }

    // ─── Title Extraction ─────────────────────────────────────────────────────

    private fun buildTitle(normalized: String, original: String): String {
        var result = original.trim()

        // Filler phrases and keywords to remove (case insensitive)
        val removePatterns = listOf(
            Regex("""\bset a reminder for\b""", RegexOption.IGNORE_CASE),
            Regex("""\bset a reminder\b""", RegexOption.IGNORE_CASE),
            Regex("""\bremind me to\b""", RegexOption.IGNORE_CASE),
            Regex("""\bremind me\b""", RegexOption.IGNORE_CASE),
            Regex("""\bi want to\b""", RegexOption.IGNORE_CASE),
            Regex("""\bcreate a reminder for\b""", RegexOption.IGNORE_CASE),
            Regex("""\bat\b""", RegexOption.IGNORE_CASE),
            Regex("""\bfor\b""", RegexOption.IGNORE_CASE),
            Regex("""\bon\b""", RegexOption.IGNORE_CASE),
            // Time patterns
            Regex("""\b\d{1,2}:\d{2}\s*([ap]\.?m\.?)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b\d{1,2}\s*([ap]\.?m\.?)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bnoon\b""", RegexOption.IGNORE_CASE),
            Regex("""\bmidnight\b""", RegexOption.IGNORE_CASE),
            // Date patterns
            Regex("""\bnext\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bnext week\b""", RegexOption.IGNORE_CASE),
            Regex("""\btoday\b""", RegexOption.IGNORE_CASE),
            Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(january|february|march|april|may|june|july|august|september|october|november|december)\s+\d{1,2}(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE),
            Regex("""\b\d{1,2}[/\-]\d{1,2}[/\-]\d{4}\b""")
        )

        removePatterns.forEach { pattern ->
            result = pattern.replace(result, " ")
        }

        // Clean up extra spaces and punctuation
        result = result.trim().replace(Regex("""\s+"""), " ")
        
        // Remove leading/trailing "for", "at", "on" if they were left over
        result = result.replace(Regex("""^(for|at|on)\s+""", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""\s+(for|at|on)$""", RegexOption.IGNORE_CASE), "")

        return result.trim().replaceFirstChar { it.uppercase() }.ifEmpty { original.trim().replaceFirstChar { it.uppercase() } }
    }

    private fun extractTitle(normalized: String, patternFound: String, original: String): String {
        // Remove the recurring pattern portion from title
        val cleaned = normalized.replace(patternFound, "").trim()
        // Remove any extracted time
        val time = extractTime(normalized)
        var title = original
        if (time != null) {
            val timePatterns = listOf(
                Regex("""\\d{1,2}:\\d{2}\\s*[ap]m""", RegexOption.IGNORE_CASE),
                Regex("""\\d{1,2}\\s*[ap]m""", RegexOption.IGNORE_CASE),
                Regex("""\\bnoon\\b""", RegexOption.IGNORE_CASE),
                Regex("""\\bmidnight\\b""", RegexOption.IGNORE_CASE)
            )
            timePatterns.forEach { title = it.replace(title, "").trim() }
        }
        // Also remove the recurring phrase from original
        title = Regex(patternFound, RegexOption.IGNORE_CASE).replace(title, "").trim()
        return title.replaceFirstChar { it.uppercase() }.ifEmpty { cleaned.replaceFirstChar { it.uppercase() } }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
}

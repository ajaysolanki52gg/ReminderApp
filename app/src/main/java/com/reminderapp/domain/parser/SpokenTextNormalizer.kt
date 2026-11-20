package com.reminderapp.domain.parser

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites common speech-to-text output into the typed forms the rule parser expects.
 */
@Singleton
class SpokenTextNormalizer @Inject constructor() {

    fun normalize(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return text

        text = text.replace(Regex("""(?i)\bp\s*\.?\s*m\.?\b"""), "pm")
        text = text.replace(Regex("""(?i)\ba\s*\.?\s*m\.?\b"""), "am")
        text = text.replace(Regex("""\s+"""), " ").trim()

        text = replaceNumberWords(text)
        text = translateHinglish(text)
        text = expandSpokenClockPhrases(text)
        text = joinSpokenClockTimes(text)
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    private fun replaceNumberWords(input: String): String {
        val tokens = input.split(Regex("""\s+""")).toMutableList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val current = tokens[i].lowercase().trim(',', '.', '!', '?')
            val next = tokens.getOrNull(i + 1)?.lowercase()?.trim(',', '.', '!', '?')

            val tens = TENS[current]
            val onesFromHyphen = hyphenated(current)
            when {
                tens != null && next != null && UNITS[next] != null && UNITS[next]!! < 10 -> {
                    out.add((tens + UNITS[next]!!).toString())
                    i += 2
                }
                onesFromHyphen != null -> {
                    out.add(onesFromHyphen.toString())
                    i += 1
                }
                tens != null -> {
                    out.add(tens.toString())
                    i += 1
                }
                UNITS[current] != null -> {
                    out.add(UNITS[current]!!.toString())
                    i += 1
                }
                TEENS[current] != null -> {
                    out.add(TEENS[current]!!.toString())
                    i += 1
                }
                else -> {
                    out.add(tokens[i])
                    i += 1
                }
            }
        }
        return out.joinToString(" ")
    }

    private fun hyphenated(token: String): Int? {
        val parts = token.split('-', '–')
        if (parts.size != 2) return null
        val tens = TENS[parts[0]] ?: return null
        val ones = UNITS[parts[1]] ?: return null
        if (ones >= 10) return null
        return tens + ones
    }

    /**
     * Rewrites romanised Hindi / Hinglish date-time words into the English forms the rule
     * parser understands, e.g.
     *   "kal subah 9 baje doctor ko call karna" -> "tomorrow morning at 9 doctor ko call karna"
     *   "saade 5 baje"      -> "at 5:30"
     *   "10 minute baad"    -> "in 10 minutes"
     *   "har somvar"        -> "every monday"
     * Only date/time words are touched; the task itself is left as the user said it.
     */
    private fun translateHinglish(input: String): String {
        var text = input

        // Hindi number words, only when they're clearly a quantity of time ("do baje",
        // "teen ghante") so English words like "do" are left alone elsewhere.
        text = HINDI_NUMBER_BEFORE_UNIT.replace(text) { m ->
            "${HINDI_NUMBERS.getValue(m.groupValues[1].lowercase())} ${m.groupValues[2]}"
        }

        // Fractional durations: "dedh ghante mein" = 90 min, "aadhe ghante baad" = 30 min.
        text = Regex("""\b(aadhe|adhe|aadha|adha)\s+(ghante|ghanta)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text, "in 30 minutes")
        text = Regex("""\b(dedh|derh)\s+(ghante|ghanta)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text, "in 90 minutes")
        text = Regex("""\b(dhai|dhaai|dhaee|dhayi)\s+(ghante|ghanta)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text, "in 150 minutes")

        // Durations: "10 minute baad", "2 ghante mein", "3 din baad", "1 hafte mein".
        text = Regex("""\b(\d+)\s*(minute|minutes|minat|min|mins)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text) { "in ${it.groupValues[1]} minutes" }
        text = Regex("""\b(\d+)\s*(ghante|ghanta|ghanton)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text) { "in ${it.groupValues[1]} hours" }
        text = Regex("""\b(\d+)\s*(din)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text) { "in ${it.groupValues[1]} days" }
        text = Regex("""\b(\d+)\s*(hafte|hafta|hafton)\s+(mein|me|main|baad|bad)\b""", IC)
            .replace(text) { "in ${it.groupValues[1]} weeks" }

        // Indian-English phrasing: "after 10 minutes", "10 minutes later".
        text = Regex("""\bafter\s+(\d+)\s*(minute|min|hour|hr|day|week)s?\b""", IC)
            .replace(text) { "in ${it.groupValues[1]} ${it.groupValues[2]}s" }
        text = Regex("""\b(\d+)\s*(minute|min|hour|hr|day|week)s?\s+later\b""", IC)
            .replace(text) { "in ${it.groupValues[1]} ${it.groupValues[2]}s" }

        // Clock fractions: "saade 5" = 5:30, "sava 5" = 5:15, "paune 5" = 4:45,
        // "dedh" = 1:30, "dhai" = 2:30.
        text = Regex("""\b(saade|sade|saadhe|sadhe)\s+(\d{1,2})\b""", IC)
            .replace(text) { "${it.groupValues[2]}:30" }
        text = Regex("""\b(sava|sawa|savaa|sawaa)\s+(\d{1,2})\b""", IC)
            .replace(text) { "${it.groupValues[2]}:15" }
        text = Regex("""\b(paune|pone|paone)\s+(\d{1,2})\b""", IC).replace(text) {
            val hour = it.groupValues[2].toIntOrNull() ?: return@replace it.value
            "${if (hour <= 1) 12 else hour - 1}:45"
        }
        text = Regex("""\b(dedh|derh)\s+(baje|bje|baaje|baj)\b""", IC).replace(text, "1:30 baje")
        text = Regex("""\b(dhai|dhaai|dhaee|dhayi)\s+(baje|bje|baaje|baj)\b""", IC).replace(text, "2:30 baje")

        // "9 baje" / "9:30 baje" -> "at 9" / "at 9:30" (the clock step below adds ":00").
        text = Regex("""\b(?:at\s+)?(\d{1,2}(?::\d{2})?)\s*(baje|bje|baaje|baj)\b""", IC)
            .replace(text) { "at ${it.groupValues[1]}" }

        // Parts of the day.
        text = Regex("""\b(subah|subha|subeh|savere|sawere|savera)\b""", IC).replace(text, "morning")
        text = Regex("""\b(dopahar|dopehar|dopaher|dupahar|dophar)\b""", IC).replace(text, "afternoon")
        text = Regex("""\b(shaam|sham|shām)\b""", IC).replace(text, "evening")
        text = Regex("""\b(raat|raath)\b""", IC).replace(text, "night")

        // Recurrence (before "kal"/weekday words so "har somvar" keeps its "every").
        text = Regex("""\b(har\s+roz|har\s+din|rozana|roz|roj|pratidin|daily)\b""", IC).replace(text, "every day")
        text = Regex("""\bhar\s+(hafte|hafta)\b""", IC).replace(text, "every week")
        text = Regex("""\bhar\s+(mahine|mahina)\s+(?:ki\s+)?(\d{1,2})(?:\s*(?:tarikh|tareekh|tarik|taarikh|ko))?\b""", IC)
            .replace(text) { "every month on ${it.groupValues[2]}" }

        // Weekdays.
        HINDI_WEEKDAYS.forEach { (pattern, english) -> text = pattern.replace(text, english) }
        text = Regex("""\bhar\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", IC)
            .replace(text) { "every ${it.groupValues[1]}" }

        // Relative days. "parso" first so it isn't shadowed; "kal" in a reminder means tomorrow.
        text = Regex("""\b(parso|parson|parsoon)\b""", IC).replace(text, "day after tomorrow")
        text = Regex("""\b(agle|agla|agli)\s+(hafte|hafta)\b""", IC).replace(text, "next week")
        text = Regex("""\b(agle|agla|agli)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", IC)
            .replace(text) { "next ${it.groupValues[2]}" }
        text = Regex("""\bkal\b""", IC).replace(text, "tomorrow")
        text = Regex("""\baaj\b""", IC).replace(text, "today")

        return text
    }

    private fun expandSpokenClockPhrases(input: String): String {
        var text = input
        text = Regex("""\b(\d{1,2})\s+o['’]?clock\b""", RegexOption.IGNORE_CASE).replace(text) {
            "${it.groupValues[1]}:00"
        }
        text = Regex("""\bquarter past (\d{1,2})\b""", RegexOption.IGNORE_CASE).replace(text) {
            "${it.groupValues[1]}:15"
        }
        text = Regex("""\bhalf past (\d{1,2})\b""", RegexOption.IGNORE_CASE).replace(text) {
            "${it.groupValues[1]}:30"
        }
        text = Regex("""\bquarter to (\d{1,2})\b""", RegexOption.IGNORE_CASE).replace(text) {
            val hour = it.groupValues[1].toIntOrNull() ?: return@replace it.value
            val previous = if (hour <= 1) 12 else hour - 1
            "$previous:45"
        }
        text = Regex(
            """\bat\s+(\d{1,2})\b(?!\s*(?::\d|am\b|pm\b|\d))""",
            RegexOption.IGNORE_CASE
        ).replace(text) { "at ${it.groupValues[1]}:00" }
        return text
    }

    private fun joinSpokenClockTimes(input: String): String {
        var text = input
        text = CLOCK_WITH_MERIDIEM.replace(text) { match ->
            val hour = match.groupValues[1]
            val minute = match.groupValues[2].padStart(2, '0')
            val meridiem = match.groupValues[3]
            "$hour:$minute $meridiem"
        }
        text = CLOCK_PLAIN.replace(text) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val minute = match.groupValues[2]
            if (hour in 1..12 && minute.toIntOrNull() in 0..59) {
                "$hour:${minute.padStart(2, '0')}"
            } else {
                match.value
            }
        }
        return text
    }

    companion object {
        private val IC = RegexOption.IGNORE_CASE

        private val HINDI_NUMBERS = mapOf(
            "ek" to 1, "do" to 2, "teen" to 3, "tin" to 3, "char" to 4, "chaar" to 4,
            "paanch" to 5, "panch" to 5, "chhe" to 6, "chhah" to 6, "che" to 6, "chhai" to 6,
            "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "gyara" to 11,
            "barah" to 12, "baara" to 12, "baarah" to 12, "pandrah" to 15, "bees" to 20,
            "tees" to 30, "chalis" to 40, "chaalis" to 40, "pachas" to 50, "pachaas" to 50
        )
        private val HINDI_NUMBER_BEFORE_UNIT = Regex(
            """\b(${HINDI_NUMBERS.keys.joinToString("|")})\s+""" +
                """(baje|bje|baaje|baj|minute|minutes|minat|min|mins|ghante|ghanta|ghanton|din|hafte|hafta|hafton|tarikh|tareekh)\b""",
            RegexOption.IGNORE_CASE
        )

        private val HINDI_WEEKDAYS = listOf(
            Regex("""\b(somvar|somwar|somvaar|somwaar)\b""", RegexOption.IGNORE_CASE) to "monday",
            Regex("""\b(mangalvar|mangalwar|mangalvaar|mangalwaar)\b""", RegexOption.IGNORE_CASE) to "tuesday",
            Regex("""\b(budhvar|budhwar|budhvaar|budhwaar)\b""", RegexOption.IGNORE_CASE) to "wednesday",
            Regex("""\b(guruvar|guruwar|guruvaar|guruwaar|brihaspativar|veervar|veerwar)\b""", RegexOption.IGNORE_CASE) to "thursday",
            Regex("""\b(shukravar|shukrawar|shukravaar|shukrawaar)\b""", RegexOption.IGNORE_CASE) to "friday",
            Regex("""\b(shanivar|shaniwar|shanivaar|shaniwaar)\b""", RegexOption.IGNORE_CASE) to "saturday",
            Regex("""\b(ravivar|raviwar|ravivaar|raviwaar|itvaar|itwar|itwaar|etwar)\b""", RegexOption.IGNORE_CASE) to "sunday"
        )

        private val UNITS = mapOf(
            "zero" to 0, "oh" to 0, "o" to 0,
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9
        )
        private val TEENS = mapOf(
            "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
            "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
            "eighteen" to 18, "nineteen" to 19
        )
        private val TENS = mapOf(
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40,
            "fifty" to 50
        )

        private val CLOCK_WITH_MERIDIEM =
            Regex("""\b(\d{1,2})\s+(\d{1,2})\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
        private val CLOCK_PLAIN =
            Regex("""\b(\d{1,2})\s+(\d{2})\b""")
    }
}

package com.avlreminder.core.rules

import com.avlreminder.core.model.Reminder
import java.time.Instant
import java.time.ZoneId

/**
 * What the speech recognizer heard, read as a quick reminder (Anf. 7.35 SR3, SR4, SR6).
 * [timeHeard]/[titleHeard] false: the value is the fallback, the summary says so.
 */
data class SpokenReminder(
    val title: String,
    val minutes: Long,
    val timeHeard: Boolean,
    val titleHeard: Boolean,
) {
    /** Hints for the summary, empty when everything was understood. */
    val hints: List<String>
        get() = buildList {
            if (!timeHeard) add("Keine Zeit erkannt, Standard ${VoiceReminders.DEFAULT_MINUTES} min")
            if (!titleHeard) add("Kein Name erkannt")
        }
}

/**
 * Reads a spoken quick reminder in a fixed pattern, without network and without AI:
 * "Zeit: in 5 Minuten. Name: Tee machen". The recognizer drops the colons and often the
 * order is loose, so the parser looks for a time phrase anywhere and takes the name after
 * the word "Name" (or what is left without the time).
 */
object VoiceReminders {
    /** Shown on screen, so Kevin knows what to say. */
    const val PATTERN = "Zeit: in 5 Minuten. Name: Tee machen"
    const val DEFAULT_MINUTES = 10L
    const val DEFAULT_TITLE = "Schnellerinnerung"
    /** SR6: 1 min to 7 days; anything else counts as not understood. */
    const val MAX_MINUTES = 7L * 24 * 60
    private const val MAX_TITLE = 80

    private val NUMBERS = mapOf(
        "ein" to 1, "eine" to 1, "einer" to 1, "einem" to 1, "zwei" to 2, "drei" to 3, "vier" to 4,
        "fünf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
        "elf" to 11, "zwölf" to 12, "fünfzehn" to 15, "zwanzig" to 20, "fünfundzwanzig" to 25,
        "dreißig" to 30, "vierzig" to 40, "fünfundvierzig" to 45, "fünfzig" to 50, "sechzig" to 60,
        "neunzig" to 90,
    )
    private val NUMBER = "(\\d+(?:[.,]\\d+)?|eineinhalb|anderthalb|${NUMBERS.keys.sortedByDescending { it.length }.joinToString("|")})"
    private val UNIT = "(minuten|minute|min\\.?|stunden|stunde|std\\.?|tagen|tage|tag)"

    /** Fixed phrases first, so "in einer halben Stunde" is not read as "in einer Stunde". */
    private val PHRASES: List<Pair<Regex, (MatchResult) -> Double?>> = listOf(
        Regex("\\bin\\s+(?:einer\\s+)?viertel\\s?stunde\\b") to { _ -> 15.0 },
        Regex("\\bin\\s+(?:einer\\s+)?dreiviertel\\s?stunde\\b") to { _ -> 45.0 },
        Regex("\\bin\\s+(?:einer\\s+)?halben\\s+stunde\\b") to { _ -> 30.0 },
        Regex("\\bin\\s+$NUMBER\\s*$UNIT(?=\\W|$)") to { m -> amount(m.groupValues[1])?.let { it * factor(m.groupValues[2]) } },
    )
    private val NAME = Regex("\\bnamen?\\b\\W*", RegexOption.IGNORE_CASE)
    private val LEADING_TIME_WORD = Regex("^zeit\\b\\W*", RegexOption.IGNORE_CASE)
    private val TRAILING_TIME_WORD = Regex("\\W*\\bzeit$", RegexOption.IGNORE_CASE)
    private val EDGES = Regex("^[\\s.,:;!?-]+|[\\s.,:;!?-]+$")

    fun parse(text: String): SpokenReminder {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val lower = clean.lowercase()
        // KRITISCH: The time decides when the alarm rings; out of range is "not understood".
        var minutes: Long? = null
        var timeRange: IntRange? = null
        for ((regex, value) in PHRASES) {
            val match = regex.find(lower) ?: continue
            val m = value(match)?.let { Math.round(it) }
            if (m != null && m in 1..MAX_MINUTES) minutes = m
            timeRange = match.range
            break
        }

        val nameMatch = NAME.findAll(clean).lastOrNull()
        val rawTitle = if (nameMatch != null) {
            // Name after "Name"; a time spoken after the name is cut off.
            val end = timeRange?.first?.takeIf { it > nameMatch.range.last } ?: clean.length
            clean.substring(nameMatch.range.last + 1, end)
        } else {
            timeRange?.let { clean.replaceRange(it, " ") } ?: clean
        }
        // Only "Zeit" before the time phrase is the keyword: trailing when the name came first,
        // leading without "Name". After "Name", "Zeit für Tee" stays a name.
        val trimmed = rawTitle.replace(EDGES, "").replace(TRAILING_TIME_WORD, "")
        val title = (if (nameMatch == null) trimmed.replace(LEADING_TIME_WORD, "") else trimmed)
            .replace(Regex("\\s+"), " ").replace(EDGES, "").take(MAX_TITLE).trim()
        return SpokenReminder(
            title = title.ifEmpty { DEFAULT_TITLE }.replaceFirstChar { it.uppercaseChar() },
            minutes = minutes ?: DEFAULT_MINUTES,
            timeHeard = minutes != null,
            titleHeard = title.isNotEmpty(),
        )
    }

    /** The reminder like the meeting buttons: normal, one-off, no proof (Anf. 7.35, offen: Strenge). */
    fun reminder(spoken: SpokenReminder, now: Instant, zone: ZoneId): Reminder =
        QuickReminders.inMinutes(spoken.title, spoken.minutes, now, zone)

    private fun amount(word: String): Double? = when (word) {
        "eineinhalb", "anderthalb" -> 1.5
        else -> word.replace(',', '.').toDoubleOrNull() ?: NUMBERS[word]?.toDouble()
    }

    private fun factor(unit: String): Double = when {
        unit.startsWith("min") -> 1.0
        unit.startsWith("st") -> 60.0
        else -> 24.0 * 60
    }
}

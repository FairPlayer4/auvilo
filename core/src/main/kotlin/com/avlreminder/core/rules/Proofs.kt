package com.avlreminder.core.rules

import com.avlreminder.core.model.Character
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder

/** What was handed in when completing. Files live in the app's own storage. */
data class Evidence(
    val photoPath: String? = null,
    val text: String? = null,
    val audioPath: String? = null,
    val audioSeconds: Int? = null,
)

object ProofCheck {
    /** Characters that count for the minimum: everything but whitespace. */
    fun chars(text: String?): Int = text?.count { !it.isWhitespace() } ?: 0

    /**
     * What is still missing for [spec]; empty if the proof is complete.
     * [lastText] is the previous text proof of the same reminder.
     */
    fun problems(spec: ProofSpec, evidence: Evidence, lastText: String? = null): List<String> = buildList {
        if (!spec.needsEvidence) return@buildList
        if (spec.photo && evidence.photoPath == null) add("Foto fehlt")
        spec.textMinChars?.let { min ->
            val text = evidence.text?.trim().orEmpty()
            val count = chars(text)
            val letters = text.filterNot { it.isWhitespace() }
            when {
                count == 0 -> add("Text fehlt")
                count < min -> add("Noch ${min - count} Zeichen")
                letters.toSet().size == 1 -> add("Text besteht nur aus einem Zeichen")
                lastText != null && normalize(text) == normalize(lastText) -> add("Gleicher Text wie beim letzten Mal")
            }
        }
        spec.audioMinSeconds?.let { min ->
            val seconds = evidence.audioSeconds ?: 0
            when {
                evidence.audioPath == null -> add("Aufnahme fehlt")
                seconds < min -> add("Aufnahme zu kurz (mind. $min s)")
            }
        }
    }

    private fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
}

/** The four "Startklar" questions (requirements 7.21). */
object Prep {
    val QUESTIONS = 1..4
    const val GOAL = 1
    const val OBSTACLES = 2
    const val READY = 3
    const val FIRST_STEP = 4
    /** Answer to question 3 when nothing is missing. */
    const val READY_YES = "Ja, sofort"

    fun isAnswered(answer: PrepAnswer?) = answer != null && (answer.unknown || !answer.text.isNullOrBlank())

    /** The newest answer per question. */
    fun latest(all: List<PrepAnswer>): Map<Int, PrepAnswer> =
        all.groupBy { it.question }.mapValues { (_, list) -> list.maxWith(compareBy({ it.at }, { it.id })) }

    /** Questions still to answer; only "Überwindung" requires them. */
    fun missing(reminder: Reminder, current: Map<Int, PrepAnswer>): List<Int> =
        if (reminder.character != Character.OVERCOME) emptyList()
        else QUESTIONS.filter { !isAnswered(current[it]) }

    /** Open points for the alarm: "don't know yet", obstacles and what is missing to start. */
    fun open(current: Map<Int, PrepAnswer>): List<PrepAnswer> =
        QUESTIONS.mapNotNull { current[it] }.filter {
            it.unknown || (it.question in OBSTACLES..READY && !it.text.isNullOrBlank() && it.text != READY_YES)
        }
}

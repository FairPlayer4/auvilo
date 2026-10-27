package com.avlreminder.core.rules

import com.avlreminder.core.at
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.reminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProofsTest {
    private val text = ProofSpec(ProofKind.EVIDENCE, textMinChars = 10)
    private val audio = ProofSpec(ProofKind.EVIDENCE, audioMinSeconds = 5)

    @Test
    fun `confirm and none need nothing`() {
        assertTrue(ProofCheck.problems(ProofSpec.CONFIRM, Evidence()).isEmpty())
        assertTrue(ProofCheck.problems(ProofSpec.NONE, Evidence()).isEmpty())
    }

    @Test
    fun `photo must be there`() {
        assertEquals(listOf("Foto fehlt"), ProofCheck.problems(ProofSpec.PHOTO, Evidence()))
        assertTrue(ProofCheck.problems(ProofSpec.PHOTO, Evidence(photoPath = "p.jpg")).isEmpty())
    }

    @Test
    fun `whitespace does not count for the text minimum`() {
        assertEquals(11, ProofCheck.chars(" Filter  neu\n ab "))
        assertEquals(0, ProofCheck.chars(null))
        assertEquals(listOf("Text fehlt"), ProofCheck.problems(text, Evidence(text = "   ")))
        assertEquals(listOf("Noch 1 Zeichen"), ProofCheck.problems(text, Evidence(text = "Filter neu")))
        assertTrue(ProofCheck.problems(text, Evidence(text = "Filter neu, Wasser")).isEmpty())
    }

    @Test
    fun `nonsense text is caught lightly`() {
        assertEquals(listOf("Text besteht nur aus einem Zeichen"), ProofCheck.problems(text, Evidence(text = "aaaaa aaaaaaa")))
        assertEquals(
            listOf("Gleicher Text wie beim letzten Mal"),
            ProofCheck.problems(text, Evidence(text = "Filter neu,  Wasser "), lastText = "filter neu, wasser"),
        )
        assertTrue(ProofCheck.problems(text, Evidence(text = "Filter neu, Wasser"), lastText = "Nur Wasser gewechselt").isEmpty())
    }

    @Test
    fun `audio needs its minimum length`() {
        assertEquals(listOf("Aufnahme fehlt"), ProofCheck.problems(audio, Evidence()))
        assertEquals(listOf("Aufnahme zu kurz (mind. 5 s)"), ProofCheck.problems(audio, Evidence(audioPath = "a.m4a", audioSeconds = 4)))
        assertEquals(listOf("Aufnahme zu kurz (mind. 5 s)"), ProofCheck.problems(audio, Evidence(audioPath = "a.m4a")))
        assertTrue(ProofCheck.problems(audio, Evidence(audioPath = "a.m4a", audioSeconds = 5)).isEmpty())
    }

    @Test
    fun `combined proofs need every part`() {
        val all = ProofSpec(ProofKind.EVIDENCE, photo = true, textMinChars = 3, audioMinSeconds = 1)
        assertEquals(listOf("Foto fehlt", "Text fehlt", "Aufnahme fehlt"), ProofCheck.problems(all, Evidence()))
        assertTrue(ProofCheck.problems(all, Evidence("p.jpg", "neu", "a.m4a", 2)).isEmpty())
    }

    private fun answer(q: Int, text: String?, unknown: Boolean = false, minute: Int = 0) =
        PrepAnswer(reminderId = 1, question = q, text = text, unknown = unknown, at = at(26, 8, minute))

    @Test
    fun `startklar is required for overcome only`() {
        val overcome = reminder(character = Character.OVERCOME)
        assertEquals(listOf(1, 2, 3, 4), Prep.missing(overcome, emptyMap()))
        assertTrue(Prep.missing(reminder(character = Character.DOCUMENT), emptyMap()).isEmpty())
        val answers = mapOf(1 to answer(1, "Sauberes Wasser"), 2 to answer(2, null, unknown = true), 3 to answer(3, " "))
        assertEquals(listOf(3, 4), Prep.missing(overcome, answers))
    }

    @Test
    fun `the newest answer counts`() {
        val latest = Prep.latest(listOf(answer(4, "alt", minute = 1), answer(4, "neu", minute = 2), answer(1, "Ziel")))
        assertEquals("neu", latest[4]!!.text)
        assertEquals(setOf(1, 4), latest.keys)
    }

    @Test
    fun `open points are unknowns, obstacles and what is missing`() {
        val current = mapOf(
            1 to answer(1, null, unknown = true),
            2 to answer(2, "Müde nach der Arbeit"),
            3 to answer(3, Prep.READY_YES),
            4 to answer(4, "Matte ausrollen"),
        )
        assertEquals(listOf(1, 2), Prep.open(current).map { it.question })
        assertEquals(listOf(3), Prep.open(mapOf(3 to answer(3, "Bereich aufräumen"))).map { it.question })
    }

    @Test
    fun `questions are numbered one to four`() {
        assertThrows<IllegalArgumentException> { answer(5, "x") }
        assertFalse(Prep.isAnswered(null))
    }
}

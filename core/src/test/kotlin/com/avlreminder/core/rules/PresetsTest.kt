package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.reminder
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresetsTest {

    @Test
    fun `presets carry the agreed start values`() {
        assertEquals(Preset(ProofSpec.CONFIRM, null, null), Presets.of(Strictness.RELAXED))
        assertEquals(Preset(ProofSpec.CONFIRM, 3, null), Presets.of(Strictness.NORMAL))
        assertEquals(Preset(ProofSpec.PHOTO, 1, 1), Presets.of(Strictness.STRICT))
    }

    @Test
    fun `applying a preset overwrites its values`() {
        val strict = Presets.apply(reminder(), Strictness.STRICT)
        assertEquals(Strictness.STRICT, strict.strictness)
        assertEquals(ProofSpec.PHOTO, strict.proof)
        assertEquals(1, strict.postponeLimit)
        assertEquals(1, strict.minimumLimitPerWeek)
    }

    @Test
    fun `a valid reminder has no problems`() {
        assertTrue(Validation.problems(reminder()).isEmpty())
    }

    @Test
    fun `validation finds every problem`() {
        val bad = reminder().copy(
            title = " ",
            strictness = Strictness.STRICT,
            proof = ProofSpec.NONE,
            postponeLimit = -1,
            minimumLimitPerWeek = -1,
        )
        assertEquals(
            listOf(
                "Titel fehlt",
                "Streng verlangt einen Nachweis",
                "Verschiebe-Limit darf nicht negativ sein",
                "Minimum-Limit darf nicht negativ sein",
                "Limit ohne Minimum",
            ),
            Validation.problems(bad),
        )
    }

    @Test
    fun `weeks run monday to sunday`() {
        assertEquals(LocalDate.of(2026, 10, 26), Weeks.start(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 10, 26), Weeks.start(LocalDate.of(2026, 10, 26)))
        assertEquals(at(26, 0) to at(2, 0, month = 11), Weeks.range(at(1, 23, month = 11), ZONE))
    }

    @Test
    fun `meeting button plans a one-off reminder`() {
        val m = QuickReminders.meeting(30, at(26, 14, 5), ZONE)
        assertEquals(Schedule.Once(LocalDateTime.of(2026, 10, 26, 14, 35)), m.schedule)
        assertEquals(ProofSpec.NONE, m.proof)
        assertEquals(Character.DOCUMENT, m.character)
        assertEquals(3, m.postponeLimit)
        assertTrue(Validation.problems(m).isEmpty())
    }

    @Test
    fun `a new reminder starts with the preset of its strictness`() {
        val r = Presets.newReminder("Lesen", Schedule.Daily(java.time.LocalTime.NOON), at(26, 14))
        assertEquals(Character.DOCUMENT, r.character)
        assertEquals(Presets.apply(r, Strictness.NORMAL), r)
        val strict = Presets.newReminder("Lesen", Schedule.Daily(java.time.LocalTime.NOON), at(26, 14), Strictness.STRICT)
        assertEquals(ProofSpec.PHOTO, strict.proof)
        assertEquals(1, strict.minimumLimitPerWeek)
    }

    @Test
    fun `validation checks proof details and snooze buttons`() {
        fun problems(r: com.avlreminder.core.model.Reminder) = Validation.problems(r)
        assertEquals(listOf("Nachweis: Foto, Text oder Audio wählen"), problems(reminder().copy(proof = ProofSpec(ProofKind.EVIDENCE))))
        val bad = ProofSpec(ProofKind.EVIDENCE, textMinChars = 0, audioMinSeconds = 0, windowMinutes = 0)
        assertEquals(
            listOf("Mindestzeichen 1 bis 2000", "Mindestdauer 1 bis 600 s", "Frist für den Nachweis 1 bis 60 min"),
            problems(reminder().copy(proof = bad)),
        )
        assertEquals(listOf("Ein oder zwei Verschiebe-Knöpfe"), problems(reminder().copy(snoozeMinutes = emptyList())))
        assertEquals(listOf("Ein oder zwei Verschiebe-Knöpfe"), problems(reminder().copy(snoozeMinutes = listOf(5, 10, 30))))
        assertEquals(listOf("Verschiebe-Knöpfe 1 bis 240 min"), problems(reminder().copy(snoozeMinutes = listOf(0))))
        assertEquals(listOf("Verschiebe-Knöpfe doppelt"), problems(reminder().copy(snoozeMinutes = listOf(30, 30))))
        assertTrue(problems(reminder().copy(snoozeMinutes = listOf(30, 60))).isEmpty())
    }
}

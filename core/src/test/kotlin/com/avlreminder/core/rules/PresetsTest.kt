package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.reminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PresetsTest {

    @Test
    fun `presets carry the agreed start values`() {
        assertEquals(Preset(ProofType.CONFIRM, null, null), Presets.of(Strictness.RELAXED))
        assertEquals(Preset(ProofType.CONFIRM, 3, null), Presets.of(Strictness.NORMAL))
        assertEquals(Preset(ProofType.PHOTO, 1, 1), Presets.of(Strictness.STRICT))
    }

    @Test
    fun `applying a preset overwrites its values`() {
        val strict = Presets.apply(reminder(), Strictness.STRICT)
        assertEquals(Strictness.STRICT, strict.strictness)
        assertEquals(ProofType.PHOTO, strict.proof)
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
            proof = ProofType.NONE,
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
        assertEquals(ProofType.NONE, m.proof)
        assertTrue(Validation.problems(m).isEmpty())
    }
}

package com.avlreminder.ui

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two taps within one frame both reach the click handler before the button is drawn disabled;
 * only [Busy.run] stops the second (V1.3c14 U3). The UI tests tap with a frame in between and see
 * the disabled button instead (mutant M5 stayed alive without this test).
 */
class BusyTest {
    @Test
    fun aSecondRunBeforeTheResultDoesNothing() {
        val busy = Busy(mutableStateOf(false))
        val started = mutableListOf<() -> Unit>()
        busy.run { started += it }
        busy.run { started += it }
        assertEquals(1, started.size)
        assertTrue(busy.busy)

        started.single()()
        assertFalse(busy.busy)
        busy.run { started += it }
        assertEquals(2, started.size)
    }
}

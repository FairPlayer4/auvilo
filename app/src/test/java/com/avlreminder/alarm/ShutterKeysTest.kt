package com.avlreminder.alarm

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** N2a: the volume keys release the proof camera's shutter, like the camera app. */
@RunWith(AndroidJUnit4::class)
class ShutterKeysTest {
    private var photos = 0
    private val keys = ShutterKeys().apply { shutter = { photos++ } }

    private fun down(code: Int, repeat: Int = 0) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat)
    private fun up(code: Int) = KeyEvent(KeyEvent.ACTION_UP, code)

    @Test
    fun bothVolumeKeysTakeAPhotoAndLeaveTheVolumeAlone() {
        assertTrue(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(keys.onKey(up(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_UP)))
        assertTrue(keys.onKey(up(KeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals(2, photos)
    }

    @Test
    fun holdingTheKeyTakesOnePhoto() {
        keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertTrue(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 1)))
        assertTrue(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 2)))
        assertEquals(1, photos)
    }

    // The photo is saved and the camera goes while the key is still held: the rest of that press
    // must not change the volume either (N2a, Review 2026-11-18).
    @Test
    fun aKeyHeldPastThePhotoStaysTakenUntilReleased() {
        assertTrue(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN)))
        keys.shutter = null
        assertTrue(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 1)))
        assertTrue(keys.onKey(up(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(1, photos)
        // The next press works as usual again.
        assertFalse(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertFalse(keys.onKey(up(KeyEvent.KEYCODE_VOLUME_DOWN)))
    }

    @Test
    fun withoutCameraTheKeysChangeTheVolume() {
        keys.shutter = null
        assertFalse(keys.onKey(down(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertFalse(keys.onKey(up(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(0, photos)
    }

    @Test
    fun otherKeysAreNotTaken() {
        assertFalse(keys.onKey(down(KeyEvent.KEYCODE_BACK)))
        assertFalse(keys.onKey(down(KeyEvent.KEYCODE_CAMERA)))
        assertEquals(0, photos)
    }
}

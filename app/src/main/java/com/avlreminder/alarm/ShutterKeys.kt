package com.avlreminder.alarm

import android.view.KeyEvent

/**
 * The volume keys release the proof camera's shutter, as in the camera app (Anf. 7.5 N2a).
 * [ProofCamera] sets [shutter] while it is on screen; otherwise the keys change the volume as usual.
 */
class ShutterKeys {
    var shutter: (() -> Unit)? = null

    /** The volume key that released the shutter, taken until it is let go (Review 2026-11-18). */
    private var held: Int? = null

    /**
     * True if the key was taken: both volume keys, while a shutter is set. Holding the key takes one
     * photo; a key held past the photo stays taken until it is let go, so the volume never changes.
     */
    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        // A fresh press is never the held one, even if its release got lost (focus changed).
        val fresh = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (fresh) held = null
        if (held == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) held = null
            return true
        }
        val shutter = shutter ?: return false
        if (fresh) {
            held = event.keyCode
            shutter()
        }
        return true
    }
}

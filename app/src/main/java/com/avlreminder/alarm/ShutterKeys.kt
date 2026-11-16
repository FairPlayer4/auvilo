package com.avlreminder.alarm

import android.view.KeyEvent

/**
 * The volume keys release the proof camera's shutter, as in the camera app (Anf. 7.5 N2a).
 * [ProofCamera] sets [shutter] while it is on screen; otherwise the keys change the volume as usual.
 */
class ShutterKeys {
    var shutter: (() -> Unit)? = null

    /** True if the key was taken: both volume keys, while a shutter is set. Holding the key takes one photo. */
    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        val shutter = shutter ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) shutter()
        return true
    }
}

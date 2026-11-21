package com.avlreminder.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The watch app's only screen (design.md 17): the running session with its answers, otherwise
 * what can be started, otherwise the next reminder. Opened from the launcher, the tile, the
 * ongoing activity or a check; [EXTRA_START] from the tile starts that session at once.
 */
class WatchActivity : ComponentActivity() {
    /** The session the tile's *Ich starte jetzt* asked for; taken once. */
    private val start = mutableStateOf<Long?>(null)

    // Without it the check does not vibrate on the watch and there is no ongoing activity; the
    // buttons work anyway and the phone still rings (design.md 17). Asked when the app opens without it.
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) start.value = intent.startId()
        setContent {
            WatchRoute(wear, start.value, onStarted = { start.value = null })
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && savedInstanceState == null) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.startId()?.let { start.value = it }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { runCatching { wear.hello() } }
    }

    private fun Intent.startId(): Long? = getLongExtra(EXTRA_START, NONE).takeIf { it != NONE }

    companion object {
        const val EXTRA_START = "com.avlreminder.wear.START"
        private const val NONE = -1L
    }
}

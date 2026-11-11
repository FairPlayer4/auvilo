package com.avlreminder.claude

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Der Provider der Schnittstelle für Claude (Debug-Build) antwortet nur Aufrufern mit DUMP,
 * also der adb-Shell. Das Manifest schützt call() nicht; die Prüfung steht im Provider
 * (Review-Lauf 2, Empfehlung zum Provider-Test).
 */
@RunWith(AndroidJUnit4::class)
class ClaudeProviderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val provider = Robolectric.setupContentProvider(ClaudeProvider::class.java)

    @Before
    fun setUp() {
        runBlocking { (app as AvlApp).started.join() }
    }

    // KRITISCH: Eine andere App ohne DUMP bekommt keine Antwort, auch nicht "ausgeschaltet".
    @Test
    fun `ohne DUMP wird der Aufruf abgelehnt`() {
        shadowOf(app).denyPermissions(DUMP)
        assertThrows(SecurityException::class.java) { provider.call("today", null, null) }
    }

    @Test
    fun `mit DUMP antwortet der Provider`() {
        shadowOf(app).grantPermissions(DUMP)
        assertNotNull(provider.call("today", null, null).getString("json"))
    }

    private companion object {
        const val DUMP = "android.permission.DUMP"
    }
}

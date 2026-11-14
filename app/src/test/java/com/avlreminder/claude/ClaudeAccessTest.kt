package com.avlreminder.claude

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Schnittstelle für Claude gibt es nur im Debug-Build und nur mit dem Schalter in den
 * Einstellungen (Anf. 7.26 AG11; Review-Lauf 2, Mutant M3).
 */
@RunWith(AndroidJUnit4::class)
class ClaudeAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph get() = (context as AvlApp).graph
    private val originalFlags = context.applicationInfo.flags


    @After
    fun tearDown() {
        context.applicationInfo.flags = originalFlags
        runBlocking { graph.settings.setClaudeAccess(false) }
    }

    private data class Fall(val name: String, val debuggable: Boolean, val schalter: Boolean, val offen: Boolean)

    // KRITISCH: Ein Release-Build bedient die Schnittstelle nie, auch mit eingeschaltetem Schalter.
    private val faelle = listOf(
        Fall("Debug-Build mit Schalter: offen", debuggable = true, schalter = true, offen = true),
        Fall("Debug-Build ohne Schalter: zu", debuggable = true, schalter = false, offen = false),
        Fall("Release-Build mit Schalter: zu", debuggable = false, schalter = true, offen = false),
        Fall("Release-Build ohne Schalter: zu", debuggable = false, schalter = false, offen = false),
    )

    @Test
    fun `die Schnittstelle ist nur im Debug-Build mit Schalter offen`() = runBlocking {
        for (f in faelle) {
            context.applicationInfo.flags = if (f.debuggable) {
                originalFlags or ApplicationInfo.FLAG_DEBUGGABLE
            } else {
                originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            }
            graph.settings.setClaudeAccess(f.schalter)
            assertEquals(f.name, f.offen, graph.enabled())
        }
    }
}

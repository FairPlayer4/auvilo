package com.avlreminder.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.core.rules.SnoozeHintChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/** What the view model says when the engine rejects something (Review-Lauf 1). */
@RunWith(AndroidJUnit4::class)
class AppViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        runBlocking { (app as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `eine abgelehnte Antwort auf die Hinweis-Karte sagt warum`() {
        val vm = AppViewModel(app)
        // The reminder is gone meanwhile (e.g. deleted on the other screen): the engine rejects.
        vm.answerSnoozeHint(999, SnoozeHintChoice.DISMISS)

        val deadline = System.currentTimeMillis() + 5_000
        while (ShadowToast.getTextOfLatestToast() == null && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertEquals("Nicht möglich: Reminder nicht gefunden", ShadowToast.getTextOfLatestToast())
    }
}

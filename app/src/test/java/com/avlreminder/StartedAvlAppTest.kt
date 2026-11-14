package com.avlreminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartedAvlAppTest {
    /** Robolectric tests get StartedAvlApp (robolectric.properties): its start is over when the test begins. */
    @Test
    fun theAppHasFinishedItsStartBeforeTheTest() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(app.javaClass.name, app is StartedAvlApp)
        assertTrue((app as AvlApp).started.isCompleted)
    }
}

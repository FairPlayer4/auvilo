package com.avlreminder.alarm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * N2a, Review 2026-11-18 (Frage 5): the volume keys release the shutter only while the preview is
 * really on screen; scrolled away under photo and text, "Leiser" must not take a photo unnoticed.
 */
@RunWith(AndroidJUnit4::class)
class ProofCameraVisibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun thePreviewCountsAsOnScreenOnlyWhileMostOfItIsVisible() {
        var visible: Boolean? = null
        compose.setContent {
            Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(300.dp).onMostlyVisible { visible = it })
                Box(Modifier.fillMaxWidth().height(1000.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).testTag("end"))
            }
        }
        compose.waitForIdle()
        assertTrue("preview on screen", visible == true)

        compose.onNodeWithTag("end").performScrollTo()
        compose.waitForIdle()
        assertFalse("scrolled away", visible == true)
    }
}

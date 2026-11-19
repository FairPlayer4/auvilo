package com.avlreminder.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.PlaceSetResult
import com.avlreminder.ui.screens.PlaceSetting
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Hier festlegen" (Anf. 7.29 OZ3) is locked while the position is determined (V1.3c14 U3,
 * Review-Lauf 6 E28 and Lauf 7 E31): a double tap made two places.
 */
@RunWith(AndroidJUnit4::class)
class PlaceSettingUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aDoubleTapDeterminesThePlaceOnceUntilTheResultIsThere() {
        val held = mutableListOf<(PlaceSetResult) -> Unit>()
        compose.setContent {
            AvlTheme(dark = false) { PlaceSetting(null, fixedName = "Zuhause", onSetPlace = { _, _, onResult -> held += onResult }) }
        }
        compose.onNodeWithTag("setPlaceNew").performClick()
        compose.onNodeWithTag("setPlaceNew").performClick()
        assertEquals(1, held.size)
        compose.onNodeWithText("Standort und WLAN werden bestimmt …").assertExists()
        compose.onNodeWithTag("setPlaceNew").assertIsNotEnabled()

        held.single()(PlaceSetResult.NoPosition)
        compose.onNodeWithText("Kein Standort – Berechtigung und GPS prüfen").assertExists()
        compose.onNodeWithTag("setPlaceNew").assertIsEnabled().performClick()
        assertEquals(2, held.size)
    }
}

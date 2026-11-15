package com.avlreminder.cloud

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** The system's screen lock prompt (backend SE5); the prompt itself is the system's. */
@RunWith(AndroidJUnit4::class)
class ScreenLockTest {
    // KRITISCH: without a screen lock there is nothing to confirm with; it says so without a prompt.
    @Test
    fun withoutAScreenLockItSaysSo() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
        assertEquals(ScreenLockAnswer.NO_LOCK, runBlocking { SystemScreenLock().confirm(activity, "Titel", "Text") })
    }

    // KRITISCH: only a missing screen lock lets the step go on; every other error of the prompt is a no.
    @Test
    fun onlyAMissingScreenLockLetsTheStepGoOn() {
        val no = ScreenLockAnswer.DECLINED
        val cases = listOf(
            Triple("keine Displaysperre", BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL, ScreenLockAnswer.NO_LOCK),
            Triple("abgebrochen", BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, no),
            Triple("vom System abgebrochen", BiometricPrompt.BIOMETRIC_ERROR_CANCELED, no),
            Triple("zu viele Fehlversuche", BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT, no),
            Triple("dauerhaft gesperrt", BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT, no),
            Triple("Zeit abgelaufen", BiometricPrompt.BIOMETRIC_ERROR_TIMEOUT, no),
            Triple("Sensor nicht verfügbar", BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE, no),
        )
        for ((case, code, want) in cases) {
            assertEquals(case, want, SystemScreenLock.answerFor(code))
        }
    }
}

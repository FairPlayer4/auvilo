package com.avlreminder.cloud

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** What the person answered to [ScreenLock.confirm]. */
enum class ScreenLockAnswer {
    /** Confirmed with fingerprint, face or the device PIN, pattern or password. */
    CONFIRMED,

    /** Cancelled, or too many wrong tries: nothing happens. */
    DECLINED,

    /** The phone has no screen lock; nothing to confirm with (see [CloudBackup]). */
    NO_LOCK,
}

/**
 * Asks for the screen lock before a destructive step (backend SE5): whoever gets hold of an
 * unlocked phone must not delete the backup with all its versions for good. Replaced in tests.
 */
interface ScreenLock {
    /** Shows the system's prompt with [title] and [text]; returns when the person answered. */
    suspend fun confirm(activity: Activity, title: String, text: String): ScreenLockAnswer
}

/**
 * [ScreenLock] through the system's BiometricPrompt: a strong biometric or the device credential
 * (PIN, pattern, password), so it works without a fingerprint too.
 */
class SystemScreenLock : ScreenLock {
    override suspend fun confirm(activity: Activity, title: String, text: String): ScreenLockAnswer {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) return ScreenLockAnswer.NO_LOCK
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { done ->
                val cancel = CancellationSignal()
                done.invokeOnCancellation { cancel.cancel() }
                // No negative button: with DEVICE_CREDENTIAL the system brings its own way out.
                BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setDescription(text)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    .build()
                    .authenticate(
                        cancel,
                        activity.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                if (done.isActive) done.resume(ScreenLockAnswer.CONFIRMED)
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                if (done.isActive) done.resume(answerFor(errorCode))
                            }
                            // A failed try (wrong finger) is no answer yet: the prompt stays open.
                        },
                    )
            }
        }
    }

    companion object {
        /**
         * KRITISCH: Only a missing screen lock lets the step go on; every other error (cancelled,
         * locked out after wrong tries, no hardware) is a no.
         */
        fun answerFor(errorCode: Int): ScreenLockAnswer =
            if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) ScreenLockAnswer.NO_LOCK else ScreenLockAnswer.DECLINED
    }
}

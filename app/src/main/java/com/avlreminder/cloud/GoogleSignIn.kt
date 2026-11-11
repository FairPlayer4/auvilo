package com.avlreminder.cloud

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Gets a Google ID token for [CloudConfig.WEB_CLIENT_ID]; replaced in tests. */
fun interface GoogleSignIn {
    /**
     * Shows Google's account picker. [nonce] binds the token to the device key
     * (`Anmeldung.versuch`, new for every sign-in), the server checks it. Throws when the person cancels
     * (`GetCredentialCancellationException`) or there is no account.
     */
    suspend fun idToken(activity: Activity, nonce: String): String
}

/**
 * "Sign in with Google" through Credential Manager (A-4). The ID token goes straight to the
 * server and is never stored or logged.
 */
class CredentialManagerSignIn(private val webClientId: String = CloudConfig.WEB_CLIENT_ID) : GoogleSignIn {
    override suspend fun idToken(activity: Activity, nonce: String): String {
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unerwartete Anmeldedaten von Google: ${credential.type}")
    }
}

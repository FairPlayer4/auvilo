package com.avlreminder.cloud

import com.avlreminder.BuildConfig

/**
 * Where the cloud backup lives. Both values come from the build (`avlCloudServerUrl`,
 * `avlCloudWebClientId`; app/build.gradle.kts, Anf. 7.34 OV1), not from the repo, so that
 * builds of others do not reach Kevin's server. Neither value is a secret: the address is
 * public, and client IDs are part of every app.
 */
object CloudConfig {
    /** Cloud Run service `server` (`tofu output server_url` in the backend); empty = none. */
    const val SERVER_URL = BuildConfig.CLOUD_SERVER_URL

    /**
     * The *web* OAuth client of the backend project. "Sign in with Google" on Android asks for an
     * ID token for this client (serverClientId), so it is the token's `aud` that the server checks
     * (`google_client_ids` in the backend). The Android client (package plus SHA-1 of the signing
     * key) only lets Google know the app; it does not appear here.
     */
    const val WEB_CLIENT_ID = BuildConfig.CLOUD_WEB_CLIENT_ID

    /** Built with a server: without one the app hides the cloud backup and does nothing in the cloud. */
    fun configured(serverUrl: String = SERVER_URL, webClientId: String = WEB_CLIENT_ID): Boolean =
        serverUrl.isNotEmpty() && webClientId.isNotEmpty()
}

package com.avlreminder.cloud

/**
 * Where the cloud backup lives (backend `../avlreminder-backend`, project avlreminder-exp-1ef2).
 * Neither value is a secret: the address is public, and client IDs are part of every app.
 */
object CloudConfig {
    /** Cloud Run service `server` (`tofu output server_url` in the backend). */
    const val SERVER_URL = "https://server-hm3oveusva-ew.a.run.app"

    /**
     * The *web* OAuth client of the backend project. "Sign in with Google" on Android asks for an
     * ID token for this client (serverClientId), so it is the token's `aud` that the server checks
     * (`google_client_ids` in the backend). The Android client (package plus SHA-1 of the signing
     * key) only lets Google know the app; it does not appear here.
     */
    const val WEB_CLIENT_ID = "82416816442-suvsga1qcl9aaj1u9rtdhbt91ekadh92.apps.googleusercontent.com"
}

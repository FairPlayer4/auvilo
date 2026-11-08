package com.avlreminder.cloud

import android.app.Activity
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential

/**
 * The phone's password service (Google Password Manager, Bitwarden, …) for the recovery key
 * (Anf. 7.33); replaced in tests. Both calls show a system dialog and throw when the person
 * declines or there is no service; the caller treats that as "not saved" or "not found".
 */
interface PasswordSafe {
    /** Offers to store [key] for [account]. */
    suspend fun save(activity: Activity, account: String, key: String)

    /** Lets the person pick a stored recovery key; null = the picked entry is not one. */
    suspend fun read(activity: Activity): String?
}

/** [PasswordSafe] through Credential Manager. The key goes only to the password service. */
class CredentialManagerSafe : PasswordSafe {
    override suspend fun save(activity: Activity, account: String, key: String) {
        CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(entryName(account), key))
    }

    override suspend fun read(activity: Activity): String? {
        val request = GetCredentialRequest.Builder().addCredentialOption(GetPasswordOption()).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        return (credential as? PasswordCredential)?.takeIf { it.id.startsWith(ENTRY) }?.password
    }

    companion object {
        const val ENTRY = "avlreminder Wiederherstellung"

        /** The name in the password service; the account pseudonym tells several apart. */
        fun entryName(account: String) = "$ENTRY ${account.take(8)}"
    }
}

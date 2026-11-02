package com.avlreminder.claude

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.avlreminder.graph
import com.avlreminder.system.Diag
import kotlinx.coroutines.runBlocking

/**
 * `adb shell content call --uri content://com.avlreminder.claude --method <m> [--arg '<json>']`;
 * the answer is the extra "json". Debug build only; the user switches it on in the settings.
 */
class ClaudeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = requireNotNull(context)
        // The manifest permission covers query/insert, not call(): check it here.
        context.enforceCallingPermission(DUMP, "Schnittstelle für Claude nur über adb")
        val answer = runBlocking { ClaudeApi(context.graph).call(method, arg) }
        Diag.log(ClaudeApi.logLine(method, arg, answer))
        return Bundle().apply { putString("json", answer) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val DUMP = "android.permission.DUMP"
    }
}

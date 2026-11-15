package com.avlreminder.system

import android.content.Intent
import androidx.core.net.toUri

/**
 * Feedback by e-mail (Anf. 7.28 PS15, playstore.md N3): the same public address as the store
 * listing and the privacy policy (Kevin, 2026-11-15). A fixed subject with the version lets
 * Gmail sort the mails into a label.
 */
object Contact {
    const val EMAIL = "kevin.bartik@gmail.com"

    fun subject(version: String) = "AuViLo-Reminder: Rückmeldung (Version $version)"

    /** Opens a mail app with address and subject; nothing is sent without the person. */
    fun mail(version: String): Intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject(version))

    /** Without a mail app the address is at least shown. */
    const val NO_MAIL_APP = "Keine Mail-App gefunden. Schreib an $EMAIL"
}

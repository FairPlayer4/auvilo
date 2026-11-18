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

    fun subject(version: String, topic: String = "Rückmeldung") = "AuViLo-Reminder: $topic (Version $version)"

    /** Opens a mail app with address and subject; nothing is sent without the person. */
    fun mail(version: String, topic: String = "Rückmeldung", body: String? = null): Intent =
        Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject(version, topic))
            .apply { body?.let { putExtra(Intent.EXTRA_TEXT, it) } }

    const val INVITATION_TOPIC = "Einladung zum Cloud-Backup"

    /**
     * Asking for access to the cloud backup (Anf. 7.28 PS16, playstore.md S2): the server lets in
     * only the accounts on its list, by their identifier (a pseudonym, no address, no name).
     */
    fun invitation(version: String, identifier: String?): Intent = mail(
        version, INVITATION_TOPIC,
        "Bitte schalte mein Google-Konto für das Cloud-Backup frei." + (identifier?.let { "\n\nKennung: $it" } ?: ""),
    )

    /** Without a mail app the address is at least shown. */
    const val NO_MAIL_APP = "Keine Mail-App gefunden. Schreib an $EMAIL"
}

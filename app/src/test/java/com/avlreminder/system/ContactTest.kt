package com.avlreminder.system

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Feedback by e-mail (Anf. 7.28 PS15): only a mail app, with address and a subject to filter by. */
@RunWith(AndroidJUnit4::class)
class ContactTest {
    @Test
    fun theMailGoesToThePublicAddressWithVersionInTheSubject() {
        val mail = Contact.mail("1.3c9-backup5")
        assertEquals("only mail apps, nothing is sent by itself", Intent.ACTION_SENDTO, mail.action)
        assertEquals("mailto:", mail.data.toString())
        assertArrayEquals(arrayOf("kevin.bartik@gmail.com"), mail.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("AuViLo-Reminder: Rückmeldung (Version 1.3c9-backup5)", mail.getStringExtra(Intent.EXTRA_SUBJECT))
        assertNull("feedback starts empty", mail.getStringExtra(Intent.EXTRA_TEXT))
    }

    // KRITISCH: the operator lets an account in by its identifier (Anf. 7.28 PS16); without it in
    // the mail the request could not be answered.
    @Test
    fun theInvitationMailCarriesTheIdentifier() {
        val mail = Contact.invitation("1.3c12", "49b0c816")
        assertEquals(Intent.ACTION_SENDTO, mail.action)
        assertArrayEquals(arrayOf("kevin.bartik@gmail.com"), mail.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("AuViLo-Reminder: Einladung zum Cloud-Backup (Version 1.3c12)", mail.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(mail.getStringExtra(Intent.EXTRA_TEXT).orEmpty().endsWith("Kennung: 49b0c816"))
        assertFalse(Contact.invitation("1.3c12", null).getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("Kennung"))
    }
}

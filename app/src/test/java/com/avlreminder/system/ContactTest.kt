package com.avlreminder.system

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    }
}

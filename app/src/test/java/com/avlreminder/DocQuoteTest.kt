package com.avlreminder

import com.avlreminder.ui.screens.LocationDisclosure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Texts of the app that the docs quote word for word must match the code (V1.3c14 U4): the Play
 * declarations show Google what the user sees, and "Doku widerspricht Code" came back in several
 * review runs (Review-Lauf 7: the location disclosure had to be fixed by hand). Whitespace and
 * line breaks do not count.
 */
class DocQuoteTest {
    private val docs = File("../docs")

    /** The quote (lines starting with "> ") that begins with [firstLine] in [file]. */
    private fun quote(file: String, firstLine: String): String {
        val lines = File(docs, file).readLines()
        val start = lines.indexOfFirst { it == "> $firstLine" }
        assertTrue("$file: no quote starting with „$firstLine“", start >= 0)
        return lines.drop(start).takeWhile { it.startsWith(">") }.joinToString(" ") { it.removePrefix(">") }.normalized()
    }

    private fun String.normalized() = trim().replace(Regex("""\s+"""), " ")

    @Test
    fun `der Standort-Hinweis in den Play-Erklaerungen ist der aus der App`() {
        val inApp = with(LocationDisclosure) { "**$TITLE** $TEXT [$CONTINUE] [$LATER]" }
        assertEquals(inApp.normalized(), quote("playstore-erklaerungen.md", "**${LocationDisclosure.TITLE}**"))
    }
}

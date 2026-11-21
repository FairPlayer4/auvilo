package com.avlreminder.core.usecases

import com.avlreminder.core.at
import com.avlreminder.core.rules.SessionAnswer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Die Nachrichten zwischen Handy und Uhr (design.md 17): hin und zurück gleich, Fremdes wird nicht gelesen. */
class WatchCodecTest {
    private val full = WatchState(
        sessions = listOf(
            WatchSession(
                7, "Training – Beine", at(26, 18, 2), at(26, 19, 32), 45, 10,
                listOf(SessionAnswer.DONE, SessionAnswer.STILL_ON, SessionAnswer.MINIMUM),
                final = false, proofOnPhone = false, minimumUsedUp = true,
            ),
            WatchSession(8, "Lernen", at(26, 18, 30), at(26, 20), null, null, listOf(SessionAnswer.STILL_ON), true, true, false),
        ),
        startable = listOf(WatchStart(9, "Aufräumen", at(26, 9), true), WatchStart(10, "Dehnen", at(26, 20), false)),
        next = WatchNext("Katzenbrunnen", at(27, 8)),
        pausedUntil = at(26, 23),
    )

    @Test
    fun `Zustand hin und zurueck`() {
        assertEquals(full, WatchCodec.decodeState(WatchCodec.encode(full)))
        assertEquals(WatchState(), WatchCodec.decodeState(WatchCodec.encode(WatchState())))
    }

    @Test
    fun `Befehl, Antwort, Nachfrage und Id hin und zurueck`() {
        WatchAction.entries.forEach { action ->
            val command = WatchCommand(123_456_789_012, 42, action)
            assertEquals(command, WatchCodec.decodeCommand(WatchCodec.encode(command)))
        }
        listOf(WatchResult(1, true), WatchResult(2, false, "Geht nicht mehr")).forEach {
            assertEquals(it, WatchCodec.decodeResult(WatchCodec.encode(it)))
        }
        val check = WatchCheck(7, "Training", audible = false, final = true)
        assertEquals(check, WatchCodec.decodeCheck(WatchCodec.encode(check)))
        assertEquals(7L, WatchCodec.decodeId(WatchCodec.encodeId(7)))
    }

    @Test
    fun `andere Version, abgeschnitten oder zu lang wird nicht gelesen`() {
        val bytes = WatchCodec.encode(full)
        assertNull(WatchCodec.decodeState(bytes.copyOf().also { it[0] = (WatchCodec.VERSION + 1).toByte() }))
        assertNull(WatchCodec.decodeState(bytes.copyOf(bytes.size - 1)))
        assertNull(WatchCodec.decodeState(bytes + 0))
        assertNull(WatchCodec.decodeState(ByteArray(0)))
        // A command is no state: the wrong message on a path is not read either.
        assertNull(WatchCodec.decodeState(WatchCodec.encode(WatchCommand(1, 2, WatchAction.START))))
    }

    @Test
    fun `unbekannte Aktion oder Antwort und unsinnige Laengen werden nicht gelesen`() {
        assertNull(WatchCodec.decodeCommand(message { writeLong(1); writeLong(2); writeUTF("SNOOZE") }))
        assertNull(WatchCodec.decodeState(message { writeLong(Long.MIN_VALUE); writeInt(-1) }))
        assertNull(WatchCodec.decodeState(message { writeLong(Long.MIN_VALUE); writeInt(Int.MAX_VALUE) }))
        val unknownAnswer = message {
            writeLong(Long.MIN_VALUE); writeInt(1)
            writeLong(7); writeUTF("T"); writeLong(0); writeLong(1); writeInt(-1); writeInt(-1)
            writeInt(1); writeUTF("PAUSE")
        }
        assertNull(WatchCodec.decodeState(unknownAnswer))
        // A session without its start is not ours.
        val noStart = message {
            writeLong(Long.MIN_VALUE); writeInt(1)
            writeLong(7); writeUTF("T"); writeLong(Long.MIN_VALUE)
        }
        assertNull(WatchCodec.decodeState(noStart))
    }

    @Test
    fun `das Format bleibt stabil`() {
        // A change to the bytes needs a new VERSION: phone and watch are installed together, but not at the same instant.
        assertArrayEquals(
            message { writeLong(5); writeBoolean(false); writeUTF("Nein") },
            WatchCodec.encode(WatchResult(5, false, "Nein")),
        )
    }

    private fun message(block: DataOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.writeByte(WatchCodec.VERSION); it.block() }
        return out.toByteArray()
    }
}

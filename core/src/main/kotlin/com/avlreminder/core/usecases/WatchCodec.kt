package com.avlreminder.core.usecases

import com.avlreminder.core.rules.SessionAnswer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.time.Instant

/**
 * The messages between phone and watch as bytes (design.md 17). Each starts with [VERSION]; a
 * message of another version, cut short or with an unknown value is not read (null), the watch
 * then asks again or shows the last state.
 */
object WatchCodec {
    /** Phone and watch are installed from the same build; a new field means a new version. */
    const val VERSION = 1

    fun encode(state: WatchState): ByteArray = write {
        writeInstant(state.pausedUntil)
        writeInt(state.sessions.size)
        state.sessions.forEach { s ->
            writeLong(s.occurrenceId)
            writeUTF(s.title)
            writeInstant(s.startedAt)
            writeInstant(s.maxAt)
            writeMinutes(s.goalMinutes)
            writeMinutes(s.minimumMinutes)
            writeInt(s.answers.size)
            s.answers.forEach { writeUTF(it.name) }
            writeBoolean(s.final)
            writeBoolean(s.proofOnPhone)
            writeBoolean(s.minimumUsedUp)
        }
        writeInt(state.startable.size)
        state.startable.forEach { s ->
            writeLong(s.occurrenceId)
            writeUTF(s.title)
            writeInstant(s.at)
            writeBoolean(s.ringing)
        }
        writeBoolean(state.next != null)
        state.next?.let { writeUTF(it.title); writeInstant(it.at) }
    }

    fun decodeState(bytes: ByteArray): WatchState? = read(bytes) {
        val pausedUntil = readInstant()
        val sessions = List(readCount()) {
            WatchSession(
                occurrenceId = readLong(),
                title = readUTF(),
                startedAt = readTime(),
                maxAt = readTime(),
                goalMinutes = readMinutes(),
                minimumMinutes = readMinutes(),
                answers = List(readCount()) { SessionAnswer.valueOf(readUTF()) },
                final = readBoolean(),
                proofOnPhone = readBoolean(),
                minimumUsedUp = readBoolean(),
            )
        }
        val startable = List(readCount()) { WatchStart(readLong(), readUTF(), readTime(), readBoolean()) }
        val next = if (readBoolean()) WatchNext(readUTF(), readTime()) else null
        WatchState(sessions, startable, next, pausedUntil)
    }

    fun encode(command: WatchCommand): ByteArray = write {
        writeLong(command.requestId)
        writeLong(command.occurrenceId)
        writeUTF(command.action.name)
    }

    fun decodeCommand(bytes: ByteArray): WatchCommand? = read(bytes) {
        WatchCommand(readLong(), readLong(), WatchAction.valueOf(readUTF()))
    }

    fun encode(result: WatchResult): ByteArray = write {
        writeLong(result.requestId)
        writeBoolean(result.ok)
        writeUTF(result.message.orEmpty())
    }

    fun decodeResult(bytes: ByteArray): WatchResult? = read(bytes) {
        WatchResult(readLong(), readBoolean(), readUTF().ifEmpty { null })
    }

    fun encode(check: WatchCheck): ByteArray = write {
        writeLong(check.occurrenceId)
        writeUTF(check.title)
        writeBoolean(check.audible)
        writeBoolean(check.final)
    }

    fun decodeCheck(bytes: ByteArray): WatchCheck? = read(bytes) {
        WatchCheck(readLong(), readUTF(), readBoolean(), readBoolean())
    }

    /** The end of a check ([Watch.PATH_CHECK_END]) and "hello" carry only an occurrence id or nothing. */
    fun encodeId(occurrenceId: Long): ByteArray = write { writeLong(occurrenceId) }

    fun decodeId(bytes: ByteArray): Long? = read(bytes) { readLong() }

    private fun write(block: DataOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.writeByte(VERSION); it.block() }
        return out.toByteArray()
    }

    /** Null for another version, a message cut short, an unknown value or bytes left over. */
    private fun <T> read(bytes: ByteArray, block: DataInputStream.() -> T): T? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readUnsignedByte() != VERSION) return null
            input.block().takeIf { input.read() == -1 }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun DataOutputStream.writeInstant(at: Instant?) = writeLong(at?.toEpochMilli() ?: NONE)

    private fun DataInputStream.readInstant(): Instant? = readLong().takeIf { it != NONE }?.let(Instant::ofEpochMilli)

    private fun DataInputStream.readTime(): Instant = requireNotNull(readInstant())

    private fun DataOutputStream.writeMinutes(minutes: Int?) = writeInt(minutes ?: -1)

    private fun DataInputStream.readMinutes(): Int? = readInt().takeIf { it >= 0 }

    /** A list length; a negative or absurd one means the bytes are not a message of ours. */
    private fun DataInputStream.readCount(): Int = readInt().also { require(it in 0..MAX_COUNT) }

    private const val NONE = Long.MIN_VALUE
    private const val MAX_COUNT = 1_000
}

package com.avlreminder.core.ports

import com.avlreminder.core.model.Event
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import java.time.Instant

/** Storage; the database behind it is the single source of truth. */
interface ReminderRepository {
    suspend fun reminders(): List<Reminder>
    suspend fun reminder(id: Long): Reminder?
    /** Inserts when id is 0, otherwise updates. Returns the id. */
    suspend fun saveReminder(reminder: Reminder): Long
    /** Deletes the reminder with its occurrences, events, proofs and prep answers. */
    suspend fun deleteReminder(id: Long)

    suspend fun occurrence(id: Long): Occurrence?
    suspend fun openOccurrences(): List<Occurrence>
    suspend fun openOccurrence(reminderId: Long): Occurrence?
    /** The most recently resolved occurrence of the reminder, if any. */
    suspend fun lastResolved(reminderId: Long): Occurrence?
    /** The most recent occurrence that was actually done (any [Resolution] of state DONE). */
    suspend fun lastDone(reminderId: Long): Occurrence?
    suspend fun countResolved(reminderId: Long, resolution: Resolution, from: Instant, until: Instant): Int
    suspend fun saveOccurrence(occurrence: Occurrence): Long

    suspend fun addEvent(event: Event)
    suspend fun addProof(proof: Proof)
    /** Text of the newest text proof of the reminder, if any. */
    suspend fun lastProofText(reminderId: Long): String?

    /** All answers ever given, oldest first; see [com.avlreminder.core.rules.Prep.latest]. */
    suspend fun prepAnswers(reminderId: Long): List<PrepAnswer>
    suspend fun addPrepAnswer(answer: PrepAnswer)
}

/** State of the phone that decides how loud an alarm may be. */
fun interface DeviceStatePort {
    /** "Do not disturb" in any form (priority only, alarms only, total silence). */
    fun isDoNotDisturb(): Boolean
}

enum class AlarmKind {
    /** Shown by the system as "next alarm"; most reliable (0 s delay in the spike). */
    ALARM_CLOCK,
    /** Exact but not shown as alarm clock; for relaxed reminders. */
    EXACT,
}

/** Derived from the database at any time; see SyncSchedule. */
interface AlarmPort {
    fun set(occurrenceId: Long, at: Instant, kind: AlarmKind)
    fun cancel(occurrenceId: Long)
}

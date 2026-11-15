package com.avlreminder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.PostponePeriod
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.WhenAway
import java.time.Instant
import java.time.LocalDateTime

// Tables of the database (Room entities); the model lives in `:core`.

enum class ScheduleType { ONCE, DAILY, WEEKDAYS, AFTER_DONE, ON_ARRIVAL, DEADLINE }

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val why: String?,
    val character: Character,
    val strictness: Strictness,
    val scheduleType: ScheduleType,
    /** Minutes after midnight; null for ONCE. For DEADLINE the start of the window. */
    val timeMinutes: Int?,
    /** ONCE: the time; AFTER_DONE: the first day; DEADLINE: the deadline. */
    val onceAt: LocalDateTime?,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val weekdays: Int,
    val intervalDays: Int?,
    val goal: String?,
    val minimum: String?,
    val minimumLimitPerWeek: Int?,
    /** Column from V1.0 (then NONE/CONFIRM/PHOTO); since version 2 the [ProofKind]. */
    @ColumnInfo(name = "proof") val proofKind: ProofKind,
    val postponeLimit: Int?,
    val active: Boolean,
    val createdAt: Instant,
    @ColumnInfo(defaultValue = "0") val proofPhoto: Boolean = false,
    val proofTextMinChars: Int? = null,
    val proofAudioMinSeconds: Int? = null,
    @ColumnInfo(defaultValue = "10") val proofWindowMinutes: Int = 10,
    @ColumnInfo(defaultValue = "0") val ignoreDnd: Boolean = false,
    /** Comma-separated minutes, e.g. "30,60". */
    @ColumnInfo(defaultValue = "10") val snoozeMinutes: String = "10",
    val motivationAudio: String? = null,
    /** Arrival trigger; both null = time only. */
    val arrivalPlaceId: Long? = null,
    val arrivalWhenAway: WhenAway? = null,
    /** Arrival window (Anf. 7.29): lead, buffer and check interval in minutes; F3 defaults. */
    @ColumnInfo(defaultValue = "90") val arrivalLead: Int = Arrival.DEFAULT_LEAD,
    @ColumnInfo(defaultValue = "5") val arrivalBuffer: Int = Arrival.DEFAULT_BUFFER,
    @ColumnInfo(defaultValue = "20") val arrivalCheck: Int = Arrival.DEFAULT_CHECK,
    val archivedAt: Instant? = null,
    /** Weekly goal; all three null = none. */
    val weekTarget: Int? = null,
    val weekMax: Int? = null,
    val weekMinimum: Int? = null,
    /** DEADLINE: end of the window (minutes after midnight) and the interval. */
    val windowUntilMinutes: Int? = null,
    val remindEveryMinutes: Int? = null,
    /** Notice before: the evening before at this time, or so many minutes before; both null = none. */
    val preNoticeEveningMinutes: Int? = null,
    val preNoticeBeforeMinutes: Int? = null,
    /** A preparation task for this reminder; no foreign key, the engine deletes tasks with it. */
    val prepFor: Long? = null,
    @ColumnInfo(defaultValue = "0") val solvedBlockerIsMinimum: Boolean = false,
    /** DAILY/WEEKDAYS: the times after [timeMinutes], e.g. "780,1140"; null = one time a day (VS1). */
    val moreTimes: String? = null,
    /** The snoozing hint (NT2) counts only occurrences due from here on. */
    val hintQuietUntil: Instant? = null,
    /** Session with timer (Anf. 7.11): the check interval; null = no session. The others only with it. */
    val sessionCheck: Int? = null,
    val sessionCheckStrictness: Strictness? = null,
    val sessionGoal: Int? = null,
    val sessionMinimum: Int? = null,
    val sessionMax: Int? = null,
    val sessionFinal: Int? = null,
    /** What [postponeLimit] counts over (Z2b); existing reminders per occurrence. */
    @ColumnInfo(defaultValue = "OCCURRENCE") val postponePeriod: PostponePeriod = PostponePeriod.OCCURRENCE,
)

@Entity(
    tableName = "blocker",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId")],
)
data class BlockerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val text: String,
    val createdAt: Instant,
    val solvedAt: Instant?,
    val origin: BlockerOrigin,
    /** The task reminder; no foreign key, the engine unlinks it. */
    val taskReminderId: Long?,
)

/**
 * A template (design 12.4 point 11): the settings of a reminder without title, why, answers,
 * audio and a concrete date. Same column meaning as in [ReminderEntity].
 */
@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val usedAt: Instant,
    val character: Character,
    val strictness: Strictness,
    val scheduleType: ScheduleType,
    val timeMinutes: Int?,
    val weekdays: Int,
    val intervalDays: Int?,
    val windowUntilMinutes: Int?,
    val remindEveryMinutes: Int?,
    val minimum: String?,
    val minimumLimitPerWeek: Int?,
    val proofKind: ProofKind,
    val proofPhoto: Boolean,
    val proofTextMinChars: Int?,
    val proofAudioMinSeconds: Int?,
    val proofWindowMinutes: Int,
    val postponeLimit: Int?,
    val ignoreDnd: Boolean,
    val snoozeMinutes: String,
    val moreTimes: String? = null,
    @ColumnInfo(defaultValue = "OCCURRENCE") val postponePeriod: PostponePeriod = PostponePeriod.OCCURRENCE,
)

@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int,
)

/**
 * A WLAN access point of a place (Anf. 7.29 OZ3). Private like a position: never in the
 * diagnostic log, replaced in the test data export.
 */
@Entity(
    tableName = "place_wifi",
    foreignKeys = [ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("placeId")],
)
data class PlaceWifiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val bssid: String,
    /** Only shown. */
    val ssid: String?,
)

@Entity(
    tableName = "checklist_item",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val position: Int,
    val title: String,
)

@Entity(
    tableName = "item_done",
    foreignKeys = [
        ForeignKey(OccurrenceEntity::class, ["id"], ["occurrenceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ChecklistItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("occurrenceId"), Index("itemId")],
)
data class ItemDoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val itemId: Long,
    val doneAt: Instant,
    val withProof: Boolean,
)

@Entity(
    tableName = "occurrence",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId"), Index("state")],
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val dueAt: Instant,
    val currentAt: Instant,
    val state: OccurrenceState,
    val postponeCount: Int,
    val resolvedAt: Instant?,
    val resolution: Resolution?,
    val note: String?,
    val pausedUntil: Instant? = null,
    val firstProgressAt: Instant? = null,
    val firedAt: Instant? = null,
    @ColumnInfo(defaultValue = "0") val awaitsArrival: Boolean = false,
    val arrivedAt: Instant? = null,
    /** The session (7.11): its start, the next check, the last reminder. */
    val startedAt: Instant? = null,
    val nextCheckAt: Instant? = null,
    val finalCallAt: Instant? = null,
)

@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(OccurrenceEntity::class, ["id"], ["occurrenceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("occurrenceId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val at: Instant,
    val type: EventType,
    val detail: String?,
)

@Entity(
    tableName = "proof",
    foreignKeys = [ForeignKey(OccurrenceEntity::class, ["id"], ["occurrenceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("occurrenceId")],
)
data class ProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val type: ProofType,
    @ColumnInfo(name = "filePath") val filePath: String?,
    val takenAt: Instant,
    val text: String? = null,
    /** Checklist item the proof is for; no foreign key, the proof stays when the item is removed. */
    val itemId: Long? = null,
)

@Entity(
    tableName = "prep_answer",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId")],
)
data class PrepAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val question: Int,
    val text: String?,
    val unknown: Boolean,
    val at: Instant,
)

// KRITISCH: every open state; a running session is open too (Anf. 7.11).

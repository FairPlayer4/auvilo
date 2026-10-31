package com.avlreminder.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SuggestionStatus { OPEN, DONE, DISCARDED }

/**
 * An improvement noticed while using the app (Anf. 7.24). No foreign key: the suggestion stays
 * when its reminder is deleted, the title is kept for that.
 */
@Entity(tableName = "suggestion")
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val important: Boolean,
    val createdAt: Instant,
    /** Where it was noted, e.g. "Heute" or "Alarm". */
    val screen: String,
    val reminderId: Long? = null,
    val reminderTitle: String? = null,
    val appVersion: String,
    val status: SuggestionStatus = SuggestionStatus.OPEN,
    /** When it was marked done or discarded. */
    val statusAt: Instant? = null,
)

@Dao
interface SuggestionDao {
    /** Open ones first; within that important ones, then the newest. */
    @Query("SELECT * FROM suggestion ORDER BY status = 'OPEN' DESC, important DESC, createdAt DESC, id DESC")
    fun observe(): Flow<List<SuggestionEntity>>

    @Insert
    suspend fun insert(suggestion: SuggestionEntity): Long

    @Update
    suspend fun update(suggestion: SuggestionEntity)

    @Query("DELETE FROM suggestion WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Plain text to take the suggestions into the planning (Anf. 7.24, VV5). */
object SuggestionExport {
    private val stamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /** Where a suggestion came from, e.g. "Alarm · „Training“ · 29.10.2026 14:03 · 1.2a-prototyp". */
    fun context(s: SuggestionEntity, zone: ZoneId): String = listOfNotNull(
        s.screen,
        s.reminderTitle?.let { "„$it“" },
        stamp.format(s.createdAt.atZone(zone)),
        s.appVersion,
    ).joinToString(" · ")

    /** The open ones as a Markdown list, important ones marked. */
    fun markdown(suggestions: List<SuggestionEntity>, zone: ZoneId): String {
        val open = suggestions.filter { it.status == SuggestionStatus.OPEN }
        return buildString {
            append("# Verbesserungsvorschläge avlreminder (${open.size} offen)\n\n")
            open.forEach { s ->
                append("- ")
                if (s.important) append("**wichtig:** ")
                append(s.text.trim().replace("\n", " "))
                append("\n  _").append(context(s, zone)).append("_\n")
            }
        }
    }
}

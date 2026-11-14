package com.avlreminder.cloud

import com.avlreminder.protokoll.Diagnose
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Level of a [CloudEvent] (formate.md, "Diagnose-Sendung"). */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** The fixed events of the backend logger; [area] and [id] are what the server's readers see. */
enum class CloudEvent(val area: String, val id: String, val level: LogLevel) {
    SWITCHED_ON("cloud", "eingeschaltet", LogLevel.INFO),
    SWITCHED_OFF("cloud", "ausgeschaltet", LogLevel.INFO),
    SIGNED_IN("cloud", "angemeldet", LogLevel.INFO),
    SET_UP("cloud", "eingerichtet", LogLevel.INFO),
    RESET("cloud", "zurueckgesetzt", LogLevel.INFO),
    SIGNED_OUT("cloud", "abgemeldet", LogLevel.INFO),
    FAILED("cloud", "fehlgeschlagen", LogLevel.WARN),
    BACKED_UP("sicherung", "gesichert", LogLevel.INFO),
    OTHER_DEVICE("sicherung", "anderes_geraet", LogLevel.WARN),
    STALE("sicherung", "veraltet", LogLevel.WARN),
    BRAKED("sicherung", "gebremst", LogLevel.WARN),
    CHECKED("sicherung", "geprueft", LogLevel.INFO),
    DELETED("sicherung", "geloescht", LogLevel.INFO),
    RESTORE_STAGED("wiederherstellen", "bereit", LogLevel.INFO),
    RESTORED("wiederherstellen", "getauscht", LogLevel.INFO),
    SWAP_FAILED("wiederherstellen", "fehlgeschlagen", LogLevel.ERROR),
    DIAGNOSIS_SENT("diagnose", "gesendet", LogLevel.INFO),
    ACCOUNT_DELETED("konto", "geloescht", LogLevel.INFO),
    DELETION_REQUESTED("konto", "loeschung_beantragt", LogLevel.INFO),
    DELETION_PENDING("konto", "loeschung_gemeldet", LogLevel.WARN),
    DELETION_CANCELLED("konto", "loeschung_abgebrochen", LogLevel.INFO),
}

/**
 * The backend logger (A-12, E-D-1): only what can be sent to the server as a diagnosis. Kept
 * apart from `diag.log`, which may hold titles.
 *
 * KRITISCH: No free text gets in. Field values are only numbers, booleans or enum constants
 * (anything else is refused), event names come from [CloudEvent], and of an exception only its
 * class and stack frames are kept, never its message.
 *
 * At most [max] events are kept (the newest), in `<dir>/events.jsonl`. Writing never throws: the
 * backup may fail, the logger may not break it.
 *
 * @param header the first line of a sending (formate.md): app, variant, Android, model, schema.
 */
class CloudLog(
    private val dir: File,
    private val header: () -> Map<String, Any>,
    private val clock: () -> Instant = Instant::now,
    private val max: Int = 500,
) {
    private val file get() = File(dir, "events.jsonl")

    @Synchronized
    fun record(event: CloudEvent, fields: Map<String, Any> = emptyMap(), error: Throwable? = null) {
        val line = JSONObject()
            .put("zeit", TIME.format(clock()))
            .put("stufe", event.level.name)
            .put("bereich", event.area)
            .put("ereignis", event.id)
        if (fields.isNotEmpty()) line.put("felder", JSONObject().apply { fields.forEach { (k, v) -> put(k, value(v)) } })
        if (error != null) line.put("ausnahme", exception(error))
        runCatching {
            dir.mkdirs()
            file.appendText(line.toString() + "\n")
            val lines = file.readLines()
            // Trimmed only now and then, so not every event rewrites the file.
            if (lines.size > max + max / 5) file.writeText(lines.takeLast(max).joinToString("\n", postfix = "\n"))
        }
    }

    /** A sending: the header with [cause], then the newest [max] events. */
    @Synchronized
    fun lines(cause: String = "knopf"): List<String> {
        val head = JSONObject().put("art", "kopf").put("format", Diagnose.FORMAT)
        header().forEach { (k, v) -> head.put(k, v) }
        head.put("anlass", cause)
        val events = runCatching { file.readLines().filter { it.isNotBlank() }.takeLast(max) }.getOrDefault(emptyList())
        return listOf(head.toString()) + events
    }

    private fun value(v: Any): Any = when (v) {
        is Int, is Long, is Boolean -> v
        is Enum<*> -> v.name
        else -> throw IllegalArgumentException("Feld der Diagnose ist weder Zahl noch Wahrheitswert noch Aufzählung")
    }

    private fun exception(e: Throwable) = JSONObject()
        .put("klasse", e.javaClass.name)
        .put("stapel", JSONArray(e.stackTrace.take(STACK).map { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }))

    companion object {
        private const val STACK = 20
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}

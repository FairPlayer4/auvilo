package com.avlreminder.restore

import com.avlreminder.cloud.BackupContents
import com.avlreminder.cloud.BackupSource
import com.avlreminder.cloud.BackupZip
import com.avlreminder.cloud.DamagedBackup
import com.avlreminder.cloud.CloudEvent
import com.avlreminder.cloud.CloudLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Where restoring on the phone stands (R-1, W-6). */
sealed interface LocalPhase {
    data object Idle : LocalPhase

    data class Busy(val what: String) : LocalPhase

    /** Asks before swapping in; [label] says what, [comparison] how it compares with now. */
    data class Ask(val kind: RestoreKind, val label: String, val comparison: Comparison) : LocalPhase
}

/**
 * [snapshots]: the daily copies (R-1); [undoUntil]: until when the last restore can be undone
 * (W-6), null = not; [restored]: this start swapped data in; [failed]: the last swap failed;
 * [exported]: name of the export file just written (PS14).
 */
data class LocalStatus(
    val phase: LocalPhase = LocalPhase.Idle,
    val snapshots: List<LocalDate> = emptyList(),
    val undoUntil: Instant? = null,
    val restored: RestoreKind? = null,
    val failed: SwapFailure? = null,
    val error: String? = null,
    val exported: String? = null,
)

/**
 * Restoring from a daily copy (R-1) or an export file (Anf. 7.28 PS14) and undoing a restore
 * (W-6), without the cloud. The same steps as from the cloud (W-5): check in its own file, show,
 * confirm, swap at the next start. Also writes the export file.
 *
 * @param currentSettings the settings now; a daily copy holds only the database, so they stay.
 * @param source what goes into an export file: the same ZIP as the cloud backup.
 */
class LocalRestore(
    private val target: RestoreTarget,
    private val files: RestoreFiles,
    private val snapshots: Snapshots,
    private val currentSettings: suspend () -> String,
    private val scope: CoroutineScope,
    private val events: CloudLog,
    private val log: (String) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val source: BackupSource = BackupSource { error("no backup source") },
    private val iterations: Int = ExportFile.ITERATIONS,
) {
    private val state = MutableStateFlow(LocalStatus())
    val status: StateFlow<LocalStatus> = state.asStateFlow()
    private val running = Mutex()
    @Volatile private var answer: CompletableDeferred<Boolean>? = null

    /** What this start did with a pending order ([DatabaseSwap.applyPending]), for the status. */
    fun started(outcome: SwapOutcome) {
        when (outcome) {
            SwapOutcome.None -> Unit
            is SwapOutcome.Swapped -> {
                state.update { it.copy(restored = outcome.order.kind) }
                events.record(CloudEvent.RESTORED, mapOf("art" to outcome.order.kind))
                log("restore: swapped in (${outcome.order.kind})")
            }
            is SwapOutcome.Failed -> {
                events.record(CloudEvent.SWAP_FAILED, mapOf("grund" to outcome.why))
                log("restore: swap failed (${outcome.why})")
            }
        }
    }

    fun load(): Job = work { }

    /**
     * Takes the daily copy (R-1) if today has none yet and shows it at once. Not inside [work]:
     * an open question must not hold the copy back.
     */
    fun takeSnapshot(): Boolean {
        val taken = snapshots.takeIfDue(LocalDate.ofInstant(clock(), zone()))
        if (taken) state.update { it.copy(snapshots = runCatching { snapshots.dates() }.getOrDefault(it.snapshots)) }
        return taken
    }

    fun fromSnapshot(date: LocalDate): Job = work {
        val file = snapshots.file(date)
        if (!file.exists()) return@work
        ask(RestoreKind.SNAPSHOT, "Kopie vom ${DAY.format(date)}") { BackupContents(file.readBytes(), currentSettings()) }
    }

    fun undo(): Job = work {
        val before = files.befores().firstOrNull() ?: return@work
        val json = File(before.path.removeSuffix(".db") + ".json")
        ask(RestoreKind.UNDO, "Stand vor dem Wiederherstellen") {
            BackupContents(before.readBytes(), if (json.exists()) json.readText() else currentSettings())
        }
    }

    /**
     * Writes the export file: the backup ZIP, encrypted with [password] ([ExportFile]). [write]
     * puts the bytes where the person chose (Android's file dialog).
     */
    fun export(name: String, password: CharArray, write: suspend (ByteArray) -> Unit): Job = work {
        state.update { it.copy(phase = LocalPhase.Busy("Verschlüsseln …"), exported = null) }
        try {
            val bytes = withContext(Dispatchers.IO) { ExportFile.encrypt(source.payload(), password, iterations) }
            withContext(Dispatchers.IO) { write(bytes) }
            log("export: written (${bytes.size} bytes)")
            state.update { it.copy(exported = name) }
        } finally {
            password.fill(' ')
        }
    }

    /** Restores from an export file; [read] gives its bytes. Asks like any restore. */
    fun fromFile(name: String, password: CharArray, read: suspend () -> ByteArray): Job = work {
        try {
            ask(RestoreKind.FILE, "Datei „$name“") { BackupZip.unpack(ExportFile.decrypt(read(), password)) }
        } finally {
            password.fill(' ')
        }
    }

    /** The answer to [LocalPhase.Ask]: true = swap in. */
    fun answer(yes: Boolean) {
        answer?.complete(yes)
    }

    fun dismiss() {
        files.failed.delete()
        state.update { it.copy(restored = null, failed = null, error = null, exported = null) }
    }

    private suspend fun ask(kind: RestoreKind, label: String, contents: suspend () -> BackupContents) {
        state.update { it.copy(phase = LocalPhase.Busy("Prüfen …"), restored = null) }
        var compared = false
        var staged = false
        try {
            val comparison = target.compare(withContext(Dispatchers.IO) { contents() })
            compared = true
            val yes = CompletableDeferred<Boolean>().also { answer = it }
            state.update { it.copy(phase = LocalPhase.Ask(kind, label, comparison)) }
            if (!yes.await()) return
            withContext(Dispatchers.IO) { target.stage(kind) }
            staged = true
            events.record(CloudEvent.RESTORE_STAGED, mapOf("art" to kind, "schema" to comparison.schema))
            log("restore: staged ($kind)")
            state.update { it.copy(phase = LocalPhase.Busy("Neu starten …")) }
            target.restart()
        } finally {
            // A failed or refused compare cleans up itself and must not touch another one's file.
            if (compared && !staged) target.discard()
        }
    }

    private fun work(block: suspend () -> Unit): Job {
        if (!running.tryLock()) return Job().apply { complete() }
        return scope.launch {
            var error: String? = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = describe(e)
                log("restore: failed (${e::class.simpleName})")
            } finally {
                answer = null
                running.unlock()
            }
            val failed = runCatching { failure() }.getOrNull()
            val dates = runCatching { snapshots.dates() }.getOrDefault(emptyList())
            val undo = runCatching { undoUntil() }.getOrNull()
            state.update { it.copy(phase = LocalPhase.Idle, snapshots = dates, undoUntil = undo, failed = failed, error = error) }
        }
    }

    private fun failure(): SwapFailure? {
        if (!files.failed.exists()) return null
        return runCatching { SwapFailure.valueOf(org.json.JSONObject(files.failed.readText()).getString("grund")) }
            .getOrDefault(SwapFailure.ORDER_UNREADABLE)
    }

    /** Until when the newest copy before a restore may be swapped back (W-6: 7 days). */
    private fun undoUntil(): Instant? {
        val newest = files.befores().firstOrNull() ?: return null
        val made = runCatching {
            LocalDateTime.parse(newest.name.removePrefix(RestoreFiles.BEFORE).removeSuffix(".db"), STAMP).atZone(zone()).toInstant()
        }.getOrNull() ?: return null
        val until = made + UNDO_FOR
        return if (until.isAfter(clock())) until else null
    }

    companion object {
        val UNDO_FOR: Duration = Duration.ofDays(7)
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        /** A short text for the person. */
        fun describe(e: Throwable): String = when (e) {
            is NewerDatabase -> "Die Kopie stammt von einer neueren App-Version."
            is WrongPassword -> "Passwort falsch oder Datei verändert. Nichts geändert."
            is NotAnExport -> "Das ist keine Sicherungsdatei von AuViLo-Reminder. Nichts geändert."
            is DamagedBackup -> "Die Sicherungsdatei ist beschädigt. Nichts geändert."
            is DamagedDatabase -> "Die Kopie ist beschädigt oder passt nicht zur App. Nichts geändert."
            is RestoreBusy -> "Es läuft schon eine Prüfung."
            else -> "Unerwarteter Fehler (${e::class.simpleName}). Nichts geändert."
        }
    }
}

/** Another check or restore uses the check file right now. */
class RestoreBusy : Exception("Es läuft schon eine Prüfung")

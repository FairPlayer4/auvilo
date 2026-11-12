package com.avlreminder.restore

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.avlreminder.data.AvlDatabase
import com.avlreminder.graph
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit

/**
 * Daily copies of the database on the phone (cloud-entscheidungen.md R-1): independent of
 * the cloud, the sign-in and the network. `backup/taeglich-<Datum>.db`; kept are the newest 7 and,
 * of the older ones, the newest of each of the last 4 weeks ([keep]).
 *
 * KRITISCH: Only files named `taeglich-*.db` are ever deleted here, never the copies before a
 * migration or a restore.
 */
class Snapshots(private val dir: File, private val copy: (File) -> Unit) {
    /** The dates there are copies of, newest first. */
    fun dates(): List<LocalDate> = dir.listFiles().orEmpty().mapNotNull { dateOf(it) }.sortedDescending()

    fun file(date: LocalDate) = File(dir, "$PREFIX$date.db")

    /** Takes today's copy unless there is one, then thins out the old ones. True = a new copy. */
    fun takeIfDue(today: LocalDate): Boolean {
        val target = file(today)
        val taken = if (target.exists()) {
            false
        } else {
            dir.mkdirs()
            val tmp = File(dir, "$PREFIX$today.db.tmp")
            tmp.delete()
            copy(tmp)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            true
        }
        val keep = keep(dates())
        dir.listFiles().orEmpty().forEach { f -> dateOf(f)?.let { if (it !in keep) f.delete() } }
        return taken
    }

    companion object {
        const val PREFIX = "taeglich-"
        private const val DAILY = 7
        private const val WEEKLY = 4

        private fun dateOf(f: File): LocalDate? {
            if (!f.name.startsWith(PREFIX) || !f.name.endsWith(".db")) return null
            return runCatching { LocalDate.parse(f.name.removePrefix(PREFIX).removeSuffix(".db")) }.getOrNull()
        }

        /** The newest [DAILY] dates, and of the rest the newest of each of the newest [WEEKLY] weeks. */
        fun keep(dates: List<LocalDate>): Set<LocalDate> {
            val sorted = dates.distinct().sortedDescending()
            val daily = sorted.take(DAILY)
            val weekly = sorted.drop(DAILY)
                .groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                .toSortedMap(compareByDescending { it })
                .values.take(WEEKLY).map { week -> week.max() }
            return (daily + weekly).toSet()
        }

        /** A copy of the running database ([AvlDatabase], `VACUUM INTO` also takes the WAL). */
        fun vacuumInto(db: AvlDatabase): (File) -> Unit = { target ->
            db.openHelper.writableDatabase.execSQL("VACUUM INTO '${target.path.replace("'", "''")}'")
        }
    }
}

/** Takes the daily copy (R-1); runs once a day without conditions, also without network. */
class SnapshotWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        applicationContext.graph.takeSnapshot()
        return Result.success()
    }

    companion object {
        const val NAME = "local-snapshot"

        /** Started at every app start; KEEP leaves the rhythm as it is. */
        fun schedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SnapshotWorker>(1, TimeUnit.DAYS).build(),
                )
            }
        }
    }
}

package com.avlreminder

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The engine's time in the glue tests (Review-Lauf 4, E15): Robolectric runs on the real time,
 * so a test at 23:10 would find occurrences already missed. [at] moves [SystemZoneClock] to a
 * fixed time of day; [reset] after each test.
 */
object TestClock {
    fun at(time: LocalTime = LocalTime.NOON) {
        SystemZoneClock.shift = Duration.ZERO
        SystemZoneClock.shift = Duration.between(Instant.now(), LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toInstant())
    }

    fun advance(by: Duration) {
        SystemZoneClock.shift = SystemZoneClock.shift.plus(by)
    }

    fun now(): Instant = SystemZoneClock.instant()

    fun time(): LocalTime = LocalTime.now(SystemZoneClock)

    fun reset() {
        SystemZoneClock.shift = Duration.ZERO
    }
}

/**
 * Waits until the work the receivers started in [AppGraph.scope] is done, instead of sleeping:
 * a negative test then knows that nothing happened, not that nothing happened yet.
 */
fun AppGraph.settle() = runBlocking {
    repeat(50) {
        ShadowLooper.idleMainLooper()
        val jobs = scope.coroutineContext[Job]!!.children.filter { it.isActive }.toList()
        if (jobs.isEmpty()) return@runBlocking
        jobs.joinAll()
    }
    error("receivers still busy")
}

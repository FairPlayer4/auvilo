package com.avlreminder.core.scenarios

import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.usecases.SaveResult
import java.time.LocalDateTime

/**
 * Engine cases whose outcome depends on how the repository stores (Review-Lauf 4, E14). They run
 * in core on the fake and in app on Room, so the fake cannot drift from Room unnoticed. Each
 * case throws an [IllegalStateException] when it does not hold. 2026-10-26 is a Monday.
 */
object RepositoryCases {
    val all: List<Pair<String, suspend (Simulation<out ReminderRepository>) -> Unit>> = listOf(
        "item checked, then removed from the list, the rest completes it" to ::removedItemTakesItsProgress,
        "undone after completion, only the completing item is open again" to ::undoKeepsTheItems,
        "deleted with an open occurrence, nothing is left" to ::deleteWithOpenOccurrence,
        "saving an unknown id creates nothing, foreign item ids are new" to ::unknownIdsAreNotTakenOver,
        "a session counts in the week of its start" to ::sessionCountsInItsStartWeek,
    )

    private fun list() = reminder().copy(
        title = "Brunnen",
        checklist = listOf(ChecklistItem(title = "Wohnzimmer"), ChecklistItem(title = "Küche"), ChecklistItem(title = "Bad")),
        proof = ProofSpec(ProofKind.EVIDENCE, textMinChars = 5),
    )

    private fun saved(result: SaveResult) = (result as SaveResult.Saved).id

    /**
     * The first item checked without the asked proof, then taken off the list: with it goes its
     * progress, so the other two with proof complete the occurrence as a goal, not as "no proof".
     */
    suspend fun removedItemTakesItsProgress(sim: Simulation<out ReminderRepository>) {
        val id = sim.create(list())
        val (a, b, c) = sim.repo.reminder(id)!!.checklist
        sim.act(id, at(26, 18, 1)) { Action.CheckItem(a.id) }
        val o = sim.open(id).id
        check(sim.engine.save(sim.repo.reminder(id)!!.copy(checklist = listOf(b, c))) is SaveResult.Saved)
        check(sim.repo.itemsDone(o).isEmpty()) { "progress of the removed item kept: ${sim.repo.itemsDone(o)}" }

        sim.act(id, at(26, 18, 5)) { Action.CheckItem(b.id, Evidence(text = "Küche sauber")) }
        val done = sim.act(id, at(26, 18, 6)) { Action.CheckItem(c.id, Evidence(text = "Bad sauber")) }
        check(done.state == OccurrenceState.DONE && done.resolution == Resolution.GOAL) { "completed as ${done.state} ${done.resolution}" }
        check(sim.repo.itemsDone(o).map { it.itemId } == listOf(b.id, c.id)) { "items done: ${sim.repo.itemsDone(o)}" }
    }

    /**
     * "Zurücknehmen" (7.31): the occurrence is open again; the item that completed it is open
     * again too, the ones before stay checked.
     */
    suspend fun undoKeepsTheItems(sim: Simulation<out ReminderRepository>) {
        val id = sim.create(list())
        val items = sim.repo.reminder(id)!!.checklist
        items.forEachIndexed { i, item -> sim.act(id, at(26, 18, 1 + i)) { Action.CheckItem(item.id, Evidence(text = "erledigt $i")) } }
        val done = sim.repo.lastResolved(id)!!
        check(done.resolution == Resolution.GOAL) { "not completed: $done" }

        sim.runUntil(at(26, 18, 10))
        check(sim.engine.reopen(done.id) is com.avlreminder.core.rules.TransitionResult.Ok) { "undo rejected" }
        sim.checkInvariants()
        val open = sim.open(id)
        check(open.id == done.id) { "another occurrence is open: $open" }
        check(sim.repo.openOccurrences().count { it.reminderId == id } == 1) { "more than one open occurrence" }
        check(sim.repo.itemsDone(open.id).map { it.itemId } == items.dropLast(1).map { it.id }) { "items done: ${sim.repo.itemsDone(open.id)}" }
    }

    suspend fun deleteWithOpenOccurrence(sim: Simulation<out ReminderRepository>) {
        val id = sim.create(list())
        val first = sim.repo.reminder(id)!!.checklist.first()
        sim.act(id, at(26, 18, 1)) { Action.CheckItem(first.id) }
        val o = sim.open(id).id

        sim.engine.delete(id)
        sim.checkInvariants()

        check(sim.repo.reminder(id) == null) { "reminder still there" }
        check(sim.repo.occurrence(o) == null) { "occurrence still there" }
        check(sim.repo.itemsDone(o).isEmpty()) { "items done still there" }
        check(o !in sim.alarms.set) { "alarm still set" }
    }

    suspend fun unknownIdsAreNotTakenOver(sim: Simulation<out ReminderRepository>) {
        val id = sim.create(list())
        val other = sim.create(list().copy(title = "Fenster"))
        val foreign = sim.repo.reminder(other)!!.checklist.first()

        check(sim.repo.saveReminder(list().copy(id = 999)) == 999L)
        check(sim.repo.reminder(999) == null) { "unknown id was created" }

        val mine = sim.repo.reminder(id)!!
        saved(sim.engine.save(mine.copy(checklist = mine.checklist + foreign)))
        val items = sim.repo.reminder(id)!!.checklist
        check(items.size == 4 && items.last().id != foreign.id) { "foreign item id taken over: $items" }
        check(sim.repo.reminder(other)!!.checklist.first() == foreign) { "the other list changed" }
    }

    /**
     * Review-Lauf 4, Frage 4: started Sunday 23:20, answered Monday 0:10. The repository counts
     * it in the week of the start (`countedAt`), which the weekly limit and goal read.
     */
    suspend fun sessionCountsInItsStartWeek(sim: Simulation<out ReminderRepository>) {
        val id = sim.create(
            reminder(Schedule.Once(LocalDateTime.of(2026, 11, 1, 23, 0))).copy(session = SessionSpec(minimumMinutes = 20)),
        )
        val o = sim.open(id)
        sim.clock.now = o.dueAt
        checkNotNull(sim.engine.onAlarm(o.id)) { "does not ring" }
        sim.clock.now = at(1, 23, 20, month = 11)
        check(sim.engine.start(o.id) is com.avlreminder.core.rules.TransitionResult.Ok) { "start rejected" }
        sim.clock.now = at(2, 0, 10, month = 11)
        check(sim.engine.act(o.id, Action.Complete(minimumOnly = false)) is com.avlreminder.core.rules.TransitionResult.Ok) { "answer rejected" }

        val startWeek = sim.repo.countResolved(id, Resolution.GOAL, at(26, 0), at(2, 0, month = 11))
        val nextWeek = sim.repo.countResolved(id, Resolution.GOAL, at(2, 0, month = 11), at(9, 0, month = 11))
        check(startWeek == 1 && nextWeek == 0) { "counted $startWeek in the start week, $nextWeek in the next" }
    }
}

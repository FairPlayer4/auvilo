package com.avlreminder.core.usecases

import com.avlreminder.core.model.Character
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.severalADay
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.Blockers
import com.avlreminder.core.rules.Checklists
import com.avlreminder.core.rules.Deadlines
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.WeekStatus
import com.avlreminder.core.rules.WeeklyGoals
import com.avlreminder.core.rules.Weeks
import java.time.Instant
import java.time.LocalDate

/** Reads what applies to a reminder now: limits, the week, how strict, and what the alarm shows. */
internal class AlarmViews(private val c: EngineContext) {
    private val repo get() = c.repo
    private val zone get() = c.zone
    private fun now() = c.now()

    /** Limits that apply to [reminder] right now; with [occurrence] also its checklist progress on [day]. */
    suspend fun limits(reminder: Reminder, occurrence: Occurrence?, day: LocalDate): Limits {
        val (from, until) = Weeks.range(now(), zone)
        val done = occurrence?.let { checklistDone(reminder, it) }.orEmpty()
        val deadline = (reminder.schedule as? Schedule.Deadline)
            ?.takeIf { d -> occurrence == null || !Deadlines.isOverdue(d, occurrence, zone) }
            ?.let { Deadlines.dueAt(it, zone) }
        val since = repo.lastResolved(reminder.id)?.resolvedAt ?: reminder.createdAt
        // Several a day: a day with the minimum counts once (VS1).
        val minimumDays = if (!reminder.schedule.severalADay) null else
            repo.recentResolved(reminder.id, MINIMUM_HISTORY)
                .filter { o -> o.resolution == Resolution.MINIMUM && o.resolvedAt.let { it != null && it >= from && it < until } }
                .map { it.resolvedAt!!.atZone(zone).toLocalDate() }.toSet()
        return Limits(
            minimumsThisWeek = minimumDays?.size ?: repo.countResolved(reminder.id, Resolution.MINIMUM, from, until),
            minimumToday = minimumDays?.contains(now().atZone(zone).toLocalDate()) == true,
            lastProofText = repo.lastProofText(reminder.id),
            itemsDone = done.associate { it.itemId to it.withProof },
            itemDoneToday = done.any { it.doneAt.atZone(zone).toLocalDate() == day },
            deadline = deadline,
            blockerSolved = reminder.solvedBlockerIsMinimum && Blockers.solvedSince(repo.blockers(reminder.id), since),
            nextTime = occurrence?.let { ScheduleRules.nextTime(reminder.schedule, it.dueAt, zone) },
        )
    }

    suspend fun limits(reminder: Reminder, occurrence: Occurrence? = null): Limits =
        limits(reminder, occurrence, now().atZone(zone).toLocalDate())

    /**
     * The items of [occurrence]'s list that are done: in the occurrence; several times a day
     * since the list was last complete, over all times (VS1).
     */
    private suspend fun checklistDone(reminder: Reminder, occurrence: Occurrence): List<ItemDone> = when {
        reminder.checklist.isEmpty() -> emptyList()
        reminder.schedule.severalADay -> Checklists.round(reminder.checklist, repo.itemsDoneOfReminder(reminder.id))
        else -> repo.itemsDone(occurrence.id)
    }

    /** The week of [day] for a reminder with a weekly goal; offers counted from [day] on. */
    suspend fun weekStatus(reminder: Reminder, day: LocalDate = now().atZone(zone).toLocalDate()): WeekStatus? {
        val goal = reminder.weeklyGoal ?: return null
        val (from, until) = Weeks.range(day, zone)
        val goals = repo.countResolved(reminder.id, Resolution.GOAL, from, until)
        val minimums = repo.countResolved(reminder.id, Resolution.MINIMUM, from, until)
        return WeekStatus(goal, goals + minimums, minimums, WeeklyGoals.offersLeft(reminder.schedule, day))
    }

    fun isRescue(reminder: Reminder, occurrence: Occurrence): Boolean =
        reminder.weeklyGoal != null && WeeklyGoals.isRescueDay(reminder.schedule, occurrence.dueAt.atZone(zone).toLocalDate())

    /** How strict the occurrence rings with its weekly goal; null = silently, without rounds. */
    suspend fun strictness(reminder: Reminder, occurrence: Occurrence): Strictness? {
        (reminder.schedule as? Schedule.Deadline)?.let { d ->
            // The last window day and overdue ring one step stricter.
            val last = Deadlines.isOverdue(d, occurrence, zone) ||
                occurrence.currentAt.atZone(zone).toLocalDate() == Deadlines.lastWindowDay(d, zone)
            return if (last) stricter(reminder.strictness) else reminder.strictness
        }
        val week = weekStatus(reminder, occurrence.dueAt.atZone(zone).toLocalDate()) ?: return reminder.strictness
        return WeeklyGoals.strictness(reminder.strictness, week, isRescue(reminder, occurrence))
    }

    private fun stricter(s: Strictness) = when (s) {
        Strictness.RELAXED -> Strictness.NORMAL
        Strictness.NORMAL, Strictness.STRICT -> Strictness.STRICT
    }

    /**
     * When an unanswered occurrence counts as missed: the end of the day, or of a deadline's window.
     * Several times a day: at the latest when the next time starts (VS1).
     */
    fun missedAt(reminder: Reminder, occurrence: Occurrence): Instant {
        val dayEnd = Escalation.missedAt(occurrence.currentAt, zone, c.settings())
        val d = reminder.schedule as? Schedule.Deadline
            ?: return ScheduleRules.nextTime(reminder.schedule, occurrence.dueAt, zone)?.let { minOf(it, dayEnd) } ?: dayEnd
        val end = Deadlines.windowEnd(d, occurrence.currentAt, zone)
        return if (Deadlines.isOverdue(d, occurrence, zone)) end else minOf(end, Deadlines.dueAt(d, zone))
    }

    /** The reminder's snooze buttons that do not pass the end of the day. */
    fun snoozeOptions(reminder: Reminder, occurrence: Occurrence): List<Int> =
        Escalation.snoozeOptions(reminder.snoozeMinutes, now(), missedAt(reminder, occurrence))

    /** Current "Startklar" answers, the newest per question. */
    suspend fun prep(reminderId: Long): Map<Int, PrepAnswer> = Prep.latest(repo.prepAnswers(reminderId))

    /** "Auf morgen" for a deadline; null if it is no deadline or no window before it is left. */
    fun tomorrow(reminder: Reminder, occurrence: Occurrence): Instant? {
        val d = reminder.schedule as? Schedule.Deadline ?: return null
        return Deadlines.tomorrow(d, now(), zone, Deadlines.isOverdue(d, occurrence, zone))
    }

    /** It got stuck [com.avlreminder.core.rules.AlertSettings.askBlockerAfter] times in a row and was not asked yet (S8). */
    suspend fun askBlocker(reminder: Reminder, occurrence: Occurrence): Boolean {
        if (reminder.character != Character.OVERCOME || reminder.prepFor != null) return false
        if (repo.events(occurrence.id).any { it.type == EventType.BLOCKER_ASKED }) return false
        val history = listOf(occurrence) + repo.recentResolved(reminder.id, ASK_HISTORY)
        val streak = Blockers.stuckStreak(history.map { o -> o to repo.events(o.id).count { it.type == EventType.SNOOZED } })
        return streak >= c.settings().askBlockerAfter
    }

    /** What the alarm shows and allows; [early] = opened before it is due ("vorab erledigen"). */
    suspend fun alarmView(reminder: Reminder, occurrence: Occurrence, early: Boolean = false): AlarmView {
        val limits = limits(reminder, occurrence)
        return AlarmView(
            reminder = reminder,
            occurrence = occurrence,
            minimumAllowed = Lifecycle.canUseMinimum(reminder, limits),
            minimumsLeft = Lifecycle.minimumsLeft(reminder, limits),
            canPostpone = Lifecycle.canPostpone(reminder, occurrence),
            postponesLeft = Lifecycle.postponesLeft(reminder, occurrence),
            snooze = snoozeOptions(reminder, occurrence),
            prep = prep(reminder.id),
            lastProofText = limits.lastProofText,
            early = early,
            done = limits.itemsDone.keys,
            // Doing an item today makes "Rest auf morgen" free; otherwise it is a postponement.
            canDeferRest = reminder.checklist.isNotEmpty() && limits.nextTime == null &&
                (limits.itemDoneToday || Lifecycle.canPostpone(reminder, occurrence)),
            week = weekStatus(reminder, occurrence.dueAt.atZone(zone).toLocalDate()),
            blockerSolved = Lifecycle.canUseSolvedBlocker(reminder, limits),
            askBlocker = !early && askBlocker(reminder, occurrence),
            tomorrow = if (early) null else tomorrow(reminder, occurrence),
            deadline = limits.deadline,
            nextTime = limits.nextTime,
            blockers = repo.blockers(reminder.id).filter { it.isOpen },
        )
    }

    /** The occurrence rings now: how loud, with what it shows; it rings once the command is stored. */
    suspend fun ring(reminder: Reminder, occurrence: Occurrence): Ring {
        val strictness = strictness(reminder, occurrence)
        val now = now()
        val level = if (strictness == null || c.settings().alarmsPaused(now)) AlarmLevel.SILENT else Escalation.level(
            strictness, now.atZone(zone).toLocalTime(), c.settings().on(now.atZone(zone).toLocalDate()),
            c.device.isDoNotDisturb(), reminder.ignoreDnd,
        )
        return Ring(alarmView(reminder, occurrence), level).also { c.pending += it }
    }

    companion object {
        /** Resolved occurrences looked at for the stuck streak (S8). */
        private const val ASK_HISTORY = 10
        /** Enough resolved occurrences for a week of six times a day. */
        private const val MINIMUM_HISTORY = 60
    }
}

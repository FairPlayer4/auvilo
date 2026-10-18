package com.avlreminder.spike

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/**
 * Schedules alarms with AlarmManager.setAlarmClock(), the most reliable exact alarm
 * (allowed in Doze, shown as the next alarm in the status bar).
 * The test plan and the quick test are chains: each fired alarm schedules the next one.
 */
object AlarmScheduler {
    const val EXTRA_PLANNED_AT = "planned_at"
    const val EXTRA_LABEL = "label"
    const val EXTRA_MODE = "mode"
    const val EXTRA_HOME_CHECK = "home_check"
    const val EXTRA_FROM_PLAN = "from_plan"
    const val EXTRA_QUICK_INDEX = "quick_index"

    private const val REQUEST_PLAN = 1
    private const val REQUEST_TEST = 2
    private const val REQUEST_QUICK = 3

    private const val PREFS = "scheduler"
    private const val KEY_PLAN_ACTIVE = "plan_active"
    private const val KEY_PLAN_NEXT_AT = "plan_next_at"
    private const val KEY_PLAN_NEXT_LABEL = "plan_next_label"
    private const val KEY_QUICK_ACTIVE = "quick_active"
    private const val KEY_QUICK_INDEX = "quick_index"
    private const val KEY_QUICK_NEXT_AT = "quick_next_at"
    private const val KEY_QUICK_END = "quick_end"

    /** Tolerance before a scheduled alarm that has not fired is reported as lost. */
    private const val MISSED_AFTER_MILLIS = 2 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPlanActive(context: Context) = prefs(context).getBoolean(KEY_PLAN_ACTIVE, false)

    fun nextPlanAlarm(context: Context): String? {
        val p = prefs(context)
        val at = p.getLong(KEY_PLAN_NEXT_AT, 0)
        return if (isPlanActive(context) && at > 0) "${SpikeLog.formatTime(at)} – ${p.getString(KEY_PLAN_NEXT_LABEL, "")}" else null
    }

    fun startPlan(context: Context) {
        prefs(context).edit().putBoolean(KEY_PLAN_ACTIVE, true).apply()
        SpikeLog.log(context, "Testplan gestartet")
        scheduleNextPlanSlot(context)
    }

    fun stopPlan(context: Context) {
        prefs(context).edit().putBoolean(KEY_PLAN_ACTIVE, false).remove(KEY_PLAN_NEXT_AT).apply()
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, REQUEST_PLAN, Intent()))
        SpikeLog.log(context, "Testplan gestoppt")
    }

    fun scheduleNextPlanSlot(context: Context, quiet: Boolean = false) {
        if (!isPlanActive(context)) return
        val (slot, at) = TestPlan.next(ZonedDateTime.now())
        val atMillis = at.toInstant().toEpochMilli()
        val intent = Intent()
            .putExtra(EXTRA_PLANNED_AT, atMillis)
            .putExtra(EXTRA_LABEL, slot.label)
            .putExtra(EXTRA_HOME_CHECK, slot.homeCheck)
            .putExtra(EXTRA_FROM_PLAN, true)
        setAlarmClock(context, REQUEST_PLAN, atMillis, intent)
        prefs(context).edit()
            .putLong(KEY_PLAN_NEXT_AT, atMillis)
            .putString(KEY_PLAN_NEXT_LABEL, slot.label)
            .apply()
        if (!quiet) SpikeLog.log(context, "Nächster Plan-Alarm: ${SpikeLog.formatTime(atMillis)} (${slot.label})")
    }

    /** One-off alarm for manual tests and snooze; the mode is fixed instead of derived from the time. */
    fun scheduleTest(context: Context, delaySeconds: Long, mode: AlarmMode, label: String) {
        val atMillis = System.currentTimeMillis() + delaySeconds * 1000
        val intent = Intent()
            .putExtra(EXTRA_PLANNED_AT, atMillis)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_MODE, mode.name)
        setAlarmClock(context, REQUEST_TEST, atMillis, intent)
        SpikeLog.log(context, "Test-Alarm geplant: ${SpikeLog.formatTime(atMillis)} ($label, ${mode.label})")
    }

    // --- One-hour quick test: own chain on a fixed 5-minute grid ---

    fun isQuickActive(context: Context) = prefs(context).getBoolean(KEY_QUICK_ACTIVE, false)

    fun quickStatus(context: Context): String? {
        if (!isQuickActive(context)) return null
        val p = prefs(context)
        val total = QuickTest.DURATION_MINUTES / QuickTest.INTERVAL_MINUTES
        return "Alarm ${p.getInt(KEY_QUICK_INDEX, 0) + 1}/$total um ${SpikeLog.formatTime(p.getLong(KEY_QUICK_NEXT_AT, 0))}, " +
            "Ende ${SpikeLog.formatTime(p.getLong(KEY_QUICK_END, 0))}"
    }

    fun startQuickTest(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putBoolean(KEY_QUICK_ACTIVE, true)
            .putLong(KEY_QUICK_END, now + QuickTest.DURATION_MINUTES * 60_000)
            .apply()
        SpikeLog.log(context, "Schnelltest gestartet (${QuickTest.DURATION_MINUTES} min, alle ${QuickTest.INTERVAL_MINUTES} min) | ${SpikeLog.deviceState(context)}")
        scheduleQuick(context, 0, now + QuickTest.INTERVAL_MINUTES * 60_000)
    }

    fun stopQuickTest(context: Context, reason: String) {
        prefs(context).edit().putBoolean(KEY_QUICK_ACTIVE, false).remove(KEY_QUICK_NEXT_AT).apply()
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, REQUEST_QUICK, Intent()))
        SpikeLog.log(context, "Schnelltest beendet ($reason)")
    }

    /** Next grid point after the alarm that just fired, so delays do not add up. */
    fun onQuickAlarmFired(context: Context, index: Int, plannedAt: Long) {
        if (!isQuickActive(context)) return
        val nextAt = maxOf(plannedAt + QuickTest.INTERVAL_MINUTES * 60_000, System.currentTimeMillis() + 30_000)
        scheduleQuick(context, index + 1, nextAt)
    }

    private fun scheduleQuick(context: Context, index: Int, atMillis: Long, quiet: Boolean = false) {
        if (atMillis > prefs(context).getLong(KEY_QUICK_END, 0)) {
            stopQuickTest(context, "Zeit abgelaufen")
            return
        }
        val mode = QuickTest.modeFor(index)
        val intent = Intent()
            .putExtra(EXTRA_PLANNED_AT, atMillis)
            .putExtra(EXTRA_LABEL, "Schnelltest ${index + 1}")
            .putExtra(EXTRA_MODE, mode.name)
            .putExtra(EXTRA_HOME_CHECK, true)
            .putExtra(EXTRA_QUICK_INDEX, index)
        setAlarmClock(context, REQUEST_QUICK, atMillis, intent)
        prefs(context).edit().putInt(KEY_QUICK_INDEX, index).putLong(KEY_QUICK_NEXT_AT, atMillis).apply()
        if (!quiet) SpikeLog.log(context, "Nächster Schnelltest-Alarm ${index + 1}: ${SpikeLog.formatTime(atMillis)} (${mode.name})")
    }

    /**
     * Detects alarms that never fired (e.g. killed by battery management or lost across
     * a reboot) and registers the chains again. Called on app start and after boot.
     */
    fun checkMissed(context: Context, reason: String) {
        val p = prefs(context)
        val now = System.currentTimeMillis()

        if (isPlanActive(context)) {
            val at = p.getLong(KEY_PLAN_NEXT_AT, 0)
            val missed = at > 0 && now - at > MISSED_AFTER_MILLIS
            if (missed) {
                SpikeLog.log(context, "VERPASST: Plan-Alarm ${SpikeLog.formatTime(at)} ist nicht ausgelöst worden (bemerkt: $reason)")
            }
            // Re-register in any case: after a reboot or force stop all AlarmManager alarms are gone.
            scheduleNextPlanSlot(context, quiet = !missed)
        }

        if (isQuickActive(context)) {
            val at = p.getLong(KEY_QUICK_NEXT_AT, 0)
            val index = p.getInt(KEY_QUICK_INDEX, 0)
            if (now - at > MISSED_AFTER_MILLIS) {
                SpikeLog.log(context, "VERPASST: Schnelltest-Alarm ${index + 1} (${SpikeLog.formatTime(at)}) ist nicht ausgelöst worden (bemerkt: $reason)")
                scheduleQuick(context, index + 1, now + 60_000)
            } else {
                scheduleQuick(context, index, at, quiet = true)
            }
        }
    }

    private fun setAlarmClock(context: Context, requestCode: Int, atMillis: Long, extras: Intent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setAlarmClock(
            AlarmManager.AlarmClockInfo(atMillis, showIntent),
            pendingIntent(context, requestCode, extras),
        )
    }

    private fun pendingIntent(context: Context, requestCode: Int, extras: Intent): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtras(extras)
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

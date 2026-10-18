package com.avlreminder.spike

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/**
 * Schedules alarms with AlarmManager.setAlarmClock(), the most reliable exact alarm
 * (allowed in Doze, shown as the next alarm in the status bar).
 * The test plan is a chain: each fired plan alarm schedules the next slot.
 */
object AlarmScheduler {
    const val EXTRA_PLANNED_AT = "planned_at"
    const val EXTRA_LABEL = "label"
    const val EXTRA_MODE = "mode"
    const val EXTRA_HOME_CHECK = "home_check"
    const val EXTRA_FROM_PLAN = "from_plan"

    private const val REQUEST_PLAN = 1
    private const val REQUEST_TEST = 2

    private const val PREFS = "scheduler"
    private const val KEY_PLAN_ACTIVE = "plan_active"
    private const val KEY_PLAN_NEXT_AT = "plan_next_at"
    private const val KEY_PLAN_NEXT_LABEL = "plan_next_label"

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

    fun scheduleNextPlanSlot(context: Context) {
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
        SpikeLog.log(context, "Nächster Plan-Alarm: ${SpikeLog.formatTime(atMillis)} (${slot.label})")
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

    /**
     * Detects plan alarms that never fired (e.g. killed by battery management or lost
     * across a reboot) and continues the chain. Called on app start and after boot.
     */
    fun checkMissed(context: Context, reason: String) {
        if (!isPlanActive(context)) return
        val at = prefs(context).getLong(KEY_PLAN_NEXT_AT, 0)
        if (at > 0 && System.currentTimeMillis() - at > MISSED_AFTER_MILLIS) {
            SpikeLog.log(context, "VERPASST: Plan-Alarm ${SpikeLog.formatTime(at)} ist nicht ausgelöst worden (bemerkt: $reason)")
        }
        // Re-schedule in any case: after a reboot all AlarmManager alarms are gone.
        scheduleNextPlanSlot(context)
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

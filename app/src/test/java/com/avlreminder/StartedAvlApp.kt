package com.avlreminder

import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The app for all Robolectric tests (robolectric.properties): it waits for the start job before a test
 * begins. Otherwise the job keeps running beside the test and reads the same DataStores; on Windows an
 * open reader makes DataStore's rename on writing fail ("Unable to rename …"), and the test sees an
 * error it did not cause.
 */
class StartedAvlApp : AvlApp() {
    override fun onCreate() {
        // Like a set-up phone: "Wecker und Erinnerungen" is granted (Robolectric starts without it).
        // Tests without it switch it off themselves.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        super.onCreate()
        runBlocking { started.join() }
    }
}

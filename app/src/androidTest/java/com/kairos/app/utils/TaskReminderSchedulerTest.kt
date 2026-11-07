package com.kairos.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskReminderSchedulerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("task_reminders", Context.MODE_PRIVATE)
    private val owner = "reminder-test-owner"
    private val taskId = "reminder-test-task"
    private val alarmIntent get() = Intent(context, TaskReminderReceiver::class.java).apply {
        action = "com.kairos.app.TASK_REMINDER"
        data = Uri.Builder().scheme("kairos-reminder").authority("task").appendPath(owner).appendPath(taskId).build()
    }

    private fun seed(): String {
        val value = JSONObject().put("owner", owner).put("reminders", JSONArray().put(
            JSONObject().put("id", taskId).put("title", "Private title").put("when", System.currentTimeMillis() + 3_600_000L)
        )).toString()
        assertTrue(prefs.edit().putString("config", value).commit())
        val pending = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3_600_000L, pending)
        return value
    }

    private fun cachedAlarm() = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

    private fun cleanup() {
        cachedAlarm()?.let {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
            it.cancel()
        }
        prefs.edit().clear().commit()
    }

    @Test fun signedOutSyncCancelsPreviousOwnersNativePendingIntent() = runBlocking {
        assumeTrue(FirebaseAuth.getInstance().currentUser == null)
        try {
            seed()
            assertNotNull(cachedAlarm())
            TaskReminderScheduler.sync(context, null, null, "UTC", enabled = true)
            assertNull(cachedAlarm())
            assertEquals("", JSONObject(prefs.getString("config", "{}")!!).getString("owner"))
        } finally { cleanup() }
    }

    @Test fun staleSessionCannotOverwriteConfigurationOrCancelAlarm() = runBlocking {
        assumeTrue(FirebaseAuth.getInstance().currentUser == null)
        try {
            val before = seed()
            TaskReminderScheduler.sync(context, null, null, "UTC", enabled = false, isCurrent = { false })
            assertEquals(before, prefs.getString("config", null))
            assertNotNull(cachedAlarm())
            TaskReminderScheduler.sync(context, "some-other-account", null, "UTC", enabled = false)
            assertEquals(before, prefs.getString("config", null))
        } finally { cleanup() }
    }

    @Test fun bootCannotRestoreAnotherAccountsAlarm() {
        assumeTrue(FirebaseAuth.getInstance().currentUser == null)
        try {
            seed()
            TaskReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
            assertNull(cachedAlarm())
            assertEquals(0, JSONObject(prefs.getString("config", "{}")!!).getJSONArray("reminders").length())
        } finally { cleanup() }
    }
}

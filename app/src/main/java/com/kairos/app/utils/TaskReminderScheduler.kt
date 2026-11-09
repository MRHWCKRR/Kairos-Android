package com.kairos.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.kairos.app.data.models.KairosPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Local, one-shot reminders. Recurring occurrence generation is intentionally not inferred. */
object TaskReminderScheduler {
    private const val PREFS = "task_reminders"
    private const val CONFIG = "config"
    private const val ACTION_REMIND = "com.kairos.app.TASK_REMINDER"
    private val lock = Any()

    private data class Config(
        val owner: String?,
        val reminders: List<PlannedTaskReminder>,
        val armed: Boolean = false
    )

    suspend fun sync(
        context: Context,
        ownerUid: String?,
        plan: KairosPlan?,
        timezone: String,
        enabled: Boolean,
        isCurrent: () -> Boolean = { true }
    ) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        fun current() = isCurrent() && currentUid() == ownerUid
        synchronized(lock) {
            if (!current()) return@synchronized
            val old = read(app)
            val now = System.currentTimeMillis()
            val desired = if (ownerUid != null && enabled && notificationsAllowed(app)) {
                TaskReminderPlanner.plan(plan, timezone, Long.MIN_VALUE).filter {
                    // Keep a valid pending alarm after its nominal deadline: inexact Android
                    // delivery can be delayed by idle, and a focus tick must not cancel it.
                    it.atMillis > now || (old.owner == ownerUid && it in old.reminders)
                }
            } else emptyList()
            val next = Config(ownerUid, desired)
            // Persisted equality survives process recreation and frequent widget/focus ticks.
            if (old.armed && old.copy(armed = false) == next) return@synchronized
            // Retain the old alarm inventory until cancellation succeeds. A process death
            // anywhere in this batch leaves an unarmed config that the next sync repairs.
            if (!current() || !write(app, old.copy(armed = false))) return@synchronized
            old.reminders.filter { old.owner != next.owner || it !in desired }.forEach {
                if (!current()) return@synchronized
                if (old.owner != null && !cancel(app, old.owner, it)) return@synchronized
            }
            if (!current()) return@synchronized
            if (!write(app, next)) return@synchronized
            // Replacing the same PendingIntent is idempotent and also repairs a batch
            // interrupted between persisting the desired config and registering alarms.
            var registered = true
            desired.forEach {
                if (!current()) return@synchronized
                if (ownerUid != null && !schedule(app, ownerUid, it)) registered = false
            }
            if (registered && current()) write(app, next.copy(armed = true))
        }
    }

    internal fun receive(context: Context, intent: Intent) = synchronized(lock) {
        val app = context.applicationContext
        val config = read(app)
        val owner = config.owner ?: return@synchronized
        if (currentUid() != owner) {
            // Old account alarms must never emit, even before the UI can clear its state.
            if (!write(app, config.copy(armed = false))) return@synchronized
            if (config.reminders.all { cancel(app, owner, it) }) write(app, Config(null, emptyList(), armed = true))
            return@synchronized
        }
        if (!notificationsAllowed(app)) {
            if (!write(app, config.copy(armed = false))) return@synchronized
            if (config.reminders.all { cancel(app, owner, it) }) write(app, Config(owner, emptyList(), armed = true))
            return@synchronized
        }
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val future = config.reminders.filter { it.atMillis > System.currentTimeMillis() }
            if (currentUid() != owner) return@synchronized
            val next = Config(owner, future)
            if (!write(app, next)) return@synchronized
            var registered = true
            future.forEach {
                if (currentUid() != owner) return@synchronized
                if (!schedule(app, owner, it)) registered = false
            }
            if (registered && currentUid() == owner) write(app, next.copy(armed = true))
            return@synchronized
        }
        if (intent.action != ACTION_REMIND) return@synchronized
        val reminder = config.reminders.firstOrNull {
            intent.data == uri(owner, it.taskId) &&
                intent.getStringExtra("fingerprint") == it.fingerprint(owner) &&
                intent.getLongExtra("when", Long.MIN_VALUE) == it.atMillis
        } ?: return@synchronized
        // Reject an early or stale broadcast, and consume before notifying to prevent replay.
        if (reminder.atMillis > System.currentTimeMillis() || currentUid() != owner) return@synchronized
        if (!write(app, config.copy(reminders = config.reminders - reminder))) return@synchronized
        if (currentUid() != owner) return@synchronized
        try {
            NotificationHelper(app).showNotification("Task reminder", reminder.title)
        } catch (_: SecurityException) {
            // Notification permission may have changed since the previous check.
        }
    }

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun notificationsAllowed(context: Context) =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun uri(owner: String, taskId: String): Uri = Uri.Builder()
        .scheme("kairos-reminder").authority("task").appendPath(owner).appendPath(taskId).build()

    private fun pending(context: Context, owner: String, reminder: PlannedTaskReminder, flags: Int): PendingIntent? {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = uri(owner, reminder.taskId)
            putExtra("fingerprint", reminder.fingerprint(owner))
            putExtra("when", reminder.atMillis)
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(context: Context, owner: String, reminder: PlannedTaskReminder): Boolean {
        return try {
            val pending = pending(context, owner, reminder, PendingIntent.FLAG_UPDATE_CURRENT)
                ?: return false
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Inexact delivery avoids special exact-alarm access; Android may delay during idle.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pending)
            true
        } catch (error: SecurityException) {
            android.util.Log.w("TaskReminderScheduler", "Alarm registration denied; will retry", error)
            false
        }
    }

    private fun cancel(context: Context, owner: String, reminder: PlannedTaskReminder): Boolean = try {
        pending(context, owner, reminder, PendingIntent.FLAG_NO_CREATE)?.let {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
            it.cancel()
        }
        true
    } catch (error: SecurityException) {
        android.util.Log.w("TaskReminderScheduler", "Alarm cancellation denied; will retry", error)
        false
    }

    private fun read(context: Context): Config = try {
        val json = JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CONFIG, "{}") ?: "{}")
        val owner = json.optString("owner").takeIf { it.isNotEmpty() }
        val list = json.optJSONArray("reminders") ?: JSONArray()
        Config(owner, (0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            PlannedTaskReminder(item.getString("id"), item.getString("title"), item.getLong("when"))
        }, armed = json.optBoolean("armed", false))
    } catch (_: org.json.JSONException) { Config(null, emptyList()) }

    private fun write(context: Context, config: Config): Boolean {
        val list = JSONArray()
        config.reminders.forEach { reminder ->
            list.put(JSONObject().put("id", reminder.taskId).put("title", reminder.title).put("when", reminder.atMillis))
        }
        val value = JSONObject().put("owner", config.owner ?: "").put("reminders", list)
            .put("armed", config.armed).toString()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CONFIG, value).commit()
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskReminderScheduler.receive(context, intent)
    }
}

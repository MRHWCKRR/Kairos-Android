package com.kairos.app.utils

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.firebase.auth.FirebaseAuth
import com.kairos.app.ui.widgets.FocusWidget
import com.kairos.app.ui.widgets.TasksWidget
import kotlinx.coroutines.CancellationException

object WidgetManager {
    val KEY_OWNER_UID = stringPreferencesKey("owner_uid")
    val KEY_FOCUS_RUNNING = booleanPreferencesKey("focus_running")
    val KEY_FOCUS_SECONDS = longPreferencesKey("focus_seconds")
    val KEY_TASK_ERROR = stringPreferencesKey("task_error")
    val KEY_TOP_TASKS = stringPreferencesKey("top_tasks")
    val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
    val KEY_IS_DARK = booleanPreferencesKey("is_dark")

    fun isCurrentOwner(ownerUid: String?): Boolean =
        ownerUid != null && FirebaseAuth.getInstance().currentUser?.uid == ownerUid

    private suspend fun update(
        context: Context,
        ownerUid: String?,
        isCurrentSession: () -> Boolean,
        focus: Boolean = true,
        tasks: Boolean = true,
        change: MutablePreferences.() -> Unit
    ) {
        fun isCurrent() = isCurrentSession() && FirebaseAuth.getInstance().currentUser?.uid == ownerUid
        try {
            if (!isCurrent()) return
            val manager = GlanceAppWidgetManager(context)
            val focusIds = if (focus) manager.getGlanceIds(FocusWidget::class.java) else emptyList()
            val taskIds = if (tasks) manager.getGlanceIds(TasksWidget::class.java) else emptyList()
            (focusIds + taskIds).forEach { id ->
                if (!isCurrent()) return
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    if (!isCurrent()) prefs else prefs.toMutablePreferences().apply {
                        // A theme update must never relabel a previous account's cached tasks.
                        if (this[KEY_OWNER_UID] != ownerUid) clear()
                        if (ownerUid == null) remove(KEY_OWNER_UID) else set(KEY_OWNER_UID, ownerUid)
                        change()
                    }
                }
                if (!isCurrent()) return
                if (id in focusIds) FocusWidget().update(context, id) else TasksWidget().update(context, id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("WidgetManager", "Failed to update widgets", e)
        }
    }

    suspend fun clearAccountState(context: Context, ownerUid: String?, isCurrentSession: () -> Boolean = { true }) =
        update(context, ownerUid, isCurrentSession) {
            clear()
            if (ownerUid != null) set(KEY_OWNER_UID, ownerUid)
        }

    suspend fun updateFocusState(context: Context, ownerUid: String?, running: Boolean, seconds: Long, isCurrentSession: () -> Boolean = { true }) =
        update(context, ownerUid, isCurrentSession, tasks = false) {
            set(KEY_FOCUS_RUNNING, running)
            set(KEY_FOCUS_SECONDS, seconds)
        }

    suspend fun updateTasks(context: Context, ownerUid: String?, tasks: List<String>, isCurrentSession: () -> Boolean = { true }) =
        update(context, ownerUid, isCurrentSession, focus = false) {
            set(KEY_TOP_TASKS, tasks.joinToString("|"))
            remove(KEY_TASK_ERROR)
        }

    suspend fun reportTaskError(context: Context, ownerUid: String) =
        update(context, ownerUid, { true }, focus = false) {
            set(KEY_TASK_ERROR, "Couldn’t save. Tap the task to retry.")
        }

    suspend fun updateTheme(context: Context, ownerUid: String?, colorHex: String, isDark: Boolean, isCurrentSession: () -> Boolean = { true }) =
        update(context, ownerUid, isCurrentSession) {
            set(KEY_THEME_COLOR, colorHex)
            set(KEY_IS_DARK, isDark)
        }
}

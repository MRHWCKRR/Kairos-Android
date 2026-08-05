package com.kairos.app.utils

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kairos.app.ui.widgets.FocusWidget
import com.kairos.app.ui.widgets.TasksWidget

object WidgetManager {
    val KEY_FOCUS_RUNNING = booleanPreferencesKey("focus_running")
    val KEY_FOCUS_SECONDS = longPreferencesKey("focus_seconds")
    val KEY_TOP_TASKS = stringPreferencesKey("top_tasks")

    suspend fun updateFocusState(context: Context, running: Boolean, seconds: Long) {
        try {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(FocusWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        set(KEY_FOCUS_RUNNING, running)
                        set(KEY_FOCUS_SECONDS, seconds)
                    }
                }
                FocusWidget().update(context, id)
            }
        } catch (e: Exception) {
            android.util.Log.e("WidgetManager", "Failed to update focus state", e)
        }
    }

    suspend fun updateTasks(context: Context, tasks: List<String>) {
        try {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TasksWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        set(KEY_TOP_TASKS, tasks.joinToString("|"))
                    }
                }
                TasksWidget().update(context, id)
            }
        } catch (e: Exception) {
            android.util.Log.e("WidgetManager", "Failed to update tasks", e)
        }
    }
}

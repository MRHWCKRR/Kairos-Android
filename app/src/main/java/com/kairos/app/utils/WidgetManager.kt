package com.kairos.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.kairos.app.ui.widgets.FocusWidget
import com.kairos.app.ui.widgets.TasksWidget

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    }
)

object WidgetManager {
    val KEY_FOCUS_RUNNING = booleanPreferencesKey("focus_running")
    val KEY_FOCUS_SECONDS = longPreferencesKey("focus_seconds")
    val KEY_TOP_TASKS = stringPreferencesKey("top_tasks") // "id:title|..."

    suspend fun updateFocusState(context: Context, running: Boolean, seconds: Long) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_FOCUS_RUNNING] = running
                prefs[KEY_FOCUS_SECONDS] = seconds
            }
            updateWidgets(context)
        } catch (e: Exception) {
            android.util.Log.e("WidgetManager", "Failed to update focus state", e)
        }
    }

    suspend fun updateTasks(context: Context, tasks: List<String>) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_TOP_TASKS] = tasks.joinToString("|")
            }
            updateWidgets(context)
        } catch (e: Exception) {
            android.util.Log.e("WidgetManager", "Failed to update tasks", e)
        }
    }

    private suspend fun updateWidgets(context: Context) {
        try {
            GlanceAppWidgetManager(context).apply {
                getGlanceIds(FocusWidget::class.java).forEach { id ->
                    FocusWidget().update(context, id)
                }
                getGlanceIds(TasksWidget::class.java).forEach { id ->
                    TasksWidget().update(context, id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WidgetManager", "Failed to trigger widget update", e)
        }
    }
}

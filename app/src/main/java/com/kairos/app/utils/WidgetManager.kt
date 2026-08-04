package com.kairos.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.kairos.app.ui.widgets.FocusWidget
import com.kairos.app.ui.widgets.TasksWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

object WidgetManager {
    val KEY_FOCUS_RUNNING = booleanPreferencesKey("focus_running")
    val KEY_FOCUS_SECONDS = longPreferencesKey("focus_seconds")
    val KEY_TOP_TASKS = stringPreferencesKey("top_tasks") // Comma separated for simplicity

    suspend fun updateFocusState(context: Context, running: Boolean, seconds: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_RUNNING] = running
            prefs[KEY_FOCUS_SECONDS] = seconds
        }
        updateWidgets(context)
    }

    suspend fun updateTasks(context: Context, tasks: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOP_TASKS] = tasks.joinToString("|")
        }
        updateWidgets(context)
    }

    private suspend fun updateWidgets(context: Context) {
        GlanceAppWidgetManager(context).apply {
            getGlanceIds(FocusWidget::class.java).forEach { id ->
                FocusWidget().update(context, id)
            }
            getGlanceIds(TasksWidget::class.java).forEach { id ->
                TasksWidget().update(context, id)
            }
        }
    }
}

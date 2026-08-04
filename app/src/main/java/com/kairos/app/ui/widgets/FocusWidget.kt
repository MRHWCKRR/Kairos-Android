package com.kairos.app.ui.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairos.app.utils.WidgetManager
import com.kairos.app.utils.dataStore
import kotlinx.coroutines.flow.first

class FocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()
        val isRunning = prefs[WidgetManager.KEY_FOCUS_RUNNING] ?: false
        val seconds = prefs[WidgetManager.KEY_FOCUS_SECONDS] ?: 0L

        provideContent {
            GlanceTheme {
                FocusWidgetContent(isRunning, seconds)
            }
        }
    }
}

@Composable
fun FocusWidgetContent(isRunning: Boolean, seconds: Long) {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val timeStr = String.format("%02d:%02d:%02d", h, m, s)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Focus Timer",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        
        Spacer(modifier = GlanceModifier.height(4.dp))
        
        Text(
            text = timeStr,
            style = TextStyle(
                color = if (isRunning) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        Button(
            text = if (isRunning) "PAUSE" else "START",
            onClick = actionRunCallback<ToggleFocusAction>(),
            modifier = GlanceModifier.fillMaxWidth().height(40.dp)
        )
    }
}

class ToggleFocusAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // In a real app, we would send an intent to the app or service
        // For now, we update the state to show interactivity
        // The app will sync back next time it's opened or via a service
        val intent = android.content.Intent(context, com.kairos.app.ui.navigation.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("action", "toggle_focus")
        }
        context.startActivity(intent)
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

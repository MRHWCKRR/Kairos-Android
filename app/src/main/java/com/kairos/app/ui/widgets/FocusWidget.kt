package com.kairos.app.ui.widgets

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairos.app.utils.WidgetManager
import java.util.Locale

class FocusWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isRunning = prefs[WidgetManager.KEY_FOCUS_RUNNING] ?: false
            val seconds = prefs[WidgetManager.KEY_FOCUS_SECONDS] ?: 0L
            val accentHex = prefs[WidgetManager.KEY_THEME_COLOR] ?: "#FFA855F7"
            
            val accentColor = try { Color(android.graphics.Color.parseColor(accentHex)) } catch (e: Exception) { Color(0xFFA855F7) }
            
            val colorProviders = ColorProviders(
                light = lightColorScheme(
                    primary = accentColor,
                    onPrimary = Color.White,
                    surface = Color.White,
                    onSurface = Color(0xFF161616),
                    onSurfaceVariant = Color(0xFF808080)
                ),
                dark = darkColorScheme(
                    primary = accentColor,
                    onPrimary = Color.White,
                    surface = Color(0xFF161616),
                    onSurface = Color(0xFFF5F5F5),
                    onSurfaceVariant = Color(0xFF808080)
                )
            )

            GlanceTheme(colors = colorProviders) {
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
    val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(if (isRunning) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant)
            ) {}
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = if (isRunning) "FOCUS ACTIVE" else "TIMER READY",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        
        Spacer(modifier = GlanceModifier.height(12.dp))
        
        Text(
            text = timeStr,
            style = TextStyle(
                color = if (isRunning) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = GlanceModifier.height(16.dp))
        
        Button(
            text = if (isRunning) "PAUSE" else "START",
            onClick = actionRunCallback<ToggleFocusAction>(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = GlanceTheme.colors.primary,
                contentColor = GlanceTheme.colors.onPrimary
            ),
            modifier = GlanceModifier.fillMaxWidth().height(48.dp)
        )
    }
}

class ToggleFocusAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
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

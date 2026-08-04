package com.kairos.app.ui.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairos.app.utils.WidgetManager
import com.kairos.app.utils.dataStore
import kotlinx.coroutines.flow.first

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()
        val tasksStr = prefs[WidgetManager.KEY_TOP_TASKS] ?: ""
        val tasks = if (tasksStr.isEmpty()) emptyList() else tasksStr.split("|")

        provideContent {
            GlanceTheme {
                TasksWidgetContent(tasks)
            }
        }
    }
}

@Composable
fun TasksWidgetContent(tasks: List<String>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        Text(
            text = "Must-Do Tasks",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        if (tasks.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "All caught up!", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
        } else {
            tasks.forEach { task ->
                TaskItemRow(task)
            }
        }
    }
}

@Composable
fun TaskItemRow(title: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(12.dp)
                .background(GlanceTheme.colors.primaryContainer)
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
        )
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

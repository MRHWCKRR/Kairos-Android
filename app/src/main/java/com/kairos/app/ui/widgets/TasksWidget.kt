package com.kairos.app.ui.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextDecoration
import com.kairos.app.utils.WidgetManager
import com.kairos.app.utils.dataStore
import kotlinx.coroutines.flow.first

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()
        val tasksStr = prefs[WidgetManager.KEY_TOP_TASKS] ?: ""
        
        val tasks = if (tasksStr.isEmpty()) emptyList() else tasksStr.split("|").mapNotNull {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }

        provideContent {
            GlanceTheme {
                TasksWidgetContent(tasks)
            }
        }
    }
}

@Composable
fun TasksWidgetContent(tasks: List<Pair<String, String>>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = GlanceModifier
                    .size(4.dp, 16.dp)
                    .background(GlanceTheme.colors.primary)
            ) {}
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "MUST-DO",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
        
        Spacer(modifier = GlanceModifier.height(12.dp))
        
        if (tasks.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "✨ All caught up!", 
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                tasks.forEach { (id, title) ->
                    TaskItemRow(id, title)
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun TaskItemRow(id: String, title: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTaskAction>(
                actionParametersOf(CompleteTaskAction.taskIdKey to id)
            )
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface, 
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

class CompleteTaskAction : ActionCallback {
    companion object {
        val taskIdKey = ActionParameters.Key<String>("task_id")
    }
    
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[taskIdKey] ?: return
        
        val intent = Intent(context, com.kairos.app.ui.navigation.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("action", "complete_task")
            putExtra("task_id", id)
        }
        context.startActivity(intent)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

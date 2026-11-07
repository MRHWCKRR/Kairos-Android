package com.kairos.app.ui.widgets

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairos.app.utils.WidgetManager

class TasksWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val ownerUid = prefs[WidgetManager.KEY_OWNER_UID]
            val matchesOwner = WidgetManager.isCurrentOwner(ownerUid)
            val tasksStr = if (matchesOwner) prefs[WidgetManager.KEY_TOP_TASKS].orEmpty() else ""
            val accentHex = if (matchesOwner) prefs[WidgetManager.KEY_THEME_COLOR] ?: "#FF7D45F5" else "#FF7D45F5"
            
            val accentColor = try { Color(android.graphics.Color.parseColor(accentHex)) } catch (e: Exception) { Color(0xFF7D45F5) }
            
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

            val tasks = if (tasksStr.isEmpty()) emptyList() else tasksStr.split("|").mapNotNull {
                val parts = it.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }

            GlanceTheme(colors = colorProviders) {
                TasksWidgetContent(tasks, ownerUid.orEmpty())
            }
        }
    }
}

@Composable
fun TasksWidgetContent(tasks: List<Pair<String, String>>, ownerUid: String = "") {
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
                text = "TO DO",
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
                    TaskItemRow(id, title, ownerUid)
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun TaskItemRow(id: String, title: String, ownerUid: String = "") {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTaskAction>(
                actionParametersOf(CompleteTaskAction.taskIdKey to id, CompleteTaskAction.ownerUidKey to ownerUid)
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
        val ownerUidKey = ActionParameters.Key<String>("owner_uid")
        val taskIdKey = ActionParameters.Key<String>("task_id")
    }
    
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[taskIdKey] ?: return
        
        // --- SILENT BACKGROUND UPDATE ---
        val userId = parameters[ownerUidKey]?.takeIf { it.isNotBlank() } ?: return
        if (!WidgetManager.isCurrentOwner(userId)) return
        val repository = com.kairos.app.data.repository.FirebaseRepository()

        // 1. Fetch current plan
        val plan = repository.getPlanOnce(userId)
        if (!WidgetManager.isCurrentOwner(userId)) return
        if (plan != null) {
            // 2. Update task completion
            val updatedBoards = plan.boards.map { board ->
                board.copy(sections = board.sections.map { section ->
                    section.copy(tasks = section.tasks.map { task ->
                        if (task.id == id) task.copy(completed = true) else task
                    })
                })
            }
            val updatedPlan = plan.copy(boards = updatedBoards)
            
            // 3. Save back to Firestore
            if (!WidgetManager.isCurrentOwner(userId)) return
            repository.updatePlan(userId, plan, updatedPlan)
            
            // 4. Update local widget state immediately
            val topTasks = updatedBoards
                .filter { !it.archived }
                .flatMap { it.sections }
                .filter { !it.archived }
                .flatMap { it.tasks }
                .filter { !it.completed && !it.archived }
                .distinctBy { it.id }
                .take(3)
                .map { "${it.id}:${it.title}" }
            
            if (!WidgetManager.isCurrentOwner(userId)) return
            WidgetManager.updateTasks(context, userId, topTasks)
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

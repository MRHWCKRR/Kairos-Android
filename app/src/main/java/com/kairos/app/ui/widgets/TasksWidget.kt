package com.kairos.app.ui.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            TasksWidgetContent()
        }
    }
}

@Composable
fun TasksWidgetContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        Text(
            text = "Must-Do Tasks",
            style = TextStyle(
                color = ColorProvider(Color(0xFFA855F7)),
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        // Static placeholders for now
        TaskItemPlaceholder("Complete Project UI")
        TaskItemPlaceholder("Sync Cloud Data")
        TaskItemPlaceholder("Fix Build Issues")
    }
}

@Composable
fun TaskItemPlaceholder(title: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .background(Color.White)
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = title,
            style = TextStyle(color = ColorProvider(Color.White))
        )
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

package com.kairos.app.ui.screens.schedule

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosScheduleEvent
import com.kairos.app.ui.navigation.MainViewModel
import com.kairos.app.ui.theme.TextMuted

private val CategoryColors = mapOf(
    "sleep" to Color(0xFF6366F1),
    "class" to Color(0xFFA855F7),
    "tutoring" to Color(0xFF14B8A6),
    "training" to Color(0xFFF97316),
    "other" to Color(0xFF64748B)
)

private val DayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private const val HourHeightDp = 48

@Composable
fun ScheduleScreen(viewModel: MainViewModel = viewModel()) {
    val plan by viewModel.plan.collectAsState()
    val events = plan?.scheduleEvents ?: emptyList()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScheduleLegend()
            
            Box(modifier = Modifier.fillMaxSize()) {
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()

                // Default scroll to 6 AM
                val density = LocalDensity.current
                val scrollPos = remember { with(density) { (6 * HourHeightDp).dp.toPx().toInt() } }
                
                LaunchedEffect(Unit) {
                    verticalScrollState.scrollTo(scrollPos)
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    // Time Column (Fixed)
                    TimeColumn(verticalScrollState)

                    // Days Grid
                    DayGrid(events, verticalScrollState)
                }
            }
        }
    }
}

@Composable
fun ScheduleLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryColors.forEach { (id, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = id.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun TimeColumn(scrollState: ScrollState) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .verticalScroll(scrollState)
            .padding(top = 40.dp) // Offset for day header
    ) {
        (0..23).forEach { hour ->
            Box(
                modifier = Modifier
                    .height(HourHeightDp.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = formatHour(hour),
                    fontSize = 10.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DayGrid(events: List<KairosScheduleEvent>, scrollState: ScrollState) {
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HourHeightDp.dp.toPx() }

    Row {
        DayLabels.forEachIndexed { index, label ->
            Column(
                modifier = Modifier
                    .width(100.dp)
            ) {
                // Day Header
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Time Rows for this Day
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((24 * HourHeightDp).dp)
                        .verticalScroll(scrollState)
                        .drawBehind {
                            // Hour lines
                            for (i in 0..24) {
                                val y = i * hourHeightPx
                                drawLine(
                                    color = Color.White.copy(alpha = 0.05f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                        }
                ) {
                    val eventsForDay = events.filter { it.day == index }
                    val prevDay = (index + 6) % 7
                    val overflowEvents = events.filter { 
                        it.day == prevDay && scheduleTimeToMinutes(it.end) < scheduleTimeToMinutes(it.start) 
                    }

                    ScheduleEventLayout(
                        primaryEvents = eventsForDay,
                        overflowEvents = overflowEvents
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleEventLayout(
    primaryEvents: List<KairosScheduleEvent>,
    overflowEvents: List<KairosScheduleEvent>
) {
    data class LaidOutEvent(
        val event: KairosScheduleEvent,
        val startMin: Int,
        val endMin: Int,
        val isOverflow: Boolean,
        var col: Int = 0,
        var colCount: Int = 1
    )

    val items = remember(primaryEvents, overflowEvents) {
        val list = mutableListOf<LaidOutEvent>()
        primaryEvents.forEach { ev ->
            val start = scheduleTimeToMinutes(ev.start)
            var end = scheduleTimeToMinutes(ev.end)
            if (end <= start) end = 24 * 60
            list.add(LaidOutEvent(ev, start, end, false))
        }
        overflowEvents.forEach { ev ->
            list.add(LaidOutEvent(ev, 0, scheduleTimeToMinutes(ev.end), true))
        }
        list.sortBy { it.startMin }

        // Multi-column logic
        val columnEnds = mutableListOf<Int>()
        list.forEach { item ->
            var placed = false
            for (c in columnEnds.indices) {
                if (columnEnds[c] <= item.startMin) {
                    columnEnds[c] = item.endMin
                    item.col = c
                    placed = true
                    break
                }
            }
            if (!placed) {
                item.col = columnEnds.size
                columnEnds.add(item.endMin)
            }
        }
        list.forEach { it.colCount = columnEnds.size }
        list
    }

    Layout(
        content = {
            items.forEach { item ->
                EventBlock(item.event, item.isOverflow)
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val item = items[index]
            val width = (constraints.maxWidth / item.colCount) - 4
            measurable.measure(Constraints.fixed(width, ((item.endMin - item.startMin) * HourHeightDp / 60).dp.roundToPx()))
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val item = items[index]
                val x = (item.col * constraints.maxWidth / item.colCount) + 2
                val y = (item.startMin * HourHeightDp / 60).dp.roundToPx()
                placeable.place(x, y)
            }
        }
    }
}

@Composable
fun EventBlock(event: KairosScheduleEvent, isOverflow: Boolean) {
    val color = CategoryColors[event.category] ?: CategoryColors["other"]!!
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
        modifier = Modifier.padding(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Text(
                text = if (isOverflow) "${event.title} ⤴" else event.title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
            if (!isOverflow) {
                Text(
                    text = "${event.start} - ${event.end}",
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun scheduleTimeToMinutes(t: String): Int {
    if (t.isEmpty()) return 0
    return try {
        val parts = t.split(":")
        val h = parts[0].toInt()
        val m = if (parts.size > 1) parts[1].toInt() else 0
        (h * 60) + m
    } catch (e: Exception) { 0 }
}

private fun formatHour(hour: Int): String {
    val h = if (hour == 0 || hour == 12) 12 else hour % 12
    val ampm = if (hour < 12) "AM" else "PM"
    return "$h $ampm"
}

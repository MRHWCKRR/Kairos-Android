package com.kairos.app.ui.screens.schedule

import com.kairos.app.ui.components.KairosTextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairos.app.data.models.KairosScheduleEvent
import com.kairos.app.ui.navigation.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.Dp

private val CategoryColors = mapOf(
    "sleep" to Color(0xFF6366F1),
    "class" to Color(0xFF7D45F5),
    "tutoring" to Color(0xFF14B8A6),
    "training" to Color(0xFFF97316),
    "other" to Color(0xFF64748B)
)

private const val HourHeightDp = 64

@Composable
fun ScheduleScreen(
    viewModel: MainViewModel = viewModel(),
    dates: List<LocalDate> = List(7) { LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value % 7).toLong()).plusDays(it.toLong()) },
    showEvents: Boolean = true,
    onDayDetails: (LocalDate) -> Unit = {}
) {
    val plan by viewModel.plan.collectAsState()
    val events = if (showEvents) plan?.scheduleEvents.orEmpty() else emptyList()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (events.isNotEmpty()) ScheduleLegend()
            Text("Fixed-event timeline · Tasks are in the agenda", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            val density = LocalDensity.current
            val scrollPos = with(density) { (7 * HourHeightDp).dp.toPx().toInt() }

            LaunchedEffect(Unit) {
                verticalScrollState.scrollTo(scrollPos)
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val dayWidth = if (dates.size == 1) maxWidth - 56.dp else 160.dp
                Row(modifier = Modifier.fillMaxSize().verticalScroll(verticalScrollState)) {
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .padding(top = 80.dp)
                    ) {
                        repeat(24) { hour ->
                            Box(
                                modifier = Modifier
                                    .height(HourHeightDp.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = formatHour(hour),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Row {
                            dates.forEach { date ->
                                val dayIndex = date.dayOfWeek.value % 7
                                val label = date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                                val eventsForDay = events.filter { it.day == dayIndex }
                                val prevDay = (dayIndex + 6) % 7
                                val overflowEvents = events.filter {
                                    val start = scheduleTimeToMinutes(it.start)
                                    val end = scheduleTimeToMinutes(it.end)
                                    it.day == prevDay && end < start
                                }

                                DayColumn(
                                    label = label,
                                    primaryEvents = eventsForDay,
                                    overflowEvents = overflowEvents,
                                    columnWidth = dayWidth,
                                    onDayDetails = { onDayDetails(date) }
                                )
                            }
                        }
                    }
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CategoryColors.forEach { (id, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = id.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun DayColumn(
    label: String,
    primaryEvents: List<KairosScheduleEvent>,
    overflowEvents: List<KairosScheduleEvent>,
    columnWidth: Dp = 160.dp,
    onDayDetails: () -> Unit = {}
) {
    val totalHeight = (24 * HourHeightDp).dp
    val gridColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)

    Column(modifier = Modifier.width(columnWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall)
                KairosTextButton(onClick = onDayDetails) { Text("Day details", style = MaterialTheme.typography.labelSmall) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .drawBehind {
                    for (i in 0..24) {
                        val y = i * HourHeightDp.dp.toPx()
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }
                }
        ) {
            ScheduleEventLayout(
                primaryEvents = primaryEvents,
                overflowEvents = overflowEvents,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ScheduleEventLayout(
    primaryEvents: List<KairosScheduleEvent>,
    overflowEvents: List<KairosScheduleEvent>,
    modifier: Modifier = Modifier
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
        val finalColCount = if (columnEnds.isEmpty()) 1 else columnEnds.size
        list.forEach { it.colCount = finalColCount }
        list
    }

    Layout(
        content = {
            items.forEach { item ->
                EventBlock(item.event, item.isOverflow)
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val item = items[index]
            val width = (constraints.maxWidth / item.colCount) - 4
            val height = ((item.endMin - item.startMin) * HourHeightDp / 60).dp.roundToPx()
            measurable.measure(Constraints.fixed(width.coerceAtLeast(0), height.coerceAtLeast(0)))
        }

        layout(constraints.maxWidth, (24 * HourHeightDp).dp.roundToPx()) {
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
        shape = RoundedCornerShape(7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.padding(2.dp).border(1.dp, color, RoundedCornerShape(7.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Text(
                text = if (isOverflow) "${event.title} ⤴" else event.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
            if (!isOverflow) {
                Text(
                    text = "${event.start}-${event.end}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

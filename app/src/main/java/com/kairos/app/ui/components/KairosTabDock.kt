package com.kairos.app.ui.components

import com.kairos.app.ui.icons.KairosIcons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.navigation.KairosDestination
import com.kairos.app.ui.navigation.KairosDestinations
import kotlin.coroutines.coroutineContext

/** Compact iOS geometry with opaque surfaces and Android tab semantics. */
@Composable
fun KairosTabDock(
    selectedRoute: String,
    onSelect: (String) -> Unit,
    onSearch: () -> Unit,
    reduceMotion: Boolean = false
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    // On unusually narrow displays or large text, scroll the tabs rather than shrink hit targets.
    val minimumTabWidth = (48f * fontScale).dp
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            BoxWithConstraints {
                val tabWidth = maxOf(minimumTabWidth, (maxWidth - 10.dp) / KairosDestinations.all.size)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(5.dp).selectableGroup()) {
                    KairosDestinations.all.forEach { destination ->
                        KairosDockTab(
                            destination = destination,
                            selected = selectedRoute == destination.route,
                            reduceMotion = reduceMotion,
                            modifier = Modifier.width(tabWidth),
                            onClick = { onSelect(destination.route) }
                        )
                    }
                }
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                Icon(KairosIcons.Search, "Search planner", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun KairosDockTab(
    destination: KairosDestination,
    selected: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 340f),
        label = "Tab selection"
    )
    val bounce = remember { Animatable(0f) }
    var previouslySelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected, reduceMotion) {
        val newlySelected = selected && !previouslySelected
        previouslySelected = selected
        // Compose's duration scale also makes the selection spring instantaneous when motion is disabled.
        if (newlySelected && !reduceMotion && (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f) {
            bounce.snapTo(1f)
            bounce.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 650f))
        } else {
            bounce.snapTo(0f)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val highlight = if (reduceMotion) (if (selected) 1f else 0f) else selection
    val foreground = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.selectable(selected, onClick = onClick, role = Role.Tab).heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.matchParentSize().graphicsLayer {
                scaleX = 0.94f + 0.06f * highlight
                scaleY = 0.94f + 0.06f * highlight
            },
            shape = RoundedCornerShape(16.dp),
            color = primary.copy(alpha = 0.13f * highlight.coerceIn(0f, 1f)),
            border = if (destination == KairosDestination.AiHelper) {
                BorderStroke(0.5.dp, primary.copy(alpha = 0.18f * highlight.coerceIn(0f, 1f)))
            } else null
        ) {}
        Column(
            Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                modifier = Modifier.size(if (destination == KairosDestination.AiHelper) 17.dp else 15.dp)
                    .graphicsLayer {
                        val displacement = if (reduceMotion) 0f else bounce.value
                        translationY = -3.dp.toPx() * displacement
                        scaleX = 1f + 0.08f * displacement
                        scaleY = 1f - 0.10f * displacement
                    },
                tint = foreground
            )
            Text(
                destination.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

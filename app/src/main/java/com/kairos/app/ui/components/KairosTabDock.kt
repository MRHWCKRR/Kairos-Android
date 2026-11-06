package com.kairos.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ui.navigation.KairosDestinations

/** iOS tab order and geometry with opaque surfaces and Android tab semantics. */
@Composable
fun KairosTabDock(selectedRoute: String, onSelect: (String) -> Unit, onSearch: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KairosDestinations.all.forEach { destination ->
                        val selected = selectedRoute == destination.route
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Column(
                                Modifier.selectable(selected, onClick = { onSelect(destination.route) }, role = Role.Tab)
                                    .heightIn(min = 52.dp).padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                            ) {
                                Icon(destination.icon, null, Modifier.size(19.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(destination.label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                                    color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            // At narrow widths search stays in Home's toolbar to retain full touch targets.
            if (maxWidth >= 332.dp) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Search, "Search planner", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

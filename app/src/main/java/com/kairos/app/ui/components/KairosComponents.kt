package com.kairos.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.theme.KairosMetrics

/** The shell measures chrome once; destinations receive an already-safe viewport. */
@Composable
fun KairosScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbar,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content
    )
}

@Composable
fun KairosPageHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
        trailing()
    }
}

@Composable
fun KairosCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(KairosMetrics.pageHorizontal),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

@Composable
fun KairosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = KairosMetrics.minimumTouchTarget),
        shape = MaterialTheme.shapes.medium
    ) { Text(text) }
}

@Composable
fun KairosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, singleLine = singleLine, enabled = enabled,
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun KairosTaskRow(
    title: String,
    completed: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = KairosMetrics.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconToggleButton(
            checked = completed, onCheckedChange = onToggle,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Complete $title"; role = Role.Checkbox }
        ) {
            Icon(if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null, modifier = Modifier.size(24.dp),
                tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            title,
            modifier = Modifier.weight(1f).clickable(onClickLabel = "Open task", onClick = onOpen)
                .padding(vertical = KairosMetrics.itemSpacing),
            style = MaterialTheme.typography.bodyMedium,
            color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (completed) TextDecoration.LineThrough else null
        )
        trailing()
    }
}

@Composable
fun KairosInputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { KairosTextField(value, { value = it }, "Name", singleLine = true) },
        confirmButton = { KairosButton("Save", { onConfirm(value.trim()) }, enabled = value.isNotBlank()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun KairosOverflowMenu(
    label: String,
    content: @Composable (dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreHoriz, label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun KairosStateMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier.fillMaxWidth().padding(KairosMetrics.pageHorizontal)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(KairosMetrics.itemSpacing)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun KairosErrorFeedback(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(KairosMetrics.itemSpacing).semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        action = { TextButton(onClick = onDismiss) { Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer) } }
    ) { Text(message) }
}

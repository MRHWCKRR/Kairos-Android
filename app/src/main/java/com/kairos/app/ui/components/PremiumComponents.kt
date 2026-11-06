package com.kairos.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.theme.KairosMetrics

// Compatibility entry points for existing screens; new UI uses Kairos components.
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = KairosMetrics.cardRadius,
    contentPadding: PaddingValues = PaddingValues(KairosMetrics.pageHorizontal),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

@Composable
fun PremiumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = KairosMetrics.minimumTouchTarget,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = KairosMetrics.pageHorizontal, vertical = KairosMetrics.itemSpacing),
        content = content
    )
}

@Composable
fun PremiumSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = KairosMetrics.minimumTouchTarget,
    content: @Composable RowScope.() -> Unit
) {
    KairosOutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content
    )
}


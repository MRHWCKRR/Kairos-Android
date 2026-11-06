package com.kairos.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Keep brand fills exact while text-only controls use the readable accent role. */
@Composable
fun KairosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) = TextButton(
    onClick, modifier, enabled, contentPadding = contentPadding,
    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), content = content
)

@Composable
fun KairosOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) = OutlinedButton(onClick, modifier, enabled, shape, colors, border = border, contentPadding = contentPadding, content = content)

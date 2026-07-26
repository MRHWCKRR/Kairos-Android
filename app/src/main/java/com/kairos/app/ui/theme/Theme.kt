package com.kairos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KairosDarkColors = darkColorScheme(
    background = BgMain,
    surface = BgCard,
    primary = AccentGlow,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderSubtle
)

@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KairosDarkColors,
        typography = KairosTypography,
        content = content
    )
}
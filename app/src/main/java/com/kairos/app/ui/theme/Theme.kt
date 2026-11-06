package com.kairos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kairos.app.data.models.AppearanceSettings

// iOS is the visual source of truth; legacy web accent names stay readable.
fun kairosColorScheme(isLight: Boolean, theme: String): ColorScheme {
    val accent = when (theme) {
        "fairyfloss" -> ThemeFairyFloss
        "poseidon" -> ThemePoseidon
        "peacefulplains" -> ThemePeacefulPlains
        "blue" -> Color(0xFF3373F2)
        "green" -> Color(0xFF2E9E66)
        "orange" -> Color(0xFFF26B29)
        "pink" -> Color(0xFFE64094)
        "red" -> Color(0xFFEB3D47)
        else -> AccentGlow
    }
    val background = if (isLight) ThemeLightBg else BgMain
    val surface = if (isLight) ThemeLightSurface else BgCard
    // Brand fills are exact. Contrast adaptation belongs to text, never the brand.
    val primary = accent
    val accentText = readableAccent(readableAccent(accent, surface), background)
    val onPrimary = if (contrastRatio(Color.White, primary) >= 4.5f) Color.White else Color.Black
    val container = primary.copy(alpha = if (isLight) 0.10f else 0.18f).over(surface)
    val onSurface = if (isLight) ThemeLightText else TextPrimary
    val muted = if (isLight) ThemeLightMuted else TextMuted
    val outline = if (isLight) ThemeLightBorder else BorderSubtle
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = container, onPrimaryContainer = onSurface,
        secondary = accentText, onSecondary = if (contrastRatio(Color.White, accentText) >= 4.5f) Color.White else Color.Black,
        secondaryContainer = container, onSecondaryContainer = onSurface,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = container, onTertiaryContainer = onSurface,
        background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface,
        surfaceVariant = if (isLight) Color(0xFFEBEBEF) else Color(0xFF202020),
        onSurfaceVariant = muted,
        outline = muted, outlineVariant = outline,
        surfaceTint = Color.Transparent,
        inverseSurface = if (isLight) BgCard else ThemeLightSurface,
        inverseOnSurface = if (isLight) TextPrimary else ThemeLightText,
        inversePrimary = if (isLight) AccentGlow else readableAccent(AccentGlow, ThemeLightSurface)
    )
}

private fun readableAccent(accent: Color, surface: Color): Color {
    var value = accent
    val target = if (surface.luminance() > 0.5f) Color.Black else Color.White
    repeat(32) {
        if (contrastRatio(value, surface) >= 4.5f) return value
        value = Color(
            value.red * 0.94f + target.red * 0.06f,
            value.green * 0.94f + target.green * 0.06f,
            value.blue * 0.94f + target.blue * 0.06f
        )
    }
    return value
}

private fun contrastRatio(a: Color, b: Color): Float =
    (maxOf(a.luminance(), b.luminance()) + 0.05f) /
        (minOf(a.luminance(), b.luminance()) + 0.05f)

private fun Color.over(background: Color): Color = Color(
    red * alpha + background.red * (1 - alpha),
    green * alpha + background.green * (1 - alpha),
    blue * alpha + background.blue * (1 - alpha)
)

@Composable
fun KairosTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val isLight = when (appearance.mode) {
        "light" -> true
        "system" -> !isSystemInDarkTheme()
        else -> false
    }
    MaterialTheme(
        colorScheme = kairosColorScheme(isLight, appearance.theme),
        typography = getKairosTypography(appearance.font),
        shapes = KairosShapes,
        content = content
    )
}


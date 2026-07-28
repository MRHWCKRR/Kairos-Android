package com.kairos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.kairos.app.data.models.AppearanceSettings

@Composable
fun KairosTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val isLight = appearance.mode == "light"
    val accent = when (appearance.theme) {
        "fairyfloss" -> ThemeFairyFloss
        "poseidon" -> ThemePoseidon
        "peacefulplains" -> ThemePeacefulPlains
        else -> AccentGlow
    }

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = accent,
            background = ThemeLightBg,
            surface = ThemeLightSurface,
            onBackground = ThemeLightText,
            onSurface = ThemeLightText,
            outline = BorderSubtle.copy(alpha = 0.5f)
        )
    } else {
        darkColorScheme(
            primary = accent,
            background = BgMain,
            surface = BgCard,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            outline = BorderSubtle
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KairosTypography,
        content = content
    )
}

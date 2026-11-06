package com.kairos.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class KairosColorContrastTest {
    @Test
    fun readableTextForEveryAppearanceAndAccent() {
        val themes = listOf("default", "fairyfloss", "poseidon", "peacefulplains", "blue", "green", "orange", "pink", "red")
        for (light in listOf(false, true)) for (theme in themes) {
            val colors = kairosColorScheme(light, theme)
            val pairs = listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surface,
                colors.onPrimary to colors.primary,
                colors.primary to colors.surface,
                colors.primary to colors.background,
                colors.onPrimaryContainer to colors.primaryContainer
            )
            pairs.forEach { (text, background) ->
                assertTrue("Unreadable text for light=$light theme=$theme: $text on $background", contrast(text, background) >= 4.5f)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}

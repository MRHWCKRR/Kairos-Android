package com.kairos.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
                colors.secondary to colors.surface,
                colors.secondary to colors.background,
                colors.onPrimaryContainer to colors.primaryContainer
            )
            pairs.forEach { (text, background) ->
                assertTrue("Unreadable text for light=$light theme=$theme: $text on $background", contrast(text, background) >= 4.5f)
            }
        }
    }

    @Test fun defaultBrandMatchesIOSInBothAppearances() {
        val iosPurple = Color(red = 0.49f, green = 0.27f, blue = 0.96f)
        assertEquals(iosPurple, kairosColorScheme(false, "default").primary)
        assertEquals(iosPurple, kairosColorScheme(true, "default").primary)
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}

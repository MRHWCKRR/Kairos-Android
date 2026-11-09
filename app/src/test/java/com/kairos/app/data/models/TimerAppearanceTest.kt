package com.kairos.app.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerAppearanceTest {
    @Test
    fun `unknown timer appearance values fall back safely`() {
        val appearance = AppearanceSettings(
            timerType = "hourglass",
            timerTheme = "lava",
            timerBorder = "spikes"
        )

        assertEquals("digital", appearance.resolvedTimerType)
        assertEquals("violet", appearance.resolvedTimerTheme)
        assertEquals("glow", appearance.resolvedTimerBorder)
    }
}

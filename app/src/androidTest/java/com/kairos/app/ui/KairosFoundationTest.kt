package com.kairos.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.components.*
import com.kairos.app.ui.theme.KairosTheme
import com.kairos.app.data.models.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KairosFoundationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun transparentShellUsesReadableThemeForeground() {
        var mode by mutableStateOf("dark")
        var actual = Color.Transparent
        var expected = Color.Transparent
        compose.setContent {
            KairosTheme(AppearanceSettings(mode = mode)) {
                val themeForeground = MaterialTheme.colorScheme.onBackground
                KairosScaffold {
                    val foreground = LocalContentColor.current
                    SideEffect { actual = foreground; expected = themeForeground }
                }
            }
        }
        compose.runOnIdle { assertEquals(expected, actual); mode = "light" }
        compose.runOnIdle { assertEquals(expected, actual) }
    }

    @Test fun contentFitsBetweenMeasuredBars() {
        compose.setContent {
            KairosTheme {
                KairosScaffold(
                    topBar = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("top")) },
                    bottomBar = { Box(Modifier.fillMaxWidth().height(80.dp).testTag("bottom")) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        Box(Modifier.fillMaxSize().testTag("content"))
                    }
                }
            }
        }
        val top = compose.onNodeWithTag("top").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithTag("bottom").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertTrue(content.top >= top.bottom)
        assertTrue(content.bottom <= bottom.top)
        assertTrue(content.height > 0)
    }

    @Test fun taskCompletionAndOpeningAreIndependentActions() {
        var completed = false
        var opened = false
        compose.setContent {
            KairosTheme {
                var checked by remember { mutableStateOf(false) }
                KairosTaskRow(
                    "Read the long assignment brief and prepare the first draft", checked,
                    { checked = it; completed = it }, { opened = true }
                )
            }
        }
        compose.onNodeWithContentDescription("Complete Read the long assignment brief and prepare the first draft").performClick().assertIsOn()
        compose.runOnIdle { assertTrue(completed); assertTrue(!opened) }
        compose.onNodeWithText("Read the long assignment brief and prepare the first draft").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun disabledSaveCannotSubmit() {
        var submitted = false
        compose.setContent { KairosTheme { KairosButton("Save", { submitted = true }, enabled = false) } }
        compose.onNodeWithText("Save").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertTrue(!submitted) }
    }

    @Test fun longHeaderRetainsItsActionAtLargeFontScale() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                KairosTheme {
                    Box(Modifier.width(280.dp).testTag("header")) {
                        KairosPageHeader("Kairos AI and assignment planning") {
                            KairosButton("Menu", {}, Modifier.testTag("menu"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("menu").assertIsDisplayed()
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("menu").fetchSemanticsNode().boundsInRoot
        assertTrue(menu.left >= header.left && menu.right <= header.right)
    }
}

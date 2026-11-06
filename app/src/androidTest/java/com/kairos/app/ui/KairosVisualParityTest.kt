package com.kairos.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kairos.app.data.models.*
import com.kairos.app.ui.components.*
import com.kairos.app.ui.screens.dashboard.HomeContent
import com.kairos.app.ui.screens.tasks.BoardCard
import com.kairos.app.ui.screens.tasks.TasksSummary
import com.kairos.app.ui.screens.ai.AiConversationContent
import com.kairos.app.ui.theme.KairosTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

/** Synthetic data through the production composables, never an authenticated account. */
class KairosVisualParityTest {
    @get:Rule val compose = createComposeRule()
    private var appearance by mutableStateOf("dark")
    private var scale by mutableStateOf(1f)
    private val board = KairosBoard("board", "Assignments", listOf(
        KairosSection("section", "English", listOf(
            KairosTask("one", "Read the assignment brief", completed = true, date = "2026-11-06"),
            KairosTask("two", "Prepare an outline for the first draft", date = "2026-11-06"),
            KairosTask("three", "Collect supporting sources", date = "2026-11-06")
        ))
    ))
    private val plan = KairosPlan(boards = listOf(board))

    private fun screen(route: String, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                KairosTheme(AppearanceSettings(mode = appearance)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        KairosScaffold(bottomBar = { KairosTabDock(route, {}, {}) }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
                        }
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "visual-parity")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun captureAppearances(name: String) {
        capture("$name-dark")
        compose.runOnIdle { appearance = "light" }
        capture("$name-light")
        compose.runOnIdle { scale = 2f }
        capture("$name-large-text")
    }

    @Test fun homeUsesProductionLayout() {
        screen("dashboard") {
            HomeContent(plan, KairosUserProfile(settings = KairosSettings(profile = ProfileSettings(displayName = "Alex"))),
                ZonedDateTime.parse("2026-11-06T09:00:00+10:00[Australia/Brisbane]"), 1500L, false,
                {}, {}, {}, {}, { _, _ -> })
        }
        compose.onNodeWithText("Alex").assertExists()
        captureAppearances("home")
    }

    @Test fun boardsUseProductionComponents() {
        screen("tasks") {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Tasks", style = MaterialTheme.typography.headlineLarge)
                TasksSummary(listOf(board))
                BoardCard(board, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
            }
        }
        captureAppearances("boards")
    }

    @Test fun aiUsesProductionConversation() {
        screen("ai_helper") {
            AiConversationContent(
                listOf(ChatMessage("user", "Help me plan my assignment."), ChatMessage("assistant", "When is it due, and what have you finished so far?")),
                "It is due next Friday.", false, null, {}, {}, {}, {}, {}
            )
        }
        compose.onAllNodesWithText("Kairos AI").onFirst().assertExists()
        captureAppearances("ai")
    }

    @Test fun retiredDiscoveryIsAbsentFromNavigation() {
        screen("dashboard") { Text("Workspace") }
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Boards").assertExists()
        compose.onNodeWithText("AI").assertExists()
        compose.onNodeWithText("Schedule").assertExists()
        compose.onNodeWithText("Goals").assertExists()
        compose.onNodeWithText("Discovery").assertDoesNotExist()
    }
}

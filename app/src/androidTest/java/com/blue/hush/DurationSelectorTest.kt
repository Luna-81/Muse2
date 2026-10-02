package com.blue.hush

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.ui.*
import com.blue.hush.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class DurationSelectorTest {
    @get:Rule val compose = createComposeRule()
    private var selectedSeconds = 600
    private var musicOpens = 0
    private var deviceOpens = 0
    private var starts = 0
    private var simulation by mutableStateOf(false)

    private fun show(largeText: Boolean = false) {
        compose.setContent {
            var seconds by remember { mutableIntStateOf(selectedSeconds) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                HushTheme {
                    Column(Modifier.width(320.dp).background(HushColors.Background).padding(8.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            MusicButton("Rain") { musicOpens++ }
                        }
                        SessionPreparationPanel(seconds, ConnectionUiState(simulationMode = simulation, simulationDataAvailable = true),
                            onDurationSelected = { seconds = it; selectedSeconds = it },
                            onDeviceSelected = { deviceOpens++ }, onStart = { starts++ })
                    }
                }
            }
        }
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun singleWheelDefaultsToTenAndMusicAndDeviceControlsRemainReachable() {
        show()
        compose.onNodeWithContentDescription("Duration in minutes").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10 min"))
        compose.onNodeWithText("5 min").assertDoesNotExist()
        compose.onNodeWithText("10 min").assertDoesNotExist()
        compose.onNodeWithText("Guided Meditation").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
        saveScreenshot("hush-preparation.png")
        compose.onNodeWithContentDescription("Soundscape · Rain").performClick()
        compose.onNodeWithContentDescription("Muse connection").performClick()
        compose.onNodeWithText("Connect Muse").performClick()
        compose.runOnIdle { assertEquals(1, musicOpens); assertEquals(1, deviceOpens); assertEquals(1, starts) }
    }

    @Test fun swipeChangesDurationImmediatelyWithoutADialog() {
        show()
        compose.onNodeWithContentDescription("Duration in minutes").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(selectedSeconds > 600) }
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test fun wheelSupportsBothBoundsAndLargeText() {
        show(largeText = true)
        saveScreenshot("hush-preparation-large.png")
        listOf(1, 60).forEach { minutes ->
            compose.onNodeWithContentDescription("Duration in minutes").performSemanticsAction(SemanticsActions.SetProgress) { it(minutes.toFloat()) }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Duration in minutes").assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$minutes min"))
            compose.runOnIdle { assertEquals(minutes * 60, selectedSeconds) }
        }
        compose.onNodeWithText("Connect Muse").assertIsDisplayed()
    }

    @Test fun simulationResetsVisibleWheelToTenAndDisablesScrolling() {
        show()
        compose.onNodeWithContentDescription("Duration in minutes").performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
        compose.waitForIdle()
        compose.runOnIdle { simulation = true }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Duration in minutes").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10 min"))
        val previousSeconds = selectedSeconds
        compose.onNodeWithContentDescription("Duration in minutes").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(previousSeconds, selectedSeconds) }
        compose.onNodeWithText("Start meditation").assertIsDisplayed()
    }
}

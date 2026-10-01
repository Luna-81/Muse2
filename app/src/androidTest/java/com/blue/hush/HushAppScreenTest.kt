package com.blue.hush

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.*
import com.blue.hush.ui.*
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class HushAppScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var starts = 0
    private var previewStops = 0

    private fun show(largeText: Boolean = false, finished: Boolean = false) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                var tab by remember { mutableStateOf(AppTab.MEDITATE) }
                var track by remember { mutableStateOf(MusicTrack.MIST) }
                var duration by remember { mutableIntStateOf(1200) }
                var connection by remember { mutableStateOf(ConnectionUiState(simulationDataAvailable = true)) }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                    HushTheme {
                        HushApp(
                            sessionState = SessionState(phase = if (finished) SessionPhase.FINISHED else SessionPhase.IDLE),
                            history = emptyList(), activeTab = tab, selectedDurationSeconds = duration,
                            selectedTrack = track, detailSummary = null, detailSamples = emptyList(),
                            replayProgress = 0f, connectionState = connection, previewTrack = null,
                            onTabSelected = { tab = it }, onDurationSelected = { duration = it },
                            onTrackSelected = { track = it }, onStartScanning = {}, onConnect = {}, onDisconnect = {},
                            onStartSession = { starts++ }, onSimulationModeChanged = {
                                connection = connection.copy(simulationMode = it); if (it) duration = 600
                            }, onPause = {}, onResume = {}, onFinish = {}, onStartNewSession = {}, onVolumeChanged = {},
                            onOpenDetail = {}, onCloseDetail = {}, onReplayProgressChanged = {},
                            onDeleteSession = {},
                            onPreviewTrack = {}, onStopPreview = { previewStops++ },
                        )
                    }
                }
            }
        }
    }

    @Test fun simulationAndSoundscapeAreReachableWithoutExtraNavigation() {
        show()
        compose.onNodeWithText("Hush").assertIsDisplayed()
        saveScreenshot("hush-home.png")
        compose.onNodeWithText("Soundscape · Mist").performScrollTo().performClick()
        compose.onNodeWithText("Soundscapes").assertIsDisplayed()
        compose.onNodeWithText("Select").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Soundscape · Tide").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, previewStops) }
        compose.onNodeWithText("Connect Muse").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Use saved simulation data").performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("20 min").assertIsNotEnabled()
        compose.onNodeWithText("Start meditation").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Complete a session to see it here.").assertIsDisplayed()
        saveScreenshot("hush-history-empty.png")
    }

    @Test fun largeTextKeepsPrimaryActionReachable() {
        show(largeText = true)
        compose.onNodeWithText("Connect Muse").performScrollTo().assertIsDisplayed()
        saveScreenshot("hush-home-large-text.png")
    }

    @Test fun completionDoesNotInventAnAssessmentWithoutSignal() {
        show(finished = true)
        compose.onNodeWithText("Not enough signal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View session").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Back to home").performScrollTo().assertIsDisplayed()
        saveScreenshot("hush-completion.png")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

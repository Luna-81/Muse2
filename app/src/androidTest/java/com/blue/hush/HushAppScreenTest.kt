package com.blue.hush

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.*
import com.blue.hush.processing.SessionScoreCalculator
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
    private var homeReturns = 0

    private fun show(largeText: Boolean = false, finished: Boolean = false, withHistory: Boolean = false) {
        // The Home preview has a continuous frame loop and never idles.
        compose.mainClock.autoAdvance = finished
        val samples = if (withHistory) (1..40).map { StateSample(it, alpha = 0.3, theta = 0.2, beta = 0.2,
            calmness = 0.8, valid = true, eegBandsAvailable = true, algorithmVersion = 1) } else emptyList()
        val summary = SessionSummary(1, 1000, 41000, 600, 40, MusicTrack.MIST, ResultLabel.STEADY, 40, 40)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                var tab by remember { mutableStateOf(AppTab.MEDITATE) }
                var track by remember { mutableStateOf(MusicTrack.MIST) }
                var duration by remember { mutableIntStateOf(1200) }
                var detail by remember { mutableStateOf<SessionSummary?>(null) }
                var connection by remember { mutableStateOf(ConnectionUiState(simulationDataAvailable = true)) }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                    HushTheme {
                        HushApp(
                            sessionState = SessionState(phase = if (finished) SessionPhase.FINISHED else SessionPhase.IDLE,
                                sessionId = 1, elapsedSeconds = 40, trendSamples = samples,
                                latestSample = samples.lastOrNull(), scores = SessionScoreCalculator.calculate(samples)),
                            history = if (withHistory) listOf(summary) else emptyList(), activeTab = tab, selectedDurationSeconds = duration,
                            selectedTrack = track, detailSummary = detail, detailSamples = samples,
                            replayProgress = 0f, connectionState = connection, previewTrack = null,
                            onTabSelected = { tab = it }, onDurationSelected = { duration = it },
                            onTrackSelected = { track = it }, onStartScanning = {}, onConnect = {}, onDisconnect = {},
                            onStartSession = { starts++ }, onSimulationModeChanged = {
                                connection = connection.copy(simulationMode = it); if (it) duration = 600
                            }, onPause = {}, onResume = {}, onFinish = {}, onStartNewSession = { homeReturns++ }, onVolumeChanged = {},
                            onOpenDetail = { detail = it }, onCloseDetail = { detail = null }, onReplayProgressChanged = {},
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
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Hush").assertIsDisplayed()
        saveScreenshot("hush-home.png")
        compose.onNodeWithText("Soundscape · Mist").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Soundscapes").assertIsDisplayed()
        compose.onNodeWithText("Select").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Soundscape · Tide").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, previewStops) }
        compose.onNodeWithText("Connect Muse").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Use saved simulation data").performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("20 min").assertIsNotEnabled()
        compose.onNodeWithText("Start meditation").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        compose.onNodeWithText("History").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Complete a session to see it here.").assertIsDisplayed()
        saveScreenshot("hush-history-empty.png")
    }

    @Test fun largeTextKeepsPrimaryActionReachable() {
        show(largeText = true)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Connect Muse").performScrollTo().assertIsDisplayed()
        saveScreenshot("hush-home-large-text.png")
    }

    @Test fun completionDoesNotInventAnAssessmentWithoutSignal() {
        show(finished = true)
        compose.onNodeWithContentDescription("Overall grade: unavailable").assertIsDisplayed()
        listOf("Calm", "Focus", "Stability").forEach {
            compose.onNodeWithContentDescription("$it score: unavailable").assertIsDisplayed()
        }
        compose.onNodeWithText("More Details").performScrollTo().assertIsNotEnabled()
        saveScreenshot("hush-completion.png")
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithText("Results").performScrollTo().performClick()
        compose.onNodeWithText("More Details").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, homeReturns) }
    }

    @Test fun completionAndDetailsShowSameScoresAndReturnWithSheetClosed() {
        show(finished = true, withHistory = true)
        compose.onNodeWithContentDescription("Calm score: 80 out of 100").assertIsDisplayed()
        compose.onNodeWithContentDescription("Focus score: 50 out of 100").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stability score: 100 out of 100").assertIsDisplayed()
        compose.onNodeWithContentDescription("Overall grade: B").assertIsDisplayed()
        compose.onNodeWithText("More Details").performScrollTo().performClick()
        compose.onNodeWithText("Session details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Calm score: 80 out of 100").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Focus score: 50 out of 100").assertExists()
        compose.onNodeWithContentDescription("Overall grade: B").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithText("More Details").assertDoesNotExist()
        compose.onNodeWithText("Results").performScrollTo().performClick()
        compose.onNodeWithText("More Details").assertExists()
    }

    @Test fun largeTextKeepsCompletionDetailsReachable() {
        show(largeText = true, finished = true, withHistory = true)
        compose.onNodeWithText("More Details").performScrollTo().assertIsDisplayed()
        saveScreenshot("hush-completion-large-text.png")
    }

    @Test fun landscapeKeepsCompletionDetailsReachable() {
        compose.mainClock.autoAdvance = false
        val original = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            show(largeText = true, finished = true, withHistory = true)
            compose.onNodeWithText("More Details").performScrollTo().assertIsDisplayed()
            saveScreenshot("hush-completion-landscape.png")
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = original }
        }
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

package com.blue.hush

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.*
import com.blue.hush.session.*
import com.blue.hush.ui.*
import com.blue.hush.ui.galaxy.rememberGalaxyMotion
import com.blue.hush.ui.screens.CompletionScreen
import com.blue.hush.ui.theme.HushTheme
import org.junit.Rule
import org.junit.Test

class CompletionSheetStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completionReplaysOnlyCalmnessAndPreservesMissingSamples() {
        val samples = (1..120).map { second ->
            StateSample(second, valid = second != 60,
                calmness = if (second == 60) null else 0.7, algorithmVersion = 5)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var showResults by remember { mutableStateOf(true) }
            HushTheme {
                CompletionScreen(
                    SessionState(
                        phase = SessionPhase.FINISHED, sessionId = 42,
                        elapsedSeconds = 120, trendSamples = samples, latestSample = samples.last()
                    ),
                    rememberGalaxyMotion(),
                    showResults = showResults,
                    onShowResults = { showResults = true },
                    onDismissResults = { showResults = false },
                    onBack = {},
                    detailAvailable = false,
                    onDetails = {},
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_000)
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeByFrame()
        val playingDescription = compose.onNodeWithContentDescription("Session replay")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        org.junit.Assert.assertTrue("Playback should advance while results are open", playingDescription.substringBefore(',') != "00:01")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Pause replay").performScrollTo().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Pause replay").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Play replay").assertIsDisplayed()
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithText("Stability").assertDoesNotExist()
        val chart = compose.onNodeWithContentDescription("Session replay")
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.mainClock.advanceTimeByFrame()
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "01:00, Calmness —"))
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        compose.mainClock.advanceTimeByFrame()
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "01:30, Calmness 70"))
    }

    private fun show(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            HushTheme {
                HushApp(
                    sessionState = SessionState(phase = SessionPhase.FINISHED, sessionId = 42),
                    history = emptyList(), activeTab = AppTab.MEDITATE, selectedDurationSeconds = 600,
                    selectedTrack = MusicTrack.RAIN, detailSummary = null, detailSamples = emptyList(),
                    replayProgress = 0f, connectionState = ConnectionUiState(), previewTrack = null,
                    onTabSelected = {}, onDurationSelected = {}, onTrackSelected = {},
                    onStartScanning = {}, onConnect = {}, onDisconnect = {}, onStartSession = {},
                    onSimulationModeChanged = {}, onPause = {}, onResume = {}, onFinish = {},
                    onStartNewSession = {}, onVolumeChanged = {}, onOpenDetail = {}, onCloseDetail = {},
                    onReplayProgressChanged = {}, onPreviewTrack = {}, onStopPreview = {}, onDeleteSession = {},
                )
            }
        }
        return restoration
    }

    @Test fun savedStatePreservesDismissalAndExplicitReopening() {
        val restoration = show()
        compose.onNodeWithText("More Details").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("More Details").assertDoesNotExist()
        compose.onNodeWithText("Results").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("More Details").assertIsDisplayed()
    }

    @Test fun swipingDownDismissesSheetAndKeepsCompletionPage() {
        show()
        compose.onNodeWithTag("completionResults").performTouchInput { swipeDown() }
        compose.onNodeWithText("More Details").assertDoesNotExist()
        compose.onNodeWithText("Finished").assertIsDisplayed()
    }

    @Test fun scrimDismissesSheetAndKeepsCompletionPage() {
        show()
        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.onNodeWithText("More Details").assertDoesNotExist()
        compose.onNodeWithText("Finished").assertIsDisplayed()
    }
}

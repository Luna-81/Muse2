package com.blue.hush

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.blue.hush.session.*
import com.blue.hush.ui.*
import com.blue.hush.ui.theme.HushTheme
import org.junit.Rule
import org.junit.Test

class CompletionSheetStateTest {
    @get:Rule val compose = createComposeRule()

    private fun show(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            HushTheme {
                HushApp(
                    sessionState = SessionState(phase = SessionPhase.FINISHED, sessionId = 42),
                    history = emptyList(), activeTab = AppTab.MEDITATE, selectedDurationSeconds = 600,
                    selectedTrack = MusicTrack.MIST, detailSummary = null, detailSamples = emptyList(),
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

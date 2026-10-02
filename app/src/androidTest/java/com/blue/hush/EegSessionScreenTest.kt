package com.blue.hush

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.blue.hush.session.EegSignalStatus
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionState
import com.blue.hush.session.StateSample
import com.blue.hush.ui.GalaxyMotion
import com.blue.hush.ui.MeditationGalaxyScreen
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EegSessionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var state by mutableStateOf(SessionState(
        phase = SessionPhase.RUNNING, connected = true, elapsedSeconds = 1,
        eegStatus = EegSignalStatus.AVAILABLE, calibrationSeconds = 5,
    ))
    private val motion = GalaxyMotion()

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    HushTheme {
                        MeditationGalaxyScreen(state, {}, {}, {}, {}, galaxyMotion = motion)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun updateState(transform: (SessionState) -> SessionState) {
        compose.runOnIdle { state = transform(state) }
        compose.mainClock.advanceTimeBy(32)
    }

    @Test fun statusSeparatesCalibrationQualityMissingDataAndDisconnect() {
        show()
        compose.onNodeWithText("Calibrating… 5/10").assertIsDisplayed()
        updateState { it.copy(eegStatus = EegSignalStatus.LOW_QUALITY) }
        compose.onNodeWithText("Low signal quality").assertDoesNotExist()
        compose.onNodeWithText("Calibrating… 5/10").assertIsDisplayed()
        updateState { it.copy(eegNotice = EegSignalStatus.LOW_QUALITY) }
        compose.onNodeWithText("Low signal quality").assertIsDisplayed()
        compose.onNodeWithText("Waiting for EEG…").assertDoesNotExist()
        compose.onNodeWithText("Reconnecting…").assertDoesNotExist()
        updateState { it.copy(eegStatus = EegSignalStatus.UNKNOWN, eegNotice = EegSignalStatus.UNKNOWN) }
        compose.onNodeWithText("Checking signal…").assertIsDisplayed()
        updateState { it.copy(eegStatus = EegSignalStatus.MISSING, eegNotice = EegSignalStatus.MISSING) }
        compose.onNodeWithText("Waiting for EEG…").assertIsDisplayed()
        updateState { it.copy(connected = false) }
        compose.onNodeWithText("Reconnecting…").assertIsDisplayed()
        updateState { it.copy(phase = SessionPhase.PAUSED) }
        compose.onNodeWithText("Paused").assertIsDisplayed()
    }

    @Test fun lowQualityKeepsAnimationMovingButDoesNotCreateCalmnessAndPauseStopsIt() {
        state = state.copy(calibrationSeconds = 10, latestSample = StateSample(
            elapsedSeconds = 1, calmness = 0.2, valid = true, algorithmVersion = 2,
        ))
        show()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {
            state = state.copy(elapsedSeconds = 2, eegStatus = EegSignalStatus.LOW_QUALITY,
                latestSample = StateSample(2, valid = true, algorithmVersion = 2))
        }
        compose.mainClock.advanceTimeBy(32)
        val phase = motion.phase
        val agitation = motion.agitation
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {
            assertTrue(motion.phase > phase)
            assertEquals(agitation, motion.agitation, 0f)
            assertEquals(null, state.latestSample?.calmness)
        }
        compose.onNodeWithText("Low signal quality").assertDoesNotExist()
        compose.onNodeWithText("No calmness data").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(phase = SessionPhase.PAUSED) }
        compose.mainClock.advanceTimeBy(32)
        val pausedPhase = motion.phase
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(pausedPhase, motion.phase, 0f) }
    }
}

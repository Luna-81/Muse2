package com.blue.hush

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionState
import com.blue.hush.session.StateSample
import com.blue.hush.ui.MeditationGalaxyScreen
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class MeditationGalaxyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun controlsAndMissingSignalRemainUsable() {
        val state = mutableStateOf(SessionState(
            phase = SessionPhase.RUNNING, connected = true, elapsedSeconds = 65,
            calibrationSeconds = 10,
            latestSample = StateSample(65, 0.45, 0.4, 0.15, valid = true, eegBandsAvailable = true),
        ))
        var finished = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                HushTheme {
                    MeditationGalaxyScreen(
                        state.value,
                        onPause = { state.value = state.value.copy(phase = SessionPhase.PAUSED) },
                        onResume = { state.value = state.value.copy(phase = SessionPhase.RUNNING) },
                        onFinish = { finished++ },
                        onVolumeChanged = { state.value = state.value.copy(volume = it) },
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(12_000)
        compose.onNodeWithText("18:55").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithText("Finish").performScrollTo().assertIsDisplayed()
        saveScreenshot("galaxy-calm.png")
        compose.runOnIdle {
            state.value = state.value.copy(latestSample = StateSample(65, 0.15, 0.15, 0.7, valid = true, eegBandsAvailable = true))
        }
        compose.mainClock.advanceTimeBy(12_000)
        saveScreenshot("galaxy-scattered.png")
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Resume").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        val frozen = galaxyCrop()
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(true, frozen.sameAs(galaxyCrop()))
        compose.onNodeWithContentDescription("Adjust volume").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Meditation volume").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Adjust volume").assertIsDisplayed()
        assertEquals(0, finished)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(30_000)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Resume").assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { state.value = state.value.copy(elapsedSeconds = 66) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Waiting for EEG…").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(connected = false) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Reconnecting…").assertIsDisplayed()
        compose.onNodeWithText("Finish").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, finished) }
        compose.onNodeWithText("End session").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test fun landscapeControlsFitAndVolumeCanExpand() {
        val previousOrientation = compose.activity.requestedOrientation
        try {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    HushTheme {
                        MeditationGalaxyScreen(
                            SessionState(phase = SessionPhase.PAUSED, elapsedSeconds = 65),
                            {}, {}, {}, {},
                        )
                    }
                }
            }
            compose.onNodeWithText("18:55").assertIsDisplayed()
            compose.onNodeWithContentDescription("Resume").assertIsDisplayed()
            compose.onNodeWithText("Finish").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Adjust volume").performClick()
            compose.onNodeWithContentDescription("Meditation volume").performScrollTo().assertIsDisplayed()
            saveScreenshot("galaxy-landscape.png")
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousOrientation }
        }
    }

    private fun galaxyCrop(): Bitmap {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        return Bitmap.createBitmap(bitmap, 0, bitmap.height / 4, bitmap.width, bitmap.height / 2)
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

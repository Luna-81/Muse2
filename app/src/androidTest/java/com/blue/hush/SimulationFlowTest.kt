package com.blue.hush

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.blue.hush.service.MeditationService
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionRuntime
import org.junit.Rule
import org.junit.Test

/** Exercises the actual activity/service path, without a live Muse or Bluetooth permission. */
class SimulationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun switchingSessionTracksPreservesPauseAndPersistsLastSelection() {
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                MeditationService.startSimulation(compose.activity, MusicTrack.RAIN, 0f)
            }
            compose.waitUntil(5000) { SessionRuntime.current.phase == SessionPhase.RUNNING }
            val id = SessionRuntime.current.sessionId!!
            compose.runOnUiThread { MeditationService.setTrack(compose.activity, MusicTrack.OCEAN) }
            compose.waitUntil(5000) { SessionRuntime.current.track == MusicTrack.OCEAN }
            compose.runOnUiThread { MeditationService.command(compose.activity, MeditationService.ACTION_PAUSE) }
            compose.waitUntil(5000) { SessionRuntime.current.phase == SessionPhase.PAUSED }
            val elapsed = SessionRuntime.current.elapsedSeconds
            compose.runOnUiThread { MeditationService.setTrack(compose.activity, MusicTrack.FIREPLACE) }
            compose.waitUntil(5000) { SessionRuntime.current.track == MusicTrack.FIREPLACE }
            org.junit.Assert.assertEquals(SessionPhase.PAUSED, SessionRuntime.current.phase)
            org.junit.Assert.assertEquals(elapsed, SessionRuntime.current.elapsedSeconds)
            org.junit.Assert.assertEquals(0f, SessionRuntime.current.volume)
            compose.runOnUiThread { MeditationService.command(compose.activity, MeditationService.ACTION_FINISH) }
            compose.waitUntil(5000) { SessionRuntime.current.phase == SessionPhase.FINISHED }
            com.blue.hush.storage.HushDatabase(compose.activity).use { database ->
                org.junit.Assert.assertEquals(MusicTrack.FIREPLACE,
                    database.loadSummaries().first { it.id == id }.track)
                database.deleteSession(id)
            }
        } finally {
            if (SessionRuntime.current.phase in listOf(SessionPhase.RUNNING, SessionPhase.PAUSED)) {
                compose.runOnUiThread { MeditationService.command(compose.activity, MeditationService.ACTION_FINISH) }
            }
            SessionRuntime.resetToIdle(600, MusicTrack.RAIN, 0.7f)
        }
    }

    @Test fun simulationCanStartPauseFinishAndOpenSavedDetails() {
        try {
            // The Home preview is animated before the service starts.
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Muse 2").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription("Use saved simulation data").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Use saved simulation data").performScrollTo().performClick()
            androidx.test.espresso.Espresso.pressBack()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Start meditation").performScrollTo().performClick()
            // The live frame loop intentionally never idles; advance the render clock explicitly.
            compose.waitUntil(15000) { SessionRuntime.current.elapsedSeconds >= 8 }
            org.junit.Assert.assertNotNull(SessionRuntime.current.latestSample?.calmness)
            org.junit.Assert.assertNotNull(SessionRuntime.current.latestSample?.heartRateBpm)
            org.junit.Assert.assertEquals(5, SessionRuntime.current.latestSample?.algorithmVersion)
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(hasText("Calibrating", substring = true)).assertCountEquals(0)
            compose.onNodeWithContentDescription("Pause").performClick()
            compose.waitUntil(5000) { SessionRuntime.current.phase == SessionPhase.PAUSED }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithContentDescription("Resume").assertIsDisplayed()
            compose.onNodeWithText("Finish").performClick()
            compose.onNodeWithText("End session").performClick()
            compose.waitUntil(5000) { SessionRuntime.current.phase == SessionPhase.FINISHED }
            compose.onNodeWithText("More Details").performScrollTo().performClick()
            compose.onNodeWithText("Session details").assertIsDisplayed()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Session replay"))
            compose.onNodeWithContentDescription("Session replay").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("Finished").assertIsDisplayed()
            compose.onNodeWithText("More Details").assertDoesNotExist()
            compose.onNodeWithText("Results").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Heart Rate: unavailable").assertIsDisplayed()
        } finally {
            if (SessionRuntime.current.phase in listOf(SessionPhase.RUNNING, SessionPhase.PAUSED)) {
                compose.runOnUiThread { MeditationService.command(compose.activity, MeditationService.ACTION_FINISH) }
            }
        }
    }
}

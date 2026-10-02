package com.blue.hush

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.blue.hush.session.*
import com.blue.hush.ui.CalmnessChart
import com.blue.hush.ui.MeditationGalaxyScreen
import com.blue.hush.ui.SessionDetailScreen
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushTheme
import com.blue.hush.replay.ReplayCursor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import java.io.File

class CalmnessChartScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun sample(second: Int, value: Double?) = StateSample(second, 0.4, 0.3, 0.2, 0.9,
        valid = true, eegBandsAvailable = true, calmness = value, algorithmVersion = 1)

    @Test fun replayChartSupportsTappingDraggingAndAccessibleSeekingThroughGaps() {
        val values = (1..100).map { sample(it, if (it == 50) null else 0.6) }
        val cursor = ReplayCursor(values)
        val progress = mutableStateOf(0f)
        compose.activity.runOnUiThread {
            compose.activity.setContent { HushTheme {
                CalmnessChart(values, 100, replaySecond = cursor.sampleAt(progress.value)?.elapsedSeconds,
                    onReplaySecondSelected = { progress.value = cursor.progressAtSecond(it) })
            } }
        }
        val chart = compose.onNodeWithContentDescription("Session replay")
        chart.performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
        compose.runOnIdle { assertTrue(cursor.sampleAt(progress.value)!!.elapsedSeconds in 73..77) }
        chart.performTouchInput { swipe(Offset(width * 0.75f, height / 2f), Offset(width * 0.25f, height / 2f), 500) }
        compose.runOnIdle { assertTrue(cursor.sampleAt(progress.value)!!.elapsedSeconds in 23..27) }
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00:50"))
        compose.runOnIdle { assertNull(cursor.sampleAt(progress.value)!!.calmness) }
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "01:40"))
        saveScreenshot("calmness-replay.png")
    }

    @Test fun liveMissingDataAndFirstMeasuredSecondUseTheSameChart() {
        val state = mutableStateOf(SessionState(phase = SessionPhase.PAUSED, connected = true,
            elapsedSeconds = 0, latestSample = null))
        compose.activity.runOnUiThread {
            compose.activity.setContent { HushTheme { MeditationGalaxyScreen(state.value, {}, {}, {}, {}) } }
        }
        compose.onNodeWithText("Calmness").assertIsDisplayed()
        compose.onNodeWithContentDescription("Calmness trend, no data").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(phase = SessionPhase.RUNNING) }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Calibrating…").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(phase = SessionPhase.PAUSED, elapsedSeconds = 1,
            latestSample = sample(1, 0.7), trendSamples = listOf(sample(1, 0.7))) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Calmness trend, latest 70 out of 100").assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume").assertIsDisplayed()
        saveScreenshot("calmness-live.png")
    }

    @Test fun historyAddsCalmnessAndPreservesRelativeTrendsAndLegacyEmptyState() {
        val values = mutableStateOf(listOf(sample(10, 0.7), sample(11, 0.8)))
        val summary = SessionSummary(1, 0, 11000, 600, 11, MusicTrack.MIST, ResultLabel.STEADY, 2, 2)
        compose.activity.runOnUiThread {
            compose.activity.setContent { HushTheme { SessionDetailScreen(summary, values.value, 0f, {}, {}) } }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Calmness"))
        compose.onNodeWithText("Calmness").assertIsDisplayed()
        compose.onNodeWithContentDescription("Calmness trend, latest 80 out of 100").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Relative trends"))
        compose.onNodeWithText("Relative trends").assertIsDisplayed()
        compose.onNodeWithText("Alpha · Theta · Beta · Stillness").performScrollTo().assertIsDisplayed()
        saveScreenshot("calmness-history.png")
        compose.runOnIdle { values.value = values.value.map { it.copy(calmness = null, algorithmVersion = 0) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No calmness data"))
        compose.onNodeWithText("No calmness data").performScrollTo().assertIsDisplayed()
    }

    @Test fun renderedCurveLeavesGapsAndStillDrawsAnIsolatedPoint() {
        val values = listOf(sample(1, 0.2), sample(2, 0.8), sample(3, null), sample(4, 0.2), sample(5, 0.8))
        compose.activity.runOnUiThread {
            compose.activity.setContent { HushTheme { CalmnessChart(values, 5) } }
        }
        val pixels = compose.onNodeWithContentDescription("Calmness trend, latest 80 out of 100").captureToImage().toPixelMap()
        fun isCurve(x: Int, y: Int): Boolean {
            val color = pixels[x, y]
            val expected = HushColors.Lavender
            return abs(color.red - expected.red) < 0.05 && abs(color.green - expected.green) < 0.05 && abs(color.blue - expected.blue) < 0.05
        }
        val gapX = (pixels.width * 0.6).toInt()
        assertFalse((gapX - 2..gapX + 2).any { x -> (0 until pixels.height).any { y -> isCurve(x, y) } })
        assertTrue((0 until pixels.width).any { x -> (0 until pixels.height).any { y -> isCurve(x, y) } })
        compose.activity.runOnUiThread {
            compose.activity.setContent { HushTheme { CalmnessChart(listOf(sample(1, 0.7)), 1) } }
        }
        val point = compose.onNodeWithContentDescription("Calmness trend, latest 70 out of 100").captureToImage().toPixelMap()
        assertTrue((0 until point.width).any { x -> (0 until point.height).any { y -> abs(point[x, y].blue - HushColors.Lavender.blue) < 0.05 && abs(point[x, y].red - HushColors.Lavender.red) < 0.05 } })
    }

    private fun saveScreenshot(name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

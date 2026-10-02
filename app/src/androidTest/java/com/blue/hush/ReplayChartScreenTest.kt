package com.blue.hush

import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.blue.hush.replay.ReplayCursor
import com.blue.hush.session.*
import com.blue.hush.ui.*
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReplayChartScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val values = (1..100).map { second -> StateSample(second,
        alpha = 0.5, theta = 0.5, beta = 0.5, stillness = 0.5,
        valid = true, eegBandsAvailable = second != 50, heartRateBpm = 110.0,
        calmness = if (second == 50) null else 0.5, algorithmVersion = 5) }

    @Test fun pinchZoomAndTwoFingerPanPreserveSelectionAndRemapSingleFingerSeeking() {
        val cursor = ReplayCursor(values)
        val progress = mutableStateOf(cursor.progressAtSecond(50f))
        compose.activity.runOnUiThread { compose.activity.setContent { HushTheme {
            ReplayChart(values, 100, cursor.sampleAt(progress.value), ReplayMetric.entries.toSet(),
                { _, _ -> }, { progress.value = cursor.progressAtSecond(it) })
        } } }
        val chart = compose.onNodeWithContentDescription("Session replay")
        chart.performTouchInput {
            val y = height / 2f
            down(0, Offset(width * 0.35f, y))
            down(1, Offset(width * 0.65f, y))
            for (step in 1..5) {
                updatePointerTo(0, Offset(width * (0.35f - step * 0.03f), y))
                updatePointerTo(1, Offset(width * (0.65f + step * 0.03f), y))
                move()
            }
            up(0)
            moveTo(1, Offset(width * 0.9f, y))
            up(1)
        }
        compose.runOnIdle { assertEquals(50, cursor.sampleAt(progress.value)!!.elapsedSeconds) }
        compose.onNodeWithText("00:25").assertExists()
        compose.onNodeWithText("01:15").assertExists()
        chart.performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
        compose.runOnIdle { assertTrue(cursor.sampleAt(progress.value)!!.elapsedSeconds in 61..64) }
        val selected = cursor.sampleAt(progress.value)!!.elapsedSeconds
        chart.performTouchInput {
            val y = height / 2f
            down(0, Offset(width * 0.4f, y))
            down(1, Offset(width * 0.6f, y))
            for (step in 1..4) {
                updatePointerTo(0, Offset(width * (0.4f + step * 0.04f), y))
                updatePointerTo(1, Offset(width * (0.6f + step * 0.04f), y))
                move()
            }
            up(0); up(1)
        }
        compose.runOnIdle { assertEquals(selected, cursor.sampleAt(progress.value)!!.elapsedSeconds) }
        compose.onNodeWithText("00:25").assertDoesNotExist()
        val reset = chart.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Reset zoom" }
        compose.runOnIdle { assertTrue(reset.action()) }
        compose.onNodeWithText("00:00").assertExists()
        compose.onNodeWithText("01:40").assertExists()
    }

    @Test fun singleFingerPansPlotWhileHandleDragSeeks() {
        val cursor = ReplayCursor(values)
        val progress = mutableStateOf(cursor.progressAtSecond(50f))
        compose.activity.runOnUiThread { compose.activity.setContent { HushTheme {
            ReplayChart(values, 100, cursor.sampleAt(progress.value), ReplayMetric.entries.toSet(),
                { _, _ -> }, { progress.value = cursor.progressAtSecond(it) })
        } } }
        val chart = compose.onNodeWithContentDescription("Session replay")
        val zoom = chart.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Zoom in" }
        compose.runOnIdle { assertTrue(zoom.action()) }
        compose.onNodeWithText("00:25").assertExists()
        chart.performTouchInput { swipe(Offset(width * 0.4f, height * 0.25f), Offset(width * 0.6f, height * 0.25f), 300) }
        compose.runOnIdle { assertEquals(50, cursor.sampleAt(progress.value)!!.elapsedSeconds) }
        compose.onNodeWithText("00:25").assertDoesNotExist()
        chart.performTouchInput { click(Offset(width / 2f, height / 2f)) }
        var centerSecond = 0
        compose.runOnIdle { centerSecond = cursor.sampleAt(progress.value)!!.elapsedSeconds; assertTrue(centerSecond < 50) }
        chart.performTouchInput { swipe(Offset(width / 2f, height / 2f), Offset(width * 0.7f, height / 2f), 300) }
        compose.runOnIdle { assertTrue(cursor.sampleAt(progress.value)!!.elapsedSeconds > centerSecond + 5) }
    }

    @Test fun togglesHideValuesAndAllHiddenStillSupportsReplay() {
        val cursor = ReplayCursor(values)
        val progress = mutableStateOf(0f)
        compose.activity.runOnUiThread { compose.activity.setContent { HushTheme {
            var visible by remember { mutableStateOf(ReplayMetric.entries.toSet()) }
            ReplayChart(values, 100, cursor.sampleAt(progress.value), visible,
                { metric, checked -> visible = if (checked) visible + metric else visible - metric },
                { progress.value = cursor.progressAtSecond(it) })
        } } }
        ReplayMetric.entries.forEach { compose.onNode(hasText(it.title) and isToggleable()).assertIsOn() }
        val chart = compose.onNodeWithContentDescription("Session replay")
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            "00:50, Calmness —, Alpha —, Theta —, Beta —, Heart Rate 110 BPM, Stability 50"))
        compose.onNodeWithText("Calmness —").assertDoesNotExist()
        ReplayMetric.entries.forEach { compose.onNode(hasText(it.title) and isToggleable()).performClick().assertIsOff() }
        compose.onNodeWithText("Calmness —").assertDoesNotExist()
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00:50"))
        chart.performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
        compose.runOnIdle { assertTrue(cursor.sampleAt(progress.value)!!.elapsedSeconds in 73..77) }
        chart.performTouchInput { swipe(Offset(width * 0.75f, height / 2f), Offset(width * 0.25f, height / 2f), 500) }
        compose.runOnIdle { assertTrue(cursor.sampleAt(progress.value)!!.elapsedSeconds in 23..27) }
        chart.performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "01:40"))
    }

    @Test fun historySelectionSurvivesRestorationAndResetsForAnotherSession() {
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        val id = mutableStateOf(1L)
        restoration.setContent { HushTheme {
            val summary = SessionSummary(id.value, 0, 100000, 100, 100, MusicTrack.RAIN, ResultLabel.STEADY, 100, 100)
            SessionDetailScreen(summary, values, 0f, {}, {})
        } }
        compose.mainClock.advanceTimeBy(32)
        fun toggle() = compose.onNode(hasText("Alpha") and isToggleable())
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        toggle().performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(32)
        toggle().assertIsOff()
        val zoom = compose.onNodeWithContentDescription("Session replay").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == "Zoom in" }
        compose.runOnIdle { assertTrue(zoom.action()) }
        compose.mainClock.advanceTimeBy(32)
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(32)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        toggle().performScrollTo().assertIsOff()
        compose.onNodeWithText("00:25").assertExists()
        compose.onNodeWithText("01:15").assertExists()
        compose.runOnIdle { id.value = 2L }
        compose.mainClock.advanceTimeBy(32)
        toggle().performScrollTo().assertIsOn()
        compose.onNodeWithText("00:00").assertExists()
    }

    @Test fun narrowLargeFontAndLandscapeKeepCoincidentLabelsInsidePlot() {
        val width = mutableStateOf(320.dp)
        val fontScale = mutableStateOf(1.6f)
        val second = mutableStateOf(1)
        compose.activity.runOnUiThread { compose.activity.setContent { HushTheme {
            val density = LocalDensity.current
            val viewportDensity = density.density * if (width.value > 320.dp) 0.55f else 1f
            CompositionLocalProvider(LocalDensity provides Density(viewportDensity, fontScale.value)) {
                Box(Modifier.width(width.value)) {
                    ReplayChart(values, 100, values[second.value - 1], ReplayMetric.entries.toSet(), { _, _ -> }, {})
                }
            }
        } } }
        val chart = compose.onNodeWithContentDescription("Session replay")
        chart.assertIsDisplayed()
        saveScreenshot("replay-narrow-start.png")
        compose.runOnIdle { second.value = 100 }
        saveScreenshot("replay-narrow-end.png")
        compose.runOnIdle { width.value = 600.dp; fontScale.value = 1f; second.value = 50 }
        saveScreenshot("replay-wide-gap.png")
        compose.onNodeWithText("Calmness —").assertDoesNotExist()
    }

    private fun saveScreenshot(name: String) {
        val context = compose.activity
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

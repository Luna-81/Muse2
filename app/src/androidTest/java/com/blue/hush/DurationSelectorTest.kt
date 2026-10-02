package com.blue.hush

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
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
import com.blue.hush.ui.DurationSelector
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class DurationSelectorTest {
    @get:Rule val compose = createComposeRule()
    private var selectedSeconds = 900

    private fun show(simulation: Boolean = false, largeText: Boolean = false) {
        if (simulation) selectedSeconds = 600
        compose.setContent {
            var seconds by remember { mutableIntStateOf(selectedSeconds) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                HushTheme {
                    Box(Modifier.width(320.dp)) {
                        DurationSelector(seconds, simulation) { seconds = it; selectedSeconds = it }
                    }
                }
            }
        }
    }

    @Test fun inlineWheelDefaultsToFifteenAndPresetsPreserveItsPosition() {
        show()
        compose.onNodeWithContentDescription("Custom duration").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "15 min"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "duration-inline.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("5 min").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(300, selectedSeconds) }
        compose.onNodeWithText("10 min").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(600, selectedSeconds) }
        compose.onNodeWithContentDescription("Custom duration").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(900, selectedSeconds) }
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test fun swipeChangesDurationImmediatelyAndPresetStopsTheWheel() {
        show()
        compose.onNodeWithContentDescription("Custom duration").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertTrue(selectedSeconds > 900) }
        compose.onNodeWithContentDescription("Custom duration").assertIsSelected()
        compose.onNodeWithText("5 min").performClick().assertIsSelected()
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(300, selectedSeconds) }
    }

    @Test fun wheelSupportsBothBoundsWithLargeTextAndImmediateSelection() {
        show(largeText = true)
        listOf(1, 60, 5).forEach { minutes ->
            compose.onNodeWithContentDescription("Custom duration").performSemanticsAction(SemanticsActions.SetProgress) { it(minutes.toFloat()) }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Custom duration").assertIsDisplayed().assertIsSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$minutes min"))
            compose.runOnIdle { assertEquals(minutes * 60, selectedSeconds) }
        }
        compose.onNodeWithText("10 min").assertIsDisplayed()
    }

    @Test fun simulationKeepsTenMinutesAndDisablesOtherChoices() {
        show(simulation = true)
        compose.onNodeWithText("10 min").assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("5 min").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Custom duration").assertIsNotEnabled()
            .performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(600, selectedSeconds) }
    }
}

package com.blue.hush

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.blue.hush.ui.DurationSelector
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DurationSelectorTest {
    @get:Rule val compose = createComposeRule()
    private var selectedSeconds = 600

    private fun show(simulation: Boolean = false, largeText: Boolean = false) {
        compose.setContent {
            var seconds by remember { mutableIntStateOf(selectedSeconds) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                HushTheme {
                    DurationSelector(seconds, simulation) { seconds = it; selectedSeconds = it }
                }
            }
        }
    }

    @Test fun presetsAndCustomWheelApplyMinutesWithoutChangingOnCancel() {
        show()
        compose.onNodeWithText("5 min").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(300, selectedSeconds) }
        compose.onNodeWithText("10 min").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Custom duration").performClick()
        compose.onNodeWithContentDescription("Duration in minutes")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 min"))
            .performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Duration in minutes")
            .assert(SemanticsMatcher("Wheel moved") { it.config[SemanticsProperties.StateDescription] != "20 min" })
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(600, selectedSeconds) }
        compose.onNodeWithContentDescription("Custom duration").performClick()
        compose.onNodeWithContentDescription("Duration in minutes").performSemanticsAction(SemanticsActions.SetProgress) { it(37f) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Duration in minutes")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "37 min"))
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(2220, selectedSeconds) }
        compose.onNodeWithContentDescription("Custom duration").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Duration in minutes")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "37 min"))
    }

    @Test fun wheelSupportsBothBoundsWithLargeText() {
        show(largeText = true)
        compose.onNodeWithContentDescription("Custom duration").performClick()
        listOf(1, 60).forEach { minutes ->
            compose.onNodeWithContentDescription("Duration in minutes").performSemanticsAction(SemanticsActions.SetProgress) { it(minutes.toFloat()) }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Duration in minutes")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$minutes min"))
        }
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(3600, selectedSeconds) }
    }

    @Test fun simulationKeepsTenMinutesAndDisablesOtherChoices() {
        show(simulation = true)
        compose.onNodeWithText("10 min").assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("5 min").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Custom duration").assertIsNotEnabled()
    }
}

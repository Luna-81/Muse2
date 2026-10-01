package com.blue.hush

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.blue.hush.session.*
import com.blue.hush.ui.HistoryScreen
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryDeletionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun swipeRequiresConfirmationAndCancelKeepsCardUsable() {
        val summary = SessionSummary(BUNDLED_SIMULATION_SESSION_ID, 0, 600_000, 600, 600,
            MusicTrack.MIST, ResultLabel.STEADY, 600, 600)
        var opens = 0
        val deleted = mutableListOf<Long>()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                var history by remember { mutableStateOf(listOf(summary)) }
                HushTheme {
                    HistoryScreen(history, onOpen = { opens++ }, onDelete = {
                        deleted.add(it.id)
                        history = history.filterNot { saved -> saved.id == it.id }
                    })
                }
            }
        }

        val card = compose.onNodeWithText("Saved simulation")
        card.performTouchInput { swipeRight() }
        compose.onNodeWithText("Delete session?").assertDoesNotExist()
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete session?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Long>(), deleted) }
        compose.onNodeWithText("Cancel").performClick()
        card.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opens) }

        card.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete session?").assertIsDisplayed()
        compose.onAllNodesWithText("Delete").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Saved simulation").assertDoesNotExist()
        compose.onNodeWithText("Complete a session to see it here.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(summary.id), deleted) }
    }
}

package com.blue.hush

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.blue.hush.session.*
import com.blue.hush.ui.HistoryScreen
import com.blue.hush.ui.theme.HushTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryDeletionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun minuteTimeAndCalmRemainVisibleOnNarrowScreenWithLargeText() {
        val timestamp = 1790940658496L
        val summary = SessionSummary(1L, timestamp, timestamp + 600_000, 600, 600,
            MusicTrack.RAIN, ResultLabel.STEADY, 600, 600, calm = 82.6)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    HushTheme {
                        Box(Modifier.requiredWidth(320.dp)) {
                            HistoryScreen(listOf(summary), onOpen = {}, onDelete = {})
                        }
                    }
                }
            }
        }
        val time = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(timestamp))
        val date = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(timestamp))
        compose.onNodeWithText("$date · $time").assertIsDisplayed()
        compose.onNodeWithText("10:00 · Rain").assertIsDisplayed()
        compose.onNodeWithText("Calm").assertIsDisplayed()
        compose.onNodeWithText("83").assertIsDisplayed()
        compose.onNodeWithContentDescription("Calm score: 83 out of 100").assertIsDisplayed()
    }

    @Test fun swipeRequiresConfirmationAndCancelKeepsCardUsable() {
        val summary = SessionSummary(1L, 0, 600_000, 600, 600,
            MusicTrack.RAIN, ResultLabel.STEADY, 600, 600)
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

        val card = compose.onNodeWithText("10:00 · Mist", substring = true)
        card.performTouchInput { swipeRight() }
        compose.onNodeWithText("Delete session?").assertDoesNotExist()
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete session?").assertIsDisplayed()
        compose.onNodeWithText("This session and its replay data will be permanently deleted.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Long>(), deleted) }
        compose.onNodeWithText("Cancel").performClick()
        card.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opens) }

        card.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete session?").assertIsDisplayed()
        compose.onAllNodesWithText("Delete").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("10:00 · Mist", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Complete a session to see it here.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(summary.id), deleted) }
    }
}

package com.blue.hush.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blue.hush.session.SessionSummary
import com.blue.hush.ui.ChartHelpButton
import com.blue.hush.ui.components.HushPanel
import com.blue.hush.ui.components.MindprintThumbnail
import com.blue.hush.ui.formatDate
import com.blue.hush.ui.formatDuration
import com.blue.hush.ui.formatTime
import com.blue.hush.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun HistoryScreen(
    history: List<SessionSummary>,
    onOpen: (SessionSummary) -> Unit,
    onDelete: (SessionSummary) -> Unit,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = history.firstOrNull { it.id == pendingDeleteId }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete session?") },
            text = { Text("This session and its replay data will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    onDelete(pendingDelete)
                }) { Text("Delete", color = HushColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
            containerColor = HushColors.Surface,
        )
    }

    LazyColumn(
        Modifier.widthIn(max = HushSpace.contentWidth).fillMaxSize(),
        contentPadding = PaddingValues(HushSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HushSpace.lg),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("History", style = MaterialTheme.typography.headlineLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChartHelpButton()
                }
            }
        }

        if (history.isEmpty()) item {
            HushPanel(Modifier.fillMaxWidth()) {
                Text("Your quiet moments, collected.")
                Text("Complete a session to see it here.", color = HushColors.Muted)
            }
        }

        items(history, key = { it.id }) { summary ->
            val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f })
            LaunchedEffect(dismissState.settledValue) {
                if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
                    pendingDeleteId = summary.id
                    dismissState.reset()
                }
            }
            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.animateItem().clip(HushShapes.Panel),
                enableDismissFromStartToEnd = false,
                gesturesEnabled = pendingDelete == null,
                backgroundContent = {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = HushColors.Error.copy(alpha = 0.15f),
                        shape = HushShapes.Panel,
                    ) {
                        Box(
                            Modifier.fillMaxSize().padding(HushSpace.lg),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text("Delete", color = HushColors.Error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            ) {
                Surface(
                    onClick = { onOpen(summary) },
                    shape = HushShapes.Panel,
                    color = HushColors.Surface,
                    border = BorderStroke(1.dp, HushColors.Border),
                ) {
                    Row(
                        Modifier.fillMaxWidth().semantics {
                            customActions = listOf(CustomAccessibilityAction("Delete session") {
                                pendingDeleteId = summary.id
                                true
                            })
                        }.padding(HushSpace.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HushSpace.sm),
                    ) {
                        MindprintThumbnail(summary.id, Modifier.size(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                            Text(
                                "${formatDate(summary.startedAt)} \u00B7 ${formatTime(summary.startedAt)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${formatDuration(summary.actualSeconds)} \u00B7 ${summary.track.title}",
                                color = HushColors.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (summary.resultSampleCount >= 2) summary.result.title else "Not enough signal",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Column(
                            Modifier.widthIn(min = 40.dp).semantics {
                                contentDescription = "Calm score: ${summary.calm?.roundToInt()?.let { "$it out of 100" } ?: "unavailable"}"
                            },
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(HushSpace.xs),
                        ) {
                            Text("Calm", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                            Text(
                                summary.calm?.roundToInt()?.toString() ?: "\u2014",
                                style = MaterialTheme.typography.headlineSmall,
                                color = HushColors.Lavender,
                            )
                        }
                    }
                }
            }
        }
    }
}
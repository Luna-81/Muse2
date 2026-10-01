@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blue.hush.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blue.hush.session.SessionScores
import com.blue.hush.session.SessionState
import com.blue.hush.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun CompletionScreen(
    state: SessionState, motion: GalaxyMotion, showResults: Boolean,
    onShowResults: () -> Unit, onDismissResults: () -> Unit, onBack: () -> Unit,
    detailAvailable: Boolean, onDetails: () -> Unit,
) {
    Page("Finished", onBack) {
        BoxWithConstraints(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxSize()) {
            // Leave room above the sheet for the full-session chart on portrait screens.
            val visualHeight = (maxHeight * 0.34f).coerceIn(120.dp, 440.dp)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(HushSpace.lg),
                verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
                item { ParticlePanel(state.latestSample, state.latestSample?.valid != true, motion, maxHeight = visualHeight) }
                item { HushPanel(Modifier.fillMaxWidth()) {
                    CalmnessChart(state.trendSamples, state.elapsedSeconds, plotHeight = 96.dp)
                } }
                item { PrimaryAction("Results", onShowResults) }
            }
        }
    }
    if (showResults) {
        ModalBottomSheet(
            onDismissRequest = onDismissResults,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = HushColors.Surface,
        ) {
            Column(Modifier.testTag("completionResults").widthIn(max = HushSpace.contentWidth).fillMaxWidth()
                .align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState())
                .padding(horizontal = HushSpace.lg).padding(bottom = HushSpace.lg),
                verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
                SessionScoreSummary(state.elapsedSeconds, state.scores)
                TextButton(onClick = onDetails, enabled = detailAvailable, modifier = Modifier.fillMaxWidth()) {
                    Text("More Details")
                }
            }
        }
    }
}

@Composable
internal fun SessionScoreSummary(seconds: Int, scores: SessionScores) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
            Column(Modifier.weight(1f)) {
                Text("Total Time", style = MaterialTheme.typography.labelLarge, color = HushColors.Muted)
                Text(formatDuration(seconds), style = MaterialTheme.typography.displayLarge)
            }
            Surface(shape = CircleShape, color = HushColors.SurfaceRaised,
                border = BorderStroke(1.dp, HushColors.Accent)) {
                Box(Modifier.defaultMinSize(minWidth = 80.dp, minHeight = 80.dp).padding(HushSpace.sm)
                    .semantics { contentDescription = "Overall grade: ${scores.grade ?: "unavailable"}" },
                    contentAlignment = Alignment.Center) {
                    Text(scores.grade ?: "—", style = MaterialTheme.typography.headlineLarge, color = HushColors.Accent)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
            listOf("Calm" to scores.calm, "Focus" to scores.focus, "Stability" to scores.stability).forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Surface(Modifier.fillMaxWidth(), shape = HushShapes.Control, color = HushColors.SurfaceRaised) {
                        Box(Modifier.heightIn(min = 56.dp).padding(HushSpace.sm)
                            .semantics { contentDescription = "$label score: ${value?.roundToInt()?.let { "$it out of 100" } ?: "unavailable"}" },
                            contentAlignment = Alignment.Center) {
                            Text(value?.roundToInt()?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

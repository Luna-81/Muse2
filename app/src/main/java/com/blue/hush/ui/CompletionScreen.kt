@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blue.hush.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
        Column {
            Text("Total Time", style = MaterialTheme.typography.labelLarge, color = HushColors.Muted)
            Text(formatDuration(seconds), style = MaterialTheme.typography.displayLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
            listOf("Calm" to scores.calm, "Stability" to scores.stability, "Heart Rate" to scores.heartRateBpm).forEach { (label, value) ->
                val isHeartRate = label == "Heart Rate"
                val rounded = value?.roundToInt()
                val description = if (isHeartRate) "Heart Rate: ${rounded?.let { "$it BPM" } ?: "unavailable"}"
                    else "$label score: ${rounded?.let { "$it out of 100" } ?: "unavailable"}"
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Surface(Modifier.fillMaxWidth(), shape = HushShapes.Control, color = HushColors.SurfaceRaised) {
                        Box(Modifier.heightIn(min = 56.dp).padding(HushSpace.sm)
                            .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(rounded?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
                                if (isHeartRate) Text("BPM", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

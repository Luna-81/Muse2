package com.blue.hush.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.replay.ReplayCursor
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample
import com.blue.hush.ui.DataCoverageSummary
import com.blue.hush.ui.Page
import com.blue.hush.ui.charts.ReplayChart
import com.blue.hush.ui.charts.ReplayMetric
import com.blue.hush.ui.components.HushPanel
import com.blue.hush.ui.components.ParticlePanel
import com.blue.hush.ui.formatDate
import com.blue.hush.ui.formatDuration
import com.blue.hush.ui.formatTime
import com.blue.hush.ui.galaxy.galaxyAgitation
import com.blue.hush.ui.theme.*

@Composable
internal fun SessionDetailScreen(
    summary: SessionSummary,
    samples: List<StateSample>,
    progress: Float,
    onBack: () -> Unit,
    onProgress: (Float) -> Unit,
) {
    var visibleMask by rememberSaveable(summary.id) { mutableStateOf((1 shl ReplayMetric.entries.size) - 1) }
    val visibleMetrics = ReplayMetric.entries.filter { visibleMask and (1 shl it.ordinal) != 0 }.toSet()
    val cursor = remember(samples) { ReplayCursor(samples) }
    val scores = remember(samples) { SessionScoreCalculator.calculate(samples) }
    val sample = cursor.sampleAt(progress)
    val retainedSample = remember(samples, sample) {
        samples.lastOrNull { it.elapsedSeconds <= (sample?.elapsedSeconds ?: 0) && galaxyAgitation(it) != null }
    }

    Page("Session details", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = HushSpace.xs, bottom = HushSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HushSpace.xl),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth().padding(horizontal = HushSpace.xl),
                        verticalArrangement = Arrangement.spacedBy(HushSpace.sm),
                    ) {
                        Text(
                            "${formatDate(summary.startedAt)} \u00B7 ${formatTime(summary.startedAt)} \u00B7 ${formatDuration(summary.actualSeconds)}",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = HushColors.Muted,
                        )
                        HushPanel(Modifier.fillMaxWidth()) {
                            SessionScoreSummary(summary.actualSeconds, scores, compact = true)
                        }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth().padding(horizontal = HushSpace.xl)) {
                        ParticlePanel(sample, sample?.valid != true, animate = true, retainedSample = retainedSample)
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HushPanel(
                        Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth()
                            .padding(horizontal = HushSpace.xs),
                        contentPadding = PaddingValues(horizontal = HushSpace.sm, vertical = HushSpace.xs),
                    ) {
                        key(summary.id) {
                            ReplayChart(
                                samples, summary.actualSeconds, sample, visibleMetrics,
                                onMetricChanged = { metric, checked ->
                                    val bit = 1 shl metric.ordinal
                                    visibleMask = if (checked) visibleMask or bit else visibleMask and bit.inv()
                                },
                                onReplaySecondSelected = { second -> onProgress(cursor.progressAtSecond(second)) },
                                autoPlay = true,
                            )
                        }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HushPanel(
                        Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth()
                            .padding(horizontal = HushSpace.xs),
                    ) {
                        DataCoverageSummary(samples, summary.actualSeconds)
                    }
                }
            }
        }
    }
}
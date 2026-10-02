package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushSpace
import java.util.Locale

internal fun StateSample.chartCalmness(): Double? = calmness?.takeIf { valid && it.isFinite() && it in 0.0..1.0 }

// Horizontal endpoint tangents round corners without overshooting either recorded value.
internal fun Path.smoothLineTo(previous: Offset, next: Offset) {
    val handle = (next.x - previous.x) / 3f
    cubicTo(previous.x + handle, previous.y, next.x - handle, next.y, next.x, next.y)
}

// A subdued dashed bridge is visual interpolation, not an accepted measurement.
internal fun DrawScope.drawChartBridge(from: Offset, to: Offset, color: Color, width: Float) {
    if (to.x <= from.x) return
    val bridge = Path().apply {
        moveTo(from.x, from.y)
        smoothLineTo(from, to)
    }
    drawPath(bridge, color.copy(alpha = 0.45f), style = Stroke(width, cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
}

/** Uses recorded values; visually bridges gaps without adding statistical samples. */
@Composable
internal fun CalmnessChart(
    samples: List<StateSample>, elapsedSeconds: Int, plotHeight: Dp = 96.dp,
    replaySecond: Int? = null, onReplaySecondSelected: ((Float) -> Unit)? = null,
) {
    val currentOnReplaySelected by rememberUpdatedState(onReplaySecondSelected)
    val latest = remember(samples) { samples.lastOrNull { it.chartCalmness() != null }?.chartCalmness() }
    val description = if (latest == null) "Calmness trend, no data" else "Calmness trend, latest ${String.format(Locale.US, "%.0f", latest * 100)} out of 100"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Calmness", style = MaterialTheme.typography.labelMedium, color = HushColors.Muted)
            replaySecond?.let { Text(formatDuration(it), style = MaterialTheme.typography.labelMedium, color = HushColors.Accent) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
            Column(Modifier.height(plotHeight), verticalArrangement = Arrangement.SpaceBetween) {
                Text("100", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                Text("0", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
            }
            Box(Modifier.weight(1f).height(plotHeight), contentAlignment = Alignment.Center) {
                val replayModifier = if (onReplaySecondSelected == null) Modifier.semantics { contentDescription = description } else Modifier
                    .semantics {
                        this[SemanticsProperties.ContentDescription] = listOf("Session replay", description)
                        stateDescription = formatDuration(replaySecond ?: 0)
                        progressBarRangeInfo = ProgressBarRangeInfo((replaySecond ?: 0).toFloat(), 0f..elapsedSeconds.coerceAtLeast(1).toFloat())
                        setProgress { value ->
                            if (!value.isFinite()) false else {
                                currentOnReplaySelected?.invoke(value.coerceIn(0f, elapsedSeconds.coerceAtLeast(0).toFloat()))
                                true
                            }
                        }
                    }
                    .pointerInput(elapsedSeconds) {
                        val inset = 8.dp.toPx()
                        fun seek(x: Float) {
                            currentOnReplaySelected?.invoke(((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f) * elapsedSeconds)
                        }
                        detectHorizontalDragGestures(onDragStart = { seek(it.x) }) { change, _ ->
                            change.consume()
                            seek(change.position.x)
                        }
                    }
                    .pointerInput(elapsedSeconds) {
                        val inset = 8.dp.toPx()
                        detectTapGestures { position ->
                            currentOnReplaySelected?.invoke(((position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f) * elapsedSeconds)
                        }
                    }
                Canvas(Modifier.fillMaxSize().then(replayModifier)) {
                    // Leave enough room for the replay handle at both timeline endpoints.
                    val inset = if (onReplaySecondSelected != null) 8.dp.toPx() else 3.dp.toPx()
                    val plotWidth = (size.width - 2 * inset).coerceAtLeast(0f)
                    val plotHeightPx = (size.height - 2 * inset).coerceAtLeast(0f)
                    for (fraction in listOf(0f, 0.5f, 1f)) {
                        val y = inset + fraction * plotHeightPx
                        drawLine(HushColors.Border.copy(alpha = 0.45f), Offset(inset, y), Offset(inset + plotWidth, y), strokeWidth = 1.dp.toPx())
                    }
                    var path: Path? = null
                    var previousPoint: Offset? = null
                    var pointCount = 0
                    var previousSecond: Int? = null
                    var lastTrustedPoint: Offset? = null
                    var lastTrustedSecond: Int? = null
                    fun flush() {
                        if (pointCount == 1) previousPoint?.let { drawCircle(HushColors.Lavender, 2.dp.toPx(), it) }
                        else path?.let { drawPath(it, HushColors.Lavender, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
                        path = null
                        previousPoint = null
                        pointCount = 0
                    }
                    for (sample in samples) {
                        val value = sample.chartCalmness()
                        if (value == null) { flush(); previousSecond = null; continue }
                        if (previousSecond != null && sample.elapsedSeconds != previousSecond + 1) flush()
                        val point = Offset(inset + sample.elapsedSeconds.toFloat() / elapsedSeconds.coerceAtLeast(1) * plotWidth,
                            inset + (1 - value.toFloat()) * plotHeightPx)
                        if (lastTrustedSecond != null && sample.elapsedSeconds > lastTrustedSecond!! + 1) {
                            lastTrustedPoint?.let { drawChartBridge(it, point, HushColors.Lavender, 2.dp.toPx()) }
                        }
                        if (path == null) path = Path().apply { moveTo(point.x, point.y) }
                        else previousPoint?.let { path?.smoothLineTo(it, point) }
                        previousPoint = point
                        pointCount++
                        previousSecond = sample.elapsedSeconds
                        lastTrustedPoint = point
                        lastTrustedSecond = sample.elapsedSeconds
                    }
                    flush()
                    // Hold the last known level decoratively while waiting for a new trusted value.
                    if (lastTrustedSecond != null && lastTrustedSecond!! < elapsedSeconds) {
                        lastTrustedPoint?.let { drawChartBridge(it, Offset(inset + plotWidth, it.y), HushColors.Lavender, 2.dp.toPx()) }
                    }
                    replaySecond?.let { second ->
                        val x = inset + (second.toFloat() / elapsedSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) * plotWidth
                        drawLine(HushColors.Accent, Offset(x, inset), Offset(x, inset + plotHeightPx), 2.dp.toPx())
                        val handle = Offset(x, size.height / 2)
                        drawCircle(HushColors.Surface, 7.dp.toPx(), handle)
                        drawCircle(HushColors.Accent, 5.dp.toPx(), handle)
                    }
                }
                if (latest == null) Text("No calmness data", style = MaterialTheme.typography.bodySmall, color = HushColors.Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
            Text(formatDuration(elapsedSeconds), style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
        }
    }
}

package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
internal fun CalmnessChart(samples: List<StateSample>, elapsedSeconds: Int, plotHeight: Dp = 96.dp) {
    val latest = remember(samples) { samples.lastOrNull { it.chartCalmness() != null }?.chartCalmness() }
    val description = if (latest == null) "Calmness trend, no data" else "Calmness trend, latest ${String.format(Locale.US, "%.0f", latest * 100)} out of 100"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
        Text("Calmness", style = MaterialTheme.typography.labelMedium, color = HushColors.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
            Column(Modifier.height(plotHeight), verticalArrangement = Arrangement.SpaceBetween) {
                Text("100", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                Text("0", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
            }
            Box(Modifier.weight(1f).height(plotHeight), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                    val inset = 3.dp.toPx()
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

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushSpace
import java.util.Locale

internal fun StateSample.chartCalmness(): Double? = calmness?.takeIf { valid && it.isFinite() && it in 0.0..1.0 }

/** Shares persisted values and time coordinates with the live chart; never interpolates gaps. */
@Composable
internal fun CalmnessChart(samples: List<StateSample>, elapsedSeconds: Int, plotHeight: Dp = 96.dp, calibrating: Boolean = false) {
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
                    var previousSecond: Int? = null
                    fun flush() { path?.let { drawPath(it, HushColors.Lavender, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)) }; path = null }
                    for (sample in samples) {
                        val value = sample.chartCalmness()
                        if (value == null) { flush(); previousSecond = null; continue }
                        if (previousSecond != null && sample.elapsedSeconds != previousSecond + 1) flush()
                        val point = Offset(inset + sample.elapsedSeconds.toFloat() / elapsedSeconds.coerceAtLeast(1) * plotWidth,
                            inset + (1 - value.toFloat()) * plotHeightPx)
                        if (path == null) path = Path().apply { moveTo(point.x, point.y) } else path?.lineTo(point.x, point.y)
                        drawCircle(HushColors.Lavender, 1.5.dp.toPx(), point)
                        previousSecond = sample.elapsedSeconds
                    }
                    flush()
                }
                if (latest == null && !calibrating) Text("No calmness data", style = MaterialTheme.typography.bodySmall, color = HushColors.Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
            Text(formatDuration(elapsedSeconds), style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
        }
    }
}

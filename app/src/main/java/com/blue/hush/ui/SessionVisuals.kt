package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors

@Composable
internal fun ParticlePanel(
    sample: StateSample?, dataGap: Boolean, motion: GalaxyMotion? = null,
    maxHeight: Dp = 440.dp,
) {
    // Preserve the historical band mapping only for rows predating composite processing.
    val recordedSample = if (sample?.algorithmVersion == 0) sample.copy(eegBandsAvailable = sample.valid) else sample
    val visual = motion ?: remember(recordedSample) {
        GalaxyMotion().apply { showRecordedSample(recordedSample) }
    }
    Card(shape = com.blue.hush.ui.theme.HushShapes.Panel) {
        Box(Modifier.fillMaxWidth().heightIn(max = maxHeight).aspectRatio(1f), contentAlignment = Alignment.Center) {
            GalaxyParticleField(recordedSample, dataGap, paused = true,
                modifier = Modifier.fillMaxSize(), state = visual)
            if (dataGap || galaxyAgitation(recordedSample) == null) Text("Data gap", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TrendChart(samples: List<StateSample>) {
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        if (samples.count { it.valid } < 2) return@Canvas
        val colors = HushColors.Trends
        val values = listOf<(StateSample) -> Double?>({ it.alpha }, { it.theta }, { it.beta }, { it.stillness })
        values.forEachIndexed { seriesIndex, selector ->
            var path: Path? = null
            var previousPoint: Offset? = null
            var previousSecond: Int? = null
            var lastTrustedPoint: Offset? = null
            var lastTrustedSecond: Int? = null
            var pointCount = 0
            fun flush() {
                if (pointCount == 1) previousPoint?.let { drawCircle(colors[seriesIndex], 2f, it) }
                else path?.let { drawPath(it, colors[seriesIndex], style = Stroke(width = 4f, cap = StrokeCap.Round)) }
                path = null
                previousPoint = null
                previousSecond = null
                pointCount = 0
            }
            samples.forEachIndexed { index, sample ->
                val value = selector(sample)
                if (!sample.valid || value == null || !value.isFinite()) {
                    flush()
                    return@forEachIndexed
                }
                val x = index.toFloat() / (samples.lastIndex).coerceAtLeast(1) * size.width
                val y = size.height - value.toFloat().coerceIn(0f, 1f) * size.height
                if (previousSecond != null && sample.elapsedSeconds != previousSecond!! + 1) flush()
                val point = Offset(x, y)
                if (lastTrustedSecond != null && sample.elapsedSeconds > lastTrustedSecond!! + 1) {
                    lastTrustedPoint?.let { drawChartBridge(it, point, colors[seriesIndex], 4f) }
                }
                if (path == null) path = Path().also { it.moveTo(x, y) }
                else previousPoint?.let { path?.smoothLineTo(it, point) }
                previousPoint = point
                previousSecond = sample.elapsedSeconds
                pointCount++
                lastTrustedPoint = point
                lastTrustedSecond = sample.elapsedSeconds
            }
            flush()
            if (lastTrustedSecond != null && lastTrustedSecond!! < (samples.lastOrNull()?.elapsedSeconds ?: 0)) {
                lastTrustedPoint?.let { drawChartBridge(it, Offset(size.width, it.y), colors[seriesIndex], 4f) }
            }
        }
    }
}

@Composable
internal fun HushNavIcon(tab: AppTab) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(1.5.dp.toPx())
        if (tab == AppTab.HISTORY) {
            drawCircle(color, size.minDimension * 0.4f, style = stroke)
            drawLine(color, center, Offset(center.x, size.height * 0.24f), strokeWidth = stroke.width)
            drawLine(color, center, Offset(size.width * 0.68f, size.height * 0.6f), strokeWidth = stroke.width)
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.45f)
                lineTo(size.width * 0.5f, size.height * 0.1f)
                lineTo(size.width * 0.9f, size.height * 0.45f)
                lineTo(size.width * 0.9f, size.height * 0.9f)
                lineTo(size.width * 0.1f, size.height * 0.9f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
    }
}

// Decorative session identifier; this thumbnail does not encode physiological data.
@Composable
internal fun MindprintThumbnail(id: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (i in 0 until 72) {
            val radius = size.minDimension * 0.44f * kotlin.math.sqrt(i / 72f)
            val angle = i * 2.4f + (id % 31).toFloat()
            val point = center + Offset(kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius * 0.75f)
            drawCircle(HushColors.Trends[i % 4].copy(alpha = 0.7f), 1.dp.toPx(), point)
        }
    }
}

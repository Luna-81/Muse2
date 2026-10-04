package com.blue.hush.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import java.util.Locale

internal fun StateSample.chartCalmness(): Double? = calmness?.takeIf { valid && it.isFinite() && it in 0.0..1.0 }

/** Borderless live trend; playback and seeking belong to ReplayChart. */
@Composable
internal fun CalmnessChart(samples: List<StateSample>, elapsedSeconds: Int, plotHeight: Dp = 64.dp) {
    val latest = remember(samples) { samples.lastOrNull { it.chartCalmness() != null }?.chartCalmness() }
    val description = if (latest == null) "Calmness trend, no data"
        else "Calmness trend, latest ${String.format(Locale.US, "%.0f", latest * 100)} out of 100"
    Canvas(Modifier.fillMaxWidth().height(plotHeight).semantics { contentDescription = description }) {
        val inset = 3.dp.toPx()
        val plotWidth = (size.width - 2 * inset).coerceAtLeast(0f)
        val plotHeightPx = (size.height - 2 * inset).coerceAtLeast(0f)
        drawSampleCurve(samples, elapsedSeconds, HushColors.Lavender,
            levelAt = { it.chartCalmness() }) { second, level ->
            Offset(inset + second.toFloat() / elapsedSeconds.coerceAtLeast(1) * plotWidth,
                inset + (1 - level.toFloat()) * plotHeightPx)
        }
    }
}

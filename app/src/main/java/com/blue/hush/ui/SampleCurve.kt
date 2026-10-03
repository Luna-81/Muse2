package com.blue.hush.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample

/** Draws measured runs and decorative gap guides; callers own validation and plot coordinates. */
internal fun DrawScope.drawSampleCurve(
    samples: List<StateSample>, elapsedSeconds: Int, color: Color,
    levelAt: (StateSample) -> Double?, pointAt: (Int, Double) -> Offset,
) {
    val width = 2.dp.toPx()
    var path: Path? = null
    var previous: Offset? = null
    var previousSecond: Int? = null
    var trusted: Offset? = null
    var trustedSecond: Int? = null
    var trustedLevel = 0.0
    var count = 0

    fun flush() {
        if (count == 1) previous?.let { drawCircle(color, width, it) }
        else path?.let { drawPath(it, color, style = Stroke(width, cap = StrokeCap.Round)) }
        path = null
        previous = null
        previousSecond = null
        count = 0
    }

    for (sample in samples) {
        val level = levelAt(sample)
        if (level == null) { flush(); continue }
        if (previousSecond != null && sample.elapsedSeconds != previousSecond!! + 1) flush()
        val point = pointAt(sample.elapsedSeconds, level)
        if (trustedSecond != null && sample.elapsedSeconds > trustedSecond!! + 1) {
            trusted?.let { drawChartBridge(it, point, color, width) }
        }
        if (path == null) path = Path().apply { moveTo(point.x, point.y) }
        else previous?.let { path?.smoothLineTo(it, point) }
        previous = point
        previousSecond = sample.elapsedSeconds
        count++
        trusted = point
        trustedSecond = sample.elapsedSeconds
        trustedLevel = level
    }
    flush()
    // Hold the last measured level visually; never synthesize a sample for a trailing gap.
    if (trustedSecond != null && trustedSecond!! < elapsedSeconds) {
        trusted?.let { drawChartBridge(it, pointAt(elapsedSeconds, trustedLevel), color, width) }
    }
}

// Horizontal endpoint tangents round corners without overshooting either recorded value.
private fun Path.smoothLineTo(previous: Offset, next: Offset) {
    val handle = (next.x - previous.x) / 3f
    cubicTo(previous.x + handle, previous.y, next.x - handle, next.y, next.x, next.y)
}

// A subdued dashed bridge is visual interpolation, not an accepted measurement.
private fun DrawScope.drawChartBridge(from: Offset, to: Offset, color: Color, width: Float) {
    if (to.x <= from.x) return
    val bridge = Path().apply {
        moveTo(from.x, from.y)
        smoothLineTo(from, to)
    }
    drawPath(bridge, color.copy(alpha = 0.45f), style = Stroke(width, cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
}

package com.blue.hush.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample

/**
 * Visual smoothing parameters.
 * - SMOOTH_WINDOW: moving-average window in seconds. Larger = softer.
 * - GAP_SECONDS: gaps longer than this are considered real breaks and bridged with a dashed line.
 * - TENSION: Catmull-Rom tension.
 */
private const val SMOOTH_WINDOW = 10
private const val GAP_SECONDS = 30
private const val TENSION = 0.25f

/**
 * Draws measured runs and decorative gap guides; callers own validation and plot coordinates.
 * Visual strategy: moving-average smoothing → Catmull-Rom solid curve → dashed bridge over gaps.
 */
internal fun DrawScope.drawSampleCurve(
    samples: List<StateSample>, elapsedSeconds: Int, color: Color,
    levelAt: (StateSample) -> Double?, pointAt: (Int, Double) -> Offset,
) {
    val width = 2.dp.toPx()
    val solidStroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val dashedStroke = Stroke(
        width = width,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )

    // 1) Collect every valid (second, level) pair.
    val rawPoints = ArrayList<Pair<Int, Double>>()
    for (sample in samples) {
        val level = levelAt(sample) ?: continue
        rawPoints += sample.elapsedSeconds to level
    }
    if (rawPoints.isEmpty()) return

    // 2) Apply a per-second moving average to flatten jitter.
    val smoothed = ArrayList<Pair<Int, Double>>()
    val half = SMOOTH_WINDOW / 2
    val firstSecond = rawPoints.first().first
    val lastSecond = rawPoints.last().first
    var cursor = 0
    var second = firstSecond
    while (second <= lastSecond) {
        while (cursor < rawPoints.size && rawPoints[cursor].first < second - half) cursor++
        var sum = 0.0
        var count = 0
        var j = cursor
        while (j < rawPoints.size && rawPoints[j].first <= second + half) {
            sum += rawPoints[j].second
            count++
            j++
        }
        if (count > 0) smoothed += second to (sum / count)
        second++
    }
    if (smoothed.isEmpty()) return

    // 3) Split smoothed points into segments at GAP_SECONDS and record the gaps for bridging.
    data class Segment(val points: MutableList<Offset> = mutableListOf())
    val segments = mutableListOf<Segment>()
    val gaps = mutableListOf<Pair<Offset, Offset>>()   // gap endpoints (for dashed bridging)
    var current = Segment()
    var previousSecond: Int? = null
    var previousPoint: Offset? = null
    for ((sec, level) in smoothed) {
        val point = pointAt(sec, level)
        if (previousSecond != null && sec - previousSecond!! > GAP_SECONDS) {
            if (current.points.isNotEmpty()) segments += current
            // Record the gap: last point of the previous segment → first point of the next.
            previousPoint?.let { gaps += it to point }
            current = Segment()
        }
        current.points += point
        previousSecond = sec
        previousPoint = point
    }
    if (current.points.isNotEmpty()) segments += current

    // 4) Draw dashed bridges first so solid lines render on top.
    for ((from, to) in gaps) {
        drawDashedBridge(from, to, color, dashedStroke)
    }

    // 5) Draw solid (smoothed) segments.
    for (segment in segments) {
        when (segment.points.size) {
            0 -> {}
            1 -> drawCircle(color, width, segment.points[0])
            else -> drawPath(buildCatmullRomPath(segment.points, TENSION), color, style = solidStroke)
        }
    }

    // 6) If the smoothed data ends before elapsedSeconds, bridge the tail with a dashed line.
    val last = smoothed.last()
    if (last.first < elapsedSeconds) {
        drawDashedBridge(pointAt(last.first, last.second), pointAt(elapsedSeconds, last.second), color, dashedStroke)
    }
}

/**
 * Gap bridge: smooth cubic interpolation drawn as a dashed line.
 */
private fun DrawScope.drawDashedBridge(from: Offset, to: Offset, color: Color, stroke: Stroke) {
    if (to.x <= from.x) return
    val bridge = Path().apply {
        moveTo(from.x, from.y)
        val handle = (to.x - from.x) / 3f
        cubicTo(from.x + handle, from.y, to.x - handle, to.y, to.x, to.y)
    }
    drawPath(bridge, color.copy(alpha = 0.55f), style = stroke)
}

/**
 * Catmull-Rom spline → cubic Bezier.
 */
private fun buildCatmullRomPath(points: List<Offset>, tension: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    if (points.size == 1) { path.moveTo(points[0].x, points[0].y); return path }

    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        val c1 = Offset(
            p1.x + (p2.x - p0.x) * tension,
            p1.y + (p2.y - p0.y) * tension,
        )
        val c2 = Offset(
            p2.x - (p3.x - p1.x) * tension,
            p2.y - (p3.y - p1.y) * tension,
        )
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}
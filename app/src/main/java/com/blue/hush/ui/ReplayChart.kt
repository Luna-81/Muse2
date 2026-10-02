package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushSpace

private val ReplayMetric.color: Color
    get() = when (this) {
        ReplayMetric.CALMNESS -> HushColors.Lavender
        ReplayMetric.ALPHA -> HushColors.Success
        ReplayMetric.THETA -> HushColors.Warm
        ReplayMetric.BETA -> HushColors.Star
        ReplayMetric.HEART_RATE -> HushColors.Error
        ReplayMetric.STABILITY -> HushColors.Accent
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReplayChart(
    samples: List<StateSample>, elapsedSeconds: Int, selectedSample: StateSample?,
    visibleMetrics: Set<ReplayMetric>, onMetricChanged: (ReplayMetric, Boolean) -> Unit,
    onReplaySecondSelected: (Float) -> Unit,
) {
    val onSeek by rememberUpdatedState(onReplaySecondSelected)
    val metrics = ReplayMetric.entries.filter { it in visibleMetrics }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
            ReplayMetric.entries.forEach { metric ->
                Row(Modifier.heightIn(min = 48.dp).toggleable(
                    value = metric in visibleMetrics, role = Role.Checkbox,
                    onValueChange = { onMetricChanged(metric, it) },
                ), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(metric in visibleMetrics, onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = metric.color))
                    Text(metric.title, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = HushSpace.xs))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Relative level", style = labelStyle, color = HushColors.Muted)
            Text(formatDuration(selectedSample?.elapsedSeconds ?: 0), style = labelStyle, color = HushColors.Accent)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inset = with(density) { 8.dp.toPx() }
            val axisWidth = with(density) { 28.dp.toPx() }
            val plotWidth = with(density) { maxWidth.toPx() } - axisWidth
            val gap = with(density) { 4.dp.toPx() }
            // Limit label width to either side of a centered cursor; wrapping respects font scale.
            val labelWidth = ((plotWidth - inset * 2) / 2 - inset).toInt().coerceAtLeast(1)
            val labels = metrics.mapNotNull { metric -> metric.value(selectedSample)?.let { value ->
                Triple(metric, metric.level(value).toFloat(), measurer.measure(
                    metric.label(selectedSample), labelStyle, constraints = Constraints(maxWidth = labelWidth),
                ))
            } }.sortedByDescending { it.second }
            val labelHeight = labels.sumOf { it.third.size.height } + gap * (labels.size - 1).coerceAtLeast(0)
            val heightPx = maxOf(with(density) { (240.dp * density.fontScale.coerceAtLeast(1f)).toPx() }, labelHeight + inset * 2)
            val plotHeight = with(density) { heightPx.toDp() }
            Row {
                Column(Modifier.width(28.dp).height(plotHeight), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("100", style = labelStyle, color = HushColors.Muted)
                    Text("0", style = labelStyle, color = HushColors.Muted)
                }
                val description = metrics.joinToString(", ") { it.label(selectedSample) }
                Canvas(Modifier.weight(1f).height(plotHeight).semantics {
                    contentDescription = "Session replay"
                    stateDescription = listOf(formatDuration(selectedSample?.elapsedSeconds ?: 0), description)
                        .filter { it.isNotEmpty() }.joinToString(", ")
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        (selectedSample?.elapsedSeconds ?: 0).toFloat(), 0f..elapsedSeconds.coerceAtLeast(1).toFloat(),
                    )
                    if (samples.isNotEmpty()) setProgress { value ->
                        if (!value.isFinite()) false else {
                            onSeek(value.coerceIn(0f, elapsedSeconds.coerceAtLeast(0).toFloat()))
                            true
                        }
                    }
                }.pointerInput(elapsedSeconds, samples.isNotEmpty()) {
                    if (samples.isEmpty()) return@pointerInput
                    fun seek(x: Float) = onSeek(((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f))
                        .coerceIn(0f, 1f) * elapsedSeconds)
                    detectTapGestures { seek(it.x) }
                }.pointerInput(elapsedSeconds, samples.isNotEmpty()) {
                    if (samples.isEmpty()) return@pointerInput
                    fun seek(x: Float) = onSeek(((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f))
                        .coerceIn(0f, 1f) * elapsedSeconds)
                    detectHorizontalDragGestures(onDragStart = { seek(it.x) }) { change, _ ->
                        change.consume()
                        seek(change.position.x)
                    }
                }) {
                    val width = (size.width - 2 * inset).coerceAtLeast(0f)
                    val height = size.height - 2 * inset
                    fun x(second: Int) = inset + second.toFloat().div(elapsedSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) * width
                    fun y(level: Float) = inset + (1 - level) * height
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        drawLine(HushColors.Border.copy(alpha = 0.45f), Offset(inset, y(fraction)),
                            Offset(inset + width, y(fraction)), 1.dp.toPx())
                    }
                    metrics.forEach { metric ->
                        var path: Path? = null
                        var previous: Offset? = null
                        var previousSecond: Int? = null
                        var trusted: Offset? = null
                        var trustedSecond: Int? = null
                        var count = 0
                        fun flush() {
                            if (count == 1) previous?.let { drawCircle(metric.color, 2.dp.toPx(), it) }
                            else path?.let { drawPath(it, metric.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
                            path = null; previous = null; previousSecond = null; count = 0
                        }
                        samples.forEach { sample ->
                            val value = metric.value(sample)
                            if (value == null) { flush(); return@forEach }
                            if (previousSecond != null && sample.elapsedSeconds != previousSecond!! + 1) flush()
                            val point = Offset(x(sample.elapsedSeconds), y(metric.level(value).toFloat()))
                            if (trustedSecond != null && sample.elapsedSeconds > trustedSecond!! + 1) {
                                trusted?.let { drawChartBridge(it, point, metric.color, 2.dp.toPx()) }
                            }
                            if (path == null) path = Path().apply { moveTo(point.x, point.y) }
                            else previous?.let { path?.smoothLineTo(it, point) }
                            previous = point; previousSecond = sample.elapsedSeconds; count++
                            trusted = point; trustedSecond = sample.elapsedSeconds
                        }
                        flush()
                        if (trustedSecond != null && trustedSecond!! < elapsedSeconds) {
                            trusted?.let { drawChartBridge(it, Offset(inset + width, it.y), metric.color, 2.dp.toPx()) }
                        }
                    }
                    selectedSample?.let { selected ->
                        val cursorX = x(selected.elapsedSeconds)
                        drawLine(HushColors.Accent, Offset(cursorX, inset), Offset(cursorX, inset + height), 2.dp.toPx())
                        val tops = replayLabelTops(labels.map { y(it.second) - inset },
                            labels.map { it.third.size.height.toFloat() }, height, gap)
                        labels.forEachIndexed { index, (metric, level, text) ->
                            val point = Offset(cursorX, y(level))
                            val right = cursorX + inset + text.size.width <= size.width - inset
                            val left = if (right) cursorX + inset else cursorX - inset - text.size.width
                            val top = inset + tops[index]
                            val edge = Offset(if (right) left else left + text.size.width, top + text.size.height / 2f)
                            drawLine(metric.color.copy(alpha = 0.7f), point, edge, 1.dp.toPx())
                            drawRect(HushColors.Surface.copy(alpha = 0.95f), Offset(left, top),
                                androidx.compose.ui.geometry.Size(text.size.width.toFloat(), text.size.height.toFloat()))
                            drawText(text, metric.color, topLeft = Offset(left, top))
                            drawCircle(metric.color, 3.dp.toPx(), point)
                        }
                        drawCircle(HushColors.Surface, 7.dp.toPx(), Offset(cursorX, size.height / 2))
                        drawCircle(HushColors.Accent, 5.dp.toPx(), Offset(cursorX, size.height / 2))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", style = labelStyle, color = HushColors.Muted)
            Text(formatDuration(elapsedSeconds), style = labelStyle, color = HushColors.Muted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
            metrics.filter { it.value(selectedSample) == null }.forEach {
                Text(it.label(selectedSample), style = labelStyle, color = it.color)
            }
        }
    }
}

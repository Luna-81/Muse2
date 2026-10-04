package com.blue.hush.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.blue.hush.session.SessionDuration
import com.blue.hush.ui.theme.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun DurationSelector(seconds: Int, simulationMode: Boolean, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val initialMinutes = (seconds / 60).coerceIn(SessionDuration.MIN_MINUTES, SessionDuration.MAX_MINUTES)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialMinutes - SessionDuration.MIN_MINUTES)
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentSimulationMode by rememberUpdatedState(simulationMode)
    val rowHeight = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val timeStyle = MaterialTheme.typography.displayLarge
    val textMeasurer = rememberTextMeasurer()
    val timeWidth = with(LocalDensity.current) {
        textMeasurer.measure(AnnotatedString("00:00"), style = timeStyle).size.width.toDp()
    }
    val selectedIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: (initialMinutes - SessionDuration.MIN_MINUTES)
        }
    }
    val minutes = selectedIndex + SessionDuration.MIN_MINUTES
    LaunchedEffect(state) {
        // Layout alone must not send a duration change during state restoration.
        snapshotFlow { selectedIndex }.drop(1).collect { index ->
            if (!currentSimulationMode) currentOnSelected((index + SessionDuration.MIN_MINUTES) * 60)
        }
    }
    LaunchedEffect(simulationMode) {
        if (simulationMode) {
            state.stopScroll()
            state.scrollToItem(10 - SessionDuration.MIN_MINUTES)
        }
    }
    val wheelColor = HushColors.Text
    Box(modifier.height(rowHeight), contentAlignment = Alignment.Center) {
        Box(Modifier.width(timeWidth + 20.dp).fillMaxHeight()) {
            LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state), userScrollEnabled = !simulationMode,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                    contentDescription = "Duration in minutes"
                    stateDescription = "$minutes min"
                    if (simulationMode) disabled() else {
                        progressBarRangeInfo = ProgressBarRangeInfo(minutes.toFloat(),
                            SessionDuration.MIN_MINUTES.toFloat()..SessionDuration.MAX_MINUTES.toFloat(),
                            SessionDuration.MAX_MINUTES - SessionDuration.MIN_MINUTES - 1)
                        setProgress { value ->
                            val target = value.roundToInt().coerceIn(SessionDuration.MIN_MINUTES, SessionDuration.MAX_MINUTES)
                            scope.launch { state.animateScrollToItem(target - SessionDuration.MIN_MINUTES) }
                            true
                        }
                    }
                }) {
                items(SessionDuration.MAX_MINUTES - SessionDuration.MIN_MINUTES + 1, key = { it }) { index ->
                    Box(Modifier.fillMaxWidth().height(rowHeight).padding(end = 20.dp), contentAlignment = Alignment.CenterStart) {
                        Text(String.format(Locale.US, "%02d:00", index + SessionDuration.MIN_MINUTES),
                            style = timeStyle, color = wheelColor)
                    }
                }
            }
            Canvas(Modifier.align(Alignment.CenterEnd).width(8.dp).height(22.dp)) {
                val stroke = 1.5.dp.toPx()
                fun chevron(top: Float, upwards: Boolean) {
                    val tip = if (upwards) top else top + size.width / 2
                    val edge = if (upwards) top + size.width / 2 else top
                    drawLine(wheelColor, Offset(0f, edge), Offset(size.width / 2, tip), stroke, StrokeCap.Round)
                    drawLine(wheelColor, Offset(size.width / 2, tip), Offset(size.width, edge), stroke, StrokeCap.Round)
                }
                chevron(0f, upwards = true)
                chevron(size.height - size.width / 2, upwards = false)
            }
        }
    }
}

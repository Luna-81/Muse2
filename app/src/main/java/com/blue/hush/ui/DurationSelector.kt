package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.blue.hush.session.SessionDuration
import com.blue.hush.ui.theme.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun DurationSelector(seconds: Int, simulationMode: Boolean, onSelected: (Int) -> Unit) {
    var customMinutes by rememberSaveable { mutableIntStateOf(if (seconds / 60 !in listOf(5, 10)) seconds / 60 else 15) }
    var customSelected by rememberSaveable { mutableStateOf(false) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = customMinutes - SessionDuration.MIN_MINUTES)
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentSimulationMode by rememberUpdatedState(simulationMode)
    val rowHeight = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val selectedIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: (customMinutes - SessionDuration.MIN_MINUTES)
        }
    }
    LaunchedEffect(state) {
        // Initial layout must not override a preset; only wheel changes select a custom duration.
        snapshotFlow { selectedIndex }.drop(1).collect { index ->
            customMinutes = index + SessionDuration.MIN_MINUTES
            if (!currentSimulationMode) {
                customSelected = true
                currentOnSelected(customMinutes * 60)
            }
        }
    }
    LaunchedEffect(simulationMode) {
        if (simulationMode) { state.stopScroll(); customSelected = false }
    }
    val isCustom = !simulationMode && (customSelected || seconds / 60 !in listOf(5, 10))
    val wheelColor = when {
        simulationMode -> HushColors.Muted.copy(alpha = 0.38f)
        isCustom -> HushColors.Accent
        else -> HushColors.Text
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        listOf(5, 10).forEach { minutes ->
            FilterChip(selected = !isCustom && seconds == minutes * 60,
                onClick = { scope.launch { state.stopScroll(); customSelected = false; currentOnSelected(minutes * 60) } },
                enabled = !simulationMode || minutes == 10,
                label = { Text("$minutes min") }, modifier = Modifier.weight(1f).height(rowHeight))
        }
        Box(Modifier.weight(1f).height(rowHeight).clip(HushShapes.Control)
            .background(if (isCustom) HushColors.SurfaceRaised else HushColors.Surface)
            .border(1.dp, if (isCustom) HushColors.SurfaceRaised else HushColors.Border, HushShapes.Control)) {
            LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state), userScrollEnabled = !simulationMode,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                    contentDescription = "Custom duration"
                    stateDescription = "$customMinutes min"
                    selected = isCustom
                    if (simulationMode) disabled() else {
                        onClick { customSelected = true; currentOnSelected(customMinutes * 60); true }
                        progressBarRangeInfo = ProgressBarRangeInfo(customMinutes.toFloat(),
                            SessionDuration.MIN_MINUTES.toFloat()..SessionDuration.MAX_MINUTES.toFloat(),
                            SessionDuration.MAX_MINUTES - SessionDuration.MIN_MINUTES - 1)
                        setProgress { value ->
                            val target = value.roundToInt().coerceIn(SessionDuration.MIN_MINUTES, SessionDuration.MAX_MINUTES)
                            scope.launch {
                                state.animateScrollToItem(target - SessionDuration.MIN_MINUTES)
                                customSelected = true
                                currentOnSelected(target * 60)
                            }
                            true
                        }
                    }
                }) {
                items(SessionDuration.MAX_MINUTES - SessionDuration.MIN_MINUTES + 1, key = { it }) { index ->
                    val minutes = index + SessionDuration.MIN_MINUTES
                    Box(Modifier.fillMaxWidth().height(rowHeight).clickable(enabled = !simulationMode) {
                        customSelected = true
                        currentOnSelected(minutes * 60)
                    }.padding(start = HushSpace.xs, end = 20.dp), contentAlignment = Alignment.Center) {
                        Text("$minutes min", style = MaterialTheme.typography.bodyMedium, color = wheelColor)
                    }
                }
            }
            Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 7.dp).width(8.dp).height(22.dp)) {
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

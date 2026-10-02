package com.blue.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.blue.hush.session.SessionDuration
import com.blue.hush.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun DurationSelector(seconds: Int, simulationMode: Boolean, onSelected: (Int) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var customMinutes by rememberSaveable { mutableIntStateOf(20) }
    var customSelected by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(simulationMode) {
        if (simulationMode) {
            pickerOpen = false
            customSelected = false
        }
    }
    val isCustom = customSelected || seconds / 60 !in listOf(5, 10)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
        listOf(5, 10).forEach { minutes ->
            FilterChip(
                selected = !isCustom && seconds == minutes * 60,
                onClick = { customSelected = false; onSelected(minutes * 60) },
                enabled = !simulationMode || minutes == 10,
                label = { Text("$minutes min") }, modifier = Modifier.weight(1f),
            )
        }
        FilterChip(
            selected = isCustom, enabled = !simulationMode,
            onClick = { pickerOpen = true },
            label = { Text(if (isCustom) "${seconds / 60} min ▾" else "Custom ▾") },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Custom duration" },
        )
    }
    if (pickerOpen && !simulationMode) {
        DurationPicker(
            initialMinutes = if (isCustom) seconds / 60 else customMinutes,
            onDismiss = { pickerOpen = false },
            onConfirm = { minutes ->
                customMinutes = minutes
                customSelected = true
                onSelected(minutes * 60)
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun DurationPicker(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val initial = initialMinutes.coerceIn(SessionDuration.MIN_MINUTES, SessionDuration.MAX_MINUTES)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial - SessionDuration.MIN_MINUTES)
    val scope = rememberCoroutineScope()
    val rowHeight = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val selectedIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: (initial - SessionDuration.MIN_MINUTES)
        }
    }
    val minutes = selectedIndex + SessionDuration.MIN_MINUTES
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = HushColors.Surface,
        title = { Text("Duration") },
        text = {
            Box(Modifier.fillMaxWidth().height(rowHeight * 3), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(rowHeight).background(HushColors.SurfaceRaised, HushShapes.Control))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyColumn(
                        state = state, flingBehavior = rememberSnapFlingBehavior(state),
                        contentPadding = PaddingValues(vertical = rowHeight),
                        modifier = Modifier.width(112.dp).fillMaxHeight().clearAndSetSemantics {
                            contentDescription = "Duration in minutes"
                            stateDescription = "$minutes min"
                            progressBarRangeInfo = ProgressBarRangeInfo(minutes.toFloat(),
                                SessionDuration.MIN_MINUTES.toFloat()..SessionDuration.MAX_MINUTES.toFloat(),
                                SessionDuration.MAX_MINUTES - SessionDuration.MIN_MINUTES - 1)
                            setProgress { value ->
                                val target = value.roundToInt().coerceIn(SessionDuration.MIN_MINUTES, SessionDuration.MAX_MINUTES)
                                scope.launch { state.animateScrollToItem(target - SessionDuration.MIN_MINUTES) }
                                true
                            }
                        },
                    ) {
                        items(SessionDuration.MAX_MINUTES - SessionDuration.MIN_MINUTES + 1, key = { it }) { index ->
                            Box(Modifier.fillMaxWidth().height(rowHeight).graphicsLayer {
                                // Fade and shrink neighboring rows as they move away from the selection band.
                                val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                val center = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2f
                                val distance = item?.let { abs(it.offset + it.size / 2f - center) / it.size } ?: 2f
                                alpha = (1f - distance * 0.45f).coerceIn(0.15f, 1f)
                                scaleX = (1f - distance * 0.12f).coerceAtLeast(0.75f)
                                scaleY = scaleX
                            }.clickable { scope.launch { state.animateScrollToItem(index) } }, contentAlignment = Alignment.Center) {
                                Text("${index + SessionDuration.MIN_MINUTES}", style = MaterialTheme.typography.headlineMedium, color = HushColors.Text)
                            }
                        }
                    }
                    Text("min", style = MaterialTheme.typography.titleMedium, color = HushColors.Muted)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

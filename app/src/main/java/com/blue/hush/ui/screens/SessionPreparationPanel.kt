package com.blue.hush.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blue.hush.ui.ConnectionUiState
import com.blue.hush.ui.components.DurationSelector
import com.blue.hush.ui.components.HushPanel
import com.blue.hush.ui.theme.*

@Composable
internal fun SessionPreparationPanel(
    durationSeconds: Int,
    connectionState: ConnectionUiState,
    onDurationSelected: (Int) -> Unit,
    onDeviceSelected: () -> Unit,
    onStart: () -> Unit,
) {
    HushPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HushSpace.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HushSpace.md)
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(
                        HushColors.Muted,
                        radius = size.minDimension / 2 - stroke,
                        style = Stroke(stroke)
                    )
                    drawLine(
                        HushColors.Muted,
                        center,
                        Offset(center.x, size.height * 0.23f),
                        stroke,
                        StrokeCap.Round
                    )
                    drawLine(
                        HushColors.Muted,
                        center,
                        Offset(size.width * 0.7f, size.height * 0.6f),
                        stroke,
                        StrokeCap.Round
                    )
                }
                Text(
                    "Guided Meditation",
                    style = MaterialTheme.typography.bodySmall,
                    color = HushColors.Muted
                )
            }
            DurationSelector(
                durationSeconds,
                connectionState.simulationMode,
                onDurationSelected,
                Modifier.fillMaxWidth()
            )
            Text(
                "A quiet space for a brighter you.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = HushColors.Muted
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = HushShapes.Pill
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)
            ) {
                Text(if (connectionState.ready) "Start meditation" else "Connect Muse")
                Canvas(Modifier.size(16.dp)) {
                    val stroke = 1.5.dp.toPx()
                    drawLine(
                        HushColors.OnAccent,
                        Offset(0f, center.y),
                        Offset(size.width, center.y),
                        stroke,
                        StrokeCap.Round
                    )
                    drawLine(
                        HushColors.OnAccent,
                        Offset(size.width * 0.6f, size.height * 0.2f),
                        Offset(size.width, center.y),
                        stroke,
                        StrokeCap.Round
                    )
                    drawLine(
                        HushColors.OnAccent,
                        Offset(size.width * 0.6f, size.height * 0.8f),
                        Offset(size.width, center.y),
                        stroke,
                        StrokeCap.Round
                    )
                }
            }
        }
        TextButton(
            onClick = onDeviceSelected,
            modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = "Muse connection" }) {
            Text(
                connectionState.status,
                style = MaterialTheme.typography.bodySmall,
                color = HushColors.Muted
            )
        }
    }
}

@Composable
internal fun MusicButton(trackTitle: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Soundscape · $trackTitle" }) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.7.dp.toPx()
            val left = size.width * 0.3f
            val right = size.width * 0.82f
            drawLine(HushColors.Text, Offset(left, size.height * 0.22f), Offset(right, size.height * 0.1f), stroke, StrokeCap.Round)
            drawLine(HushColors.Text, Offset(left, size.height * 0.22f), Offset(left, size.height * 0.79f), stroke, StrokeCap.Round)
            drawLine(HushColors.Text, Offset(right, size.height * 0.1f), Offset(right, size.height * 0.67f), stroke, StrokeCap.Round)
            drawOval(HushColors.Text, Offset(size.width * 0.04f, size.height * 0.69f),
                Size(size.width * 0.3f, size.height * 0.22f)
            )
            drawOval(HushColors.Text, Offset(size.width * 0.56f, size.height * 0.57f),
                Size(size.width * 0.3f, size.height * 0.22f)
            )
        }
    }
}

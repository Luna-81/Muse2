package com.blue.hush.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushSpace
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionState

@Composable
internal fun MeditationGalaxyScreen(
    state: SessionState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    galaxyMotion: GalaxyMotion = rememberGalaxyMotion(),
) {
    var volumeExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var controlsHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val paused = state.phase == SessionPhase.PAUSED
    // The service can retain its last valid sample during a gap. Reject stale
    // seconds even if an intermediate connection update clears dataGap.
    val signalMissing = !state.connected || state.dataGap ||
        state.latestSample?.elapsedSeconds != state.elapsedSeconds || galaxyAgitation(state.latestSample) == null
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
            previousBehavior?.let { controller?.systemBarsBehavior = it }
        }
    }
    // Dismissing a panel must never also end the session.
    BackHandler { if (volumeExpanded) volumeExpanded = false else confirmFinish = true }
    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("End this session?") },
        text = { Text("Your session will be saved.") },
        confirmButton = { TextButton(onClick = { confirmFinish = false; onFinish() }) { Text("End session") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep meditating") } },
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(HushColors.Background).safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val controlsHeight = with(density) { controlsHeightPx.toDp() }
        // Reserve the measured control area so the added chart does not cover portrait particles.
        val galaxyModifier = if (landscape) Modifier.fillMaxHeight().fillMaxWidth(0.58f).align(Alignment.CenterStart)
            else Modifier.fillMaxWidth().height((maxHeight - controlsHeight).coerceAtLeast(76.dp)).padding(top = 76.dp).align(Alignment.TopCenter)
        GalaxyParticleField(state.latestSample, signalMissing, paused,
            galaxyModifier, state = galaxyMotion)
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = HushSpace.xl, vertical = HushSpace.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Meditation",
                        color = HushColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    val status = when {
                        paused -> "Paused"
                        state.phase == SessionPhase.CONNECTING -> "Connecting…"
                        !state.connected -> "Reconnecting…"
                        state.calibrationSeconds < 10 -> "Calibrating…"
                        signalMissing -> "Waiting for EEG…"
                        else -> null
                    }
                    status?.let { Text(it, color = HushColors.Muted, style = MaterialTheme.typography.labelMedium) }
                }
                IconButton(
                    onClick = { volumeExpanded = !volumeExpanded },
                    modifier = Modifier.semantics { contentDescription = if (volumeExpanded) "Hide volume" else "Adjust volume" },
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        val w = size.width
                        val h = size.height
                        val speaker = Path().apply {
                            moveTo(w * 0.12f, h * 0.38f)
                            lineTo(w * 0.3f, h * 0.38f)
                            lineTo(w * 0.52f, h * 0.18f)
                            lineTo(w * 0.52f, h * 0.82f)
                            lineTo(w * 0.3f, h * 0.62f)
                            lineTo(w * 0.12f, h * 0.62f)
                            close()
                        }
                        drawPath(speaker, HushColors.Text)
                        drawArc(HushColors.Text, -60f, 120f, false, Offset(w * 0.4f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.48f, h * 0.6f), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
            Column(
                Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                    .onSizeChanged { controlsHeightPx = it.height }
                    .widthIn(max = if (landscape) 280.dp else 420.dp).fillMaxWidth()
                    .heightIn(max = (maxHeight - 76.dp).coerceAtLeast(100.dp))
                    .verticalScroll(rememberScrollState()).padding(horizontal = HushSpace.xl, vertical = HushSpace.md),
                verticalArrangement = Arrangement.spacedBy(HushSpace.sm), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CalmnessChart(state.trendSamples, state.elapsedSeconds, calibrating = state.calibrationSeconds < 10)
                Text(formatDuration(state.plannedSeconds - state.elapsedSeconds), style = MaterialTheme.typography.displayLarge, color = HushColors.Text)
                Text("Time remaining", style = MaterialTheme.typography.bodySmall, color = HushColors.Muted)
                if (volumeExpanded) {
                    Slider(
                        value = state.volume,
                        onValueChange = onVolumeChanged,
                        modifier = Modifier.semantics { contentDescription = "Meditation volume" },
                    )
                }
                Button(
                    onClick = if (paused) onResume else onPause,
                    enabled = paused || state.phase == SessionPhase.RUNNING,
                    shape = CircleShape,
                    modifier = Modifier.size(76.dp).semantics { contentDescription = if (paused) "Resume" else "Pause" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        if (paused) {
                            val path = Path().apply { moveTo(5f, 0f); lineTo(size.width, size.height / 2); lineTo(5f, size.height); close() }
                            drawPath(path, HushColors.OnAccent)
                        } else {
                            drawRect(HushColors.OnAccent, size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height))
                            drawRect(HushColors.OnAccent, topLeft = Offset(size.width * 0.75f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height))
                        }
                    }
                }
                TextButton(onClick = { confirmFinish = true }) { Text("Finish") }
            }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

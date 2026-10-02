package com.blue.hush.ui

import com.blue.hush.ui.theme.HushColors

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.blue.hush.session.StateSample
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class Star(val radius: Float, val angle: Float, val phase: Float, val size: Float)

private val stars = Random(42).let { random ->
    List(600) { index ->
        val radius = sqrt(random.nextFloat())
        Star(
            radius,
            // Narrow arms preserve dark space; only a few stars spill into the outskirts.
            (index % 4) * PI.toFloat() / 2f + radius * 5.5f +
                (random.nextFloat() - 0.5f) *
                (if (index % 11 == 0) 0.85f else 0.28f + radius * 0.16f),
            random.nextFloat() * 2f * PI.toFloat(),
            0.45f + random.nextFloat() * 1.15f,
        )
    }
}

@Composable
internal fun rememberGalaxyMotion(): GalaxyMotion =
    rememberSaveable(saver = listSaver(
        save = { listOf(it.phase, it.agitation, it.visibility) },
        restore = { GalaxyMotion(it[0], it[1], it[2]) },
    )) { GalaxyMotion() }

@Composable
internal fun GalaxyParticleField(
    sample: StateSample?, dataGap: Boolean, paused: Boolean, modifier: Modifier = Modifier,
    state: GalaxyMotion = rememberGalaxyMotion(), preview: Boolean = false,
    continueWhenMissing: Boolean = false,
) {
    // Preview is decorative; live missing data must never be interpreted as calm.
    val target = rememberUpdatedState(if (preview) 0f else if (dataGap) null else galaxyAgitation(sample))
    val keepMoving = rememberUpdatedState(continueWhenMissing)
    val frame = remember(state) { mutableFloatStateOf(0f) }

    val view = LocalView.current
    LaunchedEffect(paused, view, state) {
        if (paused) return@LaunchedEffect
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                    previous = now
                    state.advance(dt, target.value, keepMoving.value)
                    frame.floatValue += 1f

                }
            }
        }
    }
    Canvas(modifier) {
        // Invalidate drawing without recomposing the screen.
        frame.floatValue
        val time = state.phase
        val chaos = state.agitation
        val opacity = state.visibility
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val radius = size.minDimension * 0.43f
        if (radius <= 0f) return@Canvas
        drawRect(HushColors.Background)
        drawRect(Brush.radialGradient(listOf(HushColors.Glow.copy(alpha = opacity * 0.65f), Color.Transparent), center, radius * 1.35f))
        drawGalaxyNebula(time, chaos, opacity, foreground = false, extent = radius)
        val breath = 1f + sin(time * 1.5f) * 0.04f
        for (arm in 0 until 4) {
            val angle = time + arm * PI.toFloat() / 2f
            val glowCenter = center + Offset(cos(angle) * radius * 0.43f, sin(angle) * radius * 0.3f)
            val tint = if (arm % 2 == 0) HushColors.Lavender else HushColors.Star
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = opacity * 0.09f), Color.Transparent),
                glowCenter, radius * 0.65f * breath), radius * 0.65f * breath, glowCenter)
        }
        stars.forEachIndexed { index, star ->
            val angle = star.angle + time
            val drift = radius * chaos * 0.43f
            val x = cos(angle) * star.radius * radius + sin(time * 5f + star.phase) * drift
            val y = sin(angle) * star.radius * radius * 0.64f + cos(time * 4.3f + star.phase * 2f) * drift
            val position = center + Offset(x * 0.94f - y * 0.34f, x * 0.34f + y * 0.94f)
            val color = when (index % 5) {
                0 -> HushColors.Warm
                1 -> HushColors.Lavender
                else -> HushColors.Star
            }
            val alpha = opacity * (0.7f + sin(time * 2f + star.phase) * 0.2f)
            val dotRadius = star.size * (size.minDimension / 360f)
            if (index % 6 == 0) {
                drawCircle(color.copy(alpha = alpha * 0.045f), dotRadius * 7f, position)
                drawCircle(color.copy(alpha = alpha * 0.14f), dotRadius * 3f, position)
                val tangent = Offset(-sin(angle) * 0.94f - cos(angle) * 0.64f * 0.34f,
                    -sin(angle) * 0.34f + cos(angle) * 0.64f * 0.94f)
                drawLine(color.copy(alpha = alpha * 0.22f), position - tangent * dotRadius * (4f + chaos * 5f),
                    position, strokeWidth = dotRadius, cap = StrokeCap.Round)
            }
            drawCircle(color.copy(alpha = alpha), dotRadius, position)
            if (index % 47 == 0) {
                val flare = dotRadius * (3f + sin(time * 2f + star.phase))
                drawLine(color.copy(alpha = alpha * 0.45f), position - Offset(flare, 0f),
                    position + Offset(flare, 0f), strokeWidth = dotRadius * 0.5f)
                drawLine(color.copy(alpha = alpha * 0.45f), position - Offset(0f, flare),
                    position + Offset(0f, flare), strokeWidth = dotRadius * 0.5f)
                drawCircle(HushColors.Text.copy(alpha = alpha), dotRadius * 0.55f, position)
            }
        }
        drawGalaxyNebula(time, chaos, opacity, foreground = true, extent = radius)
        drawCircle(
            Brush.radialGradient(listOf(HushColors.Warm.copy(alpha = opacity * (0.6f - chaos * 0.35f)), Color.Transparent), center, radius * 0.22f),
            radius * 0.22f,
            center,
        )
    }
}

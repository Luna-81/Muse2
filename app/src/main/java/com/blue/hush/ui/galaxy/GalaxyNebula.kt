package com.blue.hush.ui.galaxy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.blue.hush.ui.theme.HushColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Cloud(val distance: Float, val angle: Float, val radius: Float, val phase: Float)

// Fixed multi-scale wisps keep the same cloud identity on every route.
private val clouds = Random(84).let { random ->
    List(48) { index ->
        val distance = 0.12f + random.nextFloat() * 0.9f
        Cloud(distance, (index % 4) * PI.toFloat() / 2f + distance * 5.5f +
            (random.nextFloat() - 0.5f) * 0.38f,
            0.06f + random.nextFloat() * 0.12f, random.nextFloat() * 6.28f)
    }
}

// Unit-sized brushes are reused with canvas transforms, avoiding shader creation per wisp/frame.
private val cloudBrushes = listOf(HushColors.Lavender, HushColors.Star, HushColors.Accent).map { tint ->
    Brush.radialGradient(
        0f to tint.copy(alpha = 0.16f),
        0.35f to tint.copy(alpha = 0.08f),
        0.7f to tint.copy(alpha = 0.025f),
        1f to tint.copy(alpha = 0f),
        center = Offset.Zero, radius = 1f,
    )
}
private val dustBrush = Brush.radialGradient(
    listOf(HushColors.Background.copy(alpha = 0.62f), Color.Transparent),
    center = Offset.Zero, radius = 1f,
)

internal fun DrawScope.drawGalaxyNebula(time: Float, chaos: Float, opacity: Float, foreground: Boolean, extent: Float) {
    val origin = center
    clouds.forEachIndexed { index, cloud ->
        // Sparse foreground dust obscures some stars; most emission remains behind them.
        if (foreground && index % 4 != 0) return@forEachIndexed
        val angle = cloud.angle + time + if (foreground) 0.16f else 0f
        val drift = chaos * extent * 0.12f
        val x = cos(angle) * cloud.distance * extent + sin(time * 0.8f + cloud.phase) * drift
        val y = sin(angle) * cloud.distance * extent * 0.64f
        val point = origin + Offset(x * 0.94f - y * 0.34f, x * 0.34f + y * 0.94f)
        val breath = 1f + sin(time * 0.7f + cloud.phase) * 0.08f
        val radius = extent * cloud.radius * breath * if (foreground) 0.7f else 1f
        translate(point.x, point.y) {
            scale(radius, radius * 0.7f, pivot = Offset.Zero) {
                drawCircle(if (foreground) dustBrush else cloudBrushes[index % cloudBrushes.size],
                    radius = 1f, center = Offset.Zero, alpha = opacity)
            }
        }
    }
}

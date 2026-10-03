package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors

@Composable
internal fun ParticlePanel(
    sample: StateSample?, dataGap: Boolean, motion: GalaxyMotion? = null,
    maxHeight: Dp = 440.dp,
    animate: Boolean = false, retainedSample: StateSample? = null,
) {
    val visual = motion ?: remember(sample, retainedSample) {
        GalaxyMotion().apply { showRecordedSample(sample, retainedSample) }
    }
    Card(shape = com.blue.hush.ui.theme.HushShapes.Panel) {
        Box(Modifier.fillMaxWidth().heightIn(max = maxHeight).aspectRatio(1f), contentAlignment = Alignment.Center) {
            GalaxyParticleField(sample, dataGap, paused = !animate,
                modifier = Modifier.fillMaxSize(), state = visual, continueWhenMissing = animate)
        }
    }
}

@Composable
internal fun HushNavIcon(tab: AppTab) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(1.5.dp.toPx())
        if (tab == AppTab.HISTORY) {
            drawCircle(color, size.minDimension * 0.4f, style = stroke)
            drawLine(color, center, Offset(center.x, size.height * 0.24f), strokeWidth = stroke.width)
            drawLine(color, center, Offset(size.width * 0.68f, size.height * 0.6f), strokeWidth = stroke.width)
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.45f)
                lineTo(size.width * 0.5f, size.height * 0.1f)
                lineTo(size.width * 0.9f, size.height * 0.45f)
                lineTo(size.width * 0.9f, size.height * 0.9f)
                lineTo(size.width * 0.1f, size.height * 0.9f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
    }
}

// Decorative session identifier; this thumbnail does not encode physiological data.
@Composable
internal fun MindprintThumbnail(id: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (i in 0 until 72) {
            val radius = size.minDimension * 0.44f * kotlin.math.sqrt(i / 72f)
            val angle = i * 2.4f + (id % 31).toFloat()
            val point = center + Offset(kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius * 0.75f)
            drawCircle(HushColors.Trends[i % 4].copy(alpha = 0.7f), 1.dp.toPx(), point)
        }
    }
}

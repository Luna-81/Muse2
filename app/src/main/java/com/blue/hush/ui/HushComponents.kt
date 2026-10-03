package com.blue.hush.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushMotion
import com.blue.hush.ui.theme.HushShapes
import com.blue.hush.ui.theme.HushSpace

@Composable
internal fun HushPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(HushSpace.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.animateContentSize(tween(HushMotion.TransitionMillis)), shape = HushShapes.Panel, color = HushColors.Surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, HushColors.Border.copy(alpha = 0.65f))) {
        Column(Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(HushSpace.md), content = content)
    }
}
@Composable
internal fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = HushShapes.Pill) { Text(text) }
}

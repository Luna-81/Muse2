@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blue.hush.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.blue.hush.session.MusicTrack
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushSpace

@Composable
internal fun SoundscapeSheet(
    selectedTrack: MusicTrack,
    onTrackSelected: (MusicTrack) -> Unit,
    onDismiss: () -> Unit,
    previewTrack: MusicTrack? = null,
    onPreviewTrack: ((MusicTrack) -> Unit)? = null,
    volume: Float? = null,
    onVolumeChanged: (Float) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HushColors.Surface) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(HushSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HushSpace.lg),
        ) {
            item { Text("Soundscapes", style = MaterialTheme.typography.headlineMedium) }
            if (volume != null) item {
                Slider(
                    value = volume,
                    onValueChange = onVolumeChanged,
                    modifier = Modifier.semantics { contentDescription = "Meditation volume" },
                )
            }
            items(MusicTrack.soundscapes, key = { it.name }) { track ->
                HushPanel(Modifier.fillMaxWidth()) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge)
                    Text(track.subtitle, color = HushColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
                        FilterChip(
                            selected = selectedTrack == track,
                            onClick = { onTrackSelected(track) },
                            label = { Text(if (selectedTrack == track) "Selected" else "Select") },
                        )
                        if (onPreviewTrack != null) TextButton(onClick = { onPreviewTrack(track) }) {
                            Text(if (previewTrack == track) "Stop preview" else "Preview")
                        }
                    }
                }
            }
        }
    }
}
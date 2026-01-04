package com.example.musicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.R
import com.example.musicplayer.data.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    index: Int,
    song: Song,
    isPlaying: Boolean,
    isDragging: Boolean = false,
    onClick: () -> Unit,
    onStartDrag: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onEndDrag: () -> Unit = {},
    onPositionChanged: () -> Unit = {}
) {
    val songUri = song.uri.toString()
    var dragOffset by remember(songUri) { mutableStateOf(Offset.Zero) }

    // Reset drag offset when not dragging
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            dragOffset = Offset.Zero
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
        else if (isDragging) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (isDragging) {
                        shadowElevation = 8.dp.toPx()
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                    translationY = if (isDragging) dragOffset.y else 0f
                }
                .pointerInput(songUri) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            onStartDrag()
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Don't coerce - let it follow the finger
                            dragOffset = Offset(0f, dragOffset.y + dragAmount.y)
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = {
                            dragOffset = Offset.Zero
                            onEndDrag()
                        },
                        onDragCancel = {
                            dragOffset = Offset.Zero
                            onEndDrag()
                        }
                    )
                }
                .clickable(
                    enabled = !isDragging,
                    onClick = onClick
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(36.dp),
                color = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri ?: song.uri)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_error)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album thumbnail",
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 4.dp)
            )

            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Long press to reorder",
                modifier = Modifier.size(20.dp),
                tint = if (isDragging) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


package com.example.musicplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.R
import com.example.musicplayer.data.Song

/**
 * A draggable song list with proper reordering support.
 * Uses a simple approach: track which index is being dragged and compute target based on drag distance.
 */
@Composable
fun DraggableSongList(
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Drag state
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Estimated item height in pixels (adjust if needed)
    val itemHeightPx = 88f

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> song.uri.toString() }
        ) { index, song ->
            val isDragging = draggedItemIndex == index
            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 0.dp,
                label = "elevation"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        // Only apply translation to the dragged item
                        if (isDragging) {
                            translationY = dragOffsetY
                            shadowElevation = 8.dp.toPx()
                        }
                    }
                    .shadow(elevation)
                    .background(
                        when {
                            isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                            currentSong?.uri == song.uri -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedItemIndex = index
                                dragOffsetY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (draggedItemIndex != null) {
                                    dragOffsetY += dragAmount.y

                                    // Use while loops to allow continuous multi-position movement
                                    var currentDraggedIndex = draggedItemIndex!!
                                    val moveThreshold = itemHeightPx * 0.4f

                                    // Moving down - use while to allow multiple swaps per drag
                                    while (dragOffsetY > moveThreshold && currentDraggedIndex < songs.size - 1) {
                                        onReorder(currentDraggedIndex, currentDraggedIndex + 1)
                                        currentDraggedIndex += 1
                                        draggedItemIndex = currentDraggedIndex
                                        dragOffsetY -= itemHeightPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }

                                    // Moving up - use while to allow multiple swaps per drag
                                    while (dragOffsetY < -moveThreshold && currentDraggedIndex > 0) {
                                        onReorder(currentDraggedIndex, currentDraggedIndex - 1)
                                        currentDraggedIndex -= 1
                                        draggedItemIndex = currentDraggedIndex
                                        dragOffsetY += itemHeightPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedItemIndex = null
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggedItemIndex = null
                                dragOffsetY = 0f
                            }
                        )
                    }
                    .clickable(enabled = draggedItemIndex == null) {
                        onSongClick(song)
                    }
            ) {
                DraggableSongItem(
                    index = index,
                    song = song,
                    isPlaying = currentSong?.uri == song.uri,
                    isDragging = isDragging
                )
            }

            HorizontalDivider()
        }
    }
}

@Composable
private fun DraggableSongItem(
    index: Int,
    song: Song,
    isPlaying: Boolean,
    isDragging: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Text(
            text = "${index + 1}.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp),
            color = if (isDragging) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        // Album art
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.albumArtUri ?: song.uri)
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.ic_album_error)
                .crossfade(true)
                .build(),
            contentDescription = "Album art",
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Song info
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

        // Duration
        Text(
            text = formatDurationLocal(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )

        // Drag handle
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            modifier = Modifier.size(24.dp),
            tint = if (isDragging) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDurationLocal(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


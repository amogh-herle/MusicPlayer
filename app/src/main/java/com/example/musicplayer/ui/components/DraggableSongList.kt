package com.example.musicplayer.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.R
import com.example.musicplayer.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A draggable song list with proper gesture handling.
 * - Single tap: plays the song
 * - Long press + drag: reorders the list
 * - Optimistic UI updates for smooth 60fps animations
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ===== OPTIMISTIC UI STATE =====
    // This is the UI-only list that updates immediately during drag
    var isDragging by remember { mutableStateOf(false) }

    // Use a snapshot state list that doesn't recreate on recomposition
    val localList = remember { mutableStateListOf<Song>() }

    // Sync with external list only when NOT actively dragging
    LaunchedEffect(songs, isDragging) {
        if (!isDragging) {
            if (localList.size != songs.size || localList != songs) {
                localList.clear()
                localList.addAll(songs)
            }
        }
    }

    // ===== DRAG STATE =====
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggedItemOffsetY by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Container-level gesture detection
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // Find which item was long-pressed
                            val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                val itemTop = itemInfo.offset
                                val itemBottom = itemInfo.offset + itemInfo.size
                                offset.y >= itemTop && offset.y <= itemBottom
                            }

                            hitItem?.let { item ->
                                if (item.index in localList.indices) {
                                    isDragging = true
                                    draggedItemKey = localList[item.index].uri.toString()
                                    draggedItemOffsetY = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            val draggedKey = draggedItemKey ?: return@detectDragGesturesAfterLongPress
                            draggedItemOffsetY += dragAmount.y

                            // ===== AUTO-SCROLL =====
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            val scrollThreshold = with(density) { 100.dp.toPx() }
                            val pointerY = change.position.y

                            when {
                                pointerY < scrollThreshold -> {
                                    // Scroll up
                                    autoScrollJob?.cancel()
                                    autoScrollJob = scope.launch {
                                        while (true) {
                                            listState.scrollBy(-8f)
                                            kotlinx.coroutines.delay(10)
                                        }
                                    }
                                }
                                pointerY > viewportHeight - scrollThreshold -> {
                                    // Scroll down
                                    autoScrollJob?.cancel()
                                    autoScrollJob = scope.launch {
                                        while (true) {
                                            listState.scrollBy(8f)
                                            kotlinx.coroutines.delay(10)
                                        }
                                    }
                                }
                                else -> {
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                }
                            }

                            // ===== REORDER LOGIC =====
                            val currentIndex = localList.indexOfFirst { it.uri.toString() == draggedKey }
                            if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                            val currentItemInfo = listState.layoutInfo.visibleItemsInfo
                                .find { it.index == currentIndex } ?: return@detectDragGesturesAfterLongPress

                            // Calculate center Y of dragged item
                            val draggedCenterY = currentItemInfo.offset + (currentItemInfo.size / 2) + draggedItemOffsetY

                            // Find the item we're hovering over
                            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { target ->
                                target.index != currentIndex &&
                                draggedCenterY >= target.offset &&
                                draggedCenterY <= target.offset + target.size
                            }

                            targetItem?.let { target ->
                                val targetIndex = target.index
                                if (targetIndex in localList.indices && currentIndex != targetIndex) {
                                    // INSTANT UI UPDATE - swap in local list
                                    val item = localList.removeAt(currentIndex)
                                    localList.add(targetIndex, item)

                                    // Adjust offset to keep item under finger
                                    if (targetIndex > currentIndex) {
                                        draggedItemOffsetY -= target.size
                                    } else {
                                        draggedItemOffsetY += target.size
                                    }

                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            // Find final positions
                            val draggedKey = draggedItemKey
                            if (draggedKey != null) {
                                val finalIndex = localList.indexOfFirst { it.uri.toString() == draggedKey }
                                val originalIndex = songs.indexOfFirst { it.uri.toString() == draggedKey }

                                // Only notify ViewModel if position actually changed
                                if (finalIndex != -1 && originalIndex != -1 && finalIndex != originalIndex) {
                                    onReorder(originalIndex, finalIndex)
                                }
                            }

                            // Reset state
                            isDragging = false
                            draggedItemKey = null
                            draggedItemOffsetY = 0f
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        },
                        onDragCancel = {
                            // Revert to original list on cancel
                            localList.clear()
                            localList.addAll(songs)

                            isDragging = false
                            draggedItemKey = null
                            draggedItemOffsetY = 0f
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        }
                    )
                }
        ) {
            itemsIndexed(
                items = localList,
                key = { _, song -> song.uri.toString() }
            ) { index, song ->
                val isBeingDragged = song.uri.toString() == draggedItemKey

                DraggableItem(
                    song = song,
                    isPlaying = currentSong?.uri == song.uri,
                    isBeingDragged = isBeingDragged,
                    dragOffset = if (isBeingDragged) draggedItemOffsetY else 0f,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

/**
 * Individual draggable song item with click and visual feedback
 */
@Composable
private fun DraggableItem(
    song: Song,
    isPlaying: Boolean,
    isBeingDragged: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Elevation animation for dragged item
    val elevation by animateDpAsState(
        targetValue = if (isBeingDragged) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "elevation"
    )

    // Scale animation for dragged item
    val scale by animateFloatAsState(
        targetValue = if (isBeingDragged) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isBeingDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation)
            // Clickable is AFTER pointerInput so tap events work
            .clickable(
                enabled = !isBeingDragged,
                onClick = onClick
            ),
        color = when {
            isBeingDragged -> MaterialTheme.colorScheme.surfaceContainerHighest
            isPlaying -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isBeingDragged) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp)
            )

            // Album Art
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri ?: song.uri)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_error)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art for ${song.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .shadow(2.dp, MaterialTheme.shapes.small)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Song Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalDivider()
}

/**
 * Format duration in mm:ss format
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


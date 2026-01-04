package com.example.musicplayer.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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

    // Local list for optimistic updates.
    // We only sync with 'songs' when NOT dragging to prevent UI jumps.
    var localList by remember { mutableStateOf(songs) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync local list with external list when not dragging
    LaunchedEffect(songs, isDragging) {
        if (!isDragging) {
            localList = songs
        }
    }

    // Drag state
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggedItemOffset by remember { mutableFloatStateOf(0f) }
    var initialDragIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-scroll job
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // 1. Find which item is under the finger
                            val hitItem = listState.layoutInfo.visibleItemsInfo.find { itemInfo ->
                                offset.y >= itemInfo.offset && offset.y <= itemInfo.offset + itemInfo.size
                            }

                            if (hitItem != null) {
                                val index = hitItem.index
                                // Ensure index is valid in our local list
                                if (index in localList.indices) {
                                    isDragging = true
                                    initialDragIndex = index
                                    draggedItemKey = localList[index].uri.toString()
                                    draggedItemOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggedItemKey == null) return@detectDragGesturesAfterLongPress

                            draggedItemOffset += dragAmount.y

                            // 2. Auto-scrolling logic
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            val distFromTop = change.position.y
                            val distFromBottom = viewportHeight - change.position.y
                            val scrollThreshold = 150f // px

                            if (distFromTop < scrollThreshold) {
                                // Scroll up
                                if (autoScrollJob == null) {
                                    autoScrollJob = scope.launch {
                                        while (true) {
                                            listState.scrollBy(-10f)
                                            kotlinx.coroutines.delay(10)
                                        }
                                    }
                                }
                            } else if (distFromBottom < scrollThreshold) {
                                // Scroll down
                                if (autoScrollJob == null) {
                                    autoScrollJob = scope.launch {
                                        while (true) {
                                            listState.scrollBy(10f)
                                            kotlinx.coroutines.delay(10)
                                        }
                                    }
                                }
                            } else {
                                autoScrollJob?.cancel()
                                autoScrollJob = null
                            }

                            // 3. Reordering Logic
                            // Find the current index of the dragged item in the local list
                            val currentIndex = localList.indexOfFirst { it.uri.toString() == draggedItemKey }
                            if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                            // Find the item we are hovering over
                            // We calculate the absolute Y position of the dragged item center
                            val currentInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
                            if (currentInfo != null) {
                                val itemCenterY = currentInfo.offset + (currentInfo.size / 2) + draggedItemOffset

                                // Find target to swap with
                                val targetItem = listState.layoutInfo.visibleItemsInfo.find { target ->
                                    target.index != currentIndex &&
                                    itemCenterY >= target.offset &&
                                    itemCenterY <= target.offset + target.size
                                }

                                if (targetItem != null) {
                                    val targetIndex = targetItem.index
                                    if (targetIndex in localList.indices) {
                                        // SWAP in local list
                                        val newList = localList.toMutableList()
                                        val item = newList.removeAt(currentIndex)
                                        newList.add(targetIndex, item)
                                        localList = newList

                                        // Adjust offset to keep the item visually under the finger
                                        // If we moved down (target > current), the item moved +height physically, so we reduce offset
                                        // If we moved up (target < current), the item moved -height physically, so we increase offset
                                        if (targetIndex > currentIndex) {
                                            draggedItemOffset -= targetItem.size
                                        } else {
                                            draggedItemOffset += targetItem.size
                                        }

                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                        // Notify ViewModel immediately (or you can wait for onDragEnd)
                                        onReorder(currentIndex, targetIndex)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            draggedItemKey = null
                            draggedItemOffset = 0f
                            initialDragIndex = null
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        },
                        onDragCancel = {
                            isDragging = false
                            draggedItemKey = null
                            draggedItemOffset = 0f
                            initialDragIndex = null
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                            // Revert to original list if needed, or just sync with songs
                            localList = songs
                        }
                    )
                }
        ) {
            itemsIndexed(
                items = localList,
                key = { _, song -> song.uri.toString() }
            ) { index, song ->
                val isBeingDragged = (song.uri.toString() == draggedItemKey)

                // If this item is being dragged, apply the offset
                // If it's not, it sits in its list position naturally
                val translationY = if (isBeingDragged) draggedItemOffset else 0f
                val zIndex = if (isBeingDragged) 1f else 0f
                val elevation by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp, label = "elevation")

                DraggableSongItem(
                    song = song,
                    index = index,
                    isPlaying = currentSong?.uri == song.uri,
                    isDragging = isBeingDragged,
                    modifier = Modifier
                        .zIndex(zIndex)
                        .graphicsLayer {
                            this.translationY = translationY
                        }
                        .shadow(elevation)
                        .background(MaterialTheme.colorScheme.surface)
                        .fillMaxWidth()
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun DraggableSongItem(
    song: Song,
    index: Int,
    isPlaying: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp), // Fixed height helps with calculation stability
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )

        // Album Art
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.albumArtUri ?: song.uri)
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.ic_album_error)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .shadow(4.dp, MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = formatDurationLocal(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDurationLocal(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

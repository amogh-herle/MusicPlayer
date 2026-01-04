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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.R
import com.example.musicplayer.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Modern draggable song list with gradient effects and smooth animations
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

    // Optimistic UI state
    var isDragging by remember { mutableStateOf(false) }
    val localList = remember { mutableStateListOf<Song>() }
    var dragStartIndex by remember { mutableStateOf(-1) }

    // Sync with external list
    LaunchedEffect(songs, isDragging) {
        if (!isDragging) {
            if (localList.size != songs.size || localList != songs) {
                localList.clear()
                localList.addAll(songs)
            }
        }
    }

    // Drag state
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggedItemOffsetY by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                val itemTop = itemInfo.offset
                                val itemBottom = itemInfo.offset + itemInfo.size
                                offset.y >= itemTop && offset.y <= itemBottom
                            }

                            hitItem?.let { item ->
                                if (item.index in localList.indices) {
                                    isDragging = true
                                    dragStartIndex = item.index
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

                            // Auto-scroll
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            val scrollThreshold = with(density) { 100.dp.toPx() }
                            val pointerY = change.position.y

                            when {
                                pointerY < scrollThreshold -> {
                                    autoScrollJob?.cancel()
                                    autoScrollJob = scope.launch {
                                        while (true) {
                                            listState.scrollBy(-8f)
                                            kotlinx.coroutines.delay(10)
                                        }
                                    }
                                }
                                pointerY > viewportHeight - scrollThreshold -> {
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

                            // Reorder logic
                            val currentIndex = localList.indexOfFirst { it.uri.toString() == draggedKey }
                            if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                            val currentItemInfo = listState.layoutInfo.visibleItemsInfo
                                .find { it.index == currentIndex } ?: return@detectDragGesturesAfterLongPress

                            val draggedCenterY = currentItemInfo.offset + (currentItemInfo.size / 2) + draggedItemOffsetY

                            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { target ->
                                target.index != currentIndex &&
                                        draggedCenterY >= target.offset &&
                                        draggedCenterY <= target.offset + target.size
                            }

                            targetItem?.let { target ->
                                val targetIndex = target.index
                                if (targetIndex in localList.indices && currentIndex != targetIndex) {
                                    val item = localList.removeAt(currentIndex)
                                    localList.add(targetIndex, item)

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
                            val draggedKey = draggedItemKey
                            if (draggedKey != null && dragStartIndex >= 0) {
                                val finalIndex = localList.indexOfFirst { it.uri.toString() == draggedKey }

                                if (finalIndex != -1 && finalIndex != dragStartIndex) {
                                    onReorder(dragStartIndex, finalIndex)
                                }
                            }

                            isDragging = false
                            draggedItemKey = null
                            draggedItemOffsetY = 0f
                            dragStartIndex = -1
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        },
                        onDragCancel = {
                            localList.clear()
                            localList.addAll(songs)

                            isDragging = false
                            draggedItemKey = null
                            draggedItemOffsetY = 0f
                            dragStartIndex = -1
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        }
                    )
                },
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = localList,
                key = { _, song -> song.uri.toString() }
            ) { index, song ->
                val isBeingDragged = song.uri.toString() == draggedItemKey

                ModernDraggableItem(
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
 * Modern song item with gradient effects and larger album art
 */
@Composable
private fun ModernDraggableItem(
    song: Song,
    isPlaying: Boolean,
    isBeingDragged: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (isBeingDragged) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isBeingDragged) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val cardColor = when {
        isBeingDragged -> MaterialTheme.colorScheme.surfaceContainerHighest
        isPlaying -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .zIndex(if (isBeingDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, MaterialTheme.shapes.medium)
            .clickable(
                enabled = !isBeingDragged,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Box {
            // Gradient overlay for playing song
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag Handle with gradient when dragging
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = when {
                        isBeingDragged -> MaterialTheme.colorScheme.primary
                        isPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 8.dp)
                )

                // Larger Album Art (72dp)
                Card(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(if (isPlaying) 8.dp else 4.dp, MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri ?: song.uri)
                            .placeholder(R.drawable.ic_album_placeholder)
                            .error(R.drawable.ic_album_error)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art for ${song.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Song Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = if (isPlaying) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Duration
                Text(
                    text = formatDuration(song.duration),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
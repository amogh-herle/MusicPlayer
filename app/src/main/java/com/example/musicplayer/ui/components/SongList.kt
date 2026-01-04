package com.example.musicplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.musicplayer.PlaylistManager
import com.example.musicplayer.data.Song

@Composable
fun SongList(
    songs: List<Song>,
    currentSong: Song?,
    searchQuery: String,
    currentMusicDirectory: String,
    onSongClick: (Song) -> Unit,
    onReorderSongs: (Int, Int) -> Unit,
    onRequestPermission: () -> Unit,
    onChangeDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            songs.isEmpty() && searchQuery.isEmpty() -> {
                EmptyLibraryView(
                    currentMusicDirectory = currentMusicDirectory,
                    onRequestPermission = onRequestPermission,
                    onChangeDirectory = onChangeDirectory
                )
            }
            songs.isEmpty() && searchQuery.isNotEmpty() -> {
                NoSearchResultsView(searchQuery = searchQuery)
            }
            else -> {
                SongListContent(
                    songs = songs,
                    currentSong = currentSong,
                    searchQuery = searchQuery,
                    onSongClick = onSongClick,
                    onReorderSongs = onReorderSongs
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    currentMusicDirectory: String,
    onRequestPermission: () -> Unit,
    onChangeDirectory: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No songs found",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission & Scan Music")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Looking in: ${currentMusicDirectory.ifEmpty { "/Music/MySpotifyBackup" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onChangeDirectory) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Change Directory")
            }
        }
    }
}

@Composable
private fun NoSearchResultsView(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No songs match \"$searchQuery\"",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SongListContent(
    songs: List<Song>,
    currentSong: Song?,
    searchQuery: String,
    onSongClick: (Song) -> Unit,
    onReorderSongs: (Int, Int) -> Unit
) {
    Column {
        Text(
            text = "${songs.size} songs${if (searchQuery.isNotEmpty()) " found" else ""}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )

        // Queue mode indicator
        if (PlaylistManager.hasManualChanges()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Custom queue order",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (PlaylistManager.isInShuffleMode()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Smart Shuffle active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Draggable queue list
        var draggedSongUri by remember { mutableStateOf<String?>(null) }
        var accumulatedOffset by remember { mutableFloatStateOf(0f) }
        val haptic = LocalHapticFeedback.current
        val itemHeight = 80f

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState()
        ) {
            itemsIndexed(
                items = songs,
                key = { _, song -> song.uri.toString() }
            ) { index, song ->
                val songUri = song.uri.toString()
                val isDragging = draggedSongUri == songUri

                SongItem(
                    index = index,
                    song = song,
                    isPlaying = currentSong?.uri == song.uri,
                    isDragging = isDragging,
                    onClick = {
                        if (draggedSongUri == null) {
                            onSongClick(song)
                        }
                    },
                    onStartDrag = {
                        draggedSongUri = songUri
                        accumulatedOffset = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { offset ->
                        if (draggedSongUri == songUri) {
                            accumulatedOffset += offset

                            val currentPos = songs.indexOfFirst { it.uri.toString() == draggedSongUri }
                            if (currentPos >= 0) {
                                val positionsDelta = (accumulatedOffset / itemHeight).toInt()

                                if (positionsDelta != 0) {
                                    val newTargetIndex = (currentPos + positionsDelta)
                                        .coerceIn(0, songs.size - 1)

                                    if (newTargetIndex != currentPos) {
                                        onReorderSongs(currentPos, newTargetIndex)
                                        accumulatedOffset = 0f
                                    }
                                }
                            }
                        }
                    },
                    onEndDrag = {
                        if (draggedSongUri == songUri) {
                            draggedSongUri = null
                            accumulatedOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onPositionChanged = {}
                )
            }
        }
    }
}


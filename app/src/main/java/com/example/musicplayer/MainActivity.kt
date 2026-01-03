package com.example.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.data.Song
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val allSongs = mutableStateListOf<Song>()
    private val displayedSongs = mutableStateListOf<Song>()
    private var isPlaying by mutableStateOf(false)
    private var isShuffleEnabled by mutableStateOf(false)
    private var currentSong by mutableStateOf<Song?>(null)
    private var currentPosition by mutableStateOf(0L)
    private var duration by mutableStateOf(0L)
    private var searchQuery by mutableStateOf("")
    private var currentMusicDirectory by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) loadSongs()
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Extract folder path from URI
            val path = extractPathFromUri(selectedUri)
            if (path.isNotEmpty()) {
                LocalScanner.setMusicDirectory(this, path)
                currentMusicDirectory = path
                loadSongs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load saved music directory
        currentMusicDirectory = LocalScanner.getMusicDirectory(this)

        // Collect playback state from StateFlow (replaces deprecated LocalBroadcastManager)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MusicPlaybackState.playbackProgress.collect { progress ->
                        currentPosition = progress.position
                        duration = progress.duration
                        isPlaying = progress.isPlaying
                    }
                }
                launch {
                    MusicPlaybackState.currentSongInfo.collect { songInfo ->
                        if (songInfo.title.isNotEmpty()) {
                            // Search in displayedSongs (current view, could be shuffled)
                            // not allSongs (original unshuffled list)
                            currentSong = displayedSongs.find {
                                it.title == songInfo.title && it.artist == songInfo.artist
                            }
                        }
                    }
                }
            }
        }

        setContent {
            MusicPlayerTheme {
                MusicPlayerScreen(
                    songs = displayedSongs,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    currentSong = currentSong,
                    currentPosition = currentPosition,
                    duration = duration,
                    searchQuery = searchQuery,
                    currentMusicDirectory = currentMusicDirectory,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        filterSongs(query)
                    },
                    onPlayPause = { togglePlayPause() },
                    onShuffle = { toggleShuffle() },
                    onSongClick = { playSong(it) },
                    onNext = { playNext() },
                    onPrevious = { playPrevious() },
                    onSeek = { seekTo(it) },
                    onRequestPermission = { requestPermissions() },
                    onChangeDirectory = { folderPickerLauncher.launch(null) },
                    onReorderSongs = { from, to -> reorderSongs(from, to) }
                )
            }
        }

        checkAndRequestPermissions()
    }

    private fun extractPathFromUri(uri: Uri): String {
        val path = uri.path ?: return ""
        // Extract the actual folder path from content URI
        // Format is usually: /tree/primary:Music/Folder or /tree/raw:/storage/...
        return when {
            path.contains(":") -> {
                val split = path.split(":")
                if (split.size >= 2) {
                    "/" + split.last()
                } else path
            }
            else -> path
        }
    }

    override fun onDestroy() {
        // StateFlow collection is automatically cancelled when lifecycle ends
        super.onDestroy()
    }

    private fun filterSongs(query: String) {
        if (query.isBlank()) {
            displayedSongs.clear()
            displayedSongs.addAll(allSongs)
            return
        }

        val queryLower = query.lowercase(Locale.getDefault())
        val words = queryLower.split(" ").filter { it.isNotBlank() }

        // Advanced search with fuzzy matching and scoring
        val scoredSongs = allSongs.map { song ->
            val titleLower = song.title.lowercase(Locale.getDefault())
            val artistLower = song.artist.lowercase(Locale.getDefault())
            val albumLower = song.album.lowercase(Locale.getDefault())

            var score = 0

            // Exact match gets highest score
            if (titleLower == queryLower) score += 100
            if (artistLower == queryLower) score += 80
            if (albumLower == queryLower) score += 60

            // Starts with query
            if (titleLower.startsWith(queryLower)) score += 50
            if (artistLower.startsWith(queryLower)) score += 40
            if (albumLower.startsWith(queryLower)) score += 30

            // Contains query
            if (titleLower.contains(queryLower)) score += 25
            if (artistLower.contains(queryLower)) score += 20
            if (albumLower.contains(queryLower)) score += 15

            // Word matching - each word that matches adds points
            words.forEach { word ->
                if (titleLower.contains(word)) score += 10
                if (artistLower.contains(word)) score += 8
                if (albumLower.contains(word)) score += 5

                // Fuzzy matching - check if word is similar (within 2 char difference)
                titleLower.split(" ").forEach { titleWord ->
                    if (levenshteinDistance(word, titleWord) <= 2) score += 5
                }
                artistLower.split(" ").forEach { artistWord ->
                    if (levenshteinDistance(word, artistWord) <= 2) score += 4
                }
            }

            // Acronym matching (e.g., "tswift" matches "Taylor Swift")
            val titleAcronym = song.title.split(" ").mapNotNull { it.firstOrNull()?.lowercaseChar() }.joinToString("")
            val artistAcronym = song.artist.split(" ").mapNotNull { it.firstOrNull()?.lowercaseChar() }.joinToString("")
            if (titleAcronym.contains(queryLower)) score += 15
            if (artistAcronym.contains(queryLower)) score += 12

            Pair(song, score)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        displayedSongs.clear()
        displayedSongs.addAll(scoredSongs)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun loadSongs() {
        val targetDir = LocalScanner.getMusicDirectory(this)
        val scanned = LocalScanner.scanMusicFolder(contentResolver, targetDir)
        allSongs.clear()
        allSongs.addAll(scanned)
        displayedSongs.clear()
        displayedSongs.addAll(scanned)
        isShuffleEnabled = false
    }

    private fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        if (isShuffleEnabled) {
            // Pass allSongs to PlaylistManager so it can re-shuffle on loop
            val shuffled = PlaylistManager.smartShuffle(allSongs.toList())
            displayedSongs.clear()
            displayedSongs.addAll(shuffled)

            // smartShuffle already sets currentIndex to 0 and isShuffleMode to true
            // Don't call setPlaylistWithIndex as it would reset isShuffleMode to false
            // Only adjust if a song is currently playing
            currentSong?.let { song ->
                val index = displayedSongs.indexOf(song)
                if (index >= 0) {
                    // Manually update currentIndex without resetting shuffle mode
                    PlaylistManager.updateCurrentIndex(index)
                }
            }
        } else {
            // Revert to original order (filtered by search query)
            filterSongs(searchQuery)

            // Turn off shuffle mode and sync the list
            currentSong?.let { song ->
                val index = displayedSongs.indexOf(song)
                if (index >= 0) {
                    PlaylistManager.setPlaylistWithIndex(displayedSongs.toList(), index)
                }
            } ?: run {
                // No current song, just set the playlist without an index
                PlaylistManager.setPlaylist(displayedSongs.toList())
            }
        }
    }

    private fun playSong(song: Song) {
        currentSong = song
        val index = displayedSongs.indexOf(song)
        if (index >= 0) {
            // ALWAYS sync the playlist to ensure correctness
            // This prevents any desync between UI and PlaylistManager
            val currentPlaylist = PlaylistManager.getPlaylist()

            // Check if we're in shuffle mode and playlist matches
            val playlistMatches = currentPlaylist.size == displayedSongs.size &&
                    currentPlaylist.zip(displayedSongs).all { (a, b) -> a.uri == b.uri }

            // If in shuffle mode and playlist matches, preserve shuffle mode
            if (PlaylistManager.isInShuffleMode() && playlistMatches) {
                // Playlist is correct, just update index to preserve shuffle mode
                PlaylistManager.updateCurrentIndex(index)
            } else {
                // Either not shuffled or playlist doesn't match - sync completely
                PlaylistManager.setPlaylistWithIndex(displayedSongs.toList(), index)
            }

            // Verify the current song in PlaylistManager matches what we expect
            val currentInPlaylist = PlaylistManager.getCurrentSong()
            if (currentInPlaylist?.uri != song.uri) {
                // Mismatch detected! Force resync
                android.util.Log.e("MusicPlayer", "PlaylistManager desync detected! " +
                        "Expected: ${song.title}, Got: ${currentInPlaylist?.title}")
                PlaylistManager.setPlaylistWithIndex(displayedSongs.toList(), index)
            }

            val intent = Intent(this, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PLAY
                putExtra(MusicPlayerService.EXTRA_URI, song.uri.toString())
                putExtra(MusicPlayerService.EXTRA_TITLE, song.title)
                putExtra(MusicPlayerService.EXTRA_ARTIST, song.artist)
                putExtra(MusicPlayerService.EXTRA_ALBUM_ID, song.albumId)
            }
            ContextCompat.startForegroundService(this, intent)
            isPlaying = true
        }
    }

    private fun togglePlayPause() {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = if (isPlaying) MusicPlayerService.ACTION_PAUSE else MusicPlayerService.ACTION_RESUME
        }
        startService(intent)
        isPlaying = !isPlaying
    }

    private fun playNext() {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_NEXT
        }
        startService(intent)
    }

    private fun playPrevious() {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PREV
        }
        startService(intent)
    }

    private fun seekTo(position: Long) {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_SEEK
            putExtra("position", position)
        }
        startService(intent)
    }

    private fun reorderSongs(fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex && fromIndex >= 0 && fromIndex < displayedSongs.size
            && toIndex >= 0 && toIndex < displayedSongs.size) {

            // Reorder in UI list
            val song = displayedSongs.removeAt(fromIndex)
            displayedSongs.add(toIndex, song)

            // Sync the reordered list to PlaylistManager
            PlaylistManager.reorderPlaylist(fromIndex, toIndex)

            // Find current song's new index and update PlaylistManager
            currentSong?.let { current ->
                val currentIndex = displayedSongs.indexOf(current)
                if (currentIndex >= 0) {
                    // Re-sync entire playlist to ensure consistency
                    PlaylistManager.setPlaylistWithIndex(displayedSongs.toList(), currentIndex)
                }
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

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

    // Reset drag offset when the item's index changes (list was reordered)
    LaunchedEffect(index, isDragging) {
        if (isDragging) {
            // Item position changed while dragging - reset visual offset
            dragOffset = Offset.Zero
            onPositionChanged()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
        else if (isDragging) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // Elevate and scale when dragging
                    if (isDragging) {
                        shadowElevation = 8.dp.toPx()
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                    // Only apply translation if actually dragging, reset otherwise
                    translationY = if (isDragging) dragOffset.y else 0f
                }
                .pointerInput(songUri) {  // Key by song URI to reset when item changes
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ ->
                            onStartDrag()
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Clamp visual offset to prevent it from going too far
                            val newY = (dragOffset.y + dragAmount.y).coerceIn(-200f, 200f)
                            dragOffset = Offset(0f, newY)
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
            // Queue number
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(36.dp),
                color = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Album art thumbnail
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

            // Drag handle icon (visual cue for long-press)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    songs: List<Song>,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    currentSong: Song?,
    currentPosition: Long,
    duration: Long,
    searchQuery: String,
    currentMusicDirectory: String,
    onSearchQueryChange: (String) -> Unit,
    onPlayPause: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onRequestPermission: () -> Unit,
    onChangeDirectory: () -> Unit,
    onReorderSongs: (Int, Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Music Player",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Current directory info
                Text(
                    text = "Current Directory",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentMusicDirectory.ifEmpty { "/Music/MySpotifyBackup" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Change Music Directory") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onChangeDirectory()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text("Refresh Library") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onRequestPermission()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${songs.size} songs in library",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Music Player") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search songs, artists, albums...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }
            },
        bottomBar = {
            Column {
                // Now playing bar with controls
                currentSong?.let { song ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Song info + poster
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.albumArtUri ?: song.uri)
                                        .placeholder(R.drawable.ic_album_placeholder)
                                        .error(R.drawable.ic_album_error)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Album art",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress bar
                            Column {
                                Slider(
                                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                    onValueChange = { fraction ->
                                        onSeek((fraction * duration).toLong())
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatDuration(currentPosition),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = formatDuration(duration),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Control buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onShuffle) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = onPrevious) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                FilledIconButton(
                                    onClick = onPlayPause,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                IconButton(onClick = onNext) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // Shuffle button bar
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onShuffle) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Smart Shuffle All")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (songs.isEmpty() && searchQuery.isEmpty()) {
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
            } else if (songs.isEmpty() && searchQuery.isNotEmpty()) {
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
            } else {
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

                // Draggable queue list - state at parent level
                var draggedSongUri by remember { mutableStateOf<String?>(null) }
                var accumulatedOffset by remember { mutableStateOf(0f) }
                val haptic = LocalHapticFeedback.current
                val itemHeight = 80f // Approximate height of each item in pixels


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
                                // Only process drag for the song that initiated the drag
                                if (draggedSongUri == songUri) {
                                    accumulatedOffset += offset

                                    // Find current position of the dragged song
                                    val currentPos = songs.indexOfFirst { it.uri.toString() == draggedSongUri }
                                    if (currentPos >= 0) {
                                        // Calculate target position
                                        val positionsDelta = (accumulatedOffset / itemHeight).toInt()

                                        if (positionsDelta != 0) {
                                            val newTargetIndex = (currentPos + positionsDelta)
                                                .coerceIn(0, songs.size - 1)

                                            if (newTargetIndex != currentPos) {
                                                onReorderSongs(currentPos, newTargetIndex)
                                                // Reset offset after successful reorder
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
                            onPositionChanged = {
                                // Visual offset was reset in SongItem, nothing to do here
                            }
                        )
                    }
                }
            }
        }
    }
}}


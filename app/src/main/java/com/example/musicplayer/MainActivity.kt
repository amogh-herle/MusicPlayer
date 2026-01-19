package com.example.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.musicplayer.ui.components.FullScreenPlayer
import com.example.musicplayer.ui.components.MiniPlayer
import com.example.musicplayer.ui.components.SongList
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MusicPlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            // Only load if playlist is empty
            if (!PlaylistManager.hasPlaylist()) {
                viewModel.loadSongs(contentResolver)
            }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            viewModel.setMusicDirectory(selectedUri)
            viewModel.loadSongs(contentResolver) // Force reload on directory change
        }
    }

    @android.annotation.SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MusicPlayerTheme {
                MusicPlayerApp(
                    viewModel = viewModel,
                    onRequestPermission = { requestPermissions() },
                    onChangeDirectory = { folderPickerLauncher.launch(null) }
                )
            }
        }

        // FIXED: Only load songs if permission granted AND playlist empty
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(
                    this,
                    "Notification permission was revoked. Music player notifications may not appear.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // FIXED: Only load if playlist is empty (first launch)
            if (!PlaylistManager.hasPlaylist()) {
                viewModel.loadSongs(contentResolver)
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(
    viewModel: MusicPlayerViewModel,
    onRequestPermission: () -> Unit,
    onChangeDirectory: () -> Unit
) {
    val songs by viewModel.displayedSongs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentMusicDirectory by viewModel.currentMusicDirectory.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()

    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = isPlayerExpanded) {
        viewModel.collapsePlayer()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    currentMusicDirectory = currentMusicDirectory,
                    songCount = songs.size,
                    onChangeDirectory = {
                        scope.launch { drawerState.close() }
                        onChangeDirectory()
                    },
                    onRefresh = {
                        scope.launch { drawerState.close() }
                        // FIXED: Force reload when user explicitly requests refresh
                        viewModel.forceRefresh()
                    }
                )
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
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Search songs, artists, albums...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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
                    BottomAppBar {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(onClick = viewModel::toggleShuffle) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Smart Shuffle All")
                            }
                        }
                    }
                }
            ) { innerPadding ->
                SongList(
                    songs = songs,
                    currentSong = currentSong,
                    searchQuery = searchQuery,
                    currentMusicDirectory = currentMusicDirectory,
                    onSongClick = viewModel::playSong,
                    onReorderSongs = viewModel::reorderSongs,
                    onRequestPermission = onRequestPermission,
                    onChangeDirectory = onChangeDirectory,
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(bottom = if (currentSong != null) 72.dp else 0.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = currentSong != null && !isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        ) {
            currentSong?.let { song ->
                MiniPlayer(
                    currentSong = song,
                    isPlaying = isPlaying,
                    progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                    onPrevious = viewModel::playPrevious,
                    onSeek = viewModel::seekTo,
                    onClick = viewModel::expandPlayer,
                    modifier = Modifier.padding(bottom = 80.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded && currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            currentSong?.let { song ->
                FullScreenPlayer(
                    currentSong = song,
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                    onPrevious = viewModel::playPrevious,
                    onShuffle = viewModel::toggleShuffle,
                    onSeek = viewModel::seekTo,
                    onCollapse = viewModel::collapsePlayer
                )
            }
        }
    }
}

@Composable
private fun DrawerContent(
    currentMusicDirectory: String,
    songCount: Int,
    onChangeDirectory: () -> Unit,
    onRefresh: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Music Player",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Current Directory",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = currentMusicDirectory.ifEmpty { "/Music/MySongs" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = { Text("Change Music Directory") },
            selected = false,
            onClick = onChangeDirectory,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            label = { Text("Refresh Library") },
            selected = false,
            onClick = onRefresh,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "$songCount songs in library",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
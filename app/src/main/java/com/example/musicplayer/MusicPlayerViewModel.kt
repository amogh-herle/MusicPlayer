package com.example.musicplayer

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Songs state
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _displayedSongs = MutableStateFlow<List<Song>>(emptyList())
    val displayedSongs: StateFlow<List<Song>> = _displayedSongs.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // UI state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentMusicDirectory = MutableStateFlow("")
    val currentMusicDirectory: StateFlow<String> = _currentMusicDirectory.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val context: Context
        get() = getApplication()

    init {
        _currentMusicDirectory.value = LocalScanner.getMusicDirectory(context)

        // Collect playback state updates
        viewModelScope.launch {
            MusicPlaybackState.playbackProgress.collect { progress ->
                _currentPosition.value = progress.position
                _duration.value = progress.duration
                _isPlaying.value = progress.isPlaying
            }
        }

        viewModelScope.launch {
            MusicPlaybackState.currentSongInfo.collect { songInfo ->
                if (songInfo.title.isNotEmpty()) {
                    // Try to find by ID/URI first if possible, otherwise Fallback to Title/Artist
                    _currentSong.value = _displayedSongs.value.find {
                        it.title == songInfo.title && it.artist == songInfo.artist
                    } ?: _allSongs.value.find {
                        it.title == songInfo.title && it.artist == songInfo.artist
                    }
                }
            }
        }
    }

    fun loadSongs(contentResolver: ContentResolver) {
        val targetDir = LocalScanner.getMusicDirectory(context)
        val scanned = LocalScanner.scanMusicFolder(contentResolver, targetDir)
        _allSongs.value = scanned
        _displayedSongs.value = scanned
        _isShuffleEnabled.value = false
        // Update PlaylistManager with initial data
        PlaylistManager.setPlaylist(scanned)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterSongs(query)
    }

    private fun filterSongs(query: String) {
        if (query.isBlank()) {
            _displayedSongs.value = _allSongs.value
            return
        }
        val queryLower = query.lowercase(Locale.getDefault())
        val scoredSongs = _allSongs.value.mapNotNull { song ->
            if (song.title.contains(queryLower, true) || song.artist.contains(queryLower, true)) song else null
        }
        _displayedSongs.value = scoredSongs
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        val playingSong = _currentSong.value

        if (_isShuffleEnabled.value) {
            val shuffled = PlaylistManager.smartShuffle(_allSongs.value)
            _displayedSongs.value = shuffled

            // Sync current song if playing
            if (playingSong != null) {
                val newIndex = shuffled.indexOfFirst { it.uri == playingSong.uri }
                if (newIndex >= 0) PlaylistManager.updateCurrentIndex(newIndex)
            }
        } else {
            // Revert to original order
            filterSongs(_searchQuery.value) // Reset to normal list
            val currentList = _displayedSongs.value

            if (playingSong != null) {
                val newIndex = currentList.indexOfFirst { it.uri == playingSong.uri }
                if (newIndex >= 0) {
                    PlaylistManager.setPlaylistWithIndex(currentList, newIndex)
                } else {
                    PlaylistManager.setPlaylist(currentList)
                }
            }
        }
    }

    fun playSong(song: Song) {
        _currentSong.value = song
        val index = _displayedSongs.value.indexOfFirst { it.uri == song.uri }

        if (index < 0) return

        // Update Backend
        if (PlaylistManager.isInShuffleMode()) {
            // If in shuffle mode, we might be clicking a song in the visible list
            // We need to ensure the backend is aware of the exact list state
            if (_displayedSongs.value.size == PlaylistManager.getPlaylist().size) {
                PlaylistManager.updateCurrentIndex(index)
            } else {
                // Edge case: Search result click while in shuffle
                PlaylistManager.setPlaylistWithIndex(_displayedSongs.value, index)
            }
        } else {
            PlaylistManager.setPlaylistWithIndex(_displayedSongs.value, index)
        }

        // Start Service
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PLAY
            putExtra(MusicPlayerService.EXTRA_URI, song.uri.toString())
            putExtra(MusicPlayerService.EXTRA_TITLE, song.title)
            putExtra(MusicPlayerService.EXTRA_ARTIST, song.artist)
            putExtra(MusicPlayerService.EXTRA_ALBUM_ID, song.albumId)
        }
        ContextCompat.startForegroundService(context, intent)
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = if (_isPlaying.value) MusicPlayerService.ACTION_PAUSE else MusicPlayerService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun playNext() {
        context.startService(Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_NEXT
        })
    }

    fun playPrevious() {
        context.startService(Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PREV
        })
    }

    fun seekTo(position: Long) {
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_SEEK
            putExtra("position", position)
        }
        context.startService(intent)
    }

    // --- KEY FIX HERE ---
    fun reorderSongs(fromIndex: Int, toIndex: Int) {
        val currentList = _displayedSongs.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return

        // Optimistically update UI list first
        val moved = currentList.removeAt(fromIndex)
        currentList.add(toIndex, moved)
        _displayedSongs.value = currentList

        // Sync backend and get the authoritative playlist
        val newPlaylist = PlaylistManager.reorderPlaylist(fromIndex, toIndex)

        // Refresh currentSong if its index may have shifted
        val current = _currentSong.value
        if (current != null) {
            val updated = newPlaylist.firstOrNull { it.uri == current.uri }
            if (updated != null && updated != current) {
                _currentSong.value = updated
            }
        } else {
            _currentSong.value = PlaylistManager.getCurrentSong()
        }
    }

    fun setMusicDirectory(uri: Uri) {
        val path = extractPathFromUri(uri)
        if (path.isNotEmpty()) {
            LocalScanner.setMusicDirectory(context, path)
            _currentMusicDirectory.value = path
        }
    }

    private fun extractPathFromUri(uri: Uri): String {
        val path = uri.path ?: return ""
        return if (path.contains(":")) "/"+path.split(":").last() else path
    }

    fun expandPlayer() { _isPlayerExpanded.value = true }
    fun collapsePlayer() { _isPlayerExpanded.value = false }
}
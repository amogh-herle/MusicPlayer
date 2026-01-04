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
        // Load saved music directory
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
                    _currentSong.value = _displayedSongs.value.find {
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
        val words = queryLower.split(" ").filter { it.isNotBlank() }

        val scoredSongs = _allSongs.value.map { song ->
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

            // Word matching
            words.forEach { word ->
                if (titleLower.contains(word)) score += 10
                if (artistLower.contains(word)) score += 8
                if (albumLower.contains(word)) score += 5

                // Fuzzy matching
                titleLower.split(" ").forEach { titleWord ->
                    if (levenshteinDistance(word, titleWord) <= 2) score += 5
                }
                artistLower.split(" ").forEach { artistWord ->
                    if (levenshteinDistance(word, artistWord) <= 2) score += 4
                }
            }

            // Acronym matching
            val titleAcronym = song.title.split(" ").mapNotNull { it.firstOrNull()?.lowercaseChar() }.joinToString("")
            val artistAcronym = song.artist.split(" ").mapNotNull { it.firstOrNull()?.lowercaseChar() }.joinToString("")
            if (titleAcronym.contains(queryLower)) score += 15
            if (artistAcronym.contains(queryLower)) score += 12

            Pair(song, score)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        _displayedSongs.value = scoredSongs
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

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        if (_isShuffleEnabled.value) {
            val playingSong = _currentSong.value
            val shuffled = PlaylistManager.smartShuffle(_allSongs.value)
            _displayedSongs.value = shuffled

            if (playingSong != null) {
                val newIndex = shuffled.indexOfFirst { it.uri == playingSong.uri }
                if (newIndex >= 0) {
                    PlaylistManager.updateCurrentIndex(newIndex)
                    _currentSong.value = shuffled[newIndex]
                }
            }
        } else {
            val playingSong = _currentSong.value
            filterSongs(_searchQuery.value)

            if (playingSong != null) {
                val newIndex = _displayedSongs.value.indexOfFirst { it.uri == playingSong.uri }
                if (newIndex >= 0) {
                    PlaylistManager.setPlaylistWithIndex(_displayedSongs.value, newIndex)
                    _currentSong.value = _displayedSongs.value[newIndex]
                } else {
                    PlaylistManager.setPlaylist(_displayedSongs.value)
                }
            } else {
                PlaylistManager.setPlaylist(_displayedSongs.value)
            }
        }
    }

    fun playSong(song: Song) {
        _currentSong.value = song

        val index = _displayedSongs.value.indexOf(song)
        if (index < 0) {
            android.util.Log.e("MusicPlayer", "Song not found in displayedSongs: ${song.title}")
            return
        }

        val currentPlaylist = PlaylistManager.getPlaylist()
        val isCurrentlyShuffle = PlaylistManager.isInShuffleMode()

        val playlistMatches = currentPlaylist.size == _displayedSongs.value.size &&
                currentPlaylist.indices.all { i ->
                    currentPlaylist[i].uri == _displayedSongs.value[i].uri
                }

        if (isCurrentlyShuffle && playlistMatches) {
            PlaylistManager.updateCurrentIndex(index)
        } else {
            PlaylistManager.setPlaylistWithIndex(_displayedSongs.value, index)
            if (_isShuffleEnabled.value) {
                PlaylistManager.setShuffleModeEnabled(true)
            }
        }

        val verifyCurrentSong = PlaylistManager.getCurrentSong()
        if (verifyCurrentSong?.uri != song.uri) {
            android.util.Log.e("MusicPlayer",
                "CRITICAL DESYNC! Expected: ${song.title}, Got: ${verifyCurrentSong?.title}")
            PlaylistManager.setPlaylistWithIndex(_displayedSongs.value, index)
        }

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
        _isPlaying.value = !_isPlaying.value
    }

    fun playNext() {
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_NEXT
        }
        context.startService(intent)
    }

    fun playPrevious() {
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PREV
        }
        context.startService(intent)
    }

    fun seekTo(position: Long) {
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_SEEK
            putExtra("position", position)
        }
        context.startService(intent)
    }

    fun reorderSongs(fromIndex: Int, toIndex: Int) {
        val songs = _displayedSongs.value.toMutableList()
        if (fromIndex != toIndex && fromIndex >= 0 && fromIndex < songs.size
            && toIndex >= 0 && toIndex < songs.size) {

            val song = songs.removeAt(fromIndex)
            songs.add(toIndex, song)
            _displayedSongs.value = songs

            PlaylistManager.reorderPlaylist(fromIndex, toIndex)

            _currentSong.value?.let { current ->
                val currentIndex = songs.indexOf(current)
                if (currentIndex >= 0) {
                    PlaylistManager.setPlaylistWithIndex(songs, currentIndex)
                }
            }
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

    fun expandPlayer() {
        _isPlayerExpanded.value = true
    }

    fun collapsePlayer() {
        _isPlayerExpanded.value = false
    }
}


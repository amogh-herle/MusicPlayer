package com.example.musicplayer

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.Song
import com.google.gson.Gson
import java.io.File

object PlaylistManager {
    private lateinit var context: Context
    private val gson = Gson()

    private var songs: MutableList<Song> = mutableListOf()
    private var currentIndex: Int = 0
    private var isShuffleModeActive = false
    private var hasManualChanges = false

    data class SerializableSong(
        val uriString: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val albumId: Long,
        val albumArtUriString: String?
    )

    data class PlaylistState(
        val songs: List<SerializableSong>,
        val currentIndex: Int
    )

    fun initialize(appContext: Context) {
        if (!::context.isInitialized) {
            context = appContext.applicationContext
        }
    }

    fun saveState() {
        val serializableSongs = songs.map {
            SerializableSong(
                uriString = it.uri.toString(),
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration,
                albumId = it.albumId,
                albumArtUriString = it.albumArtUri?.toString()
            )
        }
        val state = PlaylistState(serializableSongs, currentIndex)
        val json = gson.toJson(state)
        try {
            context.openFileOutput("playlist_state.json", Context.MODE_PRIVATE).use { output ->
                output.write(json.toByteArray())
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun restoreState(): Boolean {
        return try {
            val file = File(context.filesDir, "playlist_state.json")
            if (!file.exists()) return false
            val json = file.readText()
            val state = gson.fromJson(json, PlaylistState::class.java)
            songs = state.songs.map {
                Song(
                    uri = Uri.parse(it.uriString),
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    duration = it.duration,
                    albumId = it.albumId,
                    albumArtUri = it.albumArtUriString?.let { Uri.parse(it) }
                )
            }.toMutableList()
            currentIndex = state.currentIndex
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isInShuffleMode(): Boolean = isShuffleModeActive
    fun hasManualChanges(): Boolean = hasManualChanges

    fun setPlaylist(newSongs: List<Song>) {
        songs = newSongs.toMutableList()
        currentIndex = 0
        isShuffleModeActive = false
        hasManualChanges = false
        saveState()
    }

    fun setPlaylistWithIndex(newSongs: List<Song>, index: Int) {
        songs = newSongs.toMutableList()
        currentIndex = index.coerceIn(0, songs.size - 1)
        hasManualChanges = true
        saveState()
    }

    fun updateCurrentIndex(index: Int) {
        currentIndex = index.coerceIn(0, songs.size - 1)
        saveState()
    }

    fun getCurrentSong(): Song? {
        return songs.getOrNull(currentIndex)
    }

    fun nextSong(): Song? {
        if (songs.isEmpty()) return null

        currentIndex++
        if (currentIndex >= songs.size) {
            // Infinite playback: reshuffle and start over
            smartShuffle(songs)
            currentIndex = 0
            isShuffleModeActive = true
        }
        saveState()
        return getCurrentSong()
    }

    fun previousSong(): Song? {
        if (songs.isEmpty()) return null

        currentIndex = if (currentIndex > 0) currentIndex - 1 else 0
        saveState()
        return getCurrentSong()
    }

    fun playSongAt(index: Int): Song? {
        if (index in songs.indices) {
            currentIndex = index
            saveState()
            return getCurrentSong()
        }
        return null
    }

    fun smartShuffle(songList: List<Song>): List<Song> {
        val shuffled = songList.shuffled().toMutableList()
        songs = shuffled
        currentIndex = 0
        isShuffleModeActive = true
        saveState()
        return shuffled
    }

    fun reorderPlaylist(fromIndex: Int, toIndex: Int): List<Song> {
        if (fromIndex !in songs.indices || toIndex !in songs.indices) return songs

        val moved = songs.removeAt(fromIndex)
        songs.add(toIndex, moved)

        // Update currentIndex if the playing song was moved
        currentIndex = when {
            fromIndex == currentIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }

        hasManualChanges = true
        saveState()
        return songs.toList()
    }

    fun getPlaylist(): List<Song> = songs.toList()

    fun hasPlaylist(): Boolean = songs.isNotEmpty()
}

package com.example.musicplayer

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PlaylistManager {
    private lateinit var context: Context
    private val gson = Gson()

    private val prefs by lazy {
        context.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)
    }

    private var songs: MutableList<Song> = mutableListOf()
    private var currentIndex: Int = 0
    private var isShuffleModeActive = false
    private var hasManualChanges = false

    fun initialize(appContext: Context) {
        if (!::context.isInitialized) {
            context = appContext.applicationContext
            restorePlaylist()
        }
    }

    fun isInShuffleMode(): Boolean = isShuffleModeActive
    fun hasManualChanges(): Boolean = hasManualChanges

    fun setPlaylist(newSongs: List<Song>) {
        songs = newSongs.toMutableList()
        currentIndex = 0
        isShuffleModeActive = false
        hasManualChanges = false
        savePlaylist()
    }

    fun setPlaylistWithIndex(newSongs: List<Song>, index: Int) {
        songs = newSongs.toMutableList()
        currentIndex = index.coerceIn(0, songs.size - 1)
        hasManualChanges = true
        savePlaylist()
    }

    fun updateCurrentIndex(index: Int) {
        currentIndex = index.coerceIn(0, songs.size - 1)
        savePlaylist()
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
        savePlaylist()
        return getCurrentSong()
    }

    fun previousSong(): Song? {
        if (songs.isEmpty()) return null

        currentIndex = if (currentIndex > 0) currentIndex - 1 else 0
        savePlaylist()
        return getCurrentSong()
    }

    fun playSongAt(index: Int): Song? {
        if (index in songs.indices) {
            currentIndex = index
            savePlaylist()
            return getCurrentSong()
        }
        return null
    }

    fun smartShuffle(songList: List<Song>): List<Song> {
        val shuffled = songList.shuffled().toMutableList()
        songs = shuffled
        currentIndex = 0
        isShuffleModeActive = true
        savePlaylist()
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
        savePlaylist()
        return songs.toList()
    }

    fun getPlaylist(): List<Song> = songs.toList()

    private fun savePlaylist() {
        val uriStrings = songs.map { it.uri.toString() }
        val json = gson.toJson(uriStrings)
        prefs.edit()
            .putString("saved_uris", json)
            .putInt("current_index", currentIndex)
            .apply()
    }

    private fun restorePlaylist() {
        val json = prefs.getString("saved_uris", null)
        currentIndex = prefs.getInt("current_index", 0)

        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val uriStrings: List<String> = gson.fromJson(json, type)
                val contentResolver = context.contentResolver

                songs = uriStrings.mapNotNull { uriString ->
                    try {
                        val uri = Uri.parse(uriString)
                        // Re-query song metadata from MediaStore
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(
                                android.provider.MediaStore.Audio.Media.TITLE,
                                android.provider.MediaStore.Audio.Media.ARTIST,
                                android.provider.MediaStore.Audio.Media.ALBUM,
                                android.provider.MediaStore.Audio.Media.ALBUM_ID,
                                android.provider.MediaStore.Audio.Media.DURATION
                            ),
                            null,
                            null,
                            null
                        )?.use { c ->
                            if (c.moveToFirst()) {
                                val albumId = c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID))
                                Song(
                                    uri = uri,
                                    title = c.getString(0) ?: "Unknown",
                                    artist = c.getString(1) ?: "Unknown Artist",
                                    album = c.getString(2) ?: "Unknown Album",
                                    duration = c.getLong(4),
                                    albumId = albumId,
                                    albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                                )
                            } else null
                        }
                        cursor
                    } catch (e: Exception) {
                        null
                    }
                }.toMutableList()
            } catch (e: Exception) {
                songs = mutableListOf()
            }
        }
    }

    fun hasPlaylist(): Boolean = songs.isNotEmpty()
}

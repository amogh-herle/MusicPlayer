package com.example.musicplayer

import com.example.musicplayer.data.Song

object PlaylistManager {
    private var playlist: MutableList<Song> = mutableListOf()
    private var currentIndex = 0
    private var isShuffleMode = false
    private var originalSongs: List<Song> = emptyList()
    private var hasManualReorder = false // Track if user manually reordered queue

    fun setPlaylist(songs: List<Song>) {
        playlist = songs.toMutableList()
        currentIndex = 0
        isShuffleMode = false
        originalSongs = emptyList()
        hasManualReorder = false
    }

    fun setPlaylistWithIndex(songs: List<Song>, index: Int) {
        playlist = songs.toMutableList()
        currentIndex = index.coerceIn(0, songs.size - 1)
        isShuffleMode = false
        originalSongs = emptyList()
        hasManualReorder = false
    }

    fun updateCurrentIndex(index: Int) {
        currentIndex = index.coerceIn(0, playlist.size - 1)
    }

    fun smartShuffle(songs: List<Song>): List<Song> {
        if (songs.size <= 2) return songs.shuffled()

        val shuffled = songs.shuffled().toMutableList()

        // Multiple passes to break up artist clumps
        repeat(3) {
            for (i in 0 until shuffled.size - 1) {
                if (shuffled[i].artist.equals(shuffled[i + 1].artist, ignoreCase = true)) {
                    // Find a song later in the list with a different artist
                    for (j in i + 2 until shuffled.size) {
                        if (!shuffled[j].artist.equals(shuffled[i].artist, ignoreCase = true)) {
                            // Swap to break the clump
                            val temp = shuffled[i + 1]
                            shuffled[i + 1] = shuffled[j]
                            shuffled[j] = temp
                            break
                        }
                    }
                }
            }
        }

        playlist = shuffled
        currentIndex = 0
        isShuffleMode = true
        originalSongs = songs
        hasManualReorder = false // Reset manual reorder flag on new shuffle
        return shuffled
    }

    fun getCurrentSong(): Song? = playlist.getOrNull(currentIndex)

    fun nextSong(): Song? {
        // Check if we're at the end of the playlist
        if (currentIndex >= playlist.size - 1) {
            // If manual reorder happened, respect it and just loop
            if (hasManualReorder) {
                currentIndex = 0
            } else if (isShuffleMode && originalSongs.isNotEmpty()) {
                // If shuffle mode and no manual changes, generate fresh smart shuffle
                smartShuffle(originalSongs)
            } else {
                // Normal loop: go back to start
                currentIndex = 0
            }
        } else {
            currentIndex++
        }
        return getCurrentSong()
    }

    fun previousSong(): Song? {
        if (currentIndex > 0) currentIndex--
        return getCurrentSong()
    }

    fun getPlaylist(): List<Song> = playlist.toList()

    fun reorderPlaylist(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= playlist.size || toIndex < 0 || toIndex >= playlist.size) {
            return
        }

        val song = playlist.removeAt(fromIndex)
        playlist.add(toIndex, song)

        // Mark that user has manually reordered the queue
        hasManualReorder = true

        // Update currentIndex if the current song was moved
        if (fromIndex == currentIndex) {
            currentIndex = toIndex
        } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
            currentIndex--
        } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
            currentIndex++
        }
    }

    fun isInShuffleMode(): Boolean = isShuffleMode

    fun hasManualChanges(): Boolean = hasManualReorder

    fun clearManualChanges() {
        hasManualReorder = false
    }
}


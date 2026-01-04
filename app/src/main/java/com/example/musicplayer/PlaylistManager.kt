package com.example.musicplayer

import com.example.musicplayer.data.Song

object PlaylistManager {
    private var playlist: MutableList<Song> = mutableListOf()
    private var currentIndex = 0
    private var isShuffleMode = false
    private var originalSongs: List<Song> = emptyList()
    private var hasManualReorder = false

    fun setPlaylist(songs: List<Song>) {
        playlist = songs.toMutableList()
        currentIndex = 0
        isShuffleMode = false
        originalSongs = emptyList()
        hasManualReorder = false
    }

    fun setPlaylistWithIndex(songs: List<Song>, index: Int) {
        playlist = songs.toMutableList()
        currentIndex = index.coerceIn(0, maxOf(0, songs.size - 1))
        isShuffleMode = false
        originalSongs = emptyList()
        hasManualReorder = false
    }

    fun updateCurrentIndex(index: Int) {
        currentIndex = index.coerceIn(0, maxOf(0, playlist.size - 1))
    }

    fun smartShuffle(songs: List<Song>): List<Song> {
        if (songs.size <= 2) return songs.shuffled()

        val shuffled = songs.shuffled().toMutableList()
        // Simple heuristic to break artist clumps
        repeat(3) {
            for (i in 0 until shuffled.size - 1) {
                if (shuffled[i].artist.equals(shuffled[i + 1].artist, ignoreCase = true)) {
                    for (j in i + 2 until shuffled.size) {
                        if (!shuffled[j].artist.equals(shuffled[i].artist, ignoreCase = true)) {
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
        hasManualReorder = false
        return shuffled
    }

    fun getCurrentSong(): Song? = playlist.getOrNull(currentIndex)

    fun nextSong(): Song? {
        if (playlist.isEmpty()) return null

        if (currentIndex >= playlist.size - 1) {
            // End of list logic
            if (hasManualReorder) {
                currentIndex = 0 // Loop if manually ordered
            } else if (isShuffleMode && originalSongs.isNotEmpty()) {
                smartShuffle(originalSongs) // Reshuffle if in auto mode
            } else {
                currentIndex = 0 // Loop normal
            }
        } else {
            currentIndex++
        }
        return getCurrentSong()
    }

    fun previousSong(): Song? {
        if (playlist.isEmpty()) return null
        if (currentIndex > 0) currentIndex--
        return getCurrentSong()
    }

    fun getPlaylist(): List<Song> = playlist.toList()

    fun reorderPlaylist(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return

        val song = playlist.removeAt(fromIndex)
        playlist.add(toIndex, song)

        hasManualReorder = true

        // LOGIC: Adjust current index based on the move
        if (currentIndex == fromIndex) {
            // Case 1: The playing song itself was moved
            currentIndex = toIndex
        } else if (currentIndex in (fromIndex + 1)..toIndex) {
            // Case 2: A song from ABOVE current was moved to BELOW current
            // (e.g. Current=2, Move 0->4. New Current should be 1)
            currentIndex--
        } else if (currentIndex in toIndex until fromIndex) {
            // Case 3: A song from BELOW current was moved to ABOVE current
            // (e.g. Current=2, Move 4->0. New Current should be 3)
            currentIndex++
        }
    }

    fun isInShuffleMode(): Boolean = isShuffleMode
    fun setShuffleModeEnabled(enabled: Boolean) { isShuffleMode = enabled }
    fun hasManualChanges(): Boolean = hasManualReorder
}
package com.example.musicplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state holder for music playback state.
 * Replaces deprecated LocalBroadcastManager for communication between Service and UI.
 */
object MusicPlaybackState {

    data class PlaybackProgress(
        val position: Long = 0L,
        val duration: Long = 0L,
        val isPlaying: Boolean = false
    )

    data class CurrentSongInfo(
        val title: String = "",
        val artist: String = "",
        val albumId: Long = 0L
    )

    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val _currentSongInfo = MutableStateFlow(CurrentSongInfo())
    val currentSongInfo: StateFlow<CurrentSongInfo> = _currentSongInfo.asStateFlow()

    fun updateProgress(position: Long, duration: Long, isPlaying: Boolean) {
        _playbackProgress.value = PlaybackProgress(position, duration, isPlaying)
    }

    fun updateCurrentSong(title: String, artist: String, albumId: Long) {
        _currentSongInfo.value = CurrentSongInfo(title, artist, albumId)
    }

    fun reset() {
        _playbackProgress.value = PlaybackProgress()
        _currentSongInfo.value = CurrentSongInfo()
    }
}


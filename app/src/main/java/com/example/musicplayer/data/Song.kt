package com.example.musicplayer.data

import android.net.Uri

data class Song(
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumId: Long = 0,
    val albumArtUri: Uri? = null
)


package com.example.musicplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.musicplayer.data.Song

object LocalScanner {
    private const val PREFS_NAME = "music_player_prefs"
    private const val KEY_MUSIC_DIRECTORY = "music_directory"
    private const val DEFAULT_DIRECTORY = "/Music/MySpotifyBackup"

    fun getMusicDirectory(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MUSIC_DIRECTORY, DEFAULT_DIRECTORY) ?: DEFAULT_DIRECTORY
    }

    fun setMusicDirectory(context: Context, directory: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MUSIC_DIRECTORY, directory).apply()
    }

    fun scanMusicFolder(contentResolver: ContentResolver, targetDirectory: String = DEFAULT_DIRECTORY): List<Song> {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataColumn) ?: continue

                if (filePath.contains(targetDirectory, ignoreCase = true) &&
                    (filePath.endsWith(".mp3", ignoreCase = true) || filePath.endsWith(".m4a", ignoreCase = true))) {

                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Generate album art URI
                    val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                    songs.add(
                        Song(
                            uri = uri,
                            title = cursor.getString(titleColumn) ?: "Unknown",
                            artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                            album = cursor.getString(albumColumn) ?: "Unknown Album",
                            duration = cursor.getLong(durationColumn),
                            albumId = albumId,
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
        }

        return songs
    }
}


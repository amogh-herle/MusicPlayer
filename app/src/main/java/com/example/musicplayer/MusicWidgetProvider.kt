package com.example.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import androidx.core.net.toUri

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "MusicWidget"
        const val ACTION_UPDATE_WIDGET = "com.example.musicplayer.action.UPDATE_WIDGET"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ALBUM_ID = "extra_album_id"

        fun updateWidget(context: Context, title: String, artist: String, isPlaying: Boolean, albumId: Long) {
            val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_ALBUM_ID, albumId)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId, "Music Player", "Tap to play", false, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
            ACTION_UPDATE_WIDGET -> {
                try {
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
                    val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    val albumId = intent.getLongExtra(EXTRA_ALBUM_ID, 0)

                    Log.d(TAG, "Updating widget: $title by $artist (playing: $isPlaying)")

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, MusicWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, title, artist, isPlaying, albumId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onReceive", e)
                }
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumId: Long
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            // Update text
            views.setTextViewText(R.id.widget_song_title, title)
            views.setTextViewText(R.id.widget_artist_name, artist)

            // Update play/pause icon
            val playPauseIcon = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            views.setImageViewResource(R.id.widget_play_pause_button, playPauseIcon)

            // Load album art
            loadAlbumArt(context, views, albumId)

            // Setup button intents
            setupButtonIntents(context, views, isPlaying)

            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "Widget $appWidgetId updated successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $appWidgetId", e)
        }
    }

    private fun loadAlbumArt(context: Context, views: RemoteViews, albumId: Long) {
        try {
            if (albumId > 0) {
                val albumArtUri = "content://media/external/audio/albumart/$albumId".toUri()
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                        views.setImageViewBitmap(R.id.widget_album_art, scaledBitmap)
                        bitmap.recycle()
                        Log.d(TAG, "Album art loaded for albumId: $albumId")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load album art: ${e.message}")
        }

        // Fallback icon
        try {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
        } catch (e: Exception) {
            Log.e(TAG, "Could not set fallback icon", e)
        }
    }

    private fun setupButtonIntents(context: Context, views: RemoteViews, isPlaying: Boolean) {
        try {
            // Previous button
            val prevIntent = PendingIntent.getService(
                context, 0,
                Intent(context, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PREV
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_prev_button, prevIntent)

            // Play/Pause button
            val playPauseIntent = PendingIntent.getService(
                context, 1,
                Intent(context, MusicPlayerService::class.java).apply {
                    action = if (isPlaying) MusicPlayerService.ACTION_PAUSE else MusicPlayerService.ACTION_RESUME
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause_button, playPauseIntent)

            // Next button
            val nextIntent = PendingIntent.getService(
                context, 2,
                Intent(context, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_NEXT
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_next_button, nextIntent)

            // Click on album art/title opens app
            val openAppIntent = PendingIntent.getActivity(
                context, 3,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_album_art, openAppIntent)

            Log.d(TAG, "Button intents setup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up button intents", e)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled")
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled")
    }
}
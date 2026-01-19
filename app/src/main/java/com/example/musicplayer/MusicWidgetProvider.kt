package com.example.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.core.net.toUri
import java.io.FileNotFoundException

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.musicplayer.action.UPDATE_WIDGET"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ALBUM_ID = "extra_album_id"

        /**
         * Helper method to update all widget instances
         */
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

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, "Music Player", "Tap to play", false, 0)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_UPDATE_WIDGET -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val albumId = intent.getLongExtra(EXTRA_ALBUM_ID, 0)

                // Update all widget instances
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, MusicWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, title, artist, isPlaying, albumId)
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        // Widget is added for the first time
    }

    override fun onDisabled(context: Context) {
        // Last widget instance is removed
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
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        // Update text
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        // Update play/pause button icon
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        views.setImageViewResource(R.id.widget_play_pause_button, playPauseIcon)

        // Load album art
        loadAlbumArt(context, views, albumId)

        // Set up button intents
        setupButtonIntents(context, views, isPlaying)

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun loadAlbumArt(context: Context, views: RemoteViews, albumId: Long) {
        try {
            if (albumId > 0) {
                val albumArtUri = "content://media/external/audio/albumart/$albumId".toUri()
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        // Scale down bitmap for widget to save memory
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                        views.setImageViewBitmap(R.id.widget_album_art, scaledBitmap)
                        if (scaledBitmap != bitmap) {
                            bitmap.recycle()
                        }
                        return
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            // Album art not found, use default
        } catch (e: Exception) {
            android.util.Log.e("MusicWidget", "Error loading album art: ${e.message}")
        }

        // Fallback to default icon
        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
    }

    private fun setupButtonIntents(context: Context, views: RemoteViews, isPlaying: Boolean) {
        // Previous button
        val prevIntent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            context,
            0,
            prevIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_prev_button, prevPendingIntent)

        // Play/Pause button
        val playPauseAction = if (isPlaying) {
            MusicPlayerService.ACTION_PAUSE
        } else {
            MusicPlayerService.ACTION_RESUME
        }
        val playPauseIntent = Intent(context, MusicPlayerService::class.java).apply {
            action = playPauseAction
        }
        val playPausePendingIntent = PendingIntent.getService(
            context,
            1,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_play_pause_button, playPausePendingIntent)

        // Next button
        val nextIntent = Intent(context, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            context,
            2,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_next_button, nextPendingIntent)

        // Click on widget opens the app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            3,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_song_title, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_artist_name, openAppPendingIntent)
    }
}


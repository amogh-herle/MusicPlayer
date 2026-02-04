package com.example.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "MusicWidget"
        const val ACTION_UPDATE_WIDGET = "com.example.musicplayer.action.UPDATE_WIDGET"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ALBUM_ID = "extra_album_id"
        const val EXTRA_ALBUM_ART_BYTES = "extra_album_art_bytes"

        fun updateWidget(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            albumId: Long,
            albumArtBytes: ByteArray? = null
        ) {
            val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_ALBUM_ID, albumId)
                putExtra(EXTRA_ALBUM_ART_BYTES, albumArtBytes)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            // Initial State
            updateAppWidget(context, appWidgetManager, appWidgetId, "Music Player", "Tap to play", false, 0, null)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
            val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
            val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            val albumId = intent.getLongExtra(EXTRA_ALBUM_ID, 0)
            val albumArtBytes = intent.getByteArrayExtra(EXTRA_ALBUM_ART_BYTES)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, title, artist, isPlaying, albumId, albumArtBytes)
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
        albumId: Long,
        albumArtBytes: ByteArray?
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            views.setTextViewText(R.id.widget_song_title, title)
            views.setTextViewText(R.id.widget_artist_name, artist)

            // Play/Pause Icon
            val playPauseIcon = if (isPlaying) R.drawable.ic_pause_black else R.drawable.ic_play_arrow_black
            views.setImageViewResource(R.id.widget_play_pause_button, playPauseIcon)

            // Album Art Loading
            var artLoaded = false
            if (albumArtBytes != null) {
                try {
                    val bmp = BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.size)
                    if (bmp != null) {
                        views.setImageViewBitmap(R.id.widget_album_art, createRoundedBitmap(bmp, 200, 16f))
                        artLoaded = true
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (!artLoaded) {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_album_placeholder)
            }

            // Button Intents
            setupButtons(context, views, isPlaying)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget", e)
        }
    }

    private fun createRoundedBitmap(source: Bitmap, size: Int, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    private fun setupButtons(context: Context, views: RemoteViews, isPlaying: Boolean) {
        // PREV
        views.setOnClickPendingIntent(R.id.widget_prev_button,
            getPendingIntent(context, MusicPlayerService.ACTION_PREV))

        // NEXT
        views.setOnClickPendingIntent(R.id.widget_next_button,
            getPendingIntent(context, MusicPlayerService.ACTION_NEXT))

        // PLAY/PAUSE
        val action = if (isPlaying) MusicPlayerService.ACTION_PAUSE else MusicPlayerService.ACTION_RESUME
        views.setOnClickPendingIntent(R.id.widget_play_pause_button,
            getPendingIntent(context, action))

        // OPEN APP
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_album_art, pendingAppIntent)
        views.setOnClickPendingIntent(R.id.song_info_container, pendingAppIntent)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicPlayerService::class.java).apply { this.action = action }
        return PendingIntent.getService(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
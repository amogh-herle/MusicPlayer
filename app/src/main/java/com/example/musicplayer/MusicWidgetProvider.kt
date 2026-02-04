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
import java.io.ByteArrayOutputStream

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
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
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

            Log.d(TAG, "Updating widget: $title by $artist (albumId: $albumId)")

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

            // Update text
            views.setTextViewText(R.id.widget_song_title, title)
            views.setTextViewText(R.id.widget_artist_name, artist)

            // Update play/pause icon
            val playPauseIcon = if (isPlaying) {
                R.drawable.ic_pause_black
            } else {
                R.drawable.ic_play_arrow_black
            }
            views.setImageViewResource(R.id.widget_play_pause_button, playPauseIcon)

            // Load and set album art
            loadAlbumArt(context, views, albumId, albumArtBytes)

            // Setup button intents
            setupButtons(context, views, isPlaying)

            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "Widget $appWidgetId updated successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $appWidgetId", e)
        }
    }

    private fun loadAlbumArt(context: Context, views: RemoteViews, albumId: Long, albumArtBytes: ByteArray?) {
        var albumArtLoaded = false

        // 1. Try to load from bytes first (passed from service)
        if (albumArtBytes != null && albumArtBytes.isNotEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.size)
                if (bitmap != null) {
                    val roundedBitmap = createRoundedBitmap(bitmap, 200, 16f)
                    views.setImageViewBitmap(R.id.widget_album_art, roundedBitmap)
                    bitmap.recycle()
                    albumArtLoaded = true
                    Log.d(TAG, "Album art loaded from bytes")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load album art from bytes: ${e.message}")
            }
        }

        // 2. Try to load album art from MediaStore as fallback
        if (!albumArtLoaded && albumId > 0) {
            try {
                val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    val originalBitmap = BitmapFactory.decodeStream(stream)
                    if (originalBitmap != null) {
                        // Create rounded bitmap for widget
                        val roundedBitmap = createRoundedBitmap(originalBitmap, 200, 16f)
                        views.setImageViewBitmap(R.id.widget_album_art, roundedBitmap)
                        originalBitmap.recycle()
                        albumArtLoaded = true
                        Log.d(TAG, "Album art loaded for ID: $albumId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load album art from MediaStore: ${e.message}")
            }
        }

        // 3. Fallback: Create gradient placeholder
        if (!albumArtLoaded) {
            try {
                val placeholder = createGradientPlaceholder(200, 200)
                views.setImageViewBitmap(R.id.widget_album_art, placeholder)
                Log.d(TAG, "Using gradient placeholder")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create placeholder: ${e.message}")
                // Last resort: use drawable resource
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_album_placeholder)
            }
        }
    }

    /**
     * Create rounded corners bitmap
     */
    private fun createRoundedBitmap(source: Bitmap, size: Int, cornerRadius: Float): Bitmap {
        val scaledBitmap = Bitmap.createScaledBitmap(source, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaledBitmap, rect, rect, paint)

        if (scaledBitmap != source) {
            scaledBitmap.recycle()
        }

        return output
    }

    /**
     * Create gradient placeholder (cyan to purple)
     */
    private fun createGradientPlaceholder(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Gradient background
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#00D9FF"), // Cyan
                Color.parseColor("#9D50BB")  // Purple
            ),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader

        // Draw rounded rect
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, 16f, 16f, paint)

        // Draw music note icon
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 180

        val centerX = width / 2f
        val centerY = height / 2f
        val noteSize = width / 3.5f

        // Note stem
        paint.strokeWidth = noteSize / 10f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            centerX + noteSize / 5f,
            centerY - noteSize / 2.5f,
            centerX + noteSize / 5f,
            centerY + noteSize / 4f,
            paint
        )

        // Note head
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY + noteSize / 4f, noteSize / 5f, paint)

        return bitmap
    }

    private fun setupButtons(context: Context, views: RemoteViews, isPlaying: Boolean) {
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
        val playPauseAction = if (isPlaying) {
            MusicPlayerService.ACTION_PAUSE
        } else {
            MusicPlayerService.ACTION_RESUME
        }
        val playPauseIntent = PendingIntent.getService(
            context, 1,
            Intent(context, MusicPlayerService::class.java).apply {
                action = playPauseAction
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
        views.setOnClickPendingIntent(R.id.widget_song_title, openAppIntent)
        views.setOnClickPendingIntent(R.id.song_info_container, openAppIntent)
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled")
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled")
    }
}

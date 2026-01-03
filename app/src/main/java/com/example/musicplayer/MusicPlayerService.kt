package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media.app.NotificationCompat.MediaStyle
import java.io.FileNotFoundException

class MusicPlayerService : Service() {
    companion object {
        const val CHANNEL_ID = "music_player_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.example.musicplayer.action.PLAY"
        const val ACTION_PAUSE = "com.example.musicplayer.action.PAUSE"
        const val ACTION_RESUME = "com.example.musicplayer.action.RESUME"
        const val ACTION_STOP = "com.example.musicplayer.action.STOP"
        const val ACTION_NEXT = "com.example.musicplayer.action.NEXT"
        const val ACTION_PREV = "com.example.musicplayer.action.PREV"
        const val ACTION_SEEK = "com.example.musicplayer.action.SEEK"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ALBUM_ID = "extra_album_id"
    }

    private var player: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentTitle = "Unknown"
    private var currentArtist = "Unknown Artist"
    private var currentUri: Uri? = null
    private var currentAlbumId: Long = 0
    private var currentAlbumArt: Bitmap? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    broadcastProgress()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayerService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stopSelf() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uriString = intent.getStringExtra(EXTRA_URI)
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                currentAlbumId = intent.getLongExtra(EXTRA_ALBUM_ID, 0)
                uriString?.let {
                    currentUri = it.toUri()
                    loadAlbumArt(currentUri!!)
                    requestAudioFocus()
                    play(currentUri!!)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> { abandonAudioFocus(); stopSelf() }
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_SEEK -> seekTo(intent.getLongExtra("position", 0L))
        }
        return START_STICKY
    }

    private fun loadAlbumArt(uri: Uri) {
        currentAlbumArt = null
        try {
            val albumArtUri = "content://media/external/audio/albumart/$currentAlbumId".toUri()
            contentResolver.openInputStream(albumArtUri)?.use { stream ->
                currentAlbumArt = BitmapFactory.decodeStream(stream)
            }
        } catch (_: FileNotFoundException) {
            // Album art not found
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (currentAlbumArt == null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val embeddedArt = retriever.embeddedPicture
                if (embeddedArt != null) {
                    currentAlbumArt = BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size)
                }
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.3f, 0.3f)
                    AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1.0f, 1.0f)
                }
            }.build()
        return audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    private fun play(uri: Uri) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(applicationContext, uri)
                setOnPreparedListener { mp ->
                    mp.start()
                    this@MusicPlayerService.isPlaying = true
                    updateMediaSessionMetadata()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    updateNotification()
                    startProgressUpdates()
                    broadcastSongChanged()
                }
                setOnCompletionListener { playNext() }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("MusicPlayerService", "MediaPlayer error: what=$what, extra=$extra")
                    // Don't auto-skip on error - just stop playback
                    this@MusicPlayerService.isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                    broadcastProgress()
                    true
                }
                prepareAsync()
            }
            startForegroundWithNotification()
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("MusicPlayerService", "Exception playing: ${e.message}")
            // Don't auto-skip on exception - just log and stop
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            broadcastProgress()
        }
    }

    private fun pause() {
        player?.pause()
        isPlaying = false
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
        stopProgressUpdates()
        broadcastProgress()
    }

    private fun resume() {
        player?.start()
        isPlaying = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateNotification()
        startProgressUpdates()
    }

    private fun seekTo(position: Long) {
        player?.seekTo(position.toInt())
        broadcastProgress()
        updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
    }

    private fun playNext() {
        val nextSong = PlaylistManager.nextSong()
        if (nextSong != null) {
            currentTitle = nextSong.title
            currentArtist = nextSong.artist
            currentAlbumId = nextSong.albumId
            currentUri = nextSong.uri
            loadAlbumArt(nextSong.uri)
            play(nextSong.uri)
        } else {
            stopSelf()
        }
    }

    private fun playPrevious() {
        player?.let { if (it.currentPosition > 3000) { it.seekTo(0); broadcastProgress(); return } }
        val prevSong = PlaylistManager.previousSong()
        if (prevSong != null) {
            currentTitle = prevSong.title
            currentArtist = prevSong.artist
            currentAlbumId = prevSong.albumId
            currentUri = prevSong.uri
            loadAlbumArt(prevSong.uri)
            play(prevSong.uri)
        }
    }

    private fun startProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun broadcastProgress() {
        MusicPlaybackState.updateProgress(
            position = player?.currentPosition?.toLong() ?: 0L,
            duration = player?.duration?.toLong() ?: 0L,
            isPlaying = isPlaying
        )
    }

    private fun broadcastSongChanged() {
        MusicPlaybackState.updateCurrentSong(
            title = currentTitle,
            artist = currentArtist,
            albumId = currentAlbumId
        )
    }

    private fun updateMediaSessionMetadata() {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player?.duration?.toLong() ?: 0L)

        currentAlbumArt?.let { bitmap ->
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState(state: Int) {
        val position = player?.currentPosition?.toLong() ?: 0L
        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, position, playbackSpeed, SystemClock.elapsedRealtime())

        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun startForegroundWithNotification() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseResumeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicPlayerService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = currentAlbumArt ?: BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(largeIcon)
            .setContentIntent(openIntent)
            .setDeleteIntent(stopIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_launcher_foreground, "Prev", prevIntent)
            .addAction(R.drawable.ic_launcher_foreground, if (isPlaying) "Pause" else "Play", pauseResumeIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Next", nextIntent)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopIntent))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music player controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopProgressUpdates()
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
        super.onDestroy()
    }
}


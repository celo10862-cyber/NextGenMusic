package com.nextgenmusic.player.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nextgenmusic.player.MainActivity
import com.nextgenmusic.player.NextGenMusicApplication
import com.nextgenmusic.player.R
import com.nextgenmusic.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    companion object {
        const val ACTION_PLAY = "com.nextgenmusic.player.PLAY"
        const val ACTION_PAUSE = "com.nextgenmusic.player.PAUSE"
        const val ACTION_NEXT = "com.nextgenmusic.player.NEXT"
        const val ACTION_PREVIOUS = "com.nextgenmusic.player.PREVIOUS"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        private const val CHANNEL_ID = "ngm_playback"
        private const val NOTIFICATION_ID = 901
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var current: Song? = null
    private var focus: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        focus = getSystemService(AUDIO_SERVICE) as AudioManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player?.start()
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT, ACTION_PREVIOUS -> Unit
            else -> {
                val uri = intent?.getStringExtra(EXTRA_URI)
                if (uri != null) prepare(Song(
                    uri.hashCode().toLong(), uri, intent.getStringExtra(EXTRA_TITLE) ?: "Unknown track",
                    intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown artist", "", "", "", 0, 0, 0, 0, 0, "audio/*", "", null
                ))
            }
        }
        updateNotification()
        return START_NOT_STICKY
    }

    private fun prepare(song: Song) {
        current = song
        player?.release()
        player = MediaPlayer().apply {
            if (Build.VERSION.SDK_INT >= 21) {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
            } else {
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_MUSIC)
            }
            setOnPreparedListener { requestFocus(); it.start(); updateNotification() }
            setOnCompletionListener {
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForegroundCompat()
                updateNotification()
            }
            setOnErrorListener { _, _, _ -> stopPlayback(); true }
            try {
                setDataSource(this@PlaybackService, android.net.Uri.parse(song.uri))
                prepareAsync()
            } catch (_: Exception) {
                stopPlayback()
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch(Dispatchers.IO) { (application as NextGenMusicApplication).repository.markPlayed(song.id) }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) focus?.requestAudioFocus(android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener(this).build())
        else @Suppress("DEPRECATION") focus?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    private fun stopPlayback() {
        player?.release(); player = null
        if (Build.VERSION.SDK_INT >= 26) stopForeground(STOP_FOREGROUND_REMOVE) else stopForegroundCompat()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(true)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        fun action(label: String, action: String): NotificationCompat.Action {
            val pending = PendingIntent.getBroadcast(this, action.hashCode(), Intent(this, PlaybackActionReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
            return NotificationCompat.Action.Builder(0, label, pending).build()
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ngm)
            .setContentTitle(current?.title ?: getString(R.string.app_name))
            .setContentText(current?.artist ?: getString(R.string.tagline))
            .setContentIntent(content)
            .setOngoing(player?.isPlaying == true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(action("Previous", ACTION_PREVIOUS))
            .addAction(action(if (player?.isPlaying == true) "Pause" else "Play", if (player?.isPlaying == true) ACTION_PAUSE else ACTION_PLAY))
            .addAction(action("Next", ACTION_NEXT))
            .build()
    }

    private fun updateNotification() {
        if (current != null) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun immutableFlag() = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
            AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f)
        }
        updateNotification()
    }
    override fun onDestroy() { player?.release(); player = null; serviceScope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
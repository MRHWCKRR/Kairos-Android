package com.kairos.app.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kairos.app.ui.navigation.MainActivity

class AmbientAudioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val soundUrls = mapOf(
        "lofi" to "https://stream.zeno.fm/0r0xa792kwzuv", // Lofi Radio Stream
        "rain" to "https://www.soundjay.com/nature/rain-01.mp3",
        "coffee" to "https://www.soundjay.com/ambient/creek-01.mp3"
    )

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
        
        mediaSession = MediaSession.Builder(this, player!!).build()

        val channel = NotificationChannel(
            "ambient_audio",
            "Ambient Audio",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sound = intent?.getStringExtra("sound") ?: "none"
        val volume = intent?.getIntExtra("volume", 50) ?: 50

        if (sound == "none") {
            player?.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val url = soundUrls[sound]
            if (url != null) {
                val mediaItem = MediaItem.fromUri(url)
                player?.setMediaItem(mediaItem)
                player?.volume = volume / 100f
                player?.prepare()
                player?.play()

                startForeground(1001, createNotification(sound))
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(sound: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "ambient_audio")
            .setContentTitle("Kairos Atmosphere")
            .setContentText("Playing: ${sound.replaceFirstChar { it.uppercase() }}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

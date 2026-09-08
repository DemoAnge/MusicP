package com.example.music.player

import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.music.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var localPlayer: LocalPlayerService

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val notifications = DefaultMediaNotificationProvider.Builder(this).build()
            notifications.setSmallIcon(R.drawable.ic_notification)
            setMediaNotificationProvider(notifications)
        }
        mediaSession = runCatching {
            MediaSession.Builder(this, localPlayer.exoPlayer).build()
        }.getOrNull()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        runCatching {
            mediaSession?.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

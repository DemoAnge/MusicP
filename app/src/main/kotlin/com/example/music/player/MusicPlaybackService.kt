package com.example.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.music.R
import com.example.music.core.CrashGuard
import com.example.music.domain.model.PlaybackSource
import com.google.common.collect.ImmutableList
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var localPlayer: LocalPlayerService

    @Inject
    lateinit var coordinator: PlayerCoordinator

    private var mediaSession: MediaSession? = null
    private var sessionPlayer: QueueAwarePlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        CrashGuard.run { ensureChannel() }
        CrashGuard.run { setupSession() }
        coordinator.state
            .map { Triple(it.currentTrack?.id, it.isPlaying, it.currentTrack?.title) }
            .distinctUntilChanged()
            .onEach { enterForeground() }
            .launchIn(serviceScope)
        enterForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        val result = runCatching { super.onStartCommand(intent, flags, startId) }
            .getOrDefault(START_STICKY)
        if (intent?.action != null) {
            serviceScope.launch {
                runCatching { coordinator.awaitReady() }
                CrashGuard.run { handleAction(intent) }
                enterForeground()
            }
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepPlaying = runCatching {
            coordinator.state.value.isPlaying || run {
                val player = localPlayer.exoPlayer
                player.playWhenReady && player.playbackState != Player.STATE_IDLE
            }
        }.getOrDefault(false)
        if (keepPlaying) return
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching {
            mediaSession?.release()
            mediaSession = null
            sessionPlayer = null
        }
        runCatching { super.onDestroy() }
    }

    private fun setupSession() {
        val player = QueueAwarePlayer(
            player = localPlayer.exoPlayer,
            onSkipNext = { CrashGuard.run { coordinator.skipNext() } },
            onSkipPrevious = { CrashGuard.run { coordinator.skipPrevious() } },
            onPlay = { CrashGuard.run { coordinator.resume() } },
            onPause = { CrashGuard.run { coordinator.pause() } },
        )
        sessionPlayer = player
        setMediaNotificationProvider(ControlsNotificationProvider())
        mediaSession = runCatching {
            MediaSession.Builder(this, player)
                .setId("music_playback")
                .setSessionActivity(pendingSessionActivity())
                .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader.Builder(this).build()))
                .build()
        }.getOrNull()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                    )
                ) {
                    enterForeground()
                }
            }
        })
    }

    private fun handleAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_PLAY -> coordinator.resume()
            ACTION_PAUSE -> coordinator.pause()
            ACTION_TOGGLE -> {
                if (coordinator.state.value.currentTrack == null) openApp()
                else coordinator.togglePlayPause()
            }
            ACTION_NEXT -> {
                if (coordinator.state.value.currentTrack == null) openApp()
                else coordinator.skipNext()
            }
            ACTION_PREVIOUS -> {
                if (coordinator.state.value.currentTrack == null) openApp()
                else coordinator.skipPrevious()
            }
        }
    }

    private fun openApp() {
        runCatching {
            val launch = (packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, com.example.music.MainActivity::class.java)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(launch)
        }
    }

    private inner class ControlsNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback,
        ): MediaNotification {
            return MediaNotification(NOTIFICATION_ID, buildMediaNotification())
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle,
        ): Boolean = false

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
            )
        }
    }

    private fun enterForeground() {
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildMediaNotification(),
                if (Build.VERSION.SDK_INT >= 29) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
        }
    }

    private fun buildMediaNotification(): Notification {
        val snapshot = coordinator.state.value
        val exo = localPlayer.exoPlayer
        val playing = snapshot.isPlaying ||
            runCatching { exo.isPlaying || exo.playWhenReady }.getOrDefault(false)
        val metadata = runCatching { exo.mediaMetadata }.getOrNull()
        val track = snapshot.currentTrack
        val title = track?.title
            ?: metadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val artist = if (track?.source == PlaybackSource.WEB) {
            track.artist.ifBlank { "Brave" }
        } else {
            metadata?.artist?.toString()?.takeIf { it.isNotBlank() }
                ?: track?.artist
                ?: getString(R.string.playback_notification_text)
        }
        val playPauseIcon = if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        val playPauseLabel = if (playing) "Pausar" else "Reproducir"
        val playPauseAction = if (playing) ACTION_PAUSE else ACTION_PLAY
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pendingSessionActivity())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
            .addAction(R.drawable.ic_widget_prev, "Anterior", serviceAction(ACTION_PREVIOUS, 31))
            .addAction(playPauseIcon, playPauseLabel, serviceAction(playPauseAction, 32))
            .addAction(R.drawable.ic_widget_next, "Siguiente", serviceAction(ACTION_NEXT, 33))
        loadLargeIcon()?.let { builder.setLargeIcon(it) }
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        runCatching {
            val token = mediaSession?.platformToken ?: return@runCatching
            style.setMediaSession(MediaSessionCompat.Token.fromToken(token))
        }
        builder.setStyle(style)
        return builder.build()
    }

    private fun loadLargeIcon(): Bitmap? {
        val metadata = runCatching { localPlayer.exoPlayer.mediaMetadata }.getOrNull() ?: return null
        val data = metadata.artworkData ?: return null
        val decoded = runCatching { decodeSampled(data, 256) }.getOrNull() ?: return null
        return scale(decoded)
    }

    private fun decodeSampled(data: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, target)
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var size = 1
        val largest = maxOf(width, height).coerceAtLeast(1)
        while (largest / size > target * 2) size *= 2
        return size
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= 256) return bitmap
        val factor = 256f / largest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return scaled
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            existing.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            existing.setSound(null, null)
            existing.enableVibration(false)
            manager.createNotificationChannel(existing)
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                description = getString(R.string.playback_channel_description)
            },
        )
    }

    private fun pendingSessionActivity(): PendingIntent {
        val launch = (packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.example.music.MainActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_controls"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.dmusic.action.PLAY"
        const val ACTION_PAUSE = "com.dmusic.action.PAUSE"
        const val ACTION_TOGGLE = "com.dmusic.action.TOGGLE"
        const val ACTION_NEXT = "com.dmusic.action.NEXT"
        const val ACTION_PREVIOUS = "com.dmusic.action.PREVIOUS"
    }
}

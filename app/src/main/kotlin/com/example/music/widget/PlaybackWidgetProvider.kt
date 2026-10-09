package com.example.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.example.music.MainActivity
import com.example.music.R
import com.example.music.domain.model.PlayerState
import com.example.music.player.PlayerCoordinator
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var coordinator: PlayerCoordinator

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = currentState()
        appWidgetIds.forEach { id ->
            runCatching { appWidgetManager.updateAppWidget(id, buildViews(context, state)) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching { super.onReceive(context, intent) }
        when (intent.action) {
            ACTION_TOGGLE -> {
                val state = currentState()
                if (state.currentTrack == null) {
                    openApp(context)
                } else {
                    runCatching { coordinatorOrNull()?.togglePlayPause() }
                }
                refresh(context)
            }
            ACTION_NEXT -> {
                runCatching { coordinatorOrNull()?.skipNext() }
                refresh(context)
            }
            ACTION_PREVIOUS -> {
                runCatching { coordinatorOrNull()?.skipPrevious() }
                refresh(context)
            }
        }
    }

    private fun coordinatorOrNull(): PlayerCoordinator? =
        if (this::coordinator.isInitialized) coordinator else null

    private fun currentState(): PlayerState =
        runCatching { coordinatorOrNull()?.state?.value }.getOrNull() ?: PlayerState()

    private fun refresh(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val state = currentState()
            val views = buildViews(context, state)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.dmusic.widget.TOGGLE"
        const val ACTION_NEXT = "com.dmusic.widget.NEXT"
        const val ACTION_PREVIOUS = "com.dmusic.widget.PREVIOUS"

        fun updateAll(context: Context, state: PlayerState) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val views = buildViews(context, state)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }

        fun buildViews(context: Context, state: PlayerState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_playback)
            val track = state.currentTrack
            if (track == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle_title))
                views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_idle_artist))
                views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_album)
                views.setImageViewResource(R.id.widget_play_icon, R.drawable.ic_widget_play)
            } else {
                views.setTextViewText(R.id.widget_title, track.title)
                views.setTextViewText(R.id.widget_artist, track.artist)
                views.setImageViewResource(
                    R.id.widget_play_icon,
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
                val art = loadArtwork(context, track.id, track.artworkUri)
                if (art != null) {
                    views.setImageViewBitmap(R.id.widget_art, art)
                } else {
                    views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_album)
                }
            }
            views.setOnClickPendingIntent(R.id.widget_root, activityIntent(context, 10))
            views.setOnClickPendingIntent(R.id.widget_art, activityIntent(context, 11))
            views.setOnClickPendingIntent(R.id.widget_play, broadcastIntent(context, ACTION_TOGGLE, 20))
            views.setOnClickPendingIntent(R.id.widget_prev, broadcastIntent(context, ACTION_PREVIOUS, 21))
            views.setOnClickPendingIntent(R.id.widget_next, broadcastIntent(context, ACTION_NEXT, 22))
            return views
        }

        private fun loadArtwork(context: Context, trackId: String, artworkUri: String?): Bitmap? {
            return runCatching {
                val cache = cacheArtFile(context, trackId)
                val fromCache = if (cache.exists() && cache.length() > 64L) {
                    decodeScaled(cache.absolutePath)
                } else {
                    null
                }
                fromCache ?: artworkUri?.takeIf { it.isNotBlank() && it != "0" }?.let { raw ->
                    val uri = Uri.parse(raw)
                    if (uri.scheme == "file") {
                        uri.path?.let(::decodeScaled)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)?.let(::scale)
                        }
                    }
                }
            }.getOrNull()
        }

        private fun cacheArtFile(context: Context, trackId: String): File {
            val safe = trackId.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return File(File(context.cacheDir, "track_art"), "$safe.jpg")
        }

        private fun decodeScaled(path: String): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 160)
            }
            return BitmapFactory.decodeFile(path, options)?.let(::scale)
        }

        private fun sampleSize(width: Int, height: Int, target: Int): Int {
            var size = 1
            val largest = maxOf(width, height).coerceAtLeast(1)
            while (largest / size > target * 2) size *= 2
            return size
        }

        private fun scale(bitmap: Bitmap): Bitmap {
            val largest = maxOf(bitmap.width, bitmap.height)
            if (largest <= 160) return bitmap
            val factor = 160f / largest
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * factor).toInt().coerceAtLeast(1),
                (bitmap.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        }

        private fun activityIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                pendingFlags(),
            )
        }

        private fun broadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, requestCode, intent, pendingFlags())
        }

        private fun pendingFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
            return flags
        }

        private fun openApp(context: Context) {
            runCatching {
                activityIntent(context, 12).send()
            }
        }
    }
}

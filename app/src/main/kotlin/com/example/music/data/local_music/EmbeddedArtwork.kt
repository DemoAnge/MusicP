package com.example.music.data.local_music

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object EmbeddedArtwork {
    private val gate = Semaphore(2)

    suspend fun resolve(
        context: Context,
        trackId: String,
        mediaUri: String?,
        isVideo: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        if (trackId.isBlank() || mediaUri.isNullOrBlank()) return@withContext null
        val file = cacheFile(context, trackId)
        if (file.exists() && file.length() > 64L) {
            return@withContext Uri.fromFile(file).toString()
        }
        gate.withPermit {
            if (file.exists() && file.length() > 64L) {
                return@withPermit Uri.fromFile(file).toString()
            }
            val uri = runCatching { Uri.parse(mediaUri) }.getOrNull() ?: return@withPermit null
            extractInto(context, uri, file, isVideo)
            if (file.exists() && file.length() > 64L) Uri.fromFile(file).toString() else null
        }
    }

    private fun cacheFile(context: Context, trackId: String): File {
        val dir = File(context.cacheDir, "track_art").apply { mkdirs() }
        val safe = trackId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.jpg")
    }

    private fun extractInto(context: Context, uri: Uri, file: File, isVideo: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    val bitmap = context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
                    writeScaled(bitmap, file)
                }
                if (file.exists() && file.length() > 64L) return
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val picture = runCatching { retriever.embeddedPicture }.getOrNull()
                if (picture != null && picture.size > 64) {
                    file.writeBytes(picture)
                    return
                }
                if (isVideo) {
                    val frame = runCatching { scaledFrame(retriever) }.getOrNull()
                    if (frame != null) {
                        writeScaled(frame, file)
                    }
                }
            } finally {
                runCatching { retriever.release() }
            }
        } catch (_: Throwable) {
            runCatching { if (file.exists() && file.length() <= 64L) file.delete() }
        }
    }

    private fun scaledFrame(retriever: MediaMetadataRetriever): Bitmap? {
        if (Build.VERSION.SDK_INT >= 27) {
            return retriever.getScaledFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                320,
                320,
            ) ?: retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                320,
                320,
            )
        }
        return retriever.frameAtTime
    }

    private fun writeScaled(bitmap: Bitmap, file: File) {
        val scaled = scaleDown(bitmap, 320)
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== bitmap) {
            runCatching { scaled.recycle() }
        }
    }

    private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= max) return bitmap
        val scale = max.toFloat() / largest.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}

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
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val picture = runCatching { retriever.embeddedPicture }.getOrNull()
            if (picture != null && picture.size > 64) {
                file.writeBytes(picture)
                return
            }
            if (isVideo) {
                val frame = runCatching {
                    retriever.getFrameAtTime(1_000_000L) ?: retriever.frameAtTime
                }.getOrNull()
                if (frame != null) {
                    file.outputStream().use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    return
                }
            }
        } catch (_: Throwable) {
            // El archivo no trae carátula embebida; se intenta thumbnail.
        } finally {
            runCatching { retriever.release() }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
            }
        }
    }
}

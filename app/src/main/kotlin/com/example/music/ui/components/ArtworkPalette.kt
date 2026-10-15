package com.example.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.example.music.core.theme.Accent
import com.example.music.core.theme.Background
import com.example.music.core.theme.Surface
import com.example.music.data.local_music.EmbeddedArtwork
import com.example.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtworkPalette {
    val fallback: List<Color> = listOf(Background, Surface)

    suspend fun extract(context: Context, track: Track?): List<Color> = withContext(Dispatchers.IO) {
        if (track == null) return@withContext fallback
        val uri = resolveUri(context, track) ?: return@withContext fallback
        val bitmap = decode(context, uri) ?: return@withContext fallback
        try {
            val palette = Palette.from(bitmap).clearFilters().generate()
            val dominant = palette.dominantSwatch?.rgb?.let(::Color) ?: Background
            val dark = palette.darkMutedSwatch?.rgb?.let(::Color)
                ?: palette.darkVibrantSwatch?.rgb?.let(::Color)
                ?: Surface
            val accent = palette.vibrantSwatch?.rgb?.let(::Color) ?: Accent
            listOf(dominant.copy(alpha = 0.95f), dark.copy(alpha = 0.95f), accent.copy(alpha = 0.35f))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun resolveUri(context: Context, track: Track): String? {
        val embedded = runCatching {
            EmbeddedArtwork.resolve(context, track.id, track.mediaUri, track.isVideo)
        }.getOrNull()
        return embedded ?: usable(track.artworkUri)
    }

    private fun usable(uri: String?): String? {
        if (uri.isNullOrBlank() || uri == "0") return null
        if (uri.endsWith("/albumart/0")) return null
        return uri
    }

    private fun decode(context: Context, artworkUri: String): Bitmap? {
        return runCatching {
            when {
                artworkUri.startsWith("http") ->
                    java.net.URL(artworkUri).openStream().use { stream ->
                        decodeSampled(stream.readBytes(), TARGET)
                    }
                artworkUri.startsWith("file:") -> {
                    val path = Uri.parse(artworkUri).path ?: return@runCatching null
                    decodeFileSampled(path, TARGET)
                }
                else -> context.contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
                    decodeSampled(stream.readBytes(), TARGET)
                }
            }
        }.getOrNull()
    }

    private fun decodeFileSampled(path: String, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, target)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, target)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var size = 1
        val largest = maxOf(width, height).coerceAtLeast(1)
        while (largest / size > target * 2) size *= 2
        return size
    }

    private const val TARGET = 160
}

@Composable
fun rememberArtworkPalette(track: Track?): List<Color> {
    val context = LocalContext.current
    return produceState(ArtworkPalette.fallback, track?.id, track?.artworkUri) {
        value = ArtworkPalette.extract(context, track)
    }.value
}

package com.example.music.data.local_music

import android.content.Context
import android.net.Uri
import com.example.music.domain.player.ReplayGainParser
import java.io.File

object ReplayGainReader {
    private const val HEADER_BYTES = 128 * 1024

    fun readDb(context: Context, mediaUri: String?): Float? {
        if (mediaUri.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(mediaUri) }.getOrNull() ?: return null
        val bytes = runCatching { headerBytes(context, uri) }.getOrNull() ?: return null
        return ReplayGainParser.parseFromBytes(bytes)
    }

    private fun headerBytes(context: Context, uri: Uri): ByteArray? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists()) return null
            return file.inputStream().use { it.readNBytesCompat(HEADER_BYTES) }
        }
        return context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(HEADER_BYTES) }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val buf = ByteArray(max)
        var offset = 0
        while (offset < max) {
            val n = read(buf, offset, max - offset)
            if (n <= 0) break
            offset += n
        }
        return if (offset == buf.size) buf else buf.copyOf(offset)
    }
}

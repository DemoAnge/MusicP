package com.example.music.data.lyrics

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.nio.charset.Charset

object LocalLyricsReader {
    private const val METADATA_KEY_LYRICS = 29
    private val sidecarExts = listOf("lrc", "lyric", "lyrics")

    fun read(context: Context, mediaUri: Uri): String? {
        retrieverLyrics(context, mediaUri)?.let { return it }
        Id3LyricsReader.read(context.contentResolver, mediaUri)?.let { return it }
        sidecarLyrics(context, mediaUri)?.let { return it }
        return null
    }

    private fun retrieverLyrics(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(METADATA_KEY_LYRICS)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sidecarLyrics(context: Context, mediaUri: Uri): String? {
        val displayName = queryDisplayName(context, mediaUri) ?: return null
        val stem = displayName.substringBeforeLast('.', missingDelimiterValue = displayName)
        if (stem.isBlank()) return null
        val names = sidecarExts.map { ext -> "$stem.$ext" }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val placeholders = names.joinToString(",") { "?" }
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} IN ($placeholders)"
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME),
                selection,
                names.toTypedArray(),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    readText(context, uri)?.let { return@use it }
                }
                null
            }
        }.getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i < 0) null else cursor.getString(i)
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun readText(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty() || bytes.size > 400_000) return@use null
                decodeText(bytes)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun decodeText(bytes: ByteArray): String? {
        val charset: Charset = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                Charsets.UTF_8
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16
            else -> Charsets.UTF_8
        }
        return String(bytes, charset).trim('\uFEFF', '\u0000', ' ')
    }
}

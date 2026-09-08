package com.example.music.data.lyrics

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.math.min

object Id3LyricsReader {
    fun read(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.openInputStream(uri)?.use { stream -> read(stream) }
        }.getOrNull()
    }

    fun read(stream: InputStream): String? {
        val header = ByteArray(10)
        if (!readFully(stream, header)) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }
        val version = header[3].toInt() and 0xFF
        val tagSize = synchsafe(header, 6)
        if (tagSize <= 0) return null
        val body = ByteArray(min(tagSize, 1_500_000))
        val n = readAsMuch(stream, body)
        if (n <= 10) return null
        return parseFrames(body, n, version)
    }

    private fun parseFrames(body: ByteArray, length: Int, version: Int): String? {
        var offset = 0
        var lyrics: String? = null
        while (offset + 10 <= length) {
            if (body[offset] == 0.toByte()) break
            val id = String(body, offset, 4, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val size = if (version >= 4) synchsafe(body, offset + 4) else beInt(body, offset + 4)
            offset += 10
            if (size <= 0 || offset + size > length) break
            val frame = body.copyOfRange(offset, offset + size)
            offset += size
            when {
                id == "USLT" || id == "ULT" -> {
                    val text = decodeUslt(frame)
                    if (!text.isNullOrBlank()) return text
                }
                id == "TXXX" || id == "TXX" -> {
                    val pair = decodeTxxx(frame)
                    if (pair != null && isLyricKey(pair.first) && pair.second.isNotBlank()) {
                        lyrics = pair.second
                    }
                }
                id == "COMM" || id == "COM" -> {
                    val pair = decodeComm(frame)
                    if (pair != null && isLyricKey(pair.first) && pair.second.isNotBlank()) {
                        lyrics = pair.second
                    }
                }
            }
        }
        return lyrics
    }

    private fun decodeUslt(frame: ByteArray): String? {
        if (frame.isEmpty()) return null
        val encoding = frame[0].toInt() and 0xFF
        if (frame.size < 5) return null
        val charset = charsetOf(encoding)
        val terminator = terminatorOf(encoding)
        var index = 4
        index = skipTerminated(frame, index, terminator)
        if (index >= frame.size) return null
        return String(frame, index, frame.size - index, charset).trim { it <= ' ' || it == '\u0000' }
            .takeIf { it.isNotEmpty() }
    }

    private fun decodeComm(frame: ByteArray): Pair<String, String>? {
        if (frame.size < 5) return null
        val encoding = frame[0].toInt() and 0xFF
        val charset = charsetOf(encoding)
        val terminator = terminatorOf(encoding)
        val descEnd = skipTerminated(frame, 4, terminator)
        if (descEnd >= frame.size) return null
        val descLen = (descEnd - terminator.size - 4).coerceAtLeast(0)
        val desc = String(frame, 4, descLen, charset).trim('\u0000', ' ')
        val value = String(frame, descEnd, frame.size - descEnd, charset).trim('\u0000', ' ')
        return desc to value
    }

    private fun decodeTxxx(frame: ByteArray): Pair<String, String>? {
        if (frame.isEmpty()) return null
        val encoding = frame[0].toInt() and 0xFF
        val charset = charsetOf(encoding)
        val terminator = terminatorOf(encoding)
        val descEnd = skipTerminated(frame, 1, terminator)
        if (descEnd >= frame.size) return null
        val descLen = (descEnd - terminator.size - 1).coerceAtLeast(0)
        val desc = String(frame, 1, descLen, charset).trim('\u0000', ' ')
        val value = String(frame, descEnd, frame.size - descEnd, charset).trim('\u0000', ' ')
        return desc to value
    }

    fun isLyricKey(value: String?): Boolean {
        val key = value?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return false
        if (key == "lyric" || key == "lyrics") return true
        return key.contains("lyric")
    }

    private fun charsetOf(encoding: Int): Charset = when (encoding) {
        1, 2 -> Charsets.UTF_16
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun terminatorOf(encoding: Int): ByteArray =
        if (encoding == 1 || encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)

    private fun skipTerminated(data: ByteArray, start: Int, terminator: ByteArray): Int {
        var i = start
        while (i + terminator.size <= data.size) {
            var match = true
            for (j in terminator.indices) {
                if (data[i + j] != terminator[j]) {
                    match = false
                    break
                }
            }
            if (match) return i + terminator.size
            i += 1
        }
        return data.size
    }

    private fun synchsafe(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)
    }

    private fun beInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read <= 0) return false
            offset += read
        }
        return true
    }

    private fun readAsMuch(stream: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read <= 0) break
            offset += read
        }
        return offset
    }
}

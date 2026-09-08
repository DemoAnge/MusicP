package com.example.music.data.lyrics

import com.example.music.domain.model.LyricsLine

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)$""")

    fun parse(lrc: String): List<LyricsLine> {
        return runCatching { parseInternal(lrc) }.getOrDefault(emptyList())
    }

    private fun parseInternal(lrc: String): List<LyricsLine> {
        return lrc.lineSequence()
            .mapNotNull { raw ->
                val match = lineRegex.find(raw.trim()) ?: return@mapNotNull null
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                val text = match.groupValues[4].trim()
                if (text.isEmpty()) null
                else LyricsLine(
                    timeMs = minutes * 60_000 + seconds * 1_000 + millis,
                    text = text,
                )
            }
            .sortedBy { it.timeMs }
            .toList()
    }

    fun plainText(lrc: String): String {
        return runCatching { plainTextInternal(lrc) }.getOrDefault(lrc)
    }

    private fun plainTextInternal(lrc: String): String {
        return lrc.lineSequence()
            .map { raw ->
                val match = lineRegex.find(raw.trim())
                match?.groupValues?.getOrNull(4)?.trim() ?: raw.trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}

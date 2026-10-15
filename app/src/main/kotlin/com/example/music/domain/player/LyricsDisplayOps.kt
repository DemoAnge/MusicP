package com.example.music.domain.player

import com.example.music.domain.model.SyncedLyrics

/**
 * Synced lines stay as-is (no translation or romanization).
 * Unsynced text is cleaned so it can be read as a poem, not as LRC tags.
 */
object LyricsDisplayOps {
    private val timedPrefix = Regex("""^\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")
    private val metaLine = Regex(
        """^\[(ti|ar|al|by|offset|re|ve|length|id):.*]$""",
        RegexOption.IGNORE_CASE,
    )

    fun readablePlain(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val lines = text.lineSequence()
            .map { raw ->
                val trimmed = raw.trim()
                when {
                    trimmed.isEmpty() -> ""
                    metaLine.matches(trimmed) -> ""
                    else -> timedPrefix.replace(trimmed, "").trim()
                }
            }
            .filter { it.isNotEmpty() }
            .toList()
        return lines.joinToString("\n")
    }

    fun hasSynced(lyrics: SyncedLyrics?): Boolean = lyrics?.lines?.isNotEmpty() == true

    fun unsyncedBody(lyrics: SyncedLyrics?): String {
        if (lyrics == null || hasSynced(lyrics)) return ""
        return readablePlain(lyrics.plainText)
    }

    fun currentText(lyrics: SyncedLyrics?, positionMs: Long, offsetMs: Long): String? {
        if (lyrics == null) return null
        if (hasSynced(lyrics)) {
            return lyrics.lineAt(positionMs, offsetMs)?.text?.ifBlank { null }
        }
        return unsyncedBody(lyrics).lineSequence().firstOrNull()?.ifBlank { null }
    }
}

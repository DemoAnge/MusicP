package com.example.music.domain.library

import com.example.music.domain.model.Track

enum class TrackKind {
    MUSIC,
    PODCAST,
    AUDIOBOOK,
}

object TrackClassifier {
    const val PODCAST_MIN_MS = 20L * 60_000L
    const val AUDIOBOOK_MIN_MS = 30L * 60_000L

    fun isDotHidden(folderPath: String, folderName: String = ""): Boolean {
        val parts = folderPath.replace('\\', '/').split('/').filter { it.isNotBlank() }
        return parts.any { it.startsWith('.') } || folderName.startsWith('.')
    }

    fun folderLabel(path: String): String {
        val trimmed = path.trim().trimEnd('/', '\\')
        if (trimmed.isEmpty()) return "Carpeta"
        val last = trimmed.substringAfterLast('/').substringAfterLast('\\')
        return last.ifBlank { trimmed }
    }

    fun kind(track: Track): TrackKind {
        val mime = track.mimeType.orEmpty().lowercase()
        val tokens = folderTokens(track.folderPath, track.folderName)
        val ext = fileExtension(track.mediaUri, track.title)
        if (isAudiobookMime(mime, ext) || tokens.any { it in AUDIOBOOK_FOLDERS }) {
            return TrackKind.AUDIOBOOK
        }
        if (tokens.any { it in PODCAST_FOLDERS }) return TrackKind.PODCAST
        if (track.durationMs >= AUDIOBOOK_MIN_MS && looksSpoken(track)) {
            return TrackKind.AUDIOBOOK
        }
        if (track.durationMs >= PODCAST_MIN_MS && looksSpoken(track)) {
            return TrackKind.PODCAST
        }
        return TrackKind.MUSIC
    }

    fun includeInLibrary(
        track: Track,
        ignoredFolders: Set<String>,
        showHidden: Boolean,
        showPodcasts: Boolean,
        showAudiobooks: Boolean,
    ): Boolean {
        if (track.isVideo) return false
        val hidden = isDotHidden(track.folderPath, track.folderName) ||
            (track.folderPath.isNotBlank() && track.folderPath in ignoredFolders)
        if (hidden && !showHidden) return false
        return when (kind(track)) {
            TrackKind.MUSIC -> true
            TrackKind.PODCAST -> showPodcasts
            TrackKind.AUDIOBOOK -> showAudiobooks
        }
    }

    private fun looksSpoken(track: Track): Boolean {
        val hay = "${track.album} ${track.artist} ${track.title}".lowercase()
        return SPOKEN_HINTS.any { it in hay }
    }

    private fun isAudiobookMime(mime: String, ext: String): Boolean {
        if (ext in AUDIOBOOK_EXT) return true
        return AUDIOBOOK_MIME_PARTS.any { it in mime }
    }

    private fun folderTokens(path: String, name: String): Set<String> {
        return (path.replace('\\', '/').split('/') + name)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun fileExtension(mediaUri: String, title: String): String {
        val raw = sequenceOf(mediaUri, title)
            .map { it.substringAfterLast('/').substringAfterLast('\\') }
            .firstOrNull { it.contains('.') }
            .orEmpty()
        return raw.substringAfterLast('.', missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
    }

    private val PODCAST_FOLDERS = setOf("podcasts", "podcast")
    private val AUDIOBOOK_FOLDERS = setOf(
        "audiobooks", "audiobook", "audiolibros", "audiolibro", "audible",
    )
    private val AUDIOBOOK_EXT = setOf("m4b", "aa", "aax")
    private val AUDIOBOOK_MIME_PARTS = listOf("m4b", "audiobook", "x-m4b")
    private val SPOKEN_HINTS = listOf("podcast", "audiobook", "audiolibro", "episodio")
}

package com.example.music.domain.usecase

import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.Track
import com.example.music.domain.repository.YouTubeSearchRepository
import javax.inject.Inject

class SearchYouTubeUseCase @Inject constructor(
    private val repository: YouTubeSearchRepository,
) {
    fun hasApiKey(): Boolean = repository.hasApiKey()

    suspend operator fun invoke(query: String): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching { repository.search(trimmed) }.getOrDefault(emptyList())
    }

    fun placeholder(query: String): Track {
        val trimmed = query.trim()
        return Track(
            id = "ytsearch:${trimmed.hashCode()}",
            title = trimmed,
            artist = "YouTube",
            album = "Brave",
            durationMs = 0L,
            artworkUri = null,
            mediaUri = "ytsearch:$trimmed",
            source = PlaybackSource.WEB,
        )
    }
}

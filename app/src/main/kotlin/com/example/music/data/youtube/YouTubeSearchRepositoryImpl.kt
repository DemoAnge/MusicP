package com.example.music.data.youtube

import com.example.music.BuildConfig
import com.example.music.core.network.YouTubeApi
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.Track
import com.example.music.domain.repository.YouTubeSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeSearchRepositoryImpl @Inject constructor(
    private val api: YouTubeApi,
) : YouTubeSearchRepository {

    override fun hasApiKey(): Boolean = BuildConfig.YOUTUBE_API_KEY.isNotBlank()

    override suspend fun search(query: String): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !hasApiKey()) return emptyList()
        val key = BuildConfig.YOUTUBE_API_KEY
        val response = runCatching { api.search(query = trimmed, key = key) }.getOrNull() ?: return emptyList()
        return response.items.mapNotNull { item ->
            val videoId = item.id?.videoId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val snippet = item.snippet
            Track(
                id = "yt:$videoId",
                title = snippet?.title?.ifBlank { trimmed } ?: trimmed,
                artist = snippet?.channelTitle?.ifBlank { "YouTube" } ?: "YouTube",
                album = "YouTube",
                durationMs = 0L,
                artworkUri = snippet?.thumbnails?.medium?.url
                    ?: snippet?.thumbnails?.high?.url
                    ?: snippet?.thumbnails?.default?.url,
                mediaUri = "yt:$videoId",
                source = PlaybackSource.WEB,
            )
        }
    }
}

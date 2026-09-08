package com.example.music.domain.usecase

import com.example.music.domain.model.Track
import com.example.music.domain.repository.LocalMusicRepository
import javax.inject.Inject

class SearchTracksUseCase @Inject constructor(
    private val localMusicRepository: LocalMusicRepository,
) {
    suspend operator fun invoke(query: String): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return localMusicRepository.search(trimmed)
    }
}

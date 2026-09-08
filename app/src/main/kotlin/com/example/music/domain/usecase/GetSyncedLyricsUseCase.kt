package com.example.music.domain.usecase

import com.example.music.domain.model.Track
import com.example.music.domain.repository.LyricsRepository
import javax.inject.Inject

class GetSyncedLyricsUseCase @Inject constructor(
    private val repository: LyricsRepository,
) {
    suspend operator fun invoke(track: Track) = repository.getSyncedLyrics(track)
}

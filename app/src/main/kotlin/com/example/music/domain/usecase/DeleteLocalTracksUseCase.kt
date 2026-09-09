package com.example.music.domain.usecase

import com.example.music.domain.model.DeleteTracksResult
import com.example.music.domain.model.Track
import com.example.music.domain.repository.LocalMusicRepository
import javax.inject.Inject

class DeleteLocalTracksUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    suspend operator fun invoke(tracks: List<Track>): DeleteTracksResult =
        repository.deleteTracks(tracks)
}

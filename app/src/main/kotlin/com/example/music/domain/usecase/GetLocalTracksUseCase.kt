package com.example.music.domain.usecase

import com.example.music.domain.repository.LocalMusicRepository
import javax.inject.Inject

class GetLocalTracksUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    fun observe() = repository.observeTracks()
    suspend fun refresh() = repository.getTracks()
}

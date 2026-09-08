package com.example.music.domain.usecase

import com.example.music.domain.model.Track
import com.example.music.domain.repository.LocalMusicRepository
import javax.inject.Inject

class OpenIncomingMediaUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    suspend operator fun invoke(uri: String, mimeType: String?): Track =
        repository.trackFromUri(uri, mimeType)
}

package com.example.music.domain.usecase

import com.example.music.domain.repository.LibraryPrefsRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val prefs: LibraryPrefsRepository,
) {
    suspend operator fun invoke(trackId: String) = prefs.toggleFavorite(trackId)
}

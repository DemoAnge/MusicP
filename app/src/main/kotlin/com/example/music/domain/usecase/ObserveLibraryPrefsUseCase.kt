package com.example.music.domain.usecase

import com.example.music.domain.repository.LibraryPrefsRepository
import javax.inject.Inject

class ObserveLibraryPrefsUseCase @Inject constructor(
    private val prefs: LibraryPrefsRepository,
) {
    fun favorites() = prefs.observeFavoriteIds()
    fun recents() = prefs.observeRecentIds()
}

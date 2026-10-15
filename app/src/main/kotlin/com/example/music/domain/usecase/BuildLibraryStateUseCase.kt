package com.example.music.domain.usecase

import com.example.music.domain.library.LibraryCatalog
import com.example.music.domain.model.LibraryBrowseQuery
import com.example.music.domain.model.LibraryPrefsSnapshot
import com.example.music.domain.model.LibraryUiState
import com.example.music.domain.model.Track
import javax.inject.Inject

class BuildLibraryStateUseCase @Inject constructor() {
    operator fun invoke(
        tracks: List<Track>,
        prefs: LibraryPrefsSnapshot,
        query: LibraryBrowseQuery,
    ): LibraryUiState = LibraryCatalog.build(tracks, prefs, query)
}

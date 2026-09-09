package com.example.music.domain.model

enum class BrowseMode {
    SONGS,
    FOLDERS,
    ARTISTS,
    ALBUMS,
    FAVORITES,
    RECENTS,
}

enum class SortMode {
    TITLE,
    DATE_NEW,
    DATE_OLD,
    SIZE_LARGE,
    SIZE_SMALL,
}

data class LibraryGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String?,
    val tracks: List<Track>,
)

data class LibraryUiState(
    val browse: BrowseMode = BrowseMode.SONGS,
    val sort: SortMode = SortMode.TITLE,
    val selectedGroupKey: String? = null,
    val selectedGroupTitle: String? = null,
    val showingGroups: Boolean = false,
    val groups: List<LibraryGroup> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val countLabel: String = "",
    val selecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

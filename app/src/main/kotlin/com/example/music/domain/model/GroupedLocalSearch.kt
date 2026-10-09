package com.example.music.domain.model

data class GroupedLocalSearch(
    val songs: List<Track> = emptyList(),
    val artists: List<LibraryGroup> = emptyList(),
    val albums: List<LibraryGroup> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

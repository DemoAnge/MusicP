package com.example.music.domain.usecase

import com.example.music.domain.model.GroupedLocalSearch
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.Track
import com.example.music.domain.repository.LocalMusicRepository
import javax.inject.Inject

class SearchTracksUseCase @Inject constructor(
    private val localMusicRepository: LocalMusicRepository,
) {
    suspend operator fun invoke(query: String): GroupedLocalSearch {
        val needle = query.trim()
        if (needle.isEmpty()) return GroupedLocalSearch()
        val q = needle.lowercase()
        val all = localMusicRepository.getTracks().filter { !it.isVideo }
        val matches = all.filter { track ->
            track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                track.album.lowercase().contains(q)
        }
        val songs = matches.sortedWith(
            compareBy<Track> { track ->
                when {
                    track.title.lowercase().contains(q) -> 0
                    track.artist.lowercase().contains(q) -> 1
                    else -> 2
                }
            }.thenBy { it.title.lowercase() },
        )
        val artists = all
            .groupBy { it.artist.trim().ifBlank { "Desconocido" } }
            .filter { (name, _) -> name.lowercase().contains(q) }
            .map { (name, tracks) ->
                val sorted = tracks.sortedBy { it.title.lowercase() }
                LibraryGroup(
                    key = "artist:$name",
                    title = name,
                    subtitle = countLabel(sorted.size),
                    artworkUri = sorted.firstOrNull()?.artworkUri,
                    tracks = sorted,
                )
            }
            .sortedBy { it.title.lowercase() }
        val albums = all
            .groupBy { track ->
                track.album.trim().ifBlank { "Sin álbum" } to track.artist.trim().ifBlank { "Desconocido" }
            }
            .filter { (pair, _) -> pair.first.lowercase().contains(q) }
            .map { (pair, tracks) ->
                val sorted = tracks.sortedBy { it.title.lowercase() }
                LibraryGroup(
                    key = "album:${pair.second}:${pair.first}",
                    title = pair.first,
                    subtitle = pair.second,
                    artworkUri = sorted.firstOrNull()?.artworkUri,
                    tracks = sorted,
                )
            }
            .sortedBy { it.title.lowercase() }
        return GroupedLocalSearch(songs = songs, artists = artists, albums = albums)
    }

    private fun countLabel(count: Int): String =
        if (count == 1) "1 canción" else "$count canciones"
}

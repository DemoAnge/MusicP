package com.example.music.domain.voice

import com.example.music.domain.model.GroupedLocalSearch
import com.example.music.domain.model.Track

data class VoicePlayPick(
    val track: Track,
    val queue: List<Track>,
    val spoken: String,
)

object VoicePlayResolver {
    fun pick(query: String, grouped: GroupedLocalSearch): VoicePlayPick? {
        val needle = query.trim()
        if (needle.isEmpty() || grouped.isEmpty) return null
        val q = needle.lowercase()

        val exactSong = grouped.songs.firstOrNull { it.title.equals(needle, ignoreCase = true) }
        if (exactSong != null) {
            return VoicePlayPick(exactSong, grouped.songs.ifEmpty { listOf(exactSong) }, exactSong.title)
        }
        val exactArtist = grouped.artists.firstOrNull { it.title.equals(needle, ignoreCase = true) }
        if (exactArtist != null) {
            val first = exactArtist.tracks.firstOrNull() ?: return null
            return VoicePlayPick(first, exactArtist.tracks, exactArtist.title)
        }
        val exactAlbum = grouped.albums.firstOrNull { it.title.equals(needle, ignoreCase = true) }
        if (exactAlbum != null) {
            val first = exactAlbum.tracks.firstOrNull() ?: return null
            return VoicePlayPick(first, exactAlbum.tracks, exactAlbum.title)
        }

        val titled = grouped.songs.firstOrNull { it.title.lowercase().contains(q) }
        if (titled != null) {
            return VoicePlayPick(titled, grouped.songs.ifEmpty { listOf(titled) }, titled.title)
        }
        val artist = grouped.artists.firstOrNull()
        if (artist != null && artist.tracks.isNotEmpty()) {
            return VoicePlayPick(artist.tracks.first(), artist.tracks, artist.title)
        }
        val album = grouped.albums.firstOrNull()
        if (album != null && album.tracks.isNotEmpty()) {
            return VoicePlayPick(album.tracks.first(), album.tracks, album.title)
        }
        val song = grouped.songs.firstOrNull() ?: return null
        return VoicePlayPick(song, grouped.songs, song.title)
    }
}

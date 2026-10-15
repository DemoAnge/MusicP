package com.example.music.domain.voice

import com.example.music.domain.model.GroupedLocalSearch
import com.example.music.domain.model.LibraryGroup
import com.example.music.testTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePlayResolverTest {
    private val song = testTrack("1", title = "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera")
    private val other = testTrack("2", title = "Somebody to Love", artist = "Queen", album = "A Day at the Races")
    private val artistGroup = LibraryGroup(
        key = "artist:Queen",
        title = "Queen",
        subtitle = "2 canciones",
        artworkUri = null,
        tracks = listOf(song, other),
    )
    private val albumGroup = LibraryGroup(
        key = "album:Queen:A Night at the Opera",
        title = "A Night at the Opera",
        subtitle = "Queen",
        artworkUri = null,
        tracks = listOf(song),
    )

    @Test
    fun prefersExactSongTitle() {
        val pick = VoicePlayResolver.pick(
            "Bohemian Rhapsody",
            GroupedLocalSearch(songs = listOf(song, other), artists = listOf(artistGroup)),
        )!!
        assertEquals("1", pick.track.id)
        assertEquals("Bohemian Rhapsody", pick.spoken)
    }

    @Test
    fun playsWholeArtistWhenNameMatches() {
        val pick = VoicePlayResolver.pick(
            "queen",
            GroupedLocalSearch(songs = listOf(song, other), artists = listOf(artistGroup)),
        )!!
        assertEquals(listOf("1", "2"), pick.queue.map { it.id })
        assertEquals("Queen", pick.spoken)
    }

    @Test
    fun playsAlbumWhenTitleMatches() {
        val pick = VoicePlayResolver.pick(
            "A Night at the Opera",
            GroupedLocalSearch(albums = listOf(albumGroup)),
        )!!
        assertEquals("1", pick.track.id)
        assertEquals("A Night at the Opera", pick.spoken)
    }

    @Test
    fun emptySearchIsNull() {
        assertNull(VoicePlayResolver.pick("coldplay", GroupedLocalSearch()))
    }
}

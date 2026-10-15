package com.example.music.domain.library

import com.example.music.domain.model.BrowseMode
import com.example.music.domain.model.LibraryBrowseQuery
import com.example.music.domain.model.LibraryPrefsSnapshot
import com.example.music.domain.model.LibraryScanPrefs
import com.example.music.domain.model.UserPlaylist
import com.example.music.testTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {
    private val songs = listOf(
        testTrack("1", title = "Alpha", artist = "A", album = "One", folderPath = "/sd/Music", folderName = "Music"),
        testTrack("2", title = "Beta", artist = "B", album = "Two", folderPath = "/sd/Rock", folderName = "Rock"),
        testTrack("vid", title = "Clip", isVideo = true),
    )

    @Test
    fun homeIgnoresVideosAndFolders() {
        val state = LibraryCatalog.build(
            songs,
            LibraryPrefsSnapshot(
                favorites = setOf("1"),
                recents = listOf("2", "1"),
                ignoredFolders = setOf("/sd/Rock"),
            ),
            LibraryBrowseQuery(BrowseMode.HOME),
        )
        assertTrue(state.isHome)
        assertEquals(listOf("1"), state.homeLiked.map { it.id })
        assertEquals(listOf("1"), state.homeRecents.map { it.id })
        assertEquals("1 canciones", state.countLabel)
    }

    @Test
    fun favoritesEmptyLabel() {
        val state = LibraryCatalog.build(
            songs,
            LibraryPrefsSnapshot(),
            LibraryBrowseQuery(BrowseMode.FAVORITES),
        )
        assertTrue(state.visibleTracks.isEmpty())
        assertEquals("Sin canciones marcadas", state.countLabel)
    }

    @Test
    fun playlistsOpenByKey() {
        val playlist = UserPlaylist(id = "p1", name = "Mix", trackIds = listOf("2", "1"))
        val state = LibraryCatalog.build(
            songs,
            LibraryPrefsSnapshot(playlists = listOf(playlist)),
            LibraryBrowseQuery(
                browse = BrowseMode.PLAYLISTS,
                groupKey = LibraryCatalog.playlistKey("p1"),
            ),
        )
        assertEquals("p1", state.selectedPlaylistId)
        assertEquals(listOf("2", "1"), state.visibleTracks.map { it.id })
    }

    @Test
    fun pickingOmitsTracksAlreadyInPlaylist() {
        val playlist = UserPlaylist(id = "p1", name = "Mix", trackIds = listOf("1"))
        val state = LibraryCatalog.build(
            songs,
            LibraryPrefsSnapshot(playlists = listOf(playlist)),
            LibraryBrowseQuery(
                browse = BrowseMode.PLAYLISTS,
                groupKey = LibraryCatalog.playlistKey("p1"),
                pickingForPlaylist = true,
            ),
        )
        assertTrue(state.pickingForPlaylist)
        assertEquals(listOf("2"), state.visibleTracks.map { it.id })
        assertFalse(state.showingGroups)
    }

    @Test
    fun idsToAddSkipsDuplicates() {
        assertEquals(listOf("b"), LibraryCatalog.idsToAddToPlaylist(setOf("a"), listOf("a", "b", "b", "")))
    }

    @Test
    fun moveIdsReordersPlaylist() {
        assertEquals(listOf("b", "a", "c"), LibraryCatalog.moveIds(listOf("a", "b", "c"), 1, 0))
        assertEquals(null, LibraryCatalog.moveIds(listOf("a"), 0, 0))
    }

    @Test
    fun groupCoverPrefersArtworkThenFirstTrack() {
        val bare = testTrack("1", artworkUri = null)
        val withArt = testTrack("2", artworkUri = "content://art/2")
        assertEquals("2", listOf(bare, withArt).let { LibraryCatalog.run { it.coverTrack() } }?.id)
        assertEquals("1", listOf(bare).let { LibraryCatalog.run { it.coverTrack() } }?.id)
    }

    @Test
    fun scannerHidesPodcastsUntilEnabled() {
        val library = songs + testTrack(
            "pod",
            title = "Episodio",
            folderPath = "/sd/Podcasts",
            folderName = "Podcasts",
        )
        val hidden = LibraryCatalog.build(library, LibraryPrefsSnapshot(), LibraryBrowseQuery(BrowseMode.SONGS))
        assertEquals(listOf("1", "2"), hidden.visibleTracks.map { it.id })
        val shown = LibraryCatalog.build(
            library,
            LibraryPrefsSnapshot(scan = LibraryScanPrefs(showPodcasts = true)),
            LibraryBrowseQuery(BrowseMode.SONGS),
        )
        assertEquals(listOf("1", "2", "pod"), shown.visibleTracks.map { it.id })
    }
}

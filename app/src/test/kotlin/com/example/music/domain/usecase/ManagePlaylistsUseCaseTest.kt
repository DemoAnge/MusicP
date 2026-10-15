package com.example.music.domain.usecase

import com.example.music.FakeLibraryPrefsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagePlaylistsUseCaseTest {
    private val prefs = FakeLibraryPrefsRepository()
    private val useCase = ManagePlaylistsUseCase(prefs)
    private val folders = IgnoreFolderUseCase(prefs)

    @Test
    fun createRejectsBlankName() = runBlocking {
        assertEquals(PlaylistMutation.BlankName, useCase.create("   "))
        assertTrue(prefs.observePlaylists().first().isEmpty())
    }

    @Test
    fun createCanSeedFirstTrack() = runBlocking {
        val result = useCase.create("Mix", addTrackId = "a") as PlaylistMutation.Created
        assertEquals("Mix", result.playlist.name)
        val stored = prefs.observePlaylists().first().single()
        assertEquals(listOf("a"), stored.trackIds)
    }

    @Test
    fun addSkipsIdsAlreadyInPlaylist() = runBlocking {
        val created = (useCase.create("Mix", addTrackId = "a") as PlaylistMutation.Created).playlist
        assertEquals(PlaylistMutation.AlreadyPresent, useCase.add(created.id, listOf("a", "a", "")))
        val added = useCase.add(created.id, listOf("a", "b", "b")) as PlaylistMutation.Added
        assertEquals(1, added.count)
        assertEquals(listOf("a", "b"), prefs.observePlaylists().first().single().trackIds)
    }

    @Test
    fun deleteAndRemoveTrack() = runBlocking {
        val created = (useCase.create("Mix", addTrackId = "a") as PlaylistMutation.Created).playlist
        useCase.add(created.id, listOf("b"))
        assertEquals(PlaylistMutation.TrackRemoved, useCase.removeTrack(created.id, "a"))
        assertEquals(listOf("b"), prefs.observePlaylists().first().single().trackIds)
        assertEquals(PlaylistMutation.Deleted, useCase.delete(created.id))
        assertTrue(prefs.observePlaylists().first().isEmpty())
    }

    @Test
    fun ignoreFolderRequiresPath() = runBlocking {
        assertEquals(false, folders.ignore("  "))
        assertTrue(folders.ignore("/sd/Podcasts"))
        assertEquals(setOf("/sd/Podcasts"), prefs.observeIgnoredFolders().first())
        folders.restore("/sd/Podcasts")
        assertTrue(prefs.observeIgnoredFolders().first().isEmpty())
    }
}

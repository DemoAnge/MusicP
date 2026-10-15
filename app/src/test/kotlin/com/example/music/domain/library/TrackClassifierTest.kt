package com.example.music.domain.library

import com.example.music.testTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackClassifierTest {
    @Test
    fun folderNameIsLastSegment() {
        assertEquals("Podcasts", TrackClassifier.folderLabel("/sdcard/Music/Podcasts"))
        assertEquals("Carpeta", TrackClassifier.folderLabel("  "))
    }

    @Test
    fun dotFoldersAreHidden() {
        assertTrue(TrackClassifier.isDotHidden("/sd/.hidden", ".hidden"))
        assertTrue(TrackClassifier.isDotHidden("/sd/Music/.cache", "cache"))
        assertFalse(TrackClassifier.isDotHidden("/sd/Music", "Music"))
    }

    @Test
    fun podcastsFolderWins() {
        val track = testTrack("p", folderPath = "/sd/Podcasts", folderName = "Podcasts")
        assertEquals(TrackKind.PODCAST, TrackClassifier.kind(track))
    }

    @Test
    fun m4bIsAudiobook() {
        val track = testTrack(
            "b",
            mimeType = "audio/x-m4b",
            mediaUri = "content://media/book.m4b",
        )
        assertEquals(TrackKind.AUDIOBOOK, TrackClassifier.kind(track))
    }

    @Test
    fun longSpokenTitleIsPodcast() {
        val track = testTrack(
            "ep",
            title = "Podcast episodio 12",
            durationMs = 25L * 60_000L,
        )
        assertEquals(TrackKind.PODCAST, TrackClassifier.kind(track))
    }

    @Test
    fun longMusicStaysMusic() {
        val track = testTrack("mix", title = "Live at the Garden", durationMs = 40L * 60_000L)
        assertEquals(TrackKind.MUSIC, TrackClassifier.kind(track))
    }

    @Test
    fun includeHonorsScanToggles() {
        val podcast = testTrack("p", folderPath = "/sd/Podcasts", folderName = "Podcasts")
        val hidden = testTrack("h", folderPath = "/sd/.private", folderName = ".private")
        val music = testTrack("m", folderPath = "/sd/Music", folderName = "Music")
        val ignored = testTrack("i", folderPath = "/sd/Ringtones", folderName = "Ringtones")
        assertTrue(
            TrackClassifier.includeInLibrary(music, ignoredFolders = emptySet(), showHidden = false, showPodcasts = false, showAudiobooks = false),
        )
        assertFalse(
            TrackClassifier.includeInLibrary(podcast, ignoredFolders = emptySet(), showHidden = false, showPodcasts = false, showAudiobooks = false),
        )
        assertTrue(
            TrackClassifier.includeInLibrary(podcast, ignoredFolders = emptySet(), showHidden = false, showPodcasts = true, showAudiobooks = false),
        )
        assertFalse(
            TrackClassifier.includeInLibrary(hidden, ignoredFolders = emptySet(), showHidden = false, showPodcasts = false, showAudiobooks = false),
        )
        assertTrue(
            TrackClassifier.includeInLibrary(hidden, ignoredFolders = emptySet(), showHidden = true, showPodcasts = false, showAudiobooks = false),
        )
        assertFalse(
            TrackClassifier.includeInLibrary(ignored, ignoredFolders = setOf("/sd/Ringtones"), showHidden = false, showPodcasts = false, showAudiobooks = false),
        )
        assertTrue(
            TrackClassifier.includeInLibrary(ignored, ignoredFolders = setOf("/sd/Ringtones"), showHidden = true, showPodcasts = false, showAudiobooks = false),
        )
    }
}

package com.example.music.domain.player

import com.example.music.domain.model.PlaybackSession
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.RepeatMode
import com.example.music.testTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestorePlaybackSessionTest {
    private val localA = testTrack("a")
    private val localB = testTrack("b")
    private val missing = testTrack("gone")
    private val web = testTrack("yt", source = PlaybackSource.WEB)

    @Test
    fun dropsUnplayableAndKeepsWantedIndex() {
        val session = PlaybackSession(
            queue = listOf(localA, missing, localB),
            originalQueue = listOf(localA, missing, localB),
            queueIndex = 2,
            positionMs = 12_000L,
            isShuffleEnabled = true,
            repeatMode = RepeatMode.ALL,
        )
        val restored = RestorePlaybackSession.restore(session) { it.id != "gone" }!!
        assertEquals(listOf("a", "b"), restored.playable.map { it.id })
        assertEquals(1, restored.index)
        assertEquals(12_000L, restored.positionMs)
        assertEquals(true, restored.isShuffleEnabled)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertEquals(listOf("a", "b"), restored.original.map { it.id })
    }

    @Test
    fun missingCurrentFallsBackToFirstPlayable() {
        val session = PlaybackSession(
            queue = listOf(missing, localA),
            originalQueue = listOf(missing, localA),
            queueIndex = 0,
        )
        val restored = RestorePlaybackSession.restore(session) { it.id != "gone" }!!
        assertEquals(listOf("a"), restored.playable.map { it.id })
        assertEquals(0, restored.index)
    }

    @Test
    fun emptyIfNothingPlayable() {
        val session = PlaybackSession(queue = listOf(missing), originalQueue = listOf(missing))
        assertNull(RestorePlaybackSession.restore(session) { false })
    }

    @Test
    fun webTracksStayPlayable() {
        val session = PlaybackSession(
            queue = listOf(web, localA),
            originalQueue = listOf(web, localA),
            queueIndex = 0,
        )
        val restored = RestorePlaybackSession.restore(session) { it.source == PlaybackSource.WEB || it.id == "a" }!!
        assertEquals("yt", restored.playable[restored.index].id)
    }
}

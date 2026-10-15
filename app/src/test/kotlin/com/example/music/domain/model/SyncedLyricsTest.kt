package com.example.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedLyricsTest {
    private val lyrics = SyncedLyrics(
        trackId = "1",
        lines = listOf(
            LyricsLine(0L, "uno"),
            LyricsLine(1_000L, "dos"),
            LyricsLine(3_000L, "tres"),
        ),
        plainText = "uno\ndos\ntres",
    )

    @Test
    fun lineAtPicksLatestStarted() {
        assertEquals("uno", lyrics.lineAt(0L)?.text)
        assertEquals("dos", lyrics.lineAt(1_500L)?.text)
        assertEquals("tres", lyrics.lineAt(9_000L)?.text)
    }

    @Test
    fun offsetShiftsTheActiveLine() {
        assertEquals("uno", lyrics.lineAt(1_200L, offsetMs = -500L)?.text)
        assertEquals("dos", lyrics.lineAt(600L, offsetMs = 500L)?.text)
        assertEquals(0, lyrics.lineIndexAt(200L, offsetMs = -500L))
        assertEquals(1, lyrics.lineIndexAt(600L, offsetMs = 500L))
    }

    @Test
    fun emptyLyricsHaveNoLine() {
        assertNull(SyncedLyrics("1", emptyList(), null).lineAt(10L))
        assertEquals(-1, SyncedLyrics("1", emptyList(), null).lineIndexAt(10L))
    }
}

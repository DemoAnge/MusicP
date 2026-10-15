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
    fun emptyLyricsHaveNoLine() {
        assertNull(SyncedLyrics("1", emptyList(), null).lineAt(10L))
    }
}

package com.example.music.domain.player

import com.example.music.domain.model.LyricsLine
import com.example.music.domain.model.SyncedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTimingOpsTest {
    @Test
    fun nudgeStepsHalfSecondAndClamps() {
        assertEquals(500L, LyricsTimingOps.nudge(0L, 1))
        assertEquals(-500L, LyricsTimingOps.nudge(0L, -1))
        assertEquals(LyricsTimingOps.MAX_MS, LyricsTimingOps.nudge(9_800L, 2))
        assertEquals(LyricsTimingOps.MIN_MS, LyricsTimingOps.nudge(-9_800L, -2))
    }

    @Test
    fun labelUsesCommaTenths() {
        assertEquals("0 s", LyricsTimingOps.label(0L))
        assertEquals("+0,5 s", LyricsTimingOps.label(500L))
        assertEquals("−0,5 s", LyricsTimingOps.label(-500L))
        assertEquals("+1 s", LyricsTimingOps.label(1_000L))
    }
}

class LyricsDisplayOpsTest {
    private val synced = SyncedLyrics(
        trackId = "1",
        lines = listOf(LyricsLine(0L, "hola"), LyricsLine(1_000L, "mundo")),
        plainText = "hola\nmundo",
    )

    @Test
    fun keepsSyncedAndIgnoresPlain() {
        assertTrue(LyricsDisplayOps.hasSynced(synced))
        assertEquals("", LyricsDisplayOps.unsyncedBody(synced))
        assertEquals("hola", LyricsDisplayOps.currentText(synced, 0L, 0L))
        assertEquals("mundo", LyricsDisplayOps.currentText(synced, 800L, 500L))
    }

    @Test
    fun readablePlainStripsTagsAndMetadata() {
        val raw = """
            [ti:Demo]
            [ar:Queen]
            [00:01.00]Is this the real life
            [00:02.50]Is this just fantasy
        """.trimIndent()
        assertEquals(
            "Is this the real life\nIs this just fantasy",
            LyricsDisplayOps.readablePlain(raw),
        )
        val unsynced = SyncedLyrics("1", emptyList(), raw)
        assertFalse(LyricsDisplayOps.hasSynced(unsynced))
        assertEquals(
            "Is this the real life\nIs this just fantasy",
            LyricsDisplayOps.unsyncedBody(unsynced),
        )
        assertEquals("Is this the real life", LyricsDisplayOps.currentText(unsynced, 0L, 0L))
    }

    @Test
    fun emptyLyrics() {
        assertNull(LyricsDisplayOps.currentText(null, 0L, 0L))
        assertEquals("", LyricsDisplayOps.readablePlain("   "))
    }
}

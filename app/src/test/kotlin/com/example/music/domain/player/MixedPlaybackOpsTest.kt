package com.example.music.domain.player

import com.example.music.domain.model.PlaybackSource
import com.example.music.testTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedPlaybackOpsTest {
    private val local = testTrack("a")
    private val web = testTrack("yt", source = PlaybackSource.WEB, mediaUri = "yt:abcdefghijk")

    @Test
    fun pauseRules() {
        assertTrue(MixedPlaybackOps.pauseLocalWhenPlaying(web))
        assertFalse(MixedPlaybackOps.pauseLocalWhenPlaying(local))
        assertTrue(MixedPlaybackOps.pauseWebWhenPlaying(local))
        assertFalse(MixedPlaybackOps.pauseWebWhenPlaying(web))
    }

    @Test
    fun mixedQueueAndExoPlaylist() {
        assertTrue(MixedPlaybackOps.isMixed(listOf(local, web)))
        assertFalse(MixedPlaybackOps.isMixed(listOf(local, testTrack("b"))))
        assertFalse(MixedPlaybackOps.isMixed(listOf(web)))
        assertTrue(MixedPlaybackOps.usesExoPlaylist(listOf(local, testTrack("b"))))
        assertFalse(MixedPlaybackOps.usesExoPlaylist(listOf(local, web)))
        assertFalse(MixedPlaybackOps.usesExoPlaylist(listOf(web)))
    }

    @Test
    fun skipCrossesSourcesByIndex() {
        assertEquals(1, MixedPlaybackOps.nextIndex(2, 0, wrap = true))
        assertEquals(0, MixedPlaybackOps.nextIndex(2, 1, wrap = true))
        assertNull(MixedPlaybackOps.nextIndex(2, 1, wrap = false))
        assertEquals(0, MixedPlaybackOps.previousIndex(2, 1))
        assertEquals(1, MixedPlaybackOps.previousIndex(2, 0))
    }
}

class WebBridgeOpsTest {
    @Test
    fun reconnectWindow() {
        assertFalse(WebBridgeOps.isFresh(0L, 10_000L))
        assertTrue(WebBridgeOps.isFresh(9_000L, 10_000L))
        assertFalse(WebBridgeOps.isFresh(1_000L, 10_000L))
    }

    @Test
    fun notifiesOnceAfterTabGone() {
        assertFalse(
            WebBridgeOps.shouldNotifyBridgeDown(
                lastClientAt = 1_000L,
                now = 5_000L,
                hasActiveTrack = true,
                alreadyNotified = false,
            ),
        )
        assertTrue(
            WebBridgeOps.shouldNotifyBridgeDown(
                lastClientAt = 1_000L,
                now = 14_000L,
                hasActiveTrack = true,
                alreadyNotified = false,
            ),
        )
        assertFalse(
            WebBridgeOps.shouldNotifyBridgeDown(
                lastClientAt = 1_000L,
                now = 14_000L,
                hasActiveTrack = true,
                alreadyNotified = true,
            ),
        )
    }

    @Test
    fun bridgeDownDoesNotSkipQueue() {
        assertFalse(WebBridgeOps.shouldAdvanceQueueOnFailure(com.example.music.domain.model.PlaybackUserMessage.BRIDGE_DOWN))
        assertFalse(WebBridgeOps.shouldAdvanceQueueOnFailure(com.example.music.domain.model.PlaybackUserMessage.YOUTUBE))
        assertFalse(WebBridgeOps.shouldAdvanceQueueOnFailure(com.example.music.domain.model.PlaybackUserMessage.YOUTUBE_EMBED))
        assertTrue(WebBridgeOps.shouldAdvanceQueueOnFailure(com.example.music.domain.model.PlaybackUserMessage.FILE_MISSING))
    }

    @Test
    fun braveSubtitle() {
        assertEquals("Suena en Brave", WebBridgeOps.sourceSubtitle(""))
        assertEquals("Suena en Brave", WebBridgeOps.sourceSubtitle("YouTube"))
        assertEquals("Queen · Suena en Brave", WebBridgeOps.sourceSubtitle("Queen"))
    }
}

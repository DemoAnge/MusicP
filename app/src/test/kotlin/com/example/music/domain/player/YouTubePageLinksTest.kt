package com.example.music.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePageLinksTest {
    @Test
    fun resultsUrlEncodesTheQuery() {
        assertEquals(
            "https://www.youtube.com/results?search_query=queen%20bohemian",
            YouTubePageLinks.resultsUrl("  queen bohemian "),
        )
        assertEquals("", YouTubePageLinks.resultsUrl("   "))
    }

    @Test
    fun watchUrlKeepsTheChosenVideo() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            YouTubePageLinks.watchUrl("dQw4w9WgXcQ"),
        )
        assertEquals("", YouTubePageLinks.watchUrl("no"))
        assertEquals("dQw4w9WgXcQ", YouTubePageLinks.videoIdOfMedia("yt:dQw4w9WgXcQ"))
        assertNull(YouTubePageLinks.videoIdOfMedia("ytsearch:queen"))
    }

    @Test
    fun inAppNavigationStaysOnYouTube() {
        assertTrue(YouTubePageLinks.allowInApp("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTubePageLinks.allowInApp("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YouTubePageLinks.allowInApp("https://www.youtube.com/results?search_query=queen"))
        assertFalse(YouTubePageLinks.allowInApp("intent://watch#Intent;scheme=vnd.youtube;end"))
        assertFalse(YouTubePageLinks.allowInApp("market://details?id=com.google.android.youtube"))
        assertFalse(YouTubePageLinks.allowInApp("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTubePageLinks.isWatch("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(YouTubePageLinks.isWatch("https://www.youtube.com/results?search_query=queen"))
    }
}

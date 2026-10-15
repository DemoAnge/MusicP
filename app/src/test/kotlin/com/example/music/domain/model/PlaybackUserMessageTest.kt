package com.example.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

class PlaybackUserMessageTest {
    @Test
    fun mapsLocalErrorCodes() {
        assertEquals(PlaybackUserMessage.PERMISSION, PlaybackUserMessage.local(PlaybackUserMessage.CODE_IO_NO_PERMISSION))
        assertEquals(PlaybackUserMessage.FILE_MISSING, PlaybackUserMessage.local(PlaybackUserMessage.CODE_IO_FILE_NOT_FOUND))
        assertEquals(PlaybackUserMessage.UNSUPPORTED, PlaybackUserMessage.local(PlaybackUserMessage.CODE_DECODER_INIT_FAILED))
        assertEquals(PlaybackUserMessage.GENERIC, PlaybackUserMessage.local(null, "weird exo guts"))
    }

    @Test
    fun mapsThrowables() {
        assertEquals(PlaybackUserMessage.FILE_MISSING, PlaybackUserMessage.fromThrowable(FileNotFoundException("x")))
        assertEquals(PlaybackUserMessage.PERMISSION, PlaybackUserMessage.fromThrowable(SecurityException("permission denied")))
    }

    @Test
    fun mapsWebAndBridge() {
        assertEquals(PlaybackUserMessage.YOUTUBE, PlaybackUserMessage.web("150"))
        assertEquals(PlaybackUserMessage.BRIDGE_DOWN, PlaybackUserMessage.web(PlaybackUserMessage.BRIDGE_DOWN))
        assertEquals(PlaybackUserMessage.BRIDGE_DOWN, PlaybackUserMessage.web("el puente se cayó"))
        assertTrue(PlaybackUserMessage.canReopenBridge(PlaybackUserMessage.YOUTUBE))
        assertTrue(PlaybackUserMessage.canReopenBridge(PlaybackUserMessage.BRIDGE_DOWN))
        assertFalse(PlaybackUserMessage.canReopenBridge(PlaybackUserMessage.FILE_MISSING))
        assertEquals(PlaybackUserMessage.ACTION_REOPEN, PlaybackUserMessage.actionLabel(PlaybackUserMessage.BRIDGE_DOWN))
        assertEquals(PlaybackUserMessage.ACTION_SETTINGS, PlaybackUserMessage.actionLabel(PlaybackUserMessage.PERMISSION))
        assertEquals(null, PlaybackUserMessage.actionLabel(PlaybackUserMessage.FILE_MISSING))
    }
}

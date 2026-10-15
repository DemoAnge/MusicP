package com.example.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    @Test
    fun wakeWordAcceptsVariants() {
        assertTrue(VoiceCommandParser.isWakeWord("música"))
        assertTrue(VoiceCommandParser.isWakeWord("oye música"))
        assertTrue(VoiceCommandParser.isWakeWord("OK musica"))
        assertFalse(VoiceCommandParser.isWakeWord("pon música"))
    }

    @Test
    fun parsesTransportCommands() {
        assertEquals(VoiceCommand.Play, VoiceCommandParser.parse("reproduce"))
        assertEquals(VoiceCommand.Pause, VoiceCommandParser.parse("pausa"))
        assertEquals(VoiceCommand.Pause, VoiceCommandParser.parse("detente"))
        assertEquals(VoiceCommand.Next, VoiceCommandParser.parse("siguiente"))
        assertEquals(VoiceCommand.Next, VoiceCommandParser.parse("adelantar"))
        assertEquals(VoiceCommand.Previous, VoiceCommandParser.parse("anterior"))
        assertEquals(VoiceCommand.Shuffle, VoiceCommandParser.parse("mezclar"))
        assertEquals(VoiceCommand.RepeatOne, VoiceCommandParser.parse("repetir 1"))
        assertEquals(VoiceCommand.RepeatAll, VoiceCommandParser.parse("repetir todos"))
        assertEquals(VoiceCommand.Queue, VoiceCommandParser.parse("la cola"))
        assertEquals(VoiceCommand.Exit, VoiceCommandParser.parse("salir"))
    }

    @Test
    fun unknownSpeechReturnsNull() {
        assertNull(VoiceCommandParser.parse("pon coldplay"))
        assertNull(VoiceCommandParser.parse(""))
    }
}

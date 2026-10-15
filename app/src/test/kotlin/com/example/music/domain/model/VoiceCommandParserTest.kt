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
        assertTrue(VoiceCommandParser.isWakeWord("música pon queen"))
        assertFalse(VoiceCommandParser.isWakeWord("pon música"))
        assertEquals("pon queen", VoiceCommandParser.stripWakePrefix("música pon queen"))
        assertEquals(VoiceCommand.PlayQuery("queen"), VoiceCommandParser.parse("música pon queen"))
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
    fun parsesPlayQueryLikeSleepAndSpeed() {
        assertEquals(VoiceCommand.PlayQuery("coldplay"), VoiceCommandParser.parse("pon coldplay"))
        assertEquals(VoiceCommand.PlayQuery("queen"), VoiceCommandParser.parse("pon a queen"))
        assertEquals(VoiceCommand.PlayQuery("bohemian rhapsody"), VoiceCommandParser.parse("quiero escuchar Bohemian Rhapsody"))
        assertEquals(VoiceCommand.LikeCurrent, VoiceCommandParser.parse("quiero esta"))
        assertEquals(VoiceCommand.LikeCurrent, VoiceCommandParser.parse("me gusta esta"))
        assertEquals(VoiceCommand.SetSleep(SleepOption.MINUTES_15), VoiceCommandParser.parse("temporizador 15"))
        assertEquals(VoiceCommand.SetSleep(SleepOption.MINUTES_30), VoiceCommandParser.parse("duerme en 30 minutos"))
        assertEquals(VoiceCommand.SetSleep(SleepOption.END_OF_TRACK), VoiceCommandParser.parse("temporizador fin de pista"))
        assertEquals(VoiceCommand.SpeedDown, VoiceCommandParser.parse("más despacio"))
        assertEquals(VoiceCommand.SpeedUp, VoiceCommandParser.parse("más rápido"))
        assertEquals(VoiceCommand.SetSpeed(1.2f), VoiceCommandParser.parse("a 1.2"))
        assertEquals(VoiceCommand.SetSpeed(1.2f), VoiceCommandParser.parse("a 1,2"))
        assertEquals(VoiceCommand.SetSpeed(1.2f), VoiceCommandParser.parse("a uno coma dos"))
        assertEquals(VoiceCommand.Play, VoiceCommandParser.parse("reproduce"))
    }

    @Test
    fun unknownSpeechReturnsNull() {
        assertNull(VoiceCommandParser.parse("pon"))
        assertNull(VoiceCommandParser.parse(""))
        assertNull(VoiceCommandParser.parse("receta de pasta"))
    }
}

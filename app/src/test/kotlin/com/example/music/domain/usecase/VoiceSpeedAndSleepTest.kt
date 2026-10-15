package com.example.music.domain.usecase

import com.example.music.domain.model.SleepOption
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSpeedAndSleepTest {
    @Test
    fun speedStepsStayInRange() {
        assertEquals(0.8f, HandleVoiceCommandUseCase.stepSpeed(1f, -1), 0.001f)
        assertEquals(1.25f, HandleVoiceCommandUseCase.stepSpeed(1f, 1), 0.001f)
        assertEquals(1.5f, HandleVoiceCommandUseCase.stepSpeed(1.5f, 1), 0.001f)
        assertEquals(0.8f, HandleVoiceCommandUseCase.stepSpeed(0.8f, -1), 0.001f)
    }

    @Test
    fun nearestSleepMapsSpokenMinutes() {
        assertEquals(SleepOption.MINUTES_15, SleepOption.nearestMinutes(15))
        assertEquals(SleepOption.MINUTES_15, SleepOption.nearestMinutes(10))
        assertEquals(SleepOption.MINUTES_30, SleepOption.nearestMinutes(30))
        assertEquals(SleepOption.MINUTES_60, SleepOption.nearestMinutes(90))
        assertEquals(SleepOption.OFF, SleepOption.nearestMinutes(0))
    }

    @Test
    fun speedSpokenUsesComma() {
        assertEquals("1,2", HandleVoiceCommandUseCase.speedSpoken(1.2f))
        assertEquals("1", HandleVoiceCommandUseCase.speedSpoken(1f))
        assertEquals("0,8", HandleVoiceCommandUseCase.speedSpoken(0.8f))
    }
}

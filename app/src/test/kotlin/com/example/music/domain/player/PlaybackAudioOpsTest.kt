package com.example.music.domain.player

import com.example.music.domain.library.TrackKind
import com.example.music.domain.model.EqPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioOpsTest {
    @Test
    fun spokenUsesSpokenSpeed() {
        assertEquals(1.25f, PlaybackAudioOps.speedFor(TrackKind.AUDIOBOOK, musicSpeed = 1f, spokenSpeed = 1.25f), 0.001f)
        assertEquals(1f, PlaybackAudioOps.speedFor(TrackKind.MUSIC, musicSpeed = 1f, spokenSpeed = 1.25f), 0.001f)
        assertEquals(1.5f, PlaybackAudioOps.speedFor(TrackKind.PODCAST, musicSpeed = 1f, spokenSpeed = 1.5f), 0.001f)
        assertEquals(0.8f, PlaybackAudioOps.speedFor(TrackKind.MUSIC, musicSpeed = 0.5f, spokenSpeed = 1.5f), 0.001f)
        assertEquals(1.5f, PlaybackAudioOps.speedFor(TrackKind.AUDIOBOOK, musicSpeed = 1f, spokenSpeed = 2f), 0.001f)
    }

    @Test
    fun linearGainIsUnityAtZeroDb() {
        assertEquals(1f, PlaybackAudioOps.linearGainFromDb(0f), 0.001f)
        assertTrue(PlaybackAudioOps.linearGainFromDb(-6f) < 0.55f)
        assertTrue(PlaybackAudioOps.linearGainFromDb(6f) > 1.9f)
        assertEquals(
            PlaybackAudioOps.linearGainFromDb(8f),
            PlaybackAudioOps.linearGainFromDb(20f),
            0.001f,
        )
    }

    @Test
    fun bassBoostsLowBands() {
        assertTrue(PlaybackAudioOps.eqGainMb(EqPreset.BASS, 80) > 0)
        assertEquals(0, PlaybackAudioOps.eqGainMb(EqPreset.OFF, 80))
        assertTrue(PlaybackAudioOps.eqGainMb(EqPreset.VOICE, 1_200) > 0)
        assertTrue(PlaybackAudioOps.eqGainMb(EqPreset.BRIGHT, 10_000) > 0)
        assertTrue(PlaybackAudioOps.eqGainMb(EqPreset.VOICE, 80) < 0)
    }
}

class ReplayGainParserTest {
    @Test
    fun readsTrackGain() {
        assertEquals(
            -3.5f,
            ReplayGainParser.parseDb("REPLAYGAIN_TRACK_GAIN = -3.50 dB")!!,
            0.01f,
        )
        assertEquals(
            5.2f,
            ReplayGainParser.parseFromBytes("foo REPLAYGAIN_TRACK_GAIN=+5.20 dB bar".toByteArray())!!,
            0.01f,
        )
    }

    @Test
    fun fallsBackToAlbumAndR128() {
        assertEquals(-2f, ReplayGainParser.parseDb("REPLAYGAIN_ALBUM_GAIN=-2.00 dB")!!, 0.01f)
        assertEquals(-1f, ReplayGainParser.parseDb("R128_TRACK_GAIN=-256")!!, 0.01f)
    }

    @Test
    fun missingOrHugeValues() {
        assertNull(ReplayGainParser.parseDb("no tags here"))
        assertEquals(8f, ReplayGainParser.parseDb("REPLAYGAIN_TRACK_GAIN=20.00 dB")!!, 0.01f)
    }
}

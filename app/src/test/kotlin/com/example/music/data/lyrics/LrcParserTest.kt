package com.example.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesTimedLinesAndSorts() {
        val lrc = """
            [ti:Demo]
            [00:02.5]segunda
            [00:01.00]primera
            [01:02.5]cuarta
            [00:03]tercera
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(listOf("primera", "segunda", "tercera", "cuarta"), lines.map { it.text })
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(2_500L, lines[1].timeMs)
        assertEquals(3_000L, lines[2].timeMs)
        assertEquals(62_500L, lines[3].timeMs)
    }

    @Test
    fun skipsEmptyTimedLines() {
        val lines = LrcParser.parse("[00:01.00]\n[00:02.00]hola")
        assertEquals(listOf("hola"), lines.map { it.text })
    }

    @Test
    fun plainTextDropsTimestamps() {
        val plain = LrcParser.plainText("[00:01.00]hola\n[00:02.00]mundo")
        assertEquals("hola\nmundo", plain)
    }

    @Test
    fun malformedInputIsEmpty() {
        assertTrue(LrcParser.parse("sin marcas").isEmpty())
    }
}

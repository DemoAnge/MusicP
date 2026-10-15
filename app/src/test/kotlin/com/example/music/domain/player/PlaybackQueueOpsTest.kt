package com.example.music.domain.player

import com.example.music.testTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueOpsTest {
    private val a = testTrack("a")
    private val b = testTrack("b")
    private val c = testTrack("c")

    @Test
    fun playKeepsOrderWithoutShuffle() {
        val load = PlaybackQueueOps.play(b, listOf(a, b, c), shuffle = false)!!
        assertEquals(listOf("a", "b", "c"), load.playQueue.map { it.id })
        assertEquals(1, load.playIndex)
        assertEquals(load.originalQueue, load.playQueue)
    }

    @Test
    fun playPutsCurrentFirstWhenShuffling() {
        val load = PlaybackQueueOps.play(c, listOf(a, b, c), shuffle = true)!!
        assertEquals("c", load.playQueue.first().id)
        assertEquals(0, load.playIndex)
        assertEquals(setOf("a", "b", "c"), load.playQueue.map { it.id }.toSet())
        assertEquals(listOf("a", "b", "c"), load.originalQueue.map { it.id })
    }

    @Test
    fun playNextOnEmptyStartsFresh() {
        val mutation = PlaybackQueueOps.playNext(a, emptyList(), emptyList(), currentId = null)!!
        assertTrue(mutation.startFresh)
        assertEquals(listOf("a"), mutation.queue.map { it.id })
        assertEquals(0, mutation.insertIndex)
    }

    @Test
    fun playNextInsertsAfterCurrent() {
        val mutation = PlaybackQueueOps.playNext(c, listOf(a, b), listOf(a, b), currentId = "a")!!
        assertEquals(listOf("a", "c", "b"), mutation.queue.map { it.id })
        assertEquals(0, mutation.currentIndex)
        assertEquals(1, mutation.insertIndex)
        assertEquals(listOf("a", "c", "b"), mutation.originalQueue.map { it.id })
    }

    @Test
    fun playNextManyKeepsIncomingOrder() {
        val mutation = PlaybackQueueOps.playNext(listOf(b, c), listOf(a), listOf(a), currentId = "a")!!
        assertEquals(listOf("a", "b", "c"), mutation.queue.map { it.id })
        assertEquals(listOf("a", "b", "c"), mutation.originalQueue.map { it.id })
        assertEquals(1, mutation.insertIndex)
    }

    @Test
    fun playNextMovesExistingTrack() {
        val mutation = PlaybackQueueOps.playNext(c, listOf(a, c, b), listOf(a, c, b), currentId = "a")!!
        assertTrue(mutation.alreadyPresent)
        assertEquals(listOf("a", "c", "b"), mutation.queue.map { it.id })
    }

    @Test
    fun addSkipsDuplicates() {
        val mutation = PlaybackQueueOps.add(listOf(b, testTrack("d")), listOf(a, b), listOf(a, b), currentEmpty = false)!!
        assertEquals(listOf("a", "b", "d"), mutation.queue.map { it.id })
        assertEquals(listOf("d"), mutation.added.map { it.id })
        assertEquals(2, mutation.appendAt)
    }

    @Test
    fun addOnEmptyStartsFresh() {
        val mutation = PlaybackQueueOps.add(listOf(a, b), emptyList(), emptyList(), currentEmpty = true)!!
        assertTrue(mutation.startFresh)
        assertEquals(listOf("a", "b"), mutation.queue.map { it.id })
    }

    @Test
    fun moveUpdatesCurrentIndex() {
        val mutation = PlaybackQueueOps.move(listOf(a, b, c), fromIndex = 2, toIndex = 0, currentId = "c", fallbackIndex = 2)!!
        assertEquals(listOf("c", "a", "b"), mutation.queue.map { it.id })
        assertEquals(0, mutation.currentIndex)
        assertEquals(listOf("c", "a", "b"), mutation.originalQueue.map { it.id })
    }

    @Test
    fun moveWhileShuffledKeepsOriginalOrder() {
        val mutation = PlaybackQueueOps.move(
            queue = listOf(c, a, b),
            fromIndex = 2,
            toIndex = 1,
            currentId = "c",
            fallbackIndex = 0,
            originalQueue = listOf(a, b, c),
            shuffleEnabled = true,
        )!!
        assertEquals(listOf("c", "b", "a"), mutation.queue.map { it.id })
        assertEquals(listOf("a", "b", "c"), mutation.originalQueue.map { it.id })
    }

    @Test
    fun removeKeepsPlayingWhenOtherTracksGo() {
        val result = PlaybackQueueOps.remove(setOf("b"), listOf(a, b, c), listOf(a, b, c), currentId = "a", queueIndex = 0)
        val kept = result as RemoveQueueMutation.KeptPlaying
        assertEquals(listOf("a", "c"), kept.remaining.map { it.id })
        assertEquals(0, kept.newIndex)
    }

    @Test
    fun removeCurrentJumpsToNext() {
        val result = PlaybackQueueOps.remove(setOf("b"), listOf(a, b, c), listOf(a, b, c), currentId = "b", queueIndex = 1)
        val jump = result as RemoveQueueMutation.JumpTo
        assertEquals(listOf("a", "c"), jump.remaining.map { it.id })
        assertEquals(1, jump.nextIndex)
    }

    @Test
    fun removeLastClearsQueue() {
        val result = PlaybackQueueOps.remove(setOf("a"), listOf(a), listOf(a), currentId = "a", queueIndex = 0)
        assertEquals(RemoveQueueMutation.Cleared, result)
    }

    @Test
    fun videoTrackIsRejected() {
        assertNull(PlaybackQueueOps.play(testTrack("v", isVideo = true), emptyList(), shuffle = false))
        assertNull(PlaybackQueueOps.playNext(testTrack("v", isVideo = true), listOf(a), listOf(a), "a"))
        assertNull(PlaybackQueueOps.add(listOf(testTrack("v", isVideo = true)), listOf(a), listOf(a), false))
    }
}

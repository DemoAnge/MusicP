package com.example.music.domain.player

import com.example.music.domain.model.Track

data class PlayLoad(
    val originalQueue: List<Track>,
    val playQueue: List<Track>,
    val playIndex: Int,
)

data class PlayNextMutation(
    val startFresh: Boolean,
    val queue: List<Track>,
    val originalQueue: List<Track>,
    val currentIndex: Int,
    val insertIndex: Int,
    val alreadyPresent: Boolean,
)

data class AddToQueueMutation(
    val startFresh: Boolean,
    val queue: List<Track>,
    val originalQueue: List<Track>,
    val added: List<Track>,
    val appendAt: Int,
)

data class MoveQueueMutation(
    val queue: List<Track>,
    val originalQueue: List<Track>,
    val currentIndex: Int,
    val fromIndex: Int,
    val toIndex: Int,
)

sealed interface RemoveQueueMutation {
    data object NoOp : RemoveQueueMutation
    data class KeptPlaying(
        val remaining: List<Track>,
        val originalQueue: List<Track>,
        val newIndex: Int,
    ) : RemoveQueueMutation
    data object Cleared : RemoveQueueMutation
    data class JumpTo(
        val remaining: List<Track>,
        val originalQueue: List<Track>,
        val nextIndex: Int,
    ) : RemoveQueueMutation
}

object PlaybackQueueOps {
    fun play(track: Track, queue: List<Track>, shuffle: Boolean): PlayLoad? {
        if (track.isVideo) return null
        val original = (if (queue.isEmpty()) listOf(track) else queue)
            .filter { !it.isVideo }
            .distinctBy { it.id }
        if (original.isEmpty()) return null
        val index = original.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val playQueue = if (shuffle) {
            listOf(original[index]) + original.filterIndexed { i, _ -> i != index }.shuffled()
        } else {
            original
        }
        val playIndex = playQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        return PlayLoad(originalQueue = original, playQueue = playQueue, playIndex = playIndex)
    }

    fun playNext(
        track: Track,
        queue: List<Track>,
        originalQueue: List<Track>,
        currentId: String?,
    ): PlayNextMutation? = playNext(listOf(track), queue, originalQueue, currentId)

    fun playNext(
        tracks: List<Track>,
        queue: List<Track>,
        originalQueue: List<Track>,
        currentId: String?,
    ): PlayNextMutation? {
        val incoming = tracks.filter { !it.isVideo }.distinctBy { it.id }
        if (incoming.isEmpty()) return null
        if (currentId == null || queue.isEmpty()) {
            return PlayNextMutation(
                startFresh = true,
                queue = incoming,
                originalQueue = incoming,
                currentIndex = 0,
                insertIndex = 0,
                alreadyPresent = false,
            )
        }
        val incomingIds = incoming.map { it.id }.toHashSet()
        val without = queue.filterNot { it.id in incomingIds }
        val currentIndex = without.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val insertIndex = (currentIndex + 1).coerceIn(0, without.size)
        val nextQueue = without.toMutableList().apply { addAll(insertIndex, incoming) }
        val originalWithout = originalQueue.filterNot { it.id in incomingIds }
        val originalAnchor = originalWithout.indexOfFirst { it.id == currentId }
        val original = originalWithout.toMutableList().apply {
            val at = if (originalAnchor >= 0) originalAnchor + 1 else size
            addAll(at, incoming)
        }
        val alreadyPresent = incoming.all { track -> queue.any { it.id == track.id } }
        val newCurrent = nextQueue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        return PlayNextMutation(
            startFresh = false,
            queue = nextQueue,
            originalQueue = original,
            currentIndex = newCurrent,
            insertIndex = insertIndex,
            alreadyPresent = alreadyPresent,
        )
    }

    fun add(
        tracks: List<Track>,
        queue: List<Track>,
        originalQueue: List<Track>,
        currentEmpty: Boolean,
    ): AddToQueueMutation? {
        val incoming = tracks.filter { !it.isVideo }.distinctBy { it.id }
        if (incoming.isEmpty()) return null
        if (currentEmpty || queue.isEmpty()) {
            return AddToQueueMutation(
                startFresh = true,
                queue = incoming,
                originalQueue = incoming,
                added = incoming,
                appendAt = 0,
            )
        }
        val existing = queue.map { it.id }.toHashSet()
        val toAdd = incoming.filter { it.id !in existing }
        if (toAdd.isEmpty()) return null
        val addedIds = toAdd.map { it.id }.toHashSet()
        return AddToQueueMutation(
            startFresh = false,
            queue = queue + toAdd,
            originalQueue = originalQueue.filterNot { it.id in addedIds } + toAdd,
            added = toAdd,
            appendAt = queue.size,
        )
    }

    fun move(
        queue: List<Track>,
        fromIndex: Int,
        toIndex: Int,
        currentId: String?,
        fallbackIndex: Int,
        originalQueue: List<Track> = queue,
        shuffleEnabled: Boolean = false,
    ): MoveQueueMutation? {
        if (fromIndex !in queue.indices) return null
        val target = toIndex.coerceIn(0, queue.lastIndex)
        if (fromIndex == target) return null
        val next = queue.toMutableList()
        val item = next.removeAt(fromIndex)
        next.add(target, item)
        val newIndex = currentId?.let { id -> next.indexOfFirst { it.id == id } } ?: fallbackIndex
        val original = if (shuffleEnabled) {
            originalQueue.ifEmpty { queue }
        } else {
            next
        }
        return MoveQueueMutation(
            queue = next,
            originalQueue = original,
            currentIndex = newIndex.coerceAtLeast(0),
            fromIndex = fromIndex,
            toIndex = target,
        )
    }

    fun remove(
        trackIds: Set<String>,
        queue: List<Track>,
        originalQueue: List<Track>,
        currentId: String?,
        queueIndex: Int,
    ): RemoveQueueMutation {
        if (trackIds.isEmpty()) return RemoveQueueMutation.NoOp
        val original = originalQueue.filter { it.id !in trackIds }
        val remaining = queue.filter { it.id !in trackIds }
        val currentDeleted = currentId != null && currentId in trackIds
        if (!currentDeleted) {
            val newIndex = remaining.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            return RemoveQueueMutation.KeptPlaying(remaining, original, newIndex)
        }
        if (remaining.isEmpty()) return RemoveQueueMutation.Cleared
        val next = queue.drop(queueIndex + 1).firstOrNull { it.id !in trackIds }
            ?: queue.take(queueIndex).firstOrNull { it.id !in trackIds }
            ?: remaining.first()
        val nextIndex = remaining.indexOfFirst { it.id == next.id }.coerceAtLeast(0)
        return RemoveQueueMutation.JumpTo(remaining, original, nextIndex)
    }
}

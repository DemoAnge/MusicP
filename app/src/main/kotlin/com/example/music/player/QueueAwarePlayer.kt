package com.example.music.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Expone play/pausa y saltos a la notificación y a la pantalla de bloqueo.
 * La cola vive en [PlayerCoordinator], no en el ExoPlayer.
 */
class QueueAwarePlayer(
    player: Player,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(COMMAND_PLAY_PAUSE)
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(COMMAND_GET_METADATA)
            .build()
    }

    override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return when (command) {
            COMMAND_PLAY_PAUSE,
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_GET_CURRENT_MEDIA_ITEM,
            COMMAND_GET_METADATA,
            -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun seekToNext() = onSkipNext()

    override fun seekToNextMediaItem() = onSkipNext()

    override fun seekToPrevious() = onSkipPrevious()

    override fun seekToPreviousMediaItem() = onSkipPrevious()

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true
}

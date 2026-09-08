package com.example.music.domain.usecase

import com.example.music.player.IPlayerService
import javax.inject.Inject

class ObservePlayerStateUseCase @Inject constructor(
    private val playerService: IPlayerService,
) {
    operator fun invoke() = playerService.state
}

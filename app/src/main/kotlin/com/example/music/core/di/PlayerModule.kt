package com.example.music.core.di

import com.example.music.player.IPlayerService
import com.example.music.player.PlayerCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    @Singleton
    abstract fun bindPlayerService(impl: PlayerCoordinator): IPlayerService
}

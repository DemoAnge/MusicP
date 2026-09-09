package com.example.music

import android.app.Application
import com.example.music.core.CrashGuard
import com.example.music.widget.PlaybackWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MusicApplication : Application() {

    @Inject
    lateinit var widgetUpdater: PlaybackWidgetUpdater

    override fun onCreate() {
        CrashGuard.install()
        super.onCreate()
        widgetUpdater.start()
    }
}

package com.example.music

import android.app.Application
import com.example.music.core.CrashGuard
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicApplication : Application() {
    override fun onCreate() {
        CrashGuard.install()
        super.onCreate()
    }
}

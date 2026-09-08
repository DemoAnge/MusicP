package com.example.music.core

import android.os.Looper
import android.util.Log

object CrashGuard {
    private const val TAG = "MusicCrashGuard"

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is VirtualMachineError) {
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            Log.e(TAG, "Excepción no capturada en ${thread.name}", throwable)
        }
        val main = Looper.getMainLooper() ?: return
        if (Looper.myLooper() == main) {
            swallowMainLoop()
        }
    }

    private fun swallowMainLoop() {
        // Si un toque o composición lanza, se registra y el loop principal sigue.
        android.os.Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    return@post
                } catch (error: VirtualMachineError) {
                    throw error
                } catch (t: Throwable) {
                    Log.e(TAG, "Excepción en el hilo principal", t)
                }
            }
        }
    }

    fun <T> run(default: T, block: () -> T): T {
        return try {
            block()
        } catch (error: VirtualMachineError) {
            throw error
        } catch (t: Throwable) {
            Log.w(TAG, t)
            default
        }
    }

    fun run(block: () -> Unit) {
        try {
            block()
        } catch (error: VirtualMachineError) {
            throw error
        } catch (t: Throwable) {
            Log.w(TAG, t)
        }
    }
}

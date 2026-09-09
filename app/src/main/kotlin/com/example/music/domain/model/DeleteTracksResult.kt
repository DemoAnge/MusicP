package com.example.music.domain.model

import android.content.IntentSender

sealed class DeleteTracksResult {
    data class Deleted(val count: Int) : DeleteTracksResult()
    data class NeedConsent(val intentSender: IntentSender) : DeleteTracksResult()
    data object Empty : DeleteTracksResult()
    data class Error(val message: String) : DeleteTracksResult()
}

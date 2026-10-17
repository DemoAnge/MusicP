package com.example.music.ui.search

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class YouTubeSessionViewModel @Inject constructor(
    session: InAppYouTubeSearch,
) : ViewModel() {
    val pageUrl: StateFlow<String> = session.pageUrl
}

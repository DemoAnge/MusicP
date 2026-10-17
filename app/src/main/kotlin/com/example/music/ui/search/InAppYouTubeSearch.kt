package com.example.music.ui.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Consulta pendiente cuando la biblioteca pide ver YouTube dentro de Buscar. */
@Singleton
class InAppYouTubeSearch @Inject constructor() {
    private val _query = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    fun submit(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) _query.value = trimmed
    }

    fun consume() {
        _query.value = null
    }

    private val _pageUrl = MutableStateFlow("")
    val pageUrl: StateFlow<String> = _pageUrl.asStateFlow()

    fun setPage(url: String) {
        _pageUrl.value = url
    }
}

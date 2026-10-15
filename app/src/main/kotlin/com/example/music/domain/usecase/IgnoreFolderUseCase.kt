package com.example.music.domain.usecase

import com.example.music.domain.repository.LibraryPrefsRepository
import javax.inject.Inject

class IgnoreFolderUseCase @Inject constructor(
    private val prefs: LibraryPrefsRepository,
) {
    suspend fun ignore(folderPath: String): Boolean {
        val path = folderPath.trim()
        if (path.isEmpty()) return false
        prefs.setFolderIgnored(path, true)
        return true
    }

    suspend fun restore(folderPath: String) {
        val path = folderPath.trim()
        if (path.isEmpty()) return
        prefs.setFolderIgnored(path, false)
    }
}

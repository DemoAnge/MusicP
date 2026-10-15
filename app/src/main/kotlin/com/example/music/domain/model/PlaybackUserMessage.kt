package com.example.music.domain.model

import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import java.security.AccessControlException

object PlaybackUserMessage {
    const val PERMISSION =
        "Sin permiso para leer este archivo. Actívalo en Ajustes del sistema."
    const val FILE_MISSING = "El archivo ya no está en el teléfono."
    const val UNSUPPORTED = "Este formato no se puede reproducir."
    const val BRIDGE_DOWN =
        "El puente con Brave se cayó. Toca para reabrir puente."
    const val YOUTUBE =
        "YouTube no pudo reproducir. En Shields permite YouTube o toca Reabrir."
    const val GENERIC = "No se pudo reproducir."
    const val ACTION_REOPEN = "Reabrir"
    const val ACTION_SETTINGS = "Ajustes"

    fun local(errorCode: Int?, raw: String? = null): String {
        return when (errorCode) {
            CODE_IO_NO_PERMISSION, CODE_IO_CLEARTEXT_NOT_PERMITTED -> PERMISSION
            CODE_IO_FILE_NOT_FOUND -> FILE_MISSING
            CODE_PARSING_CONTAINER_MALFORMED,
            CODE_PARSING_MANIFEST_MALFORMED,
            CODE_PARSING_CONTAINER_UNSUPPORTED,
            CODE_PARSING_MANIFEST_UNSUPPORTED,
            CODE_DECODER_INIT_FAILED,
            CODE_DECODER_QUERY_FAILED,
            CODE_DECODING_FAILED,
            CODE_AUDIO_TRACK_INIT_FAILED,
            -> UNSUPPORTED
            else -> fromThrowableMessage(raw)
        }
    }

    fun web(raw: String? = null): String {
        val text = raw.orEmpty()
        if (text == BRIDGE_DOWN || text.contains("puente", ignoreCase = true)) return BRIDGE_DOWN
        return YOUTUBE
    }

    fun fromThrowable(error: Throwable): String = when (error) {
        is SecurityException, is AccessControlException -> PERMISSION
        is FileNotFoundException, is NoSuchFileException -> FILE_MISSING
        else -> fromThrowableMessage(error.message)
    }

    fun canReopenBridge(message: String): Boolean =
        actionLabel(message) == ACTION_REOPEN

    fun actionLabel(message: String): String? = when (message) {
        BRIDGE_DOWN, YOUTUBE -> ACTION_REOPEN
        PERMISSION -> ACTION_SETTINGS
        else -> null
    }

    private fun fromThrowableMessage(raw: String?): String {
        val text = raw.orEmpty().lowercase()
        return when {
            "permission" in text || "eacces" in text || "security" in text -> PERMISSION
            "not found" in text || "enoent" in text || "no such file" in text ||
                "filenotfound" in text -> FILE_MISSING
            "unrecognized" in text || "unsupported" in text || "malformed" in text -> UNSUPPORTED
            else -> GENERIC
        }
    }

    // androidx.media3.common.PlaybackException (copiados para tests JVM)
    const val CODE_IO_NETWORK_CONNECTION_FAILED = 2001
    const val CODE_IO_FILE_NOT_FOUND = 2005
    const val CODE_IO_NO_PERMISSION = 2006
    const val CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007
    const val CODE_PARSING_CONTAINER_MALFORMED = 3001
    const val CODE_PARSING_MANIFEST_MALFORMED = 3002
    const val CODE_PARSING_CONTAINER_UNSUPPORTED = 3003
    const val CODE_PARSING_MANIFEST_UNSUPPORTED = 3004
    const val CODE_DECODER_INIT_FAILED = 4001
    const val CODE_DECODER_QUERY_FAILED = 4002
    const val CODE_DECODING_FAILED = 4003
    const val CODE_AUDIO_TRACK_INIT_FAILED = 5001
}

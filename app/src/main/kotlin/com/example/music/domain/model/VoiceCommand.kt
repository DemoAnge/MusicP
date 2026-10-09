package com.example.music.domain.model

sealed interface VoiceCommand {
    data object Pause : VoiceCommand
    data object Resume : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object Rewind : VoiceCommand
    data class PlayQuery(val query: String) : VoiceCommand
}

object VoiceCommandParser {
    private val playPrefix = Regex(
        "^(?:ponme|pon|reproduce|busca|play|quiero o[ií]r|pon la canci[oó]n)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): VoiceCommand? {
        val text = raw.trim().lowercase()
            .replace(Regex("[¿?¡!]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isEmpty()) return null
        return when {
            text.matches(Regex("^(pausa|pause|para|stop|det[eé]n(?:te)?|para la m[uú]sica)$")) ->
                VoiceCommand.Pause
            text.matches(Regex("^(play|sigue|contin[uú]a|continuar|reanuda|reproduce)$")) ->
                VoiceCommand.Resume
            text.matches(Regex("^(siguiente|next|skip|otra|adelanta)$")) ->
                VoiceCommand.Next
            text.matches(Regex("^(atr[aá]s|anterior|previous)$")) ->
                VoiceCommand.Previous
            text.matches(Regex("^(retrocede|rewind|diez segundos|10 segundos)$")) ->
                VoiceCommand.Rewind
            else -> {
                val match = playPrefix.find(text) ?: return null
                val query = match.groupValues[1].trim()
                query.takeIf { it.isNotEmpty() }?.let { VoiceCommand.PlayQuery(it) }
            }
        }
    }
}

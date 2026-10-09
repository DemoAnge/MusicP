package com.example.music.domain.model

sealed interface VoiceCommand {
    data object Play : VoiceCommand
    data object Pause : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object Shuffle : VoiceCommand
    data object RepeatOne : VoiceCommand
    data object RepeatAll : VoiceCommand
    data object Queue : VoiceCommand
    data object Exit : VoiceCommand
}

data class VoiceResult(
    val message: String,
    val openQueue: Boolean = false,
    val exit: Boolean = false,
)

object VoiceCommandParser {
    private val wake = Regex("^(?:oye |ok |okay |hey |activar )?m[uú]sica$")
    private val exit = Regex("^(salir|exit|cierra|cerrar|cierra la app|cierra la aplicaci[oó]n)$")
    private val queue = Regex("^(cola|queue|lista|la cola)$")
    private val shuffle = Regex("^(mezclar|shuffle|aleatorio|activar aleatorio)$")
    private val repeatOne = Regex("^(repetir (uno|1|una)|repeat one)$")
    private val repeatAll = Regex("^(repetir (todos|todo|all)|repeat all)$")
    private val next = Regex("^(siguiente|next|adelantar|adelante|skip)$")
    private val previous = Regex("^(retroceder|atr[aá]s|anterior|previous)$")
    private val pause = Regex("^(pausa|pause|para|stop|det[eé]n(?:te)?)$")
    private val play = Regex("^(play|reproduce|reproducir|contin[uú]a|continuar|reanuda|sigue)$")
    private val noise = Regex("[¿?¡!.,]")
    private val spaces = Regex("\\s+")

    fun normalize(raw: String): String = raw.trim().lowercase()
        .replace(noise, "")
        .replace(spaces, " ")
        .trim()

    fun isWakeWord(raw: String): Boolean = wake.matches(normalize(raw))

    fun parse(raw: String): VoiceCommand? {
        val text = normalize(raw)
        if (text.isEmpty()) return null
        return when {
            exit.matches(text) -> VoiceCommand.Exit
            queue.matches(text) -> VoiceCommand.Queue
            shuffle.matches(text) -> VoiceCommand.Shuffle
            repeatOne.matches(text) -> VoiceCommand.RepeatOne
            repeatAll.matches(text) -> VoiceCommand.RepeatAll
            next.matches(text) -> VoiceCommand.Next
            previous.matches(text) -> VoiceCommand.Previous
            pause.matches(text) -> VoiceCommand.Pause
            play.matches(text) -> VoiceCommand.Play
            else -> null
        }
    }
}

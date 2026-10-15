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
    data class PlayQuery(val query: String) : VoiceCommand
    data object LikeCurrent : VoiceCommand
    data class SetSleep(val option: SleepOption) : VoiceCommand
    data object SpeedDown : VoiceCommand
    data object SpeedUp : VoiceCommand
    data class SetSpeed(val speed: Float) : VoiceCommand
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
    private val like = Regex(
        "^(quiero esta(?: canci[oó]n)?|me gusta(?: esta)?|esta me gusta|gu[aá]rdala|m[aá]rcala|favorita)$",
    )
    private val sleepOff = Regex(
        "^(?:apaga(?:r)?(?: el)? temporizador|cancela(?:r)?(?: el)? temporizador|sin temporizador)$",
    )
    private val sleepEnd = Regex(
        "^(?:temporizador |du[eé]rme(?:me)? )?(?:fin de(?: la)? pista|al final(?: de(?: la)? pista)?)$",
    )
    private val sleepMinutes = Regex(
        "^(?:temporizador|timer|du[eé]rme(?:me)?(?: en)?|apaga(?:te)? en) (\\d{1,2})(?: minutos?)?$",
    )
    private val speedDown = Regex("^(m[aá]s despacio|m[aá]s lento|baja(?:r)?(?: la)? velocidad)$")
    private val speedUp = Regex("^(m[aá]s r[aá]pido|sube(?:r)?(?: la)? velocidad)$")
    private val speedAbs = Regex("^(?:a |velocidad |pon(?:me)? a )(.+)$")
    private val playQuery = Regex(
        "^(?:pon(?:me)?(?: a)?|reproduce(?:r)?|play|quiero escuchar|busca(?:r)?|escucha) (.+)$",
    )
    private val noise = Regex("[¿?¡!]")
    private val spaces = Regex("\\s+")
    private val speedWords = mapOf(
        "cero coma ocho" to 0.8f,
        "cero punto ocho" to 0.8f,
        "uno" to 1f,
        "normal" to 1f,
        "uno coma dos" to 1.2f,
        "uno punto dos" to 1.2f,
        "uno coma veinticinco" to 1.25f,
        "uno veinticinco" to 1.25f,
        "uno coma cinco" to 1.5f,
        "uno punto cinco" to 1.5f,
    )

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
            like.matches(text) -> VoiceCommand.LikeCurrent
            sleepOff.matches(text) -> VoiceCommand.SetSleep(SleepOption.OFF)
            sleepEnd.matches(text) -> VoiceCommand.SetSleep(SleepOption.END_OF_TRACK)
            sleepMinutes.matches(text) -> {
                val minutes = sleepMinutes.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: return null
                VoiceCommand.SetSleep(SleepOption.nearestMinutes(minutes))
            }
            speedDown.matches(text) -> VoiceCommand.SpeedDown
            speedUp.matches(text) -> VoiceCommand.SpeedUp
            parseAbsoluteSpeed(text) != null -> VoiceCommand.SetSpeed(parseAbsoluteSpeed(text)!!)
            playQuery.matches(text) -> {
                val query = playQuery.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                query.takeIf { it.isNotEmpty() }?.let(VoiceCommand::PlayQuery)
            }
            play.matches(text) -> VoiceCommand.Play
            else -> null
        }
    }

    private fun parseAbsoluteSpeed(text: String): Float? {
        val match = speedAbs.find(text) ?: return null
        val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        speedWords[raw]?.let { return it }
        val number = raw.removeSuffix("x").trim().replace(',', '.').toFloatOrNull() ?: return null
        if (number < 0.7f || number > 1.6f) return null
        return number.coerceIn(0.8f, 1.5f)
    }
}

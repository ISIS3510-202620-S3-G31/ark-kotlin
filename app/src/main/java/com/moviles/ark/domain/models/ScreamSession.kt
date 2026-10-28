package com.moviles.ark.domain.models

import java.util.Date

//una sesion del scream tank: solo se guardan numeros, nunca el audio
data class ScreamSession(
    //el grito mas fuerte de la sesion, de 0 a 100 (DecibelMeter)
    val peakLevel: Int,
    //segundos que el microfono estuvo escuchando, sin contar las pausas
    val durationSeconds: Int,
    //cuando termino la sesion, en milisegundos
    val timestamp: Long
) {
    //reglas de negocio: solo cuenta si el microfono alcanzo a escuchar
    fun isComplete(): Boolean {
        return durationSeconds >= MIN_DURATION_SECONDS
    }

    //la misma sesion como interaccion con la herramienta; intensity = pico de 0 a 100
    fun toInteraction(userId: String): ToolInteractionModel {
        return ToolInteractionModel(
            userId = userId,
            toolId = TOOL_ID,
            duration = durationSeconds,
            interactionType = INTERACTION_TYPE,
            intensity = peakLevel.coerceIn(0, DecibelMeter.MAX_LEVEL),
            complete = isComplete(),
            value = peakLevel,
            timestamp = Date(timestamp)
        )
    }

    companion object {
        //id de la herramienta en el catalogo (Tool.id)
        const val TOOL_ID = "scream_tank"

        //la herramienta se usa con la voz
        const val INTERACTION_TYPE = "VOICE"

        const val MIN_DURATION_SECONDS = 1
    }
}

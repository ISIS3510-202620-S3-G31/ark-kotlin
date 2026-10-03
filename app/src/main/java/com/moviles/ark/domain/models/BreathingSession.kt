package com.moviles.ark.domain.models

import java.util.Date

//una sesion de respiracion guiada que el usuario hizo en Custom breathing (#82)
//se guarda en room (breathing_sessions) y tambien como ToolInteraction, que la usan estadisticas, insights y recomendaciones
data class BreathingSession(
    //ciclos completos (inhalar, sostener y exhalar)
    val completedCycles: Int,
    //segundos que el temporizador estuvo corriendo, sin contar las pausas
    val durationSeconds: Int,
    //cuando termino la sesion, en milisegundos
    val timestamp: Long,
    //patron usado en segundos, por ejemplo "4-7-8"
    val pattern: String = ""
) {
    //reglas de negocio: solo cuenta como sesion si termino al menos un ciclo y el temporizador corrio
    fun isComplete(): Boolean {
        return completedCycles >= MIN_COMPLETED_CYCLES && durationSeconds > 0
    }

    //la misma sesion como interaccion con la herramienta; value = ciclos completos
    fun toInteraction(userId: String): ToolInteraction {
        return ToolInteraction(
            userId = userId,
            toolId = TOOL_ID,
            duration = durationSeconds,
            interactionType = INTERACTION_TYPE,
            complete = true,
            value = completedCycles,
            timestamp = Date(timestamp),
            description = if (pattern.isBlank()) "" else "pattern $pattern"
        )
    }

    companion object {
        //id de la herramienta en el catalogo (Tool.id)
        const val TOOL_ID = "custom_breathing"

        //la herramienta se usa tocando la pantalla
        const val INTERACTION_TYPE = "touch"

        const val MIN_COMPLETED_CYCLES = 1

        //patron legible a partir de las tres duraciones
        fun patternOf(inhaleSeconds: Int, holdSeconds: Int, exhaleSeconds: Int): String {
            return "$inhaleSeconds-$holdSeconds-$exhaleSeconds"
        }
    }
}

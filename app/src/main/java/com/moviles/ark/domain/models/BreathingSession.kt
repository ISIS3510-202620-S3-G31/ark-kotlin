package com.moviles.ark.domain.models

import java.util.Date

//una sesion de respiracion guiada que el usuario hizo en Custom breathing
//se guarda en room (breathing_sessions) y tambien como ToolInteractionModel, que la usan estadisticas y recomendaciones
data class BreathingSession(
    //ciclos completos (inhalar, sostener y exhalar)
    val completedCycles: Int,
    //segundos que el temporizador estuvo corriendo, sin contar las pausas
    val durationSeconds: Int,
    //cuando termino la sesion, en milisegundos
    val timestamp: Long,
    //patron usado en segundos, por ejemplo "4-7-8"
    val pattern: String = "",
    //true si el usuario toco Finish; false si salio de la herramienta (la abandono)
    val finishedByUser: Boolean = true
) {
    //reglas de negocio: cuenta como terminada si el usuario toco Finish despues de al menos un ciclo
    fun isComplete(): Boolean {
        return finishedByUser && canFinish(completedCycles) && durationSeconds > 0
    }

    //la misma sesion como interaccion con la herramienta; value = ciclos completos
    fun toInteraction(userId: String): ToolInteractionModel {
        return ToolInteractionModel(
            userId = userId,
            toolId = TOOL_ID,
            duration = durationSeconds,
            interactionType = INTERACTION_TYPE,
            intensity = completedCycles.coerceIn(1, 5),
            complete = isComplete(),
            value = completedCycles,
            timestamp = Date(timestamp),
            description = if (pattern.isBlank()) "" else "pattern $pattern"
        )
    }

    companion object {
        //id de la herramienta en el catalogo (ToolModel.id)
        const val TOOL_ID = "custom_breathing"

        //la herramienta se usa tocando la pantalla
        const val INTERACTION_TYPE = "TOUCH"

        const val MIN_COMPLETED_CYCLES = 1

        //el boton Finish se habilita desde el primer ciclo completo
        fun canFinish(completedCycles: Int): Boolean = completedCycles >= MIN_COMPLETED_CYCLES

        //patron legible a partir de las tres duraciones
        fun patternOf(
            inhaleSeconds: Int,
            holdSeconds: Int,
            exhaleSeconds: Int,
            holdPostExhaleSeconds: Int = 0
        ): String {
            return if (holdPostExhaleSeconds > 0) {
                "$inhaleSeconds-$holdSeconds-$exhaleSeconds-$holdPostExhaleSeconds"
            } else if (holdSeconds == 0) {
                "$inhaleSeconds-$exhaleSeconds"
            } else {
                "$inhaleSeconds-$holdSeconds-$exhaleSeconds"
            }
        }

        fun patternOf(inhaleSeconds: Int, holdSeconds: Int, exhaleSeconds: Int): String {
            return "$inhaleSeconds-$holdSeconds-$exhaleSeconds"
        }
    }
}

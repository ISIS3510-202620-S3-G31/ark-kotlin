package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.BreathingSessionEntity
import java.util.Date

//dto del documento users/{uid}/custom_breathing_sessions/{id} en firestore
//una sesion de Custom Breathing terminada (el usuario toco Finish despues de al menos un ciclo)
//no se llama breathing_sessions porque esa coleccion ya la usa la app de flutter con otros campos
//la extension "Stream Firestore to BigQuery" la copia a bigquery para las preguntas de negocio de respiracion
data class BreathingSessionDto(
    val userId: String = "",
    //cuando termino la sesion; firestore lo guarda como Timestamp
    val endedAt: Date = Date(0),
    //ciclos completos (BQ16)
    val completedCycles: Int = 0,
    //segundos que el temporizador estuvo corriendo, sin contar las pausas
    val durationSeconds: Int = 0,
    //opcion elegida: "BOX", "RELAXING", "QUICK_CALM" o "CUSTOM" (patron mas usado)
    val patternType: String = "",
    //true si se escucho musica en algun momento del ejercicio (sesiones con musica)
    val withMusic: Boolean = false,
    //segundos que sono la musica mientras el temporizador corria
    val musicSeconds: Int = 0,
    //modelo del celular (Build.MODEL); en bigquery separa el emulador de los celulares reales
    val deviceModel: String = ""
)

//de fila de room a dto; el userId es el del usuario con sesion, igual que en las interacciones
fun BreathingSessionEntity.toDto(userId: String, deviceModel: String): BreathingSessionDto {
    return BreathingSessionDto(
        userId = userId,
        endedAt = Date(timestamp),
        completedCycles = completedCycles,
        durationSeconds = durationSeconds,
        patternType = patternType,
        withMusic = musicSeconds > 0,
        musicSeconds = musicSeconds,
        deviceModel = deviceModel
    )
}

//id del documento en firestore: siempre el mismo para la misma sesion, asi subirla dos veces no la duplica
fun BreathingSessionEntity.documentId(): String = timestamp.toString()

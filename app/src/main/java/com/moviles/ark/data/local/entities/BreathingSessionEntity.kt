package com.moviles.ark.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.BreathingSession

//tabla de sesiones de respiracion terminadas: una fila cada vez que el usuario termina Custom breathing
//se guarda en el telefono, asi que funciona sin internet; el sync la sube a
//users/{uid}/custom_breathing_sessions en firestore (BQ16 y las demas preguntas de respiracion)
@Entity(tableName = "breathing_sessions")
data class BreathingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //ciclos completos (inhalar, sostener y exhalar)
    val completedCycles: Int,
    //segundos que el temporizador estuvo corriendo
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    //cuando termino, en milisegundos
    val timestamp: Long,
    //patron usado, por ejemplo "4-7-8"
    val pattern: String = "",
    //opcion elegida en el selector: "BOX", "RELAXING", "QUICK_CALM" o "CUSTOM"
    val patternType: String = "",
    //segundos que sono la musica mientras el temporizador corria
    val musicSeconds: Int = 0,
    //usuario que hizo la sesion; "anonymous" si no habia sesion iniciada
    val userId: String = "",
    //false mientras no se haya subido a firestore
    val isSynced: Boolean = false,
)

//de fila de la tabla a modelo de dominio
fun BreathingSessionEntity.toDomain(): BreathingSession {
    return BreathingSession(
        completedCycles = completedCycles,
        durationSeconds = durationSeconds,
        timestamp = timestamp,
        pattern = pattern,
        patternType = patternType,
        musicSeconds = musicSeconds,
    )
}

//de modelo de dominio a fila de la tabla
fun BreathingSession.toEntity(userId: String): BreathingSessionEntity {
    return BreathingSessionEntity(
        completedCycles = completedCycles,
        durationSeconds = durationSeconds,
        timestamp = timestamp,
        pattern = pattern,
        patternType = patternType,
        musicSeconds = musicSeconds,
        userId = userId,
    )
}

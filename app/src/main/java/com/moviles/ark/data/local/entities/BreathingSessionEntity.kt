package com.moviles.ark.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.BreathingSession

//tabla de sesiones de respiracion terminadas (#82): una fila cada vez que el usuario termina Custom breathing
//se guarda en el telefono, asi que funciona sin internet
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
)

//de fila de la tabla a modelo de dominio
fun BreathingSessionEntity.toDomain(): BreathingSession {
    return BreathingSession(
        completedCycles = completedCycles,
        durationSeconds = durationSeconds,
        timestamp = timestamp,
        pattern = pattern,
    )
}

//de modelo de dominio a fila de la tabla
fun BreathingSession.toEntity(): BreathingSessionEntity {
    return BreathingSessionEntity(
        completedCycles = completedCycles,
        durationSeconds = durationSeconds,
        timestamp = timestamp,
        pattern = pattern,
    )
}

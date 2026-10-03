package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.CheckInModel

//interfaz de dominio para operaciones de almacenamiento de check-in emocional
interface MoodRepository {
    suspend fun saveCheckIn(checkIn: CheckInModel): Result<Unit>
    //verifica si el usuario ya registro su check-in en el dia actual
    suspend fun hasCheckedInToday(): Result<Boolean>
    //obtiene el ultimo check-in registrado por el usuario
    suspend fun getLatestCheckIn(): Result<CheckInModel?>
    //fechas (en milisegundos) de los check-ins desde una fecha; la racha del perfil solo necesita los dias (#96)
    suspend fun getCheckInTimestampsSince(fromMillis: Long): Result<List<Long>>
}


package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.CheckInModel

//interfaz de dominio para operaciones de almacenamiento de check-in emocional
interface MoodRepository {
    suspend fun saveCheckIn(checkIn: CheckInModel): Result<Unit>
}

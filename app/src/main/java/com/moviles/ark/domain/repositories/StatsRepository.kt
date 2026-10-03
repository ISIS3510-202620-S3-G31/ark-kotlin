package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.StatsModel

//interfaz de dominio para consultar y persistir estadisticas de checkins
interface StatsRepository {
    suspend fun getUserStats(): Result<StatsModel>
    suspend fun saveUserStats(stats: StatsModel): Result<Unit>
}

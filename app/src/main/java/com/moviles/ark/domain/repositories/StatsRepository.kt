package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.StatsModel

//interfaz de dominio para consultar y persistir estadisticas de checkins
interface StatsRepository {
    suspend fun getUserStats(): Result<StatsModel>
    //calcula las estadisticas solo con lo guardado en room, sin red (#135)
    suspend fun getLocalStats(): Result<StatsModel>
    suspend fun saveUserStats(stats: StatsModel): Result<Unit>
}

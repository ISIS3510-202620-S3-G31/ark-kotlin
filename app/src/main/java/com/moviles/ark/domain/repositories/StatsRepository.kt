package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.StatsModel

//interfaz de dominio para consultar y persistir estadisticas de checkins
interface StatsRepository {
    suspend fun getUserStats(): Result<StatsModel>
    //ultimas estadisticas de firestore guardadas en el disco, o null si no hay; sin red solo se muestra esto
    suspend fun getCachedStats(): StatsModel?
    suspend fun saveUserStats(stats: StatsModel): Result<Unit>
    //registra una visita a la pantalla de estadisticas e incrementa el contador del dia (offline-first con sync a firestore)
    suspend fun recordStatsVisit(): Result<Int>
    //obtiene cuantas veces ha entrado el usuario en el dia actual
    suspend fun getTodayStatsVisitsCount(): Result<Int>
}

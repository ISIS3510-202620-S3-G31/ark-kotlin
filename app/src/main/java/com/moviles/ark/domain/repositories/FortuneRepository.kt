package com.moviles.ark.domain.repositories

import android.net.Uri
import com.moviles.ark.domain.models.FortuneCrackResult
import com.moviles.ark.domain.models.FortuneModel
import kotlinx.coroutines.flow.Flow

//interfaz del repositorio que orquesta galleta de la fortuna, favoritos y cache
interface FortuneRepository {
    //flujo reactivo de favoritos guardados en room
    fun observeFavorites(): Flow<List<FortuneModel>>

    //inicializa la cache en memoria cargando los ids de favoritos desde room
    suspend fun initCache()

    //obtiene las galletas recientes guardadas en disco lru
    suspend fun getRecentCookies(): List<FortuneModel>

    //agrega o quita de favoritos en room, memoria y la nube
    suspend fun toggleFavorite(fortune: FortuneModel): Boolean

    //obtiene los intentos diarios disponibles hoy
    suspend fun getRemainingChances(): Int

    //rompe una galleta usando estrategia cache falling back to network
    suspend fun crackCookie(): Result<FortuneCrackResult>

    //genera el bitmap en un hilo de computo y lo guarda en la galeria
    suspend fun saveCardToGallery(fortune: FortuneModel): Uri?

    //sincroniza las favoritas desde la nube si room esta vacio o se reinstala la app
    suspend fun syncFavoritesFromRemote()
}

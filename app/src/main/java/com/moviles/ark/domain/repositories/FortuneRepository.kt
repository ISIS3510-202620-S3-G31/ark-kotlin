package com.moviles.ark.domain.repositories

import android.net.Uri
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import com.moviles.ark.domain.models.FortuneCrackResult
import com.moviles.ark.domain.models.FortuneModel
import kotlinx.coroutines.flow.Flow

//interfaz del repositorio que orquesta galleta de la fortuna, favoritos y cache
interface FortuneRepository {
    //flujo reactivo de favoritos guardados en room
    fun observeFavorites(): Flow<List<FortuneModel>>

    //alias para mantener compatibilidad con observadores de favoritos
    fun getFavorites(): Flow<List<FortuneModel>> = observeFavorites()

    //flujo reactivo de tarjetas guardadas en galeria y nube
    fun observeSavedCards(): Flow<List<SavedFortuneCardEntity>>

    //inicializa la cache en memoria cargando los ids de favoritos desde room
    suspend fun initCache()

    //consulta si una galleta es favorita
    suspend fun isFavorite(cookieId: Int): Boolean

    //obtiene las galletas recientes guardadas en disco lru
    suspend fun getRecentCookies(): List<FortuneModel>

    //agrega o quita de favoritos en room, memoria y la nube
    suspend fun toggleFavorite(fortune: FortuneModel): Boolean

    //elimina un favorito por id
    suspend fun deleteFavorite(cookieId: Int)

    //obtiene los intentos diarios disponibles hoy
    suspend fun getRemainingChances(): Int

    //rompe una galleta usando estrategia cache falling back to network
    suspend fun crackCookie(): Result<FortuneCrackResult>

    //genera el bitmap en un hilo de computo, lo guarda en galeria y lo sube a la nube
    suspend fun saveCardToGallery(fortune: FortuneModel): Uri?

    //abre el selector nativo para compartir la tarjeta
    fun shareCard(uri: Uri)

    //inicializa galletas candidatas usando llamadas concurrentes con async y awaitall
    suspend fun initializeCandidates(): List<FortuneModel>

    //sincroniza las favoritas desde la nube si room esta vacio o se reinstala la app
    suspend fun syncFavoritesFromRemote()

    //sincroniza las tarjetas guardadas desde la nube si se cambia de celular
    suspend fun syncSavedCardsFromRemote()
}

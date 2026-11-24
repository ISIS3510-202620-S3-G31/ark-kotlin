package com.moviles.ark.domain.repositories

import android.net.Uri
import com.moviles.ark.domain.models.FortuneModel
import kotlinx.coroutines.flow.Flow

//interfaz del repositorio que define las operaciones de la galleta de la fortuna
interface FortuneRepository {
    //flujo reactivo con la lista de frases favoritas guardadas en room
    fun getFavorites(): Flow<List<FortuneModel>>

    //consulta si una galleta es favorita
    suspend fun isFavorite(cookieId: Int): Boolean

    //agrega o quita de favoritos y devuelve el nuevo estado
    suspend fun toggleFavorite(fortune: FortuneModel): Boolean

    //elimina un favorito por id
    suspend fun deleteFavorite(cookieId: Int)

    //obtiene las 5 galletas abiertas recientemente desde la cache en disco
    suspend fun getRecentCookies(): List<FortuneModel>

    //obtiene los intentos diarios disponibles hoy
    suspend fun getRemainingChances(): Int

    //rompe una galleta descontando un intento y avisando si viene de la reserva offline
    suspend fun crackCookie(): Result<com.moviles.ark.domain.models.FortuneCrackResult>

    //genera el bitmap en un hilo de computo y lo guarda en la galeria
    suspend fun saveCardToGallery(fortune: FortuneModel): Uri?

    //abre el selector nativo para compartir la tarjeta
    fun shareCard(uri: Uri)

    //inicializa galletas candidatas usando llamadas concurrentes con async y awaitall
    suspend fun initializeCandidates(): List<FortuneModel>
}

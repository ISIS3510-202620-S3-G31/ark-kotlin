package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.FortuneCardModel
import com.moviles.ark.domain.models.FortuneCrackResult
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.models.FortuneShareResult
import com.moviles.ark.domain.models.TableFortune
import kotlinx.coroutines.flow.Flow

//repositorio de la galleta de la fortuna, el viewmodel solo habla con esta interfaz
interface FortuneRepository {
    //frases favoritas del usuario, se actualiza sola cuando cambia room
    fun observeFavorites(): Flow<List<FortuneModel>>

    //imagenes guardadas del usuario
    fun observeSavedCards(): Flow<List<FortuneCardModel>>

    //carga en memoria los ids de las favoritas
    suspend fun initCache()

    //ultimas 5 frases abiertas
    suspend fun getRecentCookies(): List<FortuneModel>

    //galletas que quedan hoy
    suspend fun getRemainingChances(): Int

    //arma las 3 galletas de la mesa con cache falling back to network
    suspend fun getTable(): List<TableFortune>

    //abre una galleta de la mesa, las otras dos se quedan guardadas
    suspend fun openCookie(cookie: TableFortune): Result<FortuneCrackResult>

    //guarda o quita una frase de favoritas, devuelve true si quedo guardada
    suspend fun toggleFavorite(fortune: FortuneModel): Boolean

    //devuelve la imagen de la frase, si no existe la crea una sola vez
    suspend fun getOrCreateCard(fortune: FortuneModel): FortuneShareResult?

    //sube a la nube lo que quedo pendiente sin internet
    suspend fun syncPending()

    //trae de la nube las favoritas y las imagenes, por si se cambio de celular
    suspend fun syncFromRemote()
}

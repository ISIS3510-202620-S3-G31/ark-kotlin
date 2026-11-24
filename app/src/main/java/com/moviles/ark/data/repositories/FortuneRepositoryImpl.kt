package com.moviles.ark.data.repositories

import android.net.Uri
import com.moviles.ark.data.local.FortuneCardExporter
import com.moviles.ark.data.local.FortuneCacheManager
import com.moviles.ark.data.local.FortunePreferencesManager
import com.moviles.ark.data.local.daos.FavoriteFortuneCookieDao
import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import com.moviles.ark.domain.adapters.FortuneServiceAdapter
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.repositories.FortuneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

//implementacion del repositorio de la galleta de la fortuna que reparte el trabajo en hilos de fondo
class FortuneRepositoryImpl(
    private val favoriteFortuneCookieDao: FavoriteFortuneCookieDao,
    private val preferencesManager: FortunePreferencesManager,
    private val cacheManager: FortuneCacheManager,
    private val cardExporter: FortuneCardExporter,
    private val serviceAdapter: FortuneServiceAdapter
) : FortuneRepository {

    //obtiene los favoritos de room corriendo la consulta y transformacion en dispatchers.io
    override fun getFavorites(): Flow<List<FortuneModel>> {
        return favoriteFortuneCookieDao.getAllFavorites()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    //consulta si una galleta es favorita revisando primero la cache en memoria en 0 ms
    override suspend fun isFavorite(cookieId: Int): Boolean = withContext(Dispatchers.IO) {
        cacheManager.isFavorite(cookieId)
    }

    //cambia el estado de favorito guardando o borrando en room en dispatchers.io
    override suspend fun toggleFavorite(fortune: FortuneModel): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = cacheManager.isFavorite(fortune.id)
        if (currentlyFav) {
            favoriteFortuneCookieDao.deleteFavoriteById(fortune.id)
            cacheManager.setFavorite(fortune.id, false)
            false
        } else {
            val entity = FavoriteFortuneCookieEntity.fromDomain(fortune.copy(isFavorite = true))
            favoriteFortuneCookieDao.insertFavorite(entity)
            cacheManager.setFavorite(fortune.id, true)
            true
        }
    }

    //elimina un favorito directamente de la base de datos local en dispatchers.io
    override suspend fun deleteFavorite(cookieId: Int) = withContext(Dispatchers.IO) {
        favoriteFortuneCookieDao.deleteFavoriteById(cookieId)
        cacheManager.setFavorite(cookieId, false)
    }

    //lee las galletas recientes desde la cache en disco en dispatchers.io
    override suspend fun getRecentCookies(): List<FortuneModel> = withContext(Dispatchers.IO) {
        cacheManager.getRecentCookies()
    }

    //obtiene las oportunidades restantes desde sharedpreferences
    override suspend fun getRemainingChances(): Int = withContext(Dispatchers.IO) {
        preferencesManager.getChancesLeft()
    }

    //rompe una galleta descargando la frase de internet o sacandola de la reserva local
    override suspend fun crackCookie(): Result<FortuneModel> = withContext(Dispatchers.IO) {
        val chances = preferencesManager.getChancesLeft()
        if (chances <= 0) {
            return@withContext Result.failure(Exception("no chances left today"))
        }

        //llamamos a la api en el hilo de red
        val networkResult = serviceAdapter.fetchFortune()
        val fortune = if (networkResult.isSuccess) {
            networkResult.getOrThrow()
        } else {
            //si falla la red sacamos una galleta de la reserva de disco
            cacheManager.popDeckCookie() ?: FortuneModel(
                id = FortuneModel.generateId("Every day brings new chances to grow."),
                quote = "Every day brings new chances to grow.",
                category = "Wisdom"
            )
        }

        //descontamos el intento y guardamos en las recientes de disco
        preferencesManager.decrementChances()
        val isFav = cacheManager.isFavorite(fortune.id)
        val finalFortune = fortune.copy(isFavorite = isFav)
        cacheManager.addRecentCookie(finalFortune)
        Result.success(finalFortune)
    }

    //dibuja el bitmap en dispatchers.default porque es trabajo pesado de cpu y luego guarda en mediastore
    override suspend fun saveCardToGallery(fortune: FortuneModel): Uri? = withContext(Dispatchers.Default) {
        val bitmap = cardExporter.renderCardBitmap(fortune)
        withContext(Dispatchers.IO) {
            cardExporter.saveCardToGallery(bitmap, "fortune_${fortune.id}")
        }
    }

    //comparte la tarjeta llamando al selector del sistema
    override fun shareCard(uri: Uri) {
        cardExporter.shareFortuneCard(uri)
    }

    //precarga galletas usando llamadas concurrentes con async y awaitall dentro de supervisorscope
    override suspend fun initializeCandidates(): List<FortuneModel> = withContext(Dispatchers.IO) {
        //cargamos primero los ids de favoritos en la cache de memoria
        val favoriteIds = favoriteFortuneCookieDao.getAllFavoriteIds()
        cacheManager.loadFavoriteIds(favoriteIds)

        //consultamos 3 frases en paralelo sin que el fallo de una cancele las otras
        supervisorScope {
            val deferredList = (1..3).map {
                async { serviceAdapter.fetchFortune().getOrNull() }
            }
            val candidates = deferredList.awaitAll().filterNotNull()
            if (candidates.isNotEmpty()) {
                cacheManager.saveDeck(candidates)
            }
            candidates
        }
    }
}

package com.moviles.ark.data.repositories

import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.FortuneCacheManager
import com.moviles.ark.data.local.FortuneCardExporter
import com.moviles.ark.data.local.FortunePreferencesManager
import com.moviles.ark.data.local.daos.FavoriteFortuneCookieDao
import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import com.moviles.ark.domain.adapters.FortuneServiceAdapter
import com.moviles.ark.domain.models.FortuneCrackResult
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.repositories.FortuneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

//implementacion del repositorio que maneja favoritos en room y cache falling back to network
class FortuneRepositoryImpl(
    private val favoriteFortuneCookieDao: FavoriteFortuneCookieDao,
    private val preferencesManager: FortunePreferencesManager,
    private val cacheManager: FortuneCacheManager,
    private val cardExporter: FortuneCardExporter,
    private val serviceAdapter: FortuneServiceAdapter,
    private val firestore: FirebaseFirestore? = null,
    private val userIdProvider: () -> String? = { null }
) : FortuneRepository {

    //observa la lista de favoritos de room y sincroniza el estado con la cache en memoria
    override fun observeFavorites(): Flow<List<FortuneModel>> {
        return favoriteFortuneCookieDao.getAllFavorites().map { entities ->
            val list = entities.map { it.toDomain() }
            val ids = list.map { it.id }
            cacheManager.loadFavoriteIds(ids)
            list
        }
    }

    //carga los identificadores de favoritos de room a memoria para acceso en tiempo constante
    override suspend fun initCache(): Unit = withContext(Dispatchers.IO) {
        val ids = favoriteFortuneCookieDao.getAllFavoriteIds()
        cacheManager.loadFavoriteIds(ids)
    }

    //obtiene las galletas recientes almacenadas en el cache de disco lru
    override suspend fun getRecentCookies(): List<FortuneModel> = withContext(Dispatchers.IO) {
        cacheManager.getRecentCookies()
    }

    //agrega o quita de favoritos en room, memoria y sube a la nube si hay sesion
    override suspend fun toggleFavorite(fortune: FortuneModel): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = cacheManager.isFavorite(fortune.id)
        val newFav = !currentlyFav
        cacheManager.setFavorite(fortune.id, newFav)

        val userId = userIdProvider()
        if (newFav) {
            val entity = FavoriteFortuneCookieEntity.fromDomain(fortune.copy(isFavorite = true), isSynced = false)
            favoriteFortuneCookieDao.insertFavorite(entity)
            if (firestore != null && userId != null) {
                try {
                    val map = hashMapOf("id" to fortune.id, "quote" to fortune.quote, "category" to fortune.category, "luckyNumber" to fortune.luckyNumber, "savedAt" to fortune.savedAt)
                    firestore.collection("users").document(userId).collection("favorite_fortunes").document(fortune.id.toString()).set(map).await()
                    favoriteFortuneCookieDao.markAsSynced(fortune.id)
                } catch (_: Exception) {
                    //si no hay internet queda guardado en room con isSynced false
                }
            }
        } else {
            favoriteFortuneCookieDao.deleteFavoriteById(fortune.id)
            if (firestore != null && userId != null) {
                try {
                    firestore.collection("users").document(userId).collection("favorite_fortunes").document(fortune.id.toString()).delete().await()
                } catch (_: Exception) {
                    //si falla el borrado remoto se omite
                }
            }
        }
        newFav
    }

    //obtiene los intentos disponibles de hoy en shared preferences
    override suspend fun getRemainingChances(): Int = withContext(Dispatchers.IO) {
        preferencesManager.getChancesLeft()
    }

    //rompe una galleta usando estrategia cache falling back to network
    override suspend fun crackCookie(): Result<FortuneCrackResult> = withContext(Dispatchers.IO) {
        val chances = preferencesManager.getChancesLeft()
        if (chances <= 0) {
            return@withContext Result.failure(Exception("no chances left today"))
        }

        val deckCount = cacheManager.getDeckCount()
        val fortune: FortuneModel
        var isOfflineFallback = false

        //si en disco quedan al menos 3 galletas no se consulta el api
        if (deckCount >= 3) {
            fortune = cacheManager.popDeckCookie() ?: cacheManager.getDefaultFallback()
        } else {
            //si quedan pocas galletas en disco se consulta la red para traer 2 y 2
            val freshCookies = serviceAdapter.fetchTwoAndTwo()
            if (freshCookies.isNotEmpty()) {
                cacheManager.appendDeck(freshCookies)
                fortune = cacheManager.popDeckCookie() ?: freshCookies.first()
            } else {
                //si la red falla sacamos de lo que quede en disco o del respaldo local
                val cached = cacheManager.popDeckCookie()
                if (cached != null) {
                    fortune = cached
                } else {
                    fortune = cacheManager.getDefaultFallback()
                }
                isOfflineFallback = true
            }
        }

        //descontamos el intento y registramos en recientes de disco
        val remaining = preferencesManager.decrementChances()
        val isFav = cacheManager.isFavorite(fortune.id)
        val finalFortune = fortune.copy(isFavorite = isFav)
        cacheManager.addRecentCookie(finalFortune)
        Result.success(FortuneCrackResult(fortune = finalFortune, isFromOfflineReserve = isOfflineFallback, remainingChances = remaining))
    }

    //dibuja el bitmap en dispatchers.default porque es trabajo pesado de cpu y luego guarda en mediastore
    override suspend fun saveCardToGallery(fortune: FortuneModel): Uri? {
        val bitmap: Bitmap = withContext(Dispatchers.Default) {
            cardExporter.renderCardBitmap(fortune)
        }
        return withContext(Dispatchers.IO) {
            cardExporter.saveCardToGallery(bitmap, "fortune_${fortune.id}")
        }
    }

    //sincroniza las favoritas desde la nube para restaurarlas si room se vacia
    override suspend fun syncFavoritesFromRemote(): Unit = withContext(Dispatchers.IO) {
        val userId = userIdProvider()
        if (firestore != null && userId != null) {
            try {
                val snapshot = firestore.collection("users").document(userId).collection("favorite_fortunes").get().await()
                val entities = snapshot.documents.mapNotNull { doc ->
                    val id = doc.getLong("id")?.toInt() ?: return@mapNotNull null
                    val quote = doc.getString("quote") ?: ""
                    val category = doc.getString("category") ?: "Wisdom"
                    val luckyNumber = doc.getLong("luckyNumber")?.toInt() ?: 7
                    val savedAt = doc.getLong("savedAt") ?: System.currentTimeMillis()
                    FavoriteFortuneCookieEntity(id = id, quote = quote, category = category, luckyNumber = luckyNumber, savedAt = savedAt, isSynced = true)
                }
                for (entity in entities) {
                    favoriteFortuneCookieDao.insertFavorite(entity)
                }
                val ids = entities.map { it.id }
                cacheManager.loadFavoriteIds(ids)
            } catch (_: Exception) {
                //si falla la consulta remota se conservan los datos de room
            }
        }
    }
}

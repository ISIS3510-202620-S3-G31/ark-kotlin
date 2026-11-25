package com.moviles.ark.data.repositories

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.moviles.ark.data.local.FortuneCacheManager
import com.moviles.ark.data.local.FortuneCardExporter
import com.moviles.ark.data.local.FortunePreferencesManager
import com.moviles.ark.data.local.daos.FavoriteFortuneCookieDao
import com.moviles.ark.data.local.daos.SavedFortuneCardDao
import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import com.moviles.ark.data.remote.dtos.FavoriteFortuneDto
import com.moviles.ark.data.remote.dtos.FortuneCardDto
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.adapters.FortuneServiceAdapter
import com.moviles.ark.domain.models.FortuneCardModel
import com.moviles.ark.domain.models.FortuneCrackResult
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.models.FortuneShareResult
import com.moviles.ark.domain.models.FortuneSource
import com.moviles.ark.domain.models.TableFortune
import com.moviles.ark.domain.repositories.FortuneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

//repositorio de la galleta: room y disco son la fuente de verdad y firestore y storage son el respaldo
class FortuneRepositoryImpl(
    private val favoriteFortuneCookieDao: FavoriteFortuneCookieDao,
    private val savedFortuneCardDao: SavedFortuneCardDao,
    private val preferencesManager: FortunePreferencesManager,
    private val cacheManager: FortuneCacheManager,
    private val cardExporter: FortuneCardExporter,
    private val serviceAdapter: FortuneServiceAdapter,
    private val firestore: FirebaseFirestore? = null,
    private val storage: FirebaseStorage? = null,
    private val userIdProvider: () -> String? = { null }
) : FortuneRepository {

    //una sola subida a la vez, la pueden pedir la pantalla y la vuelta de la red al mismo tiempo
    private val syncMutex = Mutex()

    //sin sesion lo guardado queda a nombre de un usuario local
    private fun userId(): String = userIdProvider() ?: LOCAL_USER

    override fun observeFavorites(): Flow<List<FortuneModel>> {
        return favoriteFortuneCookieDao.observeFavorites(userId()).map { list ->
            cacheManager.loadFavoriteIds(list.map { it.id })
            list.map { it.toModel() }
        }
    }

    override fun observeSavedCards(): Flow<List<FortuneCardModel>> {
        return savedFortuneCardDao.observeCards(userId()).map { list -> list.map { it.toModel() } }
    }

    override suspend fun initCache() = withContext(Dispatchers.IO) {
        cacheManager.loadFavoriteIds(favoriteFortuneCookieDao.getFavoriteIds(userId()))
    }

    override suspend fun getRecentCookies(): List<FortuneModel> = withContext(Dispatchers.IO) {
        cacheManager.getRecentCookies().map { it.copy(isFavorite = cacheManager.isFavorite(it.id)) }
    }

    override suspend fun getRemainingChances(): Int = withContext(Dispatchers.IO) { preferencesManager.getChancesLeft(userId()) }

    //cache falling back to network: si en el telefono hay 3 frases guardadas no se llama al api
    override suspend fun getTable(): List<TableFortune> = withContext(Dispatchers.IO) {
        val deck = cacheManager.getDeck()
        if (deck.size >= 3) return@withContext deck.take(3).map { TableFortune(withFavorite(it), FortuneSource.PHONE, false) }

        //quedan pocas, se traen 2 de cada api y se guardan en disco
        val fresh = serviceAdapter.fetchTwoAndTwo()
        val isOffline = fresh.isEmpty()
        if (!isOffline) cacheManager.appendDeck(fresh)
        val freshIds = fresh.map { it.id }.toSet()
        val table = cacheManager.getDeck().take(3).map { TableFortune(withFavorite(it), if (it.id in freshIds) FortuneSource.INTERNET else FortuneSource.PHONE, isOffline) }.toMutableList()

        //si aun faltan galletas se completan con las frases que trae la app
        for (seed in cacheManager.getSeeds().shuffled()) {
            if (table.size >= 3) break
            if (table.none { it.fortune.quote == seed.quote }) table.add(TableFortune(withFavorite(seed), FortuneSource.BUILT_IN, isOffline))
        }
        table
    }

    //abre una galleta, solo esa sale del disco y las otras dos se quedan para la proxima vez
    override suspend fun openCookie(cookie: TableFortune): Result<FortuneCrackResult> = withContext(Dispatchers.IO) {
        if (preferencesManager.getChancesLeft(userId()) <= 0) return@withContext Result.failure(Exception("no cookies left today"))
        cacheManager.removeFromDeck(cookie.fortune.id)
        val remaining = preferencesManager.decrementChances(userId())
        val fortune = withFavorite(cookie.fortune)
        cacheManager.addRecentCookie(fortune)
        Result.success(FortuneCrackResult(fortune, remaining))
    }

    //le pone a la frase si es favorita segun la memoria
    private fun withFavorite(fortune: FortuneModel): FortuneModel = fortune.copy(isFavorite = cacheManager.isFavorite(fortune.id))

    //primero se guarda en memoria y room, luego se intenta subir, sin red queda pendiente
    override suspend fun toggleFavorite(fortune: FortuneModel): Boolean = withContext(Dispatchers.IO) {
        val user = userId()
        val nowFavorite = !cacheManager.isFavorite(fortune.id)
        cacheManager.setFavorite(fortune.id, nowFavorite)
        if (nowFavorite) {
            favoriteFortuneCookieDao.insertFavorite(FavoriteFortuneCookieEntity(user, fortune.id, fortune.quote, fortune.category, System.currentTimeMillis()))
        } else {
            favoriteFortuneCookieDao.markDeleted(user, fortune.id)
        }
        nowFavorite
    }

    //si la imagen ya existe se reutiliza, si no se dibuja en un hilo de computo y se guarda una sola vez
    override suspend fun getOrCreateCard(fortune: FortuneModel): FortuneShareResult? {
        val user = userId()
        val saved = withContext(Dispatchers.IO) { savedFortuneCardDao.getCard(user, fortune.id) }
        if (saved != null && withContext(Dispatchers.IO) { cardExporter.exists(saved.localImageUri) }) return FortuneShareResult(Uri.parse(saved.localImageUri), false)

        val savedAt = saved?.savedAt ?: System.currentTimeMillis()
        val bitmap = withContext(Dispatchers.Default) { cardExporter.renderCardBitmap(fortune, savedAt) }
        val uri = withContext(Dispatchers.IO) { cardExporter.saveCardToGallery(bitmap) } ?: return null
        withContext(Dispatchers.IO) {
            val card = SavedFortuneCardEntity(id = saved?.id ?: 0, cookieId = fortune.id, userId = user, quote = fortune.quote, localImageUri = uri.toString(), remoteImageUrl = saved?.remoteImageUrl, savedAt = savedAt, isSynced = saved?.isSynced ?: false)
            savedFortuneCardDao.insertCard(card)
        }
        return FortuneShareResult(uri, saved == null)
    }

    //sube lo pendiente, si algo falla se para y se reintenta cuando vuelva la red
    override suspend fun syncPending() = withContext(Dispatchers.IO) {
        val user = userIdProvider() ?: return@withContext
        if (firestore == null || storage == null) return@withContext
        syncMutex.withLock {
            try {
                for (favorite in favoriteFortuneCookieDao.getUnsynced(user)) {
                    val doc = firestore.collection("users").document(user).collection("favorite_fortunes").document(favorite.id.toString())
                    if (favorite.isDeleted) {
                        withTimeout(TIMEOUT_MS) { doc.delete().await() }
                        favoriteFortuneCookieDao.deleteFavorite(user, favorite.id)
                    } else {
                        withTimeout(TIMEOUT_MS) { doc.set(favorite.toDto().toMap()).await() }
                        favoriteFortuneCookieDao.markSynced(user, favorite.id)
                    }
                }
                for (card in savedFortuneCardDao.getUnsynced(user)) {
                    //la imagen se vuelve a dibujar desde la frase, asi se sube aunque la borren de la galeria
                    val bitmap = withContext(Dispatchers.Default) { cardExporter.renderCardBitmap(FortuneModel(card.cookieId, card.quote), card.savedAt) }
                    val bytes = withContext(Dispatchers.Default) { cardExporter.toPngBytes(bitmap) }
                    val ref = storage.reference.child("users/$user/fortune_cards/card_${card.cookieId}.png")
                    val metadata = StorageMetadata.Builder().setContentType("image/png").build()
                    withTimeout(UPLOAD_TIMEOUT_MS) { ref.putBytes(bytes, metadata).await() }
                    val url = withTimeout(TIMEOUT_MS) { ref.downloadUrl.await() }.toString()
                    val cardDoc = firestore.collection("users").document(user).collection("saved_fortune_cards").document(card.cookieId.toString())
                    withTimeout(TIMEOUT_MS) { cardDoc.set(card.toDto(url).toMap()).await() }
                    savedFortuneCardDao.markSynced(card.id, url)
                }
            } catch (_: Exception) {
                //sin red o tiempo agotado, lo que falta sigue pendiente en room
            }
        }
    }

    //trae de la nube lo que no este en el telefono, sin revivir lo que se quito sin red
    override suspend fun syncFromRemote() = withContext(Dispatchers.IO) {
        val user = userIdProvider() ?: return@withContext
        if (firestore == null) return@withContext
        try {
            val favorites = withTimeout(TIMEOUT_MS) { firestore.collection("users").document(user).collection("favorite_fortunes").get().await() }
            for (doc in favorites.documents) {
                val dto = doc.toObject(FavoriteFortuneDto::class.java) ?: continue
                if (dto.quote.isNotEmpty() && favoriteFortuneCookieDao.getFavorite(user, dto.id) == null) favoriteFortuneCookieDao.insertFavorite(dto.toEntity(user))
            }
            cacheManager.loadFavoriteIds(favoriteFortuneCookieDao.getFavoriteIds(user))
            val cards = withTimeout(TIMEOUT_MS) { firestore.collection("users").document(user).collection("saved_fortune_cards").get().await() }
            for (doc in cards.documents) {
                val dto = doc.toObject(FortuneCardDto::class.java) ?: continue
                if (dto.remoteImageUrl.isNotEmpty() && savedFortuneCardDao.getCard(user, dto.cookieId) == null) savedFortuneCardDao.insertCard(dto.toEntity(user))
            }
        } catch (_: Exception) {
            //sin red se queda con lo que hay en el telefono
        }
    }

    companion object {
        private const val LOCAL_USER = "local_user"
        private const val TIMEOUT_MS = 10_000L
        private const val UPLOAD_TIMEOUT_MS = 30_000L
    }
}

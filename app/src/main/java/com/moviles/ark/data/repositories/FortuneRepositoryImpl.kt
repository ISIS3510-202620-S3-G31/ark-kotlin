package com.moviles.ark.data.repositories

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
    //lo que se sube a la nube despues de abrir una galleta va aparte, asi la frase no espera a la red
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        cacheManager.getRecentCookies(userId()).map { it.copy(isFavorite = cacheManager.isFavorite(it.id)) }
    }

    //las galletas de hoy son de la cuenta, asi en otro telefono no se reinician
    override suspend fun getRemainingChances(): Int = withContext(Dispatchers.IO) {
        pullDailyChances()
        preferencesManager.getChancesLeft(userId())
    }

    //documento del dia en la nube, por ejemplo users/abc/fortune_daily/2026-11-25
    private fun dailyDoc(user: String) = firestore?.collection("users")?.document(user)?.collection("fortune_daily")?.document(preferencesManager.today())

    //trae de la nube cuantas se abrieron hoy y se queda con el numero mayor, sin red sigue con lo del telefono
    private suspend fun pullDailyChances() {
        val user = userIdProvider() ?: return
        val doc = dailyDoc(user) ?: return
        try {
            //Source.SERVER: si no hay red falla en vez de devolver la copia vieja que firestore guarda en el telefono
            val snapshot = withTimeout(DAILY_TIMEOUT_MS) { doc.get(Source.SERVER).await() }
            val remoteUsed = (snapshot.getLong("used") ?: 0L).toInt()
            preferencesManager.setUsedToday(user, remoteUsed)
            if (preferencesManager.getUsedToday(user) > remoteUsed || preferencesManager.isDailyPending(user)) pushDailyChances()
        } catch (_: Exception) {
            //sin red se usa lo que hay en el telefono
        }
    }

    //junta las recientes del telefono con las de la cuenta, se quedan las 5 abiertas mas nuevas en los dos lados
    private suspend fun mergeRecents() {
        val user = userIdProvider() ?: return
        val doc = firestore?.collection("users")?.document(user)?.collection("fortune_daily")?.document("recent") ?: return
        try {
            //Source.SERVER: con la copia vieja del telefono se borrarian las recientes de la cuenta al subir las del telefono
            val snapshot = withTimeout(DAILY_TIMEOUT_MS) { doc.get(Source.SERVER).await() }
            val remote = (snapshot.get("items") as? List<*>).orEmpty().mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val quote = map["quote"] as? String ?: return@mapNotNull null
                val id = (map["id"] as? Number)?.toInt() ?: FortuneModel.generateId(quote)
                FortuneModel(id = id, quote = quote, savedAt = (map["openedAt"] as? Number)?.toLong() ?: 0L)
            }
            val local = cacheManager.getRecentCookies(user)
            val merged = (local + remote).groupBy { it.id }.map { group -> group.value.maxBy { it.savedAt } }.sortedByDescending { it.savedAt }.take(5)
            cacheManager.setRecentCookies(user, merged)
            val items = merged.map { mapOf("id" to it.id, "quote" to it.quote, "openedAt" to it.savedAt) }
            if (merged.map { it.id } != remote.map { it.id }) withTimeout(DAILY_TIMEOUT_MS) { doc.set(mapOf("items" to items)).await() }
        } catch (_: Exception) {
            //sin red se quedan las del telefono
        }
    }

    //sube cuantas se abrieron hoy, en una transaccion para no bajar el numero si otro telefono abrio mas
    private suspend fun pushDailyChances() {
        val user = userIdProvider() ?: return
        val doc = dailyDoc(user) ?: return
        val store = firestore ?: return
        val used = preferencesManager.getUsedToday(user).toLong()
        try {
            withTimeout(DAILY_TIMEOUT_MS) {
                store.runTransaction { transaction ->
                    val remote = transaction.get(doc).getLong("used") ?: 0L
                    if (used > remote) transaction.set(doc, mapOf("date" to preferencesManager.today(), "used" to used, "updatedAt" to System.currentTimeMillis()))
                    null
                }.await()
            }
            preferencesManager.setDailyPending(user, false)
        } catch (_: Exception) {
            //queda pendiente y se sube cuando vuelva la red
        }
    }

    //cache falling back to network: la reserva del telefono es del dia; si es de hoy y tiene 3 frases no se llama al api
    //las frases ya abiertas no vuelven a la mesa, y sin red se completa con las de la app (generic fallback)
    override suspend fun getTable(): List<TableFortune> = withContext(Dispatchers.IO) {
        val seen = cacheManager.getSeenIds(userId())
        val today = preferencesManager.today()
        val isNewDay = cacheManager.getDeckDay() != today
        val deck = cacheManager.getDeck().filter { it.id !in seen }
        if (!isNewDay && deck.size >= 3) return@withContext deck.take(3).map { TableFortune(withFavorite(it), FortuneSource.PHONE, false) }

        //dia nuevo o quedan pocas: una llamada que trae 4 frases, esperando maximo un momento para que sin red la mesa salga rapido
        val fresh = withTimeoutOrNull(TABLE_NETWORK_WAIT_MS) { serviceAdapter.fetchTwoAndTwo() }.orEmpty()
        val isOffline = fresh.isEmpty()
        //en un dia nuevo la reserva se cambia por las frases de hoy; sin red se usan las que quedaron de ayer
        if (!isOffline && isNewDay) cacheManager.replaceDeck(fresh, seen, today)
        if (!isOffline && !isNewDay) cacheManager.appendDeck(fresh, seen)
        val freshIds = fresh.map { it.id }.toSet()
        val table = cacheManager.getDeck().filter { it.id !in seen }.take(3).map { TableFortune(withFavorite(it), if (it.id in freshIds) FortuneSource.INTERNET else FortuneSource.PHONE, isOffline) }.toMutableList()

        //si aun faltan se completan con las frases de la app: primero las que nunca se han abierto, luego las abiertas hace mas tiempo
        val seeds = cacheManager.getSeeds()
        val unseenSeeds = seeds.filter { it.id !in seen }.shuffled()
        val oldestSeenSeeds = seen.mapNotNull { id -> seeds.find { it.id == id } }
        for (seed in unseenSeeds + oldestSeenSeeds) {
            if (table.size >= 3) break
            if (table.none { it.fortune.quote == seed.quote }) table.add(TableFortune(withFavorite(seed), FortuneSource.BUILT_IN, isOffline))
        }
        table
    }

    //si a la reserva de hoy le quedan menos de 3 frases se piden 4 mas, una sola llamada y sin que la pantalla espere
    override suspend fun refillDeck() = withContext(Dispatchers.IO) {
        val seen = cacheManager.getSeenIds(userId())
        if (cacheManager.getDeckDay() == preferencesManager.today() && cacheManager.getDeck().count { it.id !in seen } >= 3) return@withContext
        val fresh = withTimeoutOrNull(REFILL_WAIT_MS) { serviceAdapter.fetchTwoAndTwo() }.orEmpty()
        if (fresh.isEmpty()) return@withContext
        if (cacheManager.getDeckDay() == preferencesManager.today()) cacheManager.appendDeck(fresh, seen) else cacheManager.replaceDeck(fresh, seen, preferencesManager.today())
    }

    //abre una galleta, solo esa sale del disco y las otras dos se quedan para la proxima vez
    override suspend fun openCookie(cookie: TableFortune): Result<FortuneCrackResult> = withContext(Dispatchers.IO) {
        if (preferencesManager.getChancesLeft(userId()) <= 0) return@withContext Result.failure(Exception("no cookies left today"))
        cacheManager.removeFromDeck(cookie.fortune.id)
        cacheManager.markSeen(userId(), cookie.fortune.id)
        val remaining = preferencesManager.decrementChances(userId())
        //savedAt queda como la hora en que se abrio, asi se ordenan las recientes entre telefonos
        val fortune = withFavorite(cookie.fortune).copy(savedAt = System.currentTimeMillis())
        cacheManager.addRecentCookie(userId(), fortune)
        //la nube se actualiza aparte; sin red queda pendiente y la frase sale de una vez
        backgroundScope.launch {
            pushDailyChances()
            mergeRecents()
        }
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
        val isCurrent = preferencesManager.isCardCurrent(user, fortune.id)
        if (saved != null && isCurrent && withContext(Dispatchers.IO) { cardExporter.exists(saved.localImageUri) }) return FortuneShareResult(Uri.parse(saved.localImageUri), false)

        val savedAt = saved?.savedAt ?: System.currentTimeMillis()
        val bitmap = withContext(Dispatchers.Default) { cardExporter.renderCardBitmap(fortune, savedAt) }
        val uri = withContext(Dispatchers.IO) { cardExporter.saveCardToGallery(bitmap) } ?: return null
        withContext(Dispatchers.IO) {
            //si era una imagen vieja se vuelve a subir con el diseno nuevo
            val card = SavedFortuneCardEntity(id = saved?.id ?: 0, cookieId = fortune.id, userId = user, quote = fortune.quote, localImageUri = uri.toString(), remoteImageUrl = saved?.remoteImageUrl, savedAt = savedAt, isSynced = (saved?.isSynced ?: false) && isCurrent)
            savedFortuneCardDao.insertCard(card)
            preferencesManager.markCardCurrent(user, fortune.id)
        }
        return FortuneShareResult(uri, saved == null)
    }

    //quita la imagen del telefono, si ya estaba en la nube queda anotada para borrarla alla
    override suspend fun deleteCard(card: FortuneCardModel) = withContext(Dispatchers.IO) {
        val user = userId()
        savedFortuneCardDao.deleteCard(user, card.cookieId)
        if (card.isBackedUp) preferencesManager.addDeletedCard(user, card.cookieId)
    }

    //sube lo pendiente, si algo falla se para y se reintenta cuando vuelva la red
    override suspend fun syncPending() = withContext(Dispatchers.IO) {
        val user = userIdProvider() ?: return@withContext
        if (firestore == null || storage == null) return@withContext
        //las galletas abiertas sin red
        if (preferencesManager.isDailyPending(user)) pushDailyChances()
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
                //imagenes quitadas sin red, se borran el archivo y el documento de la nube
                for (cookieId in preferencesManager.getDeletedCards(user)) {
                    val ref = storage.reference.child("users/$user/fortune_cards/card_${cookieId}.png")
                    //si el archivo ya no existe en la nube se sigue igual
                    try { withTimeout(TIMEOUT_MS) { ref.delete().await() } } catch (_: Exception) { }
                    val cardDoc = firestore.collection("users").document(user).collection("saved_fortune_cards").document(cookieId.toString())
                    withTimeout(TIMEOUT_MS) { cardDoc.delete().await() }
                    preferencesManager.removeDeletedCard(user, cookieId)
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
        //las recientes y las galletas de hoy van primero y aparte, asi si falla traer las favoritas igual llegan
        mergeRecents()
        pullDailyChances()
        try {
            val favorites = withTimeout(TIMEOUT_MS) { firestore.collection("users").document(user).collection("favorite_fortunes").get(Source.SERVER).await() }
            for (doc in favorites.documents) {
                val dto = doc.toObject(FavoriteFortuneDto::class.java) ?: continue
                if (dto.quote.isNotEmpty() && favoriteFortuneCookieDao.getFavorite(user, dto.id) == null) favoriteFortuneCookieDao.insertFavorite(dto.toEntity(user))
            }
            cacheManager.loadFavoriteIds(favoriteFortuneCookieDao.getFavoriteIds(user))
            val cards = withTimeout(TIMEOUT_MS) { firestore.collection("users").document(user).collection("saved_fortune_cards").get(Source.SERVER).await() }
            val deleted = preferencesManager.getDeletedCards(user)
            for (doc in cards.documents) {
                val dto = doc.toObject(FortuneCardDto::class.java) ?: continue
                if (dto.cookieId in deleted) continue
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
        private const val DAILY_TIMEOUT_MS = 3_000L
        //lo maximo que la mesa espera al api antes de usar las frases de la app
        private const val TABLE_NETWORK_WAIT_MS = 1_500L
        private const val REFILL_WAIT_MS = 8_000L
    }
}

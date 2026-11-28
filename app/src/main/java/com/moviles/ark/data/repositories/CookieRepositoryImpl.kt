package com.moviles.ark.data.repositories

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.moviles.ark.data.local.CookieImageCompressor
import com.moviles.ark.data.local.CookiePrivateImageStore
import com.moviles.ark.data.local.CookieSyncPreferences
import com.moviles.ark.data.local.daos.CookieDao
import com.moviles.ark.data.local.entities.CookieEntity
import com.moviles.ark.data.local.entities.CookieListRow
import com.moviles.ark.data.local.sensors.CameraHelper
import com.moviles.ark.data.remote.CookieCloudStore
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.CookieDto
import com.moviles.ark.domain.models.CookieImageBudget
import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.domain.models.CookieRefreshResult
import com.moviles.ark.domain.models.CookieState
import com.moviles.ark.domain.models.CookieSync
import com.moviles.ark.domain.models.CookieUploadResult
import com.moviles.ark.domain.models.StoredCookieImage
import com.moviles.ark.domain.repositories.CookieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

//cookie jar local first: el repositorio solo orquesta
//los datos van a room (CookieDao), la imagen comprimida (CookieImageCompressor) va al almacenamiento privado (CookiePrivateImageStore)
//caching: set de titulos en memoria para los repetidos y presupuesto de imagenes en disco con desalojo lru
//cada fila lleva el uid de firebase del dueno, como photo_entries; cada cambio queda con isSynced = false para la nube
//nube (CookieCloudStore): el sync sube lo pendiente, al abrir el jarro se bajan los cambios de otros telefonos
//y las imagenes que faltan se bajan cuando se necesitan
//hilos: room, archivos y red corren en Dispatchers.IO (cada funcion hace withContext(IO), asi nadie puede llamarlas
//desde el hilo principal por error); comprimir y decodificar van en Dispatchers.Default (cpu)
class CookieRepositoryImpl(
    private val cookieDao: CookieDao,
    private val imageStore: CookiePrivateImageStore,
    private val imageCompressor: CookieImageCompressor,
    //la camara del sistema deja la foto en un archivo temporal que se crea aqui (FileProvider); null en pruebas
    private val cameraHelper: CameraHelper? = null,
    //uid del usuario con sesion; null sin sesion
    private val userIdProvider: () -> String?,
    //avisa que hay algo nuevo por subir (crear, comer, devolver); no se espera
    private val onCookieChanged: () -> Unit = {},
    //pide borrar la copia de la nube de una galleta borrada en el telefono; no se espera
    private val onRemoteDeleteNeeded: (userId: String, cookieId: String) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    //presupuesto de imagenes segun el espacio libre; la regla esta en el modelo
    private val imageBudgetFor: (usableSpaceBytes: Long) -> Long = CookieImageBudget::budgetFor,
    //copia en firebase; null en pruebas y previews (todo queda solo en el telefono)
    private val cloudStore: CookieCloudStore? = null,
    //ids con el borrado en la nube aun pendiente (RemoteCookieDeleteWorker): al bajar cambios no se traen de vuelta
    private val pendingRemoteDeletes: suspend () -> Set<String> = { emptySet() },
    //si este telefono ya bajo alguna vez las galletas de la cuenta
    private val syncPreferences: CookieSyncPreferences? = null,
) : CookieRepository {

    //dos creaciones a la vez podrian calcular el mismo sufijo, y el set de titulos se toca de a uno: un solo candado
    private val titleMutex = Mutex()
    //cache de titulos en memoria: las llaves (titleKey) de todos los titulos del usuario en un HashSet
    //revisar si un titulo ya existe es O(1) sin ir a room; el indice unico de room sigue siendo la ultima defensa
    private var titleKeys: HashSet<String>? = null
    private var titleKeysUserId: String? = null
    //dos desalojos a la vez podrian borrar la misma imagen
    private val budgetMutex = Mutex()
    //borradas en esta sesion de la app: el worker de borrado puede no aparecer todavia en workmanager
    private val deletedHere: MutableSet<String> = Collections.synchronizedSet(HashSet())
    //un candado por galleta para no bajar dos veces la misma imagen
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    //una sola bajada de cambios a la vez: dos refrescos juntos (dos pantallas, o una pantalla y un reintento) leerian
    //la misma foto de room y aplicarian dos veces lo mismo (por ejemplo dos sufijos al mismo titulo repetido)
    private val refreshMutex = Mutex()

    //room hace la consulta en su propio hilo de fondo; flowOn(Dispatchers.Default) deja tambien fuera del hilo principal
    //el paso de filas a modelos (todas las galletas en cada cambio), y la pantalla recibe la lista ya lista
    override fun observeCookies(): Flow<List<CookieModel>> {
        //sin sesion no se muestran galletas de nadie
        val userId = currentUserId() ?: return flowOf(emptyList())
        val cookies = cookieDao.observeCookies(userId).map { entities -> entities.map { it.toModel() } }
        return cookies.flowOn(Dispatchers.Default)
    }

    override fun observeCookieList(): Flow<List<CookieListItem>> {
        val userId = currentUserId() ?: return flowOf(emptyList())
        val items = cookieDao.observeList(userId).map { rows -> rows.map { it.toItem() } }
        return items.flowOn(Dispatchers.Default)
    }

    override suspend fun getCookie(cookieId: String): CookieModel? = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext null
        cookieDao.getCookie(cookieId, userId)?.toModel()
    }

    override suspend fun resolveTitle(title: String): String = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext CookieModel.cleanTitle(title)
        titleMutex.withLock { CookieModel.uniqueTitle(title, titleKeysOf(userId)) }
    }

    override suspend fun createCookie(title: String, text: String, imageUri: String?): Result<CookieModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            CookieModel.validationError(title, text)?.let { error(it) }
            val id = UUID.randomUUID().toString()
            //primero la imagen: comprimir (cpu) y guardar el archivo privado (disco); si falla no se crea la galleta
            val imagePath = imageUri?.let { uri -> imageStore.save(id, imageCompressor.compress(uri)) }
            val cookie = try {
                titleMutex.withLock { insertWithUniqueTitle(userId, id, title, text.trim(), imagePath) }
            } catch (e: Exception) {
                //sin fila en room el archivo quedaria huerfano
                if (imagePath != null) imageStore.delete(id)
                throw e
            }
            onCookieChanged()
            cookie
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "createCookie", throwable = error)
        }
    }

    override suspend fun eatCookie(cookieId: String): Result<CookieModel> = changeState(cookieId, CookieState.EATEN)

    override suspend fun returnToJar(cookieId: String): Result<CookieModel> = changeState(cookieId, CookieState.ACTIVE)

    override suspend fun deleteForever(cookieId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            val cookie = cookieDao.getCookie(cookieId, userId)?.toModel() ?: return@runCatching
            if (!cookie.canBeDeletedForever()) error(CookieModel.CANNOT_DELETE_ERROR)
            //primero la fila: la galleta deja de verse aunque el archivo no se pueda borrar todavia
            cookieDao.deleteCookie(cookieId, userId)
            //el titulo queda libre
            titleMutex.withLock { if (titleKeysUserId == userId) titleKeys?.remove(cookie.titleKey) }
            if (cookie.hasImage) imageStore.delete(cookieId)
            //la copia de la nube se borra en segundo plano cuando haya red
            deletedHere.add(cookieId)
            onRemoteDeleteNeeded(userId, cookieId)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "CookieRepository",
                action = "deleteForever",
                throwable = error,
                extraKeys = mapOf("cookie_id" to cookieId)
            )
        }
    }

    override suspend fun markOpened(cookieId: String) = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext
        runCatching { cookieDao.markOpened(cookieId, userId, now()) }
        Unit
    }

    override fun observePendingUploads(): Flow<Int> {
        val userId = currentUserId() ?: return flowOf(0)
        return cookieDao.observeUnsyncedCount(userId)
    }

    //shared preferences lee un archivo la primera vez: en Dispatchers.IO para no leer disco en el hilo principal
    //sin sesion o sin preferencias (pruebas) se responde true, asi no se muestra el aviso de telefono nuevo
    override suspend fun hasDownloadedFromCloud(): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext true
        val preferences = syncPreferences ?: return@withContext true
        preferences.hasDownloaded(userId)
    }

    override suspend fun pendingUploadCount(): Int = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext 0
        runCatching { cookieDao.countUnsynced(userId) }.getOrDefault(0)
    }

    //cached then network, paso 2: la pantalla ya mostro room; aqui se traen los metadatos de la nube y se juntan
    //room vuelve a emitir con cada cambio y la pantalla se actualiza sola
    //el Mutex hace que un segundo refresco espere a que termine el primero en vez de correr encima de el
    override suspend fun refreshFromCloud(): Result<CookieRefreshResult> = withContext(Dispatchers.IO) {
        if (refreshMutex.isLocked) {
            Log.d(SYNC_TAG, "refresh waiting: another refresh is running")
        }
        refreshMutex.withLock { refreshNow() }
    }

    //se llama con refreshMutex tomado
    private suspend fun refreshNow(): Result<CookieRefreshResult> {
        return runCatching {
            Log.d(SYNC_TAG, "refresh start on ${Thread.currentThread().name}")
            val userId = currentUserId() ?: error("No user is logged in")
            val cloud = cloudStore ?: error("The cloud is not available")
            //foto de room antes de preguntarle a la nube: solo una fila que ya estaba sincronizada antes de la consulta
            //puede faltar en la nube por un borrado en otro telefono (una que se sube durante la consulta no cuenta)
            val cookiesBefore = cookieDao.getAllCookies(userId)

            val startedAt = System.nanoTime()
            val cloudCookies = cloud.fetchAll(userId)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            Log.d(SYNC_TAG, "fetched ${cloudCookies.size} cookies from firestore in $elapsedMs ms")

            val result = titleMutex.withLock { mergeWithCloud(userId, cookiesBefore, cloudCookies) }
            syncPreferences?.markDownloaded(userId)
            //un titulo con sufijo queda pendiente: se pide la subida de una vez
            if (result.renamed.isNotEmpty()) {
                onCookieChanged()
            }
            Log.d(SYNC_TAG, "refresh: ${result.arrived} arrived, ${result.removed} removed, ${result.renamed.size} renamed")
            result
        }.onFailure { error ->
            Log.w(SYNC_TAG, "refresh failed: ${error.javaClass.simpleName} ${error.message}")
            if (error !is CancellationException) {
                CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "refreshFromCloud", throwable = error)
            }
        }
    }

    //se llama con titleMutex tomado: los titulos no pueden cambiar mientras se resuelven los repetidos
    private suspend fun mergeWithCloud(
        userId: String,
        cookiesBefore: List<CookieEntity>,
        cloudCookies: List<CookieDto>,
    ): CookieRefreshResult {
        //las que se estan borrando de la nube no se tocan ni se traen de vuelta
        val idsBeingDeleted = pendingRemoteDeletes() + deletedHere

        val removed = removeCookiesDeletedOnOtherPhones(userId, cookiesBefore, cloudCookies, idsBeingDeleted)

        //nuevas de otro telefono y cambios mas nuevos (last write wins)
        //los titulos se vuelven a leer de room porque se pudieron borrar galletas
        titleKeys = null
        val keys = titleKeysOf(userId)
        var arrived = 0
        val renamed = mutableListOf<Pair<String, String>>()
        for (cloudCookie in cloudCookies) {
            if (cloudCookie.id in idsBeingDeleted) continue
            val localCookie = cookieDao.getCookie(cloudCookie.id, userId)
            if (localCookie == null) {
                insertFromCloud(userId, cloudCookie, keys, renamed)
                arrived++
            } else if (localCookie.isSynced && CookieSync.remoteWins(localCookie.updatedAt, cloudCookie.updatedAt)) {
                //una galleta con cambios sin subir no se toca aqui: el sync la sube y alla se decide cual gana
                applyCloudVersion(userId, localCookie, cloudCookie, keys, renamed)
            }
        }
        //la cache de titulos se vuelve a leer de room la proxima vez
        titleKeys = null
        return CookieRefreshResult(arrived = arrived, renamed = renamed, removed = removed)
    }

    //galletas que estaban sincronizadas y ya no estan en la nube: se borraron en otro telefono
    private suspend fun removeCookiesDeletedOnOtherPhones(
        userId: String,
        cookiesBefore: List<CookieEntity>,
        cloudCookies: List<CookieDto>,
        idsBeingDeleted: Set<String>,
    ): Int {
        val cloudIds = cloudCookies.map { it.id }.toSet()
        var removed = 0
        for (localCookie in cookiesBefore) {
            val deletedElsewhere = CookieSync.deletedInOtherPhone(
                wasSynced = localCookie.isSynced,
                existsInCloud = localCookie.id in cloudIds,
                remoteDeletePending = localCookie.id in idsBeingDeleted,
            )
            if (!deletedElsewhere) continue
            //solo se borra si no cambio desde que se leyo
            val deletedRows = cookieDao.deleteIfUnchanged(localCookie.id, userId, localCookie.updatedAt)
            if (deletedRows == 0) continue
            if (localCookie.hasImage) {
                imageStore.delete(localCookie.id)
            }
            removed++
            Log.d(SYNC_TAG, "removed id=${localCookie.id}: deleted on another phone")
        }
        return removed
    }

    //una galleta que llego de otro telefono: sin imagen local (se baja cuando haga falta)
    //si su titulo ya existe aqui, la mas nueva de las dos recibe el sufijo y queda pendiente de subir el titulo nuevo
    private suspend fun insertFromCloud(
        userId: String,
        cloudCookie: CookieDto,
        keys: HashSet<String>,
        renamed: MutableList<Pair<String, String>>,
    ) {
        val incoming = cloudCookie.toEntity(userId)
        val sameTitleHere = if (incoming.titleKey in keys) cookieDao.getByTitleKey(userId, incoming.titleKey) else null

        if (sameTitleHere == null) {
            cookieDao.insertCookie(incoming)
        } else if (CookieSync.incomingIsNewer(cloudCookie.createdAt, cloudCookie.id, sameTitleHere.createdAt, sameTitleHere.id)) {
            insertIncomingWithSuffix(cloudCookie, incoming, keys, renamed)
        } else {
            renameLocalAndInsert(userId, sameTitleHere, incoming, keys, renamed)
        }
        keys.add(incoming.titleKey)
        Log.d(SYNC_TAG, "arrived id=${cloudCookie.id} from another phone (hasImage=${cloudCookie.hasImage})")
    }

    //la que llega es la mas nueva: ella recibe el sufijo y queda pendiente de subir con el titulo nuevo
    private suspend fun insertIncomingWithSuffix(
        cloudCookie: CookieDto,
        incoming: CookieEntity,
        keys: HashSet<String>,
        renamed: MutableList<Pair<String, String>>,
    ) {
        val finalTitle = CookieModel.uniqueTitle(cloudCookie.title, keys)
        val finalTitleKey = CookieModel.titleKeyOf(finalTitle)
        val incomingRenamed = incoming.copy(title = finalTitle, titleKey = finalTitleKey, updatedAt = now(), isSynced = false)
        cookieDao.insertCookie(incomingRenamed)
        renamed.add(Pair(CookieModel.cleanTitle(cloudCookie.title), finalTitle))
        keys.add(finalTitleKey)
        Log.d(SYNC_TAG, "title clash: incoming id=${cloudCookie.id} is newer, saved as \"$finalTitle\"")
    }

    //la de este telefono es la mas nueva: ella recibe el sufijo y la que llega se guarda tal cual
    private suspend fun renameLocalAndInsert(
        userId: String,
        localCookie: CookieEntity,
        incoming: CookieEntity,
        keys: HashSet<String>,
        renamed: MutableList<Pair<String, String>>,
    ) {
        val finalTitle = CookieModel.uniqueTitle(localCookie.title, keys)
        val finalTitleKey = CookieModel.titleKeyOf(finalTitle)
        cookieDao.rename(localCookie.id, userId, finalTitle, finalTitleKey, now())
        cookieDao.insertCookie(incoming)
        renamed.add(Pair(localCookie.title, finalTitle))
        keys.add(finalTitleKey)
        Log.d(SYNC_TAG, "title clash: local id=${localCookie.id} is newer, renamed to \"$finalTitle\"")
    }

    //gano la version de la nube: se copian sus datos y se conserva la imagen local
    //si el titulo nuevo choca con otra galleta de este telefono, se le pone sufijo y queda pendiente de subir
    private suspend fun applyCloudVersion(
        userId: String,
        localCookie: CookieEntity,
        cloudCookie: CookieDto,
        keys: HashSet<String>,
        renamed: MutableList<Pair<String, String>>,
    ) {
        //el titulo viejo de esta galleta deja de estar ocupado
        keys.remove(localCookie.titleKey)
        val titleClash = CookieModel.titleKeyOf(cloudCookie.title) in keys
        val title = if (titleClash) CookieModel.uniqueTitle(cloudCookie.title, keys) else CookieModel.cleanTitle(cloudCookie.title)
        val titleKey = CookieModel.titleKeyOf(title)

        val changedRows = cookieDao.applyRemote(
            id = localCookie.id,
            userId = userId,
            title = title,
            titleKey = titleKey,
            text = cloudCookie.text,
            state = stateOf(cloudCookie.state).name,
            hasImage = cloudCookie.hasImage,
            remoteImageUrl = cloudCookie.imageUrl.ifEmpty { null },
            updatedAt = cloudCookie.updatedAt,
            seenVersion = localCookie.updatedAt,
        )
        if (changedRows == 0) {
            //la galleta cambio en este telefono mientras tanto: se queda como estaba
            keys.add(localCookie.titleKey)
            return
        }
        keys.add(titleKey)
        if (titleClash) {
            //el sufijo es un cambio de este telefono: queda pendiente de subir
            cookieDao.rename(localCookie.id, userId, title, titleKey, now())
            renamed.add(Pair(CookieModel.cleanTitle(cloudCookie.title), title))
        }
        Log.d(SYNC_TAG, "newer version from the cloud for id=${localCookie.id} (state=${cloudCookie.state})")
    }

    //sube las pendientes de a una; si una falla se cuenta y se sigue con las demas (el SyncWorker reintenta con backoff)
    //en secuencia a proposito (las descargas si van en paralelo): corre en segundo plano y nadie la espera en pantalla,
    //y todas comparten la misma subida del telefono, asi que en paralelo no suben mas rapido en total: cada foto tardaria
    //mas y con red lenta todas llegarian juntas al timeout sin marcar ninguna; de a una, cada galleta que termina queda
    //marcada como subida aunque la red se caiga despues
    override suspend fun uploadPending(): CookieUploadResult = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext CookieUploadResult()
        val cloud = cloudStore ?: return@withContext CookieUploadResult()
        val pending = try {
            cookieDao.getUnsyncedCookies(userId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "uploadPending", throwable = e)
            return@withContext CookieUploadResult(failed = 1)
        }
        var synced = 0
        var failed = 0
        for (cookie in pending) {
            try {
                uploadOne(userId, cookie, cloud)
                synced++
            } catch (e: TimeoutCancellationException) {
                //red que miente o se cayo a mitad: la galleta sigue a salvo en el telefono y se reintenta
                failed++
                Log.w(SYNC_TAG, "upload timed out id=${cookie.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                Log.w(SYNC_TAG, "upload failed id=${cookie.id}: ${e.javaClass.simpleName} ${e.message}")
            }
        }
        if (pending.isNotEmpty()) {
            Log.d(SYNC_TAG, "upload: $synced synced, $failed failed")
        }
        CookieUploadResult(synced = synced, failed = failed)
    }

    //primero la imagen y despues el documento: un documento nunca apunta a una imagen que no existe
    private suspend fun uploadOne(userId: String, cookie: CookieEntity, cloud: CookieCloudStore) {
        val imageUrl = uploadImageIfNeeded(userId, cookie, cloud)

        //el usuario pudo borrarla mientras subia la imagen: no se crea su documento y se borra lo que ya subio
        if (wasDeletedMeanwhile(userId, cookie.id)) return

        val newerInCloud = cloud.pushCookie(userId, cookie.toDto(imageUrl))
        if (wasDeletedMeanwhile(userId, cookie.id)) return

        if (newerInCloud != null) {
            keepCloudVersion(userId, cookie.id, newerInCloud)
            return
        }
        //solo queda sincronizada si no cambio mientras subia; si cambio sigue pendiente y se sube otra vez
        val markedRows = cookieDao.markAsSynced(cookie.id, imageUrl, cookie.updatedAt)
        if (markedRows == 0) {
            Log.d(SYNC_TAG, "uploaded id=${cookie.id} state=${cookie.state} (changed while uploading, stays pending)")
        } else {
            Log.d(SYNC_TAG, "uploaded id=${cookie.id} state=${cookie.state}")
        }
    }

    //sube la imagen si todavia no esta en storage y devuelve su url
    //sin imagen, ya subida o sin el archivo en el telefono, devuelve la url que ya tenia (o null)
    private suspend fun uploadImageIfNeeded(userId: String, cookie: CookieEntity, cloud: CookieCloudStore): String? {
        if (!cookie.hasImage || cookie.remoteImageUrl != null) return cookie.remoteImageUrl
        val bytes = imageStore.readBytes(cookie.localImagePath) ?: return null

        val startedAt = System.nanoTime()
        val imageUrl = cloud.uploadImage(userId, cookie.id, bytes)
        cookieDao.setRemoteImageUrl(cookie.id, imageUrl)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        Log.d(SYNC_TAG, "uploaded picture id=${cookie.id} (${bytes.size / 1024} KB) in $elapsedMs ms")
        return imageUrl
    }

    //true si la galleta ya no esta en room; en ese caso se pide borrar lo que ya se subio
    private suspend fun wasDeletedMeanwhile(userId: String, cookieId: String): Boolean {
        val cookie = cookieDao.getCookie(cookieId, userId)
        if (cookie != null) return false
        onRemoteDeleteNeeded(userId, cookieId)
        return true
    }

    //otro telefono la cambio despues (last write wins): se queda la version de la nube
    private suspend fun keepCloudVersion(userId: String, cookieId: String, cloudCookie: CookieDto) {
        Log.d(SYNC_TAG, "cloud version of id=$cookieId is newer, keeping it")
        titleMutex.withLock {
            val localCookie = cookieDao.getCookie(cookieId, userId)
            if (localCookie != null) {
                applyCloudVersion(userId, localCookie, cloudCookie, titleKeysOf(userId), mutableListOf())
            }
        }
    }

    //imagenes que faltan de las 10 del dia: async por cada una en Dispatchers.IO y awaitAll() espera a todas
    //supervisorScope: si una descarga falla (por ejemplo se cae la red) las otras siguen y las que ya bajaron quedan
    //guardadas; cada una devuelve su propio Result y el ViewModel decide que hacer con las que fallaron
    //el Semaphore deja maximo 4 a la vez: el tiempo de cada foto (~400 kb) es sobre todo la espera de la respuesta, y
    //varias a la vez la esconden; mas de 4 solo se reparten el mismo ancho de banda y suman ~400 kb de ram cada una
    override suspend fun downloadImages(cookieIds: List<String>): Map<String, Result<CookieModel>> = withContext(Dispatchers.IO) {
        if (cookieIds.isEmpty()) return@withContext emptyMap()
        val permits = Semaphore(MAX_PARALLEL_DOWNLOADS)
        val startedAt = System.nanoTime()
        Log.d(SYNC_TAG, "downloading ${cookieIds.size} pictures of today's jar, up to $MAX_PARALLEL_DOWNLOADS at a time")

        val downloadResults = supervisorScope {
            //primero se lanzan todas; cada una espera su turno en el Semaphore
            val downloads = mutableListOf<Deferred<Result<CookieModel>>>()
            for (cookieId in cookieIds) {
                val download = async(Dispatchers.IO) {
                    permits.withPermit { downloadImage(cookieId) }
                }
                downloads.add(download)
            }
            //despues se espera a que terminen todas
            downloads.awaitAll()
        }

        //resultado de cada galleta por su id (las listas van en el mismo orden)
        val resultsById = HashMap<String, Result<CookieModel>>()
        var downloaded = 0
        var failed = 0
        for (index in cookieIds.indices) {
            val result = downloadResults[index]
            resultsById[cookieIds[index]] = result
            if (result.isSuccess) downloaded++ else failed++
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        Log.d(SYNC_TAG, "today's pictures: $downloaded downloaded, $failed failed in $elapsedMs ms")
        resultsById
    }

    override suspend fun downloadImage(cookieId: String): Result<CookieModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            val cloud = cloudStore ?: error("The cloud is not available")
            //la misma imagen la pueden pedir a la vez el detalle y las del jarro de hoy: una sola descarga por galleta,
            //y la segunda encuentra la imagen ya guardada
            val lock = downloadLocks.getOrPut(cookieId) { Mutex() }
            lock.withLock { downloadImageNow(userId, cookieId, cloud) }
        }.onFailure { error ->
            Log.w(SYNC_TAG, "download failed id=$cookieId: ${error.javaClass.simpleName} ${error.message}")
            //tambien si se cancelo (la red se cayo a mitad): no debe quedar ningun temporal
            withContext(NonCancellable) { imageStore.deleteTemp(cookieId) }
            if (error !is CancellationException) {
                CrashlyticsHelper.logNonFatal(
                    componentName = "CookieRepository",
                    action = "downloadImage",
                    throwable = error,
                    extraKeys = mapOf("cookie_id" to cookieId)
                )
            }
        }
    }

    //se llama con el candado de la galleta tomado
    private suspend fun downloadImageNow(userId: String, cookieId: String, cloud: CookieCloudStore): CookieModel {
        val entity = cookieDao.getCookie(cookieId, userId) ?: error("This cookie no longer exists")
        val cookie = entity.toModel()
        //no tiene imagen, o otra descarga ya la dejo en el telefono
        if (!cookie.hasImage || cookie.hasLocalImage()) return cookie

        val startedAt = System.nanoTime()
        Log.d(SYNC_TAG, "download start id=$cookieId on ${Thread.currentThread().name}")
        //se baja completa a memoria y el almacenamiento privado la escribe en {id}.jpg.tmp y la renombra:
        //si la red se cae a mitad no queda ningun archivo a medias
        val bytes = cloud.downloadImage(userId, cookieId)
        val path = imageStore.save(cookieId, bytes)
        val updatedRows = cookieDao.setLocalImage(cookieId, userId, path)
        if (updatedRows == 0) {
            //se borro mientras bajaba
            imageStore.delete(cookieId)
            error("This cookie no longer exists")
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        Log.d(SYNC_TAG, "downloaded picture id=$cookieId (${bytes.size / 1024} KB) in $elapsedMs ms")
        return cookie.copy(localImagePath = path)
    }

    override suspend fun deleteRemoteCopy(userId: String, cookieId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (currentUserId() != userId) error("The cookie belongs to another account")
            val cloud = cloudStore ?: error("The cloud is not available")
            cloud.deleteCookie(userId, cookieId)
        }
    }

    //regalo aceptado (pasos 1 y 2 de la aceptacion): la foto ya se bajo a memoria; aqui se escribe {id}.jpg.tmp, se renombra
    //a .jpg y se inserta la fila con isSynced = false; desde aqui la galleta es del usuario aunque se caiga la red
    //y el sync la sube a su carpeta de la nube con el mismo id
    override suspend fun addReceivedCookie(
        id: String,
        title: String,
        text: String,
        receivedFrom: String,
        jpegBytes: ByteArray?,
    ): Result<CookieModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            saveReceivedCookie(userId, id, title, text, receivedFrom, jpegBytes)
        }.onFailure { error ->
            //tambien si se cancelo: no debe quedar ningun temporal
            withContext(NonCancellable) { imageStore.deleteTemp(id) }
            if (error !is CancellationException) {
                CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "addReceivedCookie", throwable = error)
            }
        }
    }

    private suspend fun saveReceivedCookie(
        userId: String,
        id: String,
        title: String,
        text: String,
        receivedFrom: String,
        jpegBytes: ByteArray?,
    ): CookieModel {
        //ya se habia aceptado (por ejemplo se reintento): no se duplica
        val alreadySaved = cookieDao.getCookie(id, userId)
        if (alreadySaved != null) return alreadySaved.toModel()

        val imagePath = if (jpegBytes != null) imageStore.save(id, jpegBytes) else null
        val cleanText = text.trim().take(CookieModel.MAX_TEXT_LENGTH)
        val cookie = try {
            titleMutex.withLock { insertWithUniqueTitle(userId, id, title, cleanText, imagePath, receivedFrom) }
        } catch (e: Exception) {
            //sin fila en room la imagen no puede quedar sola en el telefono
            if (imagePath != null) imageStore.delete(id)
            throw e
        }
        Log.d(SYNC_TAG, "gift accepted id=$id from @$receivedFrom saved as \"${cookie.title}\" (hasImage=${cookie.hasImage})")
        onCookieChanged()
        return cookie
    }

    //para regalar: el jpg ya comprimido del archivo privado; si se desalojo se baja primero de la nube
    override suspend fun imageBytesFor(cookieId: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            readImageBytes(userId, cookieId)
        }
    }

    //null si la galleta no tiene imagen
    private suspend fun readImageBytes(userId: String, cookieId: String): ByteArray? {
        val entity = cookieDao.getCookie(cookieId, userId) ?: error("This cookie no longer exists")
        val cookie = entity.toModel()
        if (!cookie.hasImage) return null

        val bytesOnPhone = imageStore.readBytes(cookie.localImagePath)
        if (bytesOnPhone != null) return bytesOnPhone

        val downloaded = downloadImage(cookieId).getOrThrow()
        return imageStore.readBytes(downloaded.localImagePath) ?: error("Couldn't read the picture of the cookie")
    }

    override fun currentUserId(): String? = userIdProvider()

    override fun hasCameraPermission(): Boolean = cameraHelper?.hasCameraPermission() ?: false

    //crear el archivo temporal de la camara escribe en disco: en Dispatchers.IO, no en el toque del boton (hilo principal)
    override suspend fun createCaptureUri(): String? = withContext(Dispatchers.IO) {
        val camera = cameraHelper ?: return@withContext null
        val pictureUri = camera.createPictureUri() ?: return@withContext null
        pictureUri.toString()
    }

    override suspend fun discardCapture(uri: String) = withContext(Dispatchers.IO) {
        runCatching { cameraHelper?.deletePicture(uri) }
        Unit
    }

    private suspend fun changeState(cookieId: String, state: CookieState): Result<CookieModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            val changed = cookieDao.updateState(cookieId, userId, state.name, now())
            if (changed == 0) error("This cookie no longer exists")
            onCookieChanged()
            cookieDao.getCookie(cookieId, userId)?.toModel() ?: error("This cookie no longer exists")
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "CookieRepository",
                action = "changeState",
                throwable = error,
                extraKeys = mapOf("cookie_id" to cookieId, "state" to state.name)
            )
        }
    }

    //calcula el titulo libre y lo inserta; si el indice unico lo rechaza (otro titulo igual llego en medio) se recalcula
    //receivedFrom: username de quien la regalo (null si es propia)
    private suspend fun insertWithUniqueTitle(
        userId: String,
        id: String,
        title: String,
        text: String,
        imagePath: String?,
        receivedFrom: String? = null,
    ): CookieModel {
        var lastError: SQLiteConstraintException? = null
        repeat(MAX_INSERT_ATTEMPTS) {
            val finalTitle = CookieModel.uniqueTitle(title, titleKeysOf(userId))
            val createdAt = now()
            val cookie = CookieModel(
                id = id,
                title = finalTitle,
                text = text,
                state = CookieState.ACTIVE,
                hasImage = imagePath != null,
                localImagePath = imagePath,
                lastOpenedAt = createdAt,
                receivedFrom = receivedFrom,
                createdAt = createdAt,
                updatedAt = createdAt,
                isSynced = false,
            )
            try {
                cookieDao.insertCookie(cookie.toEntity(userId))
                titleKeysOf(userId).add(cookie.titleKey)
                return cookie
            } catch (e: SQLiteConstraintException) {
                //la cache no tenia ese titulo (por ejemplo llego de otro lado): se recarga desde room y se recalcula
                Log.w(TAG, "title \"$finalTitle\" already in room, reloading the title cache")
                titleKeys = null
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Could not save the cookie")
    }

    //llaves de los titulos del usuario: de la cache si ya estan, si no una sola consulta a room
    //se llama con titleMutex tomado
    private suspend fun titleKeysOf(userId: String): HashSet<String> {
        val cached = titleKeys
        if (cached != null && titleKeysUserId == userId) {
            Log.d(TAG, "title cache hit (${cached.size} titles in memory)")
            return cached
        }
        val loaded = cookieDao.getTitleKeys(userId).toHashSet()
        Log.d(TAG, "title cache miss: loaded ${loaded.size} titles from room")
        titleKeys = loaded
        titleKeysUserId = userId
        return loaded
    }

    override suspend fun enforceImageBudget(protectedIds: Set<String>) = withContext(Dispatchers.IO) {
        runCatching {
            budgetMutex.withLock {
                val budget = imageBudgetFor(imageStore.usableSpace())
                val used = imageStore.usedBytes()
                if (used <= budget) {
                    Log.d(TAG, "disk budget ok: ${used / 1024} KB used of ${budget / 1024} KB")
                    return@withLock
                }
                val images = cookieDao.getImagesOnPhone().map { row ->
                    StoredCookieImage(row.id, imageStore.sizeOf(row.id), row.lastOpenedAt, row.isSynced)
                }
                val evictions = CookieImageBudget.evictions(images, used, budget, protectedIds)
                Log.d(TAG, "disk budget exceeded: ${used / 1024} KB used of ${budget / 1024} KB, evicting ${evictions.size}")
                for (image in evictions) {
                    //primero la ruta en room: la pantalla deja de buscar el archivo; remoteImageUrl se conserva
                    cookieDao.clearLocalImage(image.cookieId)
                    imageStore.delete(image.cookieId)
                    Log.d(TAG, "disk evict id=${image.cookieId} (${image.sizeBytes / 1024} KB, lastOpenedAt=${image.lastOpenedAt})")
                }
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "enforceImageBudget", throwable = error)
        }
        Unit
    }

    private fun CookieEntity.toModel() = CookieModel(
        id = id,
        title = title,
        text = text,
        state = stateOf(state),
        hasImage = hasImage,
        localImagePath = localImagePath,
        remoteImageUrl = remoteImageUrl,
        lastOpenedAt = lastOpenedAt,
        receivedFrom = receivedFrom,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced,
    )

    private fun CookieModel.toEntity(userId: String) = CookieEntity(
        id = id,
        userId = userId,
        title = title,
        titleKey = titleKey,
        text = text,
        state = state.name,
        hasImage = hasImage,
        localImagePath = localImagePath,
        remoteImageUrl = remoteImageUrl,
        lastOpenedAt = lastOpenedAt,
        receivedFrom = receivedFrom,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced,
    )

    private fun CookieEntity.toDto(imageUrl: String?) = CookieDto(
        id = id,
        userId = userId,
        title = title,
        text = text,
        state = state,
        hasImage = hasImage && imageUrl != null,
        imageUrl = imageUrl.orEmpty(),
        receivedFrom = receivedFrom,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    //una galleta que llega de la nube ya esta sincronizada y su imagen (si tiene) aun no esta en el telefono
    private fun CookieDto.toEntity(userId: String) = CookieEntity(
        id = id,
        userId = userId,
        title = CookieModel.cleanTitle(title),
        titleKey = CookieModel.titleKeyOf(title),
        text = text,
        state = stateOf(state).name,
        hasImage = hasImage,
        localImagePath = null,
        remoteImageUrl = imageUrl.ifEmpty { null },
        lastOpenedAt = 0L,
        receivedFrom = receivedFrom,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = true,
    )

    private fun CookieListRow.toItem() = CookieListItem(
        id = id,
        title = title,
        state = stateOf(state),
        hasImage = hasImage,
        receivedFrom = receivedFrom,
        isSynced = isSynced,
    )

    private fun stateOf(value: String) = runCatching { CookieState.valueOf(value) }.getOrDefault(CookieState.ACTIVE)

    companion object {
        private const val MAX_INSERT_ATTEMPTS = 3
        //descargas de las fotos del jarro de hoy al mismo tiempo
        private const val MAX_PARALLEL_DOWNLOADS = 4
        //mismo tag de la cache de imagenes para ver todo el caching junto en logcat
        private const val TAG = "CookieCache"
        //subidas, bajadas y cambios con la nube
        private const val SYNC_TAG = "CookieSync"
    }
}

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

//cookie jar local first: el repositorio solo orquesta
//los datos van a room (CookieDao), la imagen comprimida (CookieImageCompressor) va al almacenamiento privado (CookiePrivateImageStore)
//caching: set de titulos en memoria para los repetidos y presupuesto de imagenes en disco con desalojo lru
//cada fila lleva el uid de firebase del dueno, como photo_entries; cada cambio queda con isSynced = false para la nube
//nube (CookieCloudStore): el sync sube lo pendiente, al abrir el jarro se bajan los cambios de otros telefonos
//y las imagenes que faltan se bajan cuando se necesitan
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
    private val deletedHere: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
    //un candado por galleta para no bajar dos veces la misma imagen
    private val downloadLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    override fun observeCookies(): Flow<List<CookieModel>> {
        //sin sesion no se muestran galletas de nadie
        val userId = currentUserId() ?: return flowOf(emptyList())
        return cookieDao.observeCookies(userId).map { entities -> entities.map { it.toModel() } }
    }

    override fun observeCookieList(): Flow<List<CookieListItem>> {
        val userId = currentUserId() ?: return flowOf(emptyList())
        return cookieDao.observeList(userId).map { rows -> rows.map { it.toItem() } }
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

    override fun hasDownloadedFromCloud(): Boolean {
        val userId = currentUserId() ?: return true
        return syncPreferences?.hasDownloaded(userId) ?: true
    }

    override suspend fun pendingUploadCount(): Int = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext 0
        runCatching { cookieDao.countUnsynced(userId) }.getOrDefault(0)
    }

    //cached then network, paso 2: la pantalla ya mostro room; aqui se traen los metadatos de la nube y se juntan
    //room vuelve a emitir con cada cambio y la pantalla se actualiza sola
    override suspend fun refreshFromCloud(): Result<CookieRefreshResult> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            val cloud = cloudStore ?: error("The cloud is not available")
            //foto de room antes de preguntarle a la nube: solo una fila que ya estaba sincronizada antes de la consulta
            //puede faltar en la nube por un borrado en otro telefono (una que se sube durante la consulta no cuenta)
            val before = cookieDao.getAllCookies(userId)
            val started = System.nanoTime()
            val remote = cloud.fetchAll(userId)
            Log.d(SYNC_TAG, "fetched ${remote.size} cookies from firestore in ${(System.nanoTime() - started) / 1_000_000} ms")
            val result = titleMutex.withLock { mergeRemote(userId, before, remote) }
            syncPreferences?.markDownloaded(userId)
            //un titulo con sufijo queda pendiente: se pide la subida de una vez
            if (result.renamed.isNotEmpty()) onCookieChanged()
            Log.d(SYNC_TAG, "refresh: ${result.arrived} arrived, ${result.removed} removed, ${result.renamed.size} renamed")
            result
        }.onFailure { error ->
            Log.w(SYNC_TAG, "refresh failed: ${error.javaClass.simpleName} ${error.message}")
            CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "refreshFromCloud", throwable = error)
        }
    }

    //se llama con titleMutex tomado: los titulos no pueden cambiar mientras se resuelven los repetidos
    private suspend fun mergeRemote(userId: String, before: List<CookieEntity>, remote: List<CookieDto>): CookieRefreshResult {
        val skipped = pendingRemoteDeletes() + deletedHere
        val remoteIds = remote.map { it.id }.toSet()
        var removed = 0
        //1. borradas en otro telefono
        for (local in before) {
            if (!CookieSync.deletedInOtherPhone(local.isSynced, local.id in remoteIds, local.id in skipped)) continue
            if (cookieDao.deleteIfUnchanged(local.id, userId, local.updatedAt) > 0) {
                if (local.hasImage) imageStore.delete(local.id)
                removed++
                Log.d(SYNC_TAG, "removed id=${local.id}: deleted on another phone")
            }
        }
        //2. nuevas de otro telefono y cambios mas nuevos (last write wins)
        titleKeys = null
        val keys = titleKeysOf(userId)
        var arrived = 0
        val renamed = mutableListOf<Pair<String, String>>()
        for (dto in remote) {
            if (dto.id in skipped) continue
            val current = cookieDao.getCookie(dto.id, userId)
            if (current == null) {
                insertFromCloud(userId, dto, keys, renamed)
                arrived++
            } else if (current.isSynced && CookieSync.remoteWins(current.updatedAt, dto.updatedAt)) {
                applyRemote(userId, current, dto, keys, renamed)
            }
        }
        //la cache de titulos se vuelve a leer de room la proxima vez
        titleKeys = null
        return CookieRefreshResult(arrived = arrived, renamed = renamed, removed = removed)
    }

    //una galleta que llego de otro telefono: sin imagen local (se baja cuando haga falta)
    //si su titulo ya existe aqui, la mas nueva de las dos recibe el sufijo y queda pendiente de subir el titulo nuevo
    private suspend fun insertFromCloud(userId: String, dto: CookieDto, keys: HashSet<String>, renamed: MutableList<Pair<String, String>>) {
        val incoming = dto.toEntity(userId)
        val clash = if (incoming.titleKey in keys) cookieDao.getByTitleKey(userId, incoming.titleKey) else null
        when {
            clash == null -> cookieDao.insertCookie(incoming)
            CookieSync.incomingIsNewer(dto.createdAt, dto.id, clash.createdAt, clash.id) -> {
                val finalTitle = CookieModel.uniqueTitle(dto.title, keys)
                cookieDao.insertCookie(
                    incoming.copy(title = finalTitle, titleKey = CookieModel.titleKeyOf(finalTitle), updatedAt = now(), isSynced = false)
                )
                renamed += CookieModel.cleanTitle(dto.title) to finalTitle
                keys.add(CookieModel.titleKeyOf(finalTitle))
                Log.d(SYNC_TAG, "title clash: incoming id=${dto.id} is newer, saved as \"$finalTitle\"")
            }
            else -> {
                val finalTitle = CookieModel.uniqueTitle(clash.title, keys)
                cookieDao.rename(clash.id, userId, finalTitle, CookieModel.titleKeyOf(finalTitle), now())
                cookieDao.insertCookie(incoming)
                renamed += clash.title to finalTitle
                keys.add(CookieModel.titleKeyOf(finalTitle))
                Log.d(SYNC_TAG, "title clash: local id=${clash.id} is newer, renamed to \"$finalTitle\"")
            }
        }
        keys.add(incoming.titleKey)
        Log.d(SYNC_TAG, "arrived id=${dto.id} from another phone (hasImage=${dto.hasImage})")
    }

    //gano la version de la nube: se copian sus datos y se conserva la imagen local
    //si el titulo nuevo choca con otra galleta de este telefono, se le pone sufijo y queda pendiente de subir
    private suspend fun applyRemote(
        userId: String,
        current: CookieEntity,
        dto: CookieDto,
        keys: HashSet<String>,
        renamed: MutableList<Pair<String, String>>,
    ) {
        val remoteKey = CookieModel.titleKeyOf(dto.title)
        keys.remove(current.titleKey)
        val clash = remoteKey in keys
        val title = if (clash) CookieModel.uniqueTitle(dto.title, keys) else CookieModel.cleanTitle(dto.title)
        val changed = cookieDao.applyRemote(
            id = current.id, userId = userId, title = title, titleKey = CookieModel.titleKeyOf(title), text = dto.text,
            state = stateOf(dto.state).name, hasImage = dto.hasImage, remoteImageUrl = dto.imageUrl.ifEmpty { null },
            updatedAt = dto.updatedAt, seenVersion = current.updatedAt,
        )
        if (changed == 0) {
            keys.add(current.titleKey)
            return
        }
        keys.add(CookieModel.titleKeyOf(title))
        if (clash) {
            cookieDao.rename(current.id, userId, title, CookieModel.titleKeyOf(title), now())
            renamed += CookieModel.cleanTitle(dto.title) to title
        }
        Log.d(SYNC_TAG, "newer version from the cloud for id=${current.id} (state=${dto.state})")
    }

    //sube las pendientes de a una; si una falla se cuenta y se sigue con las demas (el SyncWorker reintenta con backoff)
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
        if (pending.isNotEmpty()) Log.d(SYNC_TAG, "upload: $synced synced, $failed failed")
        CookieUploadResult(synced = synced, failed = failed)
    }

    //primero la imagen y despues el documento: un documento nunca apunta a una imagen que no existe
    private suspend fun uploadOne(userId: String, cookie: CookieEntity, cloud: CookieCloudStore) {
        var imageUrl = cookie.remoteImageUrl
        if (cookie.hasImage && imageUrl == null) {
            val bytes = imageStore.readBytes(cookie.localImagePath)
            if (bytes != null) {
                val started = System.nanoTime()
                imageUrl = cloud.uploadImage(userId, cookie.id, bytes)
                cookieDao.setRemoteImageUrl(cookie.id, imageUrl)
                Log.d(SYNC_TAG, "uploaded picture id=${cookie.id} (${bytes.size / 1024} KB) in ${(System.nanoTime() - started) / 1_000_000} ms")
            }
        }
        //el usuario pudo borrarla mientras subia la imagen: no se crea su documento y se borra lo que ya subio
        if (cookieDao.getCookie(cookie.id, userId) == null) {
            onRemoteDeleteNeeded(userId, cookie.id)
            return
        }
        val dto = cookie.toDto(imageUrl)
        val newerInCloud = cloud.pushCookie(userId, dto)
        if (cookieDao.getCookie(cookie.id, userId) == null) {
            onRemoteDeleteNeeded(userId, cookie.id)
            return
        }
        if (newerInCloud != null) {
            //otro telefono la cambio despues (last write wins): se queda la version de la nube
            Log.d(SYNC_TAG, "cloud version of id=${cookie.id} is newer, keeping it")
            titleMutex.withLock {
                val current = cookieDao.getCookie(cookie.id, userId) ?: return@withLock
                applyRemote(userId, current, newerInCloud, titleKeysOf(userId), mutableListOf())
            }
            return
        }
        val marked = cookieDao.markAsSynced(cookie.id, imageUrl, cookie.updatedAt)
        Log.d(SYNC_TAG, "uploaded id=${cookie.id} state=${cookie.state}" + if (marked == 0) " (changed while uploading, stays pending)" else "")
    }

    override suspend fun downloadImage(cookieId: String): Result<CookieModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            val cloud = cloudStore ?: error("The cloud is not available")
            //la misma imagen la pueden pedir a la vez el detalle y las del jarro de hoy: una sola descarga por galleta,
            //y la segunda encuentra la imagen ya guardada
            downloadLocks.getOrPut(cookieId) { Mutex() }.withLock {
                val cookie = cookieDao.getCookie(cookieId, userId)?.toModel() ?: error("This cookie no longer exists")
                if (!cookie.hasImage || cookie.hasLocalImage()) return@withLock cookie
                val started = System.nanoTime()
                //se baja completa a memoria y el almacenamiento privado la escribe en {id}.jpg.tmp y la renombra:
                //si la red se cae a mitad no queda ningun archivo a medias
                val bytes = cloud.downloadImage(userId, cookieId)
                val path = imageStore.save(cookieId, bytes)
                if (cookieDao.setLocalImage(cookieId, userId, path) == 0) {
                    //se borro mientras bajaba
                    imageStore.delete(cookieId)
                    error("This cookie no longer exists")
                }
                Log.d(SYNC_TAG, "downloaded picture id=$cookieId (${bytes.size / 1024} KB) in ${(System.nanoTime() - started) / 1_000_000} ms")
                cookie.copy(localImagePath = path)
            }
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
            //ya se habia aceptado (por ejemplo se reintento): no se duplica
            cookieDao.getCookie(id, userId)?.let { return@runCatching it.toModel() }
            val imagePath = jpegBytes?.let { imageStore.save(id, it) }
            val cookie = try {
                titleMutex.withLock {
                    insertWithUniqueTitle(userId, id, title, text.trim().take(CookieModel.MAX_TEXT_LENGTH), imagePath, receivedFrom)
                }
            } catch (e: Exception) {
                if (imagePath != null) imageStore.delete(id)
                throw e
            }
            Log.d(SYNC_TAG, "gift accepted id=$id from @$receivedFrom saved as \"${cookie.title}\" (hasImage=${cookie.hasImage})")
            onCookieChanged()
            cookie
        }.onFailure { error ->
            //tambien si se cancelo: no debe quedar ningun temporal
            withContext(NonCancellable) { imageStore.deleteTemp(id) }
            if (error !is CancellationException) {
                CrashlyticsHelper.logNonFatal(componentName = "CookieRepository", action = "addReceivedCookie", throwable = error)
            }
        }
    }

    //para regalar: el jpg ya comprimido del archivo privado; si se desalojo se baja primero de la nube
    override suspend fun imageBytesFor(cookieId: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            val cookie = cookieDao.getCookie(cookieId, userId)?.toModel() ?: error("This cookie no longer exists")
            if (!cookie.hasImage) return@runCatching null
            imageStore.readBytes(cookie.localImagePath)?.let { return@runCatching it }
            val downloaded = downloadImage(cookieId).getOrThrow()
            imageStore.readBytes(downloaded.localImagePath) ?: error("Couldn't read the picture of the cookie")
        }
    }

    override fun currentUserId(): String? = userIdProvider()

    override fun hasCameraPermission(): Boolean = cameraHelper?.hasCameraPermission() ?: false

    override fun createCaptureUri(): String? = cameraHelper?.createPictureUri()?.toString()

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
        //mismo tag de la cache de imagenes para ver todo el caching junto en logcat
        private const val TAG = "CookieCache"
        //subidas, bajadas y cambios con la nube
        private const val SYNC_TAG = "CookieSync"
    }
}

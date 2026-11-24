package com.moviles.ark.data.repositories

import android.database.sqlite.SQLiteConstraintException
import com.moviles.ark.data.local.CookieImageCompressor
import com.moviles.ark.data.local.CookiePrivateImageStore
import com.moviles.ark.data.local.daos.CookieDao
import com.moviles.ark.data.local.entities.CookieEntity
import com.moviles.ark.data.local.entities.CookieListRow
import com.moviles.ark.data.local.sensors.CameraHelper
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.domain.models.CookieState
import com.moviles.ark.domain.repositories.CookieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

//cookie jar local first: el repositorio solo orquesta
//los datos van a room (CookieDao), la imagen comprimida (CookieImageCompressor) va al almacenamiento privado (CookiePrivateImageStore)
//cada fila lleva el uid de firebase del dueno, como photo_entries; cada cambio queda con isSynced = false para la nube
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
) : CookieRepository {

    //dos creaciones a la vez podrian calcular el mismo sufijo: se hacen de a una
    private val createMutex = Mutex()

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
        CookieModel.uniqueTitle(title, existingTitleKeys(userId))
    }

    override suspend fun createCookie(title: String, text: String, imageUri: String?): Result<CookieModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUserId() ?: error("No user is logged in")
            CookieModel.validationError(title, text)?.let { error(it) }
            val id = UUID.randomUUID().toString()
            //primero la imagen: comprimir (cpu) y guardar el archivo privado (disco); si falla no se crea la galleta
            val imagePath = imageUri?.let { uri -> imageStore.save(id, imageCompressor.compress(uri)) }
            val cookie = try {
                createMutex.withLock { insertWithUniqueTitle(userId, id, title, text.trim(), imagePath) }
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
            if (cookie.hasImage) imageStore.delete(cookieId)
            //la copia de la nube se borra en segundo plano cuando haya red
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
    private suspend fun insertWithUniqueTitle(userId: String, id: String, title: String, text: String, imagePath: String?): CookieModel {
        var lastError: SQLiteConstraintException? = null
        repeat(MAX_INSERT_ATTEMPTS) {
            val finalTitle = CookieModel.uniqueTitle(title, existingTitleKeys(userId))
            val createdAt = now()
            val cookie = CookieModel(
                id = id,
                title = finalTitle,
                text = text,
                state = CookieState.ACTIVE,
                hasImage = imagePath != null,
                localImagePath = imagePath,
                lastOpenedAt = createdAt,
                createdAt = createdAt,
                updatedAt = createdAt,
                isSynced = false,
            )
            try {
                cookieDao.insertCookie(cookie.toEntity(userId))
                return cookie
            } catch (e: SQLiteConstraintException) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Could not save the cookie")
    }

    private suspend fun existingTitleKeys(userId: String): Set<String> = cookieDao.getTitleKeys(userId).toHashSet()

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
    }
}

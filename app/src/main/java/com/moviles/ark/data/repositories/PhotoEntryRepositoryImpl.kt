package com.moviles.ark.data.repositories

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.local.sensors.CameraHelper
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.PhotoEntryDto
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.domain.repositories.PhotoEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

//fotos por usuario: cada fila de room lleva el uid y la pantalla solo lee las del usuario con sesion
//el jpg se guarda una sola vez en la galeria publica (MediaStore, Pictures/Ark/User_<uid>) y room guarda su direccion content://
//el sync sube una copia a firebase storage; las fotos de dias pasados que falten en el telefono se traen de ahi
class PhotoEntryRepositoryImpl(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val photoEntryDao: PhotoEntryDao,
    private val firestore: FirebaseFirestore? = null,
    private val storage: FirebaseStorage? = null,
    //avisa al SyncManager que hay una foto por subir; no se espera
    private val onPhotoSaved: () -> Unit = {}
) : PhotoEntryRepository {

    override fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryModel>> {
        //sin sesion no se muestran fotos de nadie
        val userId = auth.currentUser?.uid ?: return flowOf(emptyList())
        return photoEntryDao.getPhotosBetween(userId, fromMillis, toMillis).map { entities ->
            entities.map { entity ->
                PhotoEntryModel(
                    id = entity.id,
                    localFilePath = entity.localFilePath,
                    caption = entity.caption,
                    takenAt = entity.takenAt,
                    remoteUrl = entity.remoteUrl?.takeIf { it.startsWith("https://") }
                )
            }
        }
    }

    override suspend fun refreshFromCloud(fromMillis: Long, toMillis: Long) = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext
        val remote = firestore ?: return@withContext
        runCatching {
            val documents = remote.collection("users").document(userId).collection("photos")
                .whereGreaterThanOrEqualTo("takenAt", fromMillis)
                .whereLessThan("takenAt", toMillis)
                .get()
                .await()
            for (document in documents) {
                val dto = document.toObject(PhotoEntryDto::class.java)
                //solo sirven las que ya tienen copia en storage; las viejas solo guardaban la ruta del otro telefono
                if (!dto.remoteUrl.startsWith("https://")) continue
                if (photoEntryDao.getPhotoByTakenAt(userId, dto.takenAt) != null) continue
                photoEntryDao.insertPhoto(
                    PhotoEntryEntity(
                        userId = userId,
                        //no hay archivo en este telefono: la pantalla la carga desde remoteUrl
                        localFilePath = "",
                        caption = dto.caption,
                        takenAt = dto.takenAt,
                        remoteUrl = dto.remoteUrl,
                        isSynced = true
                    )
                )
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryRepository",
                action = "refreshFromCloud",
                throwable = error
            )
        }
        Unit
    }

    //la camara del sistema guarda la foto en un archivo que se crea aqui (FileProvider)
    private val cameraHelper by lazy { CameraHelper(context) }

    override fun hasCameraPermission(): Boolean = cameraHelper.hasCameraPermission()

    override fun createCaptureUri(): String? = cameraHelper.createPictureUri()?.toString()

    override suspend fun savePhoto(sourceUri: String, caption: String, takenAt: Long): Result<PhotoEntryModel> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = auth.currentUser?.uid ?: error("No user is logged in")
            //guarda directamente en la galeria publica mediastore sin duplicar en filesDir
            val publicUri = exportToPublicGallery(sourceUri, takenAt, userId)
            val finalPath = publicUri?.toString() ?: sourceUri

            val entity = PhotoEntryEntity(
                userId = userId,
                localFilePath = finalPath,
                caption = caption,
                takenAt = takenAt,
                remoteUrl = null,
                isSynced = false
            )

            val insertedId = photoEntryDao.insertPhoto(entity)
            onPhotoSaved()

            PhotoEntryModel(
                id = insertedId,
                localFilePath = finalPath,
                caption = caption,
                takenAt = takenAt
            )
        }
    }


    override suspend fun deletePhoto(photoId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = auth.currentUser?.uid ?: error("No user is logged in")
            val entity = photoEntryDao.getPhotoById(photoId, userId)
            if (entity != null) {
                //elimina la foto de mediastore publica si es content:// o del archivo si es ruta directa
                if (entity.localFilePath.startsWith("content://")) {
                    runCatching { context.contentResolver.delete(Uri.parse(entity.localFilePath), null, null) }
                } else {
                    val localFile = File(entity.localFilePath)
                    if (localFile.exists()) {
                        localFile.delete()
                    }
                }
                //elimina el registro en room
                photoEntryDao.deletePhoto(photoId, userId)
                //y la copia en la nube si ya se habia subido; sin red queda alla, pero solo se borra la de hoy
                //y la descarga solo trae dias pasados, asi que no vuelve a aparecer
                //sin internet esas esperas no terminaban y el borrado (o el retake) se quedaba cargando:
                //firestore deja el borrado en su cola hasta que vuelva la red; storage se intenta un rato y se deja
                //las dos esperas van a la vez para que el usuario espere solo una
                if (entity.isSynced) {
                    coroutineScope {
                        launch {
                            runCatching {
                                withTimeoutOrNull(REMOTE_DELETE_TIMEOUT_MS) {
                                    storage?.reference?.child(storagePath(userId, entity.takenAt))?.delete()?.await()
                                }
                            }
                        }
                        launch {
                            runCatching {
                                withTimeoutOrNull(REMOTE_DELETE_TIMEOUT_MS) {
                                    firestore?.collection("users")?.document(userId)?.collection("photos")
                                        ?.document(entity.takenAt.toString())?.delete()?.await()
                                }
                            }
                        }
                    }
                }
            }
            Unit
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryRepository",
                action = "deletePhoto",
                throwable = error,
                extraKeys = mapOf("photo_id" to photoId)
            )
        }
    }

    //guarda la foto en la galeria publica del sistema usando MediaStore API en subcarpetas por usuario
    private suspend fun exportToPublicGallery(sourceUri: String, takenAt: Long, userId: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = "Ark_Photo_${takenAt}.jpg"
            val mimeType = "image/jpeg"
            val userFolder = "Ark/User_${userId.take(8)}"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_ADDED, takenAt / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + userFolder)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, contentValues)
            if (uri != null) {
                val inputStream: InputStream = if (sourceUri.startsWith("content://") || sourceUri.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(sourceUri)) ?: error("Cannot open $sourceUri")
                } else {
                    FileInputStream(File(sourceUri))
                }
                inputStream.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            }
            uri
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryRepository",
                action = "exportToPublicGallery",
                throwable = error
            )
        }.getOrNull()
    }

    companion object {
        //donde queda el jpg en firebase storage; el nombre es el momento de la foto, igual que el id del documento en firestore
        fun storagePath(userId: String, takenAt: Long): String = "users/$userId/photos/$takenAt.jpg"

        //carpeta raiz de las fotos dentro de filesDir; file_paths.xml ya la expone con sus subcarpetas
        const val PHOTOS_DIR = "photos"

        //cuanto se espera a la nube al borrar una foto antes de seguir
        private const val REMOTE_DELETE_TIMEOUT_MS = 4_000L

        //carpeta privada de un usuario: filesDir/photos/{userId}/
        //el uid no puede traer separadores ni ser "." o "..", para no salirse de su carpeta
        fun userPhotosDir(filesDir: File, userId: String): File {
            require(userId.isNotBlank() && '/' !in userId && '\\' !in userId && userId != "." && userId != "..") {
                "Invalid user id for the photos folder"
            }
            return File(File(filesDir, PHOTOS_DIR), userId)
        }
    }
}

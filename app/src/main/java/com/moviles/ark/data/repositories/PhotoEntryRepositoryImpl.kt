package com.moviles.ark.data.repositories

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.domain.repositories.PhotoEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

//fotos aisladas por usuario (#133): las filas llevan el uid y los jpg van en filesDir/photos/{uid}/
//almacenamiento dual (#146): guarda en filesDir/photos/ para acceso privado offline y exporta a la galeria publica via MediaStore
class PhotoEntryRepositoryImpl(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val photoEntryDao: PhotoEntryDao
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
                    takenAt = entity.takenAt
                )
            }
        }
    }

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

    //guarda la foto en la galeria publica del sistema usando MediaStore API en subcarpetas por usuario (#146)
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
        //carpeta raiz de las fotos dentro de filesDir; file_paths.xml ya la expone con sus subcarpetas
        const val PHOTOS_DIR = "photos"

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

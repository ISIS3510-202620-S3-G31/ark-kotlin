package com.moviles.ark.data.repositories

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.entities.PhotoEntryEntity
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
            val photosDir = userPhotosDir(context.filesDir, userId).apply { if (!exists()) mkdirs() }
            val fileName = "photo_${takenAt}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(photosDir, fileName)

            val sourceFile = File(sourceUri)
            val finalPath = if (sourceFile.exists() && sourceFile.parentFile?.absolutePath == photosDir.absolutePath) {
                sourceFile.absolutePath
            } else {
                val inputStream: InputStream = when {
                    sourceUri.startsWith("content://") || sourceUri.startsWith("file://") -> {
                        context.contentResolver.openInputStream(Uri.parse(sourceUri))
                            ?: throw IllegalArgumentException("Cannot open input stream for URI: $sourceUri")
                    }
                    else -> FileInputStream(sourceFile)
                }
                inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            }

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

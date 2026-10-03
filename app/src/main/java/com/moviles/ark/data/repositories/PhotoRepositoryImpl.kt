package com.moviles.ark.data.repositories

import android.content.Context
import android.net.Uri
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.domain.repositories.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class PhotoRepositoryImpl(
    private val context: Context,
    private val photoEntryDao: PhotoEntryDao
) : PhotoRepository {

    override fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryModel>> {
        return photoEntryDao.getPhotosBetween(fromMillis, toMillis).map { entities ->
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
            val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
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
}

package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla photo_entries
@Dao
interface PhotoEntryDao {

    //guarda la referencia a una foto y devuelve su id
    @Insert
    suspend fun insertPhotoRaw(photo: PhotoEntryEntity): Long

    suspend fun insertPhoto(photo: PhotoEntryEntity): Long {
        return try {
            insertPhotoRaw(photo)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "insertPhoto",
                throwable = e,
                extraKeys = mapOf("takenAt" to photo.takenAt)
            )
            throw e
        }
    }

    //todas las fotos de un usuario, de la mas nueva a la mas vieja
    @Query("SELECT * FROM photo_entries WHERE userId = :currentUserId ORDER BY takenAt DESC")
    fun getPhotosRaw(currentUserId: String): Flow<List<PhotoEntryEntity>>

    fun getPhotos(currentUserId: String): Flow<List<PhotoEntryEntity>> {
        return getPhotosRaw(currentUserId).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "getPhotos",
                throwable = e
            )
            throw e
        }
    }

    //fotos de un usuario entre dos momentos; para "la foto de hoy" se pasa el inicio y el fin del dia
    @Query("SELECT * FROM photo_entries WHERE userId = :currentUserId AND takenAt >= :fromMillis AND takenAt < :toMillis ORDER BY takenAt DESC")
    fun getPhotosBetweenRaw(currentUserId: String, fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryEntity>>

    fun getPhotosBetween(currentUserId: String, fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryEntity>> {
        return getPhotosBetweenRaw(currentUserId, fromMillis, toMillis).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "getPhotosBetween",
                throwable = e,
                extraKeys = mapOf("fromMillis" to fromMillis, "toMillis" to toMillis)
            )
            throw e
        }
    }

    //las de un usuario que faltan por subir a firebase
    @Query("SELECT * FROM photo_entries WHERE userId = :currentUserId AND isSynced = 0")
    suspend fun getUnsyncedPhotosRaw(currentUserId: String): List<PhotoEntryEntity>

    suspend fun getUnsyncedPhotos(currentUserId: String): List<PhotoEntryEntity> {
        return try {
            getUnsyncedPhotosRaw(currentUserId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "getUnsyncedPhotos",
                throwable = e
            )
            emptyList()
        }
    }

    //despues de subir una foto se guarda su url y se marca como sincronizada
    @Query("UPDATE photo_entries SET isSynced = 1, remoteUrl = :remoteUrl WHERE id = :id")
    suspend fun markAsSyncedRaw(id: Long, remoteUrl: String)

    suspend fun markAsSynced(id: Long, remoteUrl: String) {
        try {
            markAsSyncedRaw(id, remoteUrl)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "markAsSynced",
                throwable = e,
                extraKeys = mapOf("photo_id" to id)
            )
            throw e
        }
    }
    //busca una foto puntual por su id y usuario
    @Query("SELECT * FROM photo_entries WHERE id = :id AND userId = :currentUserId LIMIT 1")
    suspend fun getPhotoByIdRaw(id: Long, currentUserId: String): PhotoEntryEntity?

    suspend fun getPhotoById(id: Long, currentUserId: String): PhotoEntryEntity? {
        return try {
            getPhotoByIdRaw(id, currentUserId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "getPhotoById",
                throwable = e,
                extraKeys = mapOf("photo_id" to id)
            )
            null
        }
    }

    //elimina la foto de la base de datos local
    @Query("DELETE FROM photo_entries WHERE id = :id AND userId = :currentUserId")
    suspend fun deletePhotoRaw(id: Long, currentUserId: String)

    suspend fun deletePhoto(id: Long, currentUserId: String) {
        try {
            deletePhotoRaw(id, currentUserId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "deletePhoto",
                throwable = e,
                extraKeys = mapOf("photo_id" to id)
            )
            throw e
        }
    }
}
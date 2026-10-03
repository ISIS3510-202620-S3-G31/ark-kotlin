package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla photo_entries (#26)
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

    //todas las fotos, de la mas nueva a la mas vieja
    @Query("SELECT * FROM photo_entries ORDER BY takenAt DESC")
    fun getPhotosRaw(): Flow<List<PhotoEntryEntity>>

    fun getPhotos(): Flow<List<PhotoEntryEntity>> {
        return getPhotosRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "getPhotos",
                throwable = e
            )
            throw e
        }
    }

    //fotos entre dos momentos; para "la foto de hoy" se pasa el inicio y el fin del dia
    @Query("SELECT * FROM photo_entries WHERE takenAt >= :fromMillis AND takenAt < :toMillis ORDER BY takenAt DESC")
    fun getPhotosBetweenRaw(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryEntity>>

    fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryEntity>> {
        return getPhotosBetweenRaw(fromMillis, toMillis).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "PhotoEntryDao",
                action = "getPhotosBetween",
                throwable = e,
                extraKeys = mapOf("fromMillis" to fromMillis, "toMillis" to toMillis)
            )
            throw e
        }
    }

    //las que faltan por subir a firebase storage (#33)
    @Query("SELECT * FROM photo_entries WHERE isSynced = 0")
    suspend fun getUnsyncedPhotosRaw(): List<PhotoEntryEntity>

    suspend fun getUnsyncedPhotos(): List<PhotoEntryEntity> {
        return try {
            getUnsyncedPhotosRaw()
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
}


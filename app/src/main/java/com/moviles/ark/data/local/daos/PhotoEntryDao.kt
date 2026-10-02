package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import kotlinx.coroutines.flow.Flow

//consultas de la tabla photo_entries (#26)
@Dao
interface PhotoEntryDao {

    //guarda la referencia a una foto y devuelve su id
    @Insert
    suspend fun insertPhoto(photo: PhotoEntryEntity): Long

    //todas las fotos, de la mas nueva a la mas vieja
    @Query("SELECT * FROM photo_entries ORDER BY takenAt DESC")
    fun getPhotos(): Flow<List<PhotoEntryEntity>>

    //fotos entre dos momentos; para "la foto de hoy" se pasa el inicio y el fin del dia
    @Query("SELECT * FROM photo_entries WHERE takenAt >= :fromMillis AND takenAt < :toMillis ORDER BY takenAt DESC")
    fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryEntity>>

    //las que faltan por subir a firebase storage (#33)
    @Query("SELECT * FROM photo_entries WHERE isSynced = 0")
    suspend fun getUnsyncedPhotos(): List<PhotoEntryEntity>

    //despues de subir una foto se guarda su url y se marca como sincronizada
    @Query("UPDATE photo_entries SET isSynced = 1, remoteUrl = :remoteUrl WHERE id = :id")
    suspend fun markAsSynced(id: Long, remoteUrl: String)
}

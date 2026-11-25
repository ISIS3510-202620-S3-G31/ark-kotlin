package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import kotlinx.coroutines.flow.Flow

//consultas de las frases favoritas
@Dao
interface FavoriteFortuneCookieDao {
    //favoritas del usuario de la mas nueva a la mas vieja
    @Query("SELECT * FROM favorite_fortune_cookies WHERE userId = :userId AND isDeleted = 0 ORDER BY savedAt DESC")
    fun observeFavorites(userId: String): Flow<List<FavoriteFortuneCookieEntity>>

    //solo los ids, para cargarlos en memoria
    @Query("SELECT id FROM favorite_fortune_cookies WHERE userId = :userId AND isDeleted = 0")
    suspend fun getFavoriteIds(userId: String): List<Int>

    //busca una favorita aunque este marcada como borrada
    @Query("SELECT * FROM favorite_fortune_cookies WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getFavorite(userId: String, id: Int): FavoriteFortuneCookieEntity?

    //guarda o reemplaza una favorita
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteFortuneCookieEntity)

    //la marca como borrada para borrarla de firestore cuando haya red
    @Query("UPDATE favorite_fortune_cookies SET isDeleted = 1, isSynced = 0 WHERE userId = :userId AND id = :id")
    suspend fun markDeleted(userId: String, id: Int)

    //la borra del todo cuando ya se borro de firestore
    @Query("DELETE FROM favorite_fortune_cookies WHERE userId = :userId AND id = :id")
    suspend fun deleteFavorite(userId: String, id: Int)

    //la marca como subida
    @Query("UPDATE favorite_fortune_cookies SET isSynced = 1 WHERE userId = :userId AND id = :id")
    suspend fun markSynced(userId: String, id: Int)

    //las que faltan por subir o borrar en firestore
    @Query("SELECT * FROM favorite_fortune_cookies WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsynced(userId: String): List<FavoriteFortuneCookieEntity>
}

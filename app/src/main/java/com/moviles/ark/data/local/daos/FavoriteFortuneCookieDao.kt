package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import kotlinx.coroutines.flow.Flow

//dao de room para consultar, insertar y borrar frases favoritas de la galleta
@Dao
interface FavoriteFortuneCookieDao {
    //obtiene todas las frases favoritas ordenadas de la mas reciente a la mas antigua
    @Query("SELECT * FROM favorite_fortune_cookies ORDER BY savedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteFortuneCookieEntity>>

    //obtiene los ids de todas las favoritas para poder consultar rapido en memoria
    @Query("SELECT id FROM favorite_fortune_cookies")
    suspend fun getAllFavoriteIds(): List<Int>

    //busca una favorita por su id
    @Query("SELECT * FROM favorite_fortune_cookies WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: Int): FavoriteFortuneCookieEntity?

    //guarda o reemplaza una frase favorita en la base de datos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(fortune: FavoriteFortuneCookieEntity)

    //elimina una frase de favoritos por su id
    @Query("DELETE FROM favorite_fortune_cookies WHERE id = :id")
    suspend fun deleteFavoriteById(id: Int)

    //marca una favorita como sincronizada en la nube
    @Query("UPDATE favorite_fortune_cookies SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)
}

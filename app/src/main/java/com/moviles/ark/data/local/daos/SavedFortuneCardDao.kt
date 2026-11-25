package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import kotlinx.coroutines.flow.Flow

//consultas de las imagenes guardadas
@Dao
interface SavedFortuneCardDao {
    //imagenes del usuario de la mas nueva a la mas vieja
    @Query("SELECT * FROM saved_fortune_cards WHERE userId = :userId ORDER BY savedAt DESC")
    fun observeCards(userId: String): Flow<List<SavedFortuneCardEntity>>

    //la imagen de una frase, para no crearla dos veces
    @Query("SELECT * FROM saved_fortune_cards WHERE userId = :userId AND cookieId = :cookieId LIMIT 1")
    suspend fun getCard(userId: String, cookieId: Int): SavedFortuneCardEntity?

    //guarda o reemplaza una imagen
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: SavedFortuneCardEntity): Long

    //guarda la url de la nube y la marca como subida
    @Query("UPDATE saved_fortune_cards SET remoteImageUrl = :remoteUrl, isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long, remoteUrl: String)

    //las que faltan por subir
    @Query("SELECT * FROM saved_fortune_cards WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsynced(userId: String): List<SavedFortuneCardEntity>
}

package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import kotlinx.coroutines.flow.Flow

//dao de room para registrar y consultar las tarjetas de la fortuna guardadas en galeria y nube
@Dao
interface SavedFortuneCardDao {
    //observa todas las tarjetas guardadas de un usuario ordenadas por fecha
    @Query("SELECT * FROM saved_fortune_cards WHERE userId = :userId ORDER BY savedAt DESC")
    fun observeSavedCards(userId: String): Flow<List<SavedFortuneCardEntity>>

    //inserta una tarjeta guardada
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: SavedFortuneCardEntity): Long

    //actualiza la url remota y el estado de sincronizacion
    @Query("UPDATE saved_fortune_cards SET remoteImageUrl = :remoteUrl, isSynced = 1 WHERE id = :id")
    suspend fun markCardSynced(id: Long, remoteUrl: String)

    //obtiene las tarjetas pendientes por sincronizar
    @Query("SELECT * FROM saved_fortune_cards WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedCards(userId: String): List<SavedFortuneCardEntity>

    //elimina una tarjeta por id
    @Query("DELETE FROM saved_fortune_cards WHERE id = :id")
    suspend fun deleteCardById(id: Long)
}

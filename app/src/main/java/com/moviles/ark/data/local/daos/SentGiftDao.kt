package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.SentGiftEntity
import kotlinx.coroutines.flow.Flow

//aqui se usa room: regalos enviados desde este telefono, siempre filtrados por el uid del remitente
@Dao
interface SentGiftDao {

    //REPLACE: reintentar el mismo regalo no duplica la fila
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gift: SentGiftEntity)

    //del mas nuevo al mas viejo; room vuelve a emitir con cada cambio
    @Query("SELECT * FROM sent_gifts WHERE userId = :userId ORDER BY sentAt DESC")
    fun observeSent(userId: String): Flow<List<SentGiftEntity>>

    @Query("DELETE FROM sent_gifts WHERE shareId = :shareId AND userId = :userId")
    suspend fun delete(shareId: String, userId: String)
}

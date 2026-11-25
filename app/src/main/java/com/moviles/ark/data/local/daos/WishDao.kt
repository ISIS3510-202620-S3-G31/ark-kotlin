package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.WishEntity
import kotlinx.coroutines.flow.Flow

//consultas de la tabla wishes; todas filtran por el uid del dueno
//lo que ve la pantalla nunca trae los deseos borrados (isDeleted = 1)
@Dao
interface WishDao {

    // ---------- lo que usa la herramienta ----------

    //guarda un deseo nuevo
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWish(wish: WishEntity)

    //todos los deseos del usuario con todos sus datos, para la lista completa
    //no vienen ordenados: el repositorio los ordena en otro hilo (Dispatchers.Default)
    //es un Flow: room lo vuelve a mandar solo cada vez que la tabla cambia
    @Query("SELECT * FROM wishes WHERE userId = :userId AND isDeleted = 0")
    fun observeWishes(userId: String): Flow<List<WishEntity>>

    //consulta liviana para el cielo: solo los ids, sin el texto
    //el texto de cada deseo se lee despues, cuando el usuario toca su farol
    @Query("SELECT id FROM wishes WHERE userId = :userId AND isDeleted = 0")
    fun observeWishIds(userId: String): Flow<List<String>>

    //un solo deseo; null si no existe o si ya se borro
    @Query("SELECT * FROM wishes WHERE id = :id AND userId = :userId AND isDeleted = 0 LIMIT 1")
    suspend fun getWish(id: String, userId: String): WishEntity?

    //editar: cambia el texto y queda pendiente de subir
    //devuelve cuantas filas cambio (0 si el deseo no existe)
    @Query("UPDATE wishes SET text = :text, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id AND userId = :userId AND isDeleted = 0")
    suspend fun updateText(id: String, userId: String, text: String, updatedAt: Long): Int

    //borrar: no se borra la fila todavia, se marca como borrada y queda pendiente de subir
    //asi el sync sabe que tambien la tiene que borrar de firestore
    @Query("UPDATE wishes SET isDeleted = 1, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id AND userId = :userId")
    suspend fun markAsDeleted(id: String, userId: String, updatedAt: Long): Int

    // ---------- lo que usa el sync (eventual connectivity) ----------

    //deseos del usuario con algo por subir: nuevos, editados o borrados
    @Query("SELECT * FROM wishes WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedWishes(userId: String): List<WishEntity>

    //despues de subir un deseo se marca como sincronizado
    //solo si nadie lo cambio mientras subia (updatedAt sigue igual); si cambio, queda pendiente para el siguiente sync
    @Query("UPDATE wishes SET isSynced = 1 WHERE id = :id AND updatedAt = :uploadedVersion")
    suspend fun markAsSynced(id: String, uploadedVersion: Long): Int

    //despues de borrarlo de firestore, ahora si se borra la fila del telefono
    @Query("DELETE FROM wishes WHERE id = :id AND isDeleted = 1")
    suspend fun deleteForever(id: String): Int
}

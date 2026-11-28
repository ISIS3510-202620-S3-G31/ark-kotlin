package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.CookieEntity
import com.moviles.ark.data.local.entities.CookieImageRow
import com.moviles.ark.data.local.entities.CookieListRow
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//aqui se usa room: consultas de la tabla cookies, siempre filtradas por el uid del dueno
//las lecturas de la pantalla son Flow: room vuelve a emitir cada vez que cambia la tabla
@Dao
interface CookieDao {

    //ABORT: si el titulo ya existe para ese usuario el indice unico lanza error y no se guarda
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCookieRaw(cookie: CookieEntity)

    suspend fun insertCookie(cookie: CookieEntity) {
        try {
            insertCookieRaw(cookie)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "CookieDao",
                action = "insertCookie",
                throwable = e,
                extraKeys = mapOf("cookie_id" to cookie.id)
            )
            throw e
        }
    }

    //todas las galletas del usuario con todos sus datos, de la mas nueva a la mas vieja
    @Query("SELECT * FROM cookies WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeCookiesRaw(userId: String): Flow<List<CookieEntity>>

    fun observeCookies(userId: String): Flow<List<CookieEntity>> {
        return observeCookiesRaw(userId).catch { e ->
            CrashlyticsHelper.logNonFatal(componentName = "CookieDao", action = "observeCookies", throwable = e)
            throw e
        }
    }

    //consulta liviana del listado: solo titulo y marcas, sin el texto
    @Query("SELECT id, title, state, hasImage, receivedFrom, isSynced FROM cookies WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeListRaw(userId: String): Flow<List<CookieListRow>>

    fun observeList(userId: String): Flow<List<CookieListRow>> {
        return observeListRaw(userId).catch { e ->
            CrashlyticsHelper.logNonFatal(componentName = "CookieDao", action = "observeList", throwable = e)
            throw e
        }
    }

    @Query("SELECT * FROM cookies WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getCookie(id: String, userId: String): CookieEntity?

    //la galleta del usuario que ya usa un titulo (para los titulos repetidos entre dos telefonos)
    @Query("SELECT * FROM cookies WHERE userId = :userId AND titleKey = :titleKey LIMIT 1")
    suspend fun getByTitleKey(userId: String, titleKey: String): CookieEntity?

    //llaves de todos los titulos del usuario (activas y comidas) para calcular el sufijo de un titulo repetido
    @Query("SELECT titleKey FROM cookies WHERE userId = :userId")
    suspend fun getTitleKeys(userId: String): List<String>

    //comer o devolver al jarro: cambia el estado y queda pendiente de subir
    @Query("UPDATE cookies SET state = :state, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id AND userId = :userId")
    suspend fun updateStateRaw(id: String, userId: String, state: String, updatedAt: Long): Int

    suspend fun updateState(id: String, userId: String, state: String, updatedAt: Long): Int {
        return try {
            updateStateRaw(id, userId, state, updatedAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "CookieDao",
                action = "updateState",
                throwable = e,
                extraKeys = mapOf("cookie_id" to id, "state" to state)
            )
            throw e
        }
    }

    //abrir una galleta no es un cambio para la nube: solo sirve para el desalojo lru de imagenes
    @Query("UPDATE cookies SET lastOpenedAt = :openedAt WHERE id = :id AND userId = :userId")
    suspend fun markOpened(id: String, userId: String, openedAt: Long)

    @Query("DELETE FROM cookies WHERE id = :id AND userId = :userId")
    suspend fun deleteCookieRaw(id: String, userId: String): Int

    suspend fun deleteCookie(id: String, userId: String): Int {
        return try {
            deleteCookieRaw(id, userId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "CookieDao",
                action = "deleteCookie",
                throwable = e,
                extraKeys = mapOf("cookie_id" to id)
            )
            throw e
        }
    }

    //las del usuario que faltan por subir a la nube
    @Query("SELECT * FROM cookies WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedCookies(userId: String): List<CookieEntity>

    //despues de subir: se guarda la url y se marca sincronizada solo si nadie la cambio mientras subia
    @Query("UPDATE cookies SET isSynced = 1, remoteImageUrl = :remoteImageUrl WHERE id = :id AND updatedAt = :uploadedVersion")
    suspend fun markAsSynced(id: String, remoteImageUrl: String?, uploadedVersion: Long): Int

    //cuantas galletas del usuario faltan por subir; room vuelve a emitir con cada cambio (el ⏳ de la pantalla)
    @Query("SELECT COUNT(*) FROM cookies WHERE userId = :userId AND isSynced = 0")
    fun observeUnsyncedCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cookies WHERE userId = :userId AND isSynced = 0")
    suspend fun countUnsynced(userId: String): Int

    //todas las galletas del usuario, para compararlas con las de la nube
    @Query("SELECT * FROM cookies WHERE userId = :userId")
    suspend fun getAllCookies(userId: String): List<CookieEntity>

    //la imagen ya esta en storage: se guarda la url aunque la galleta haya cambiado mientras subia
    @Query("UPDATE cookies SET remoteImageUrl = :remoteImageUrl WHERE id = :id")
    suspend fun setRemoteImageUrl(id: String, remoteImageUrl: String)

    //la imagen se bajo de la nube al archivo privado
    @Query("UPDATE cookies SET localImagePath = :localImagePath WHERE id = :id AND userId = :userId")
    suspend fun setLocalImage(id: String, userId: String, localImagePath: String): Int

    //gano la version de la nube (last write wins): se copian sus datos, queda sincronizada y se conserva la imagen local
    //solo si la fila no cambio desde que se leyo (seenVersion)
    @Query(
        """UPDATE cookies SET title = :title, titleKey = :titleKey, text = :text, state = :state, hasImage = :hasImage,
           remoteImageUrl = :remoteImageUrl, updatedAt = :updatedAt, isSynced = 1
           WHERE id = :id AND userId = :userId AND updatedAt = :seenVersion"""
    )
    suspend fun applyRemote(
        id: String, userId: String, title: String, titleKey: String, text: String, state: String,
        hasImage: Boolean, remoteImageUrl: String?, updatedAt: Long, seenVersion: Long,
    ): Int

    //titulo repetido entre dos telefonos: la galleta recibe el sufijo y queda pendiente de subir el titulo nuevo
    @Query("UPDATE cookies SET title = :title, titleKey = :titleKey, updatedAt = :updatedAt, isSynced = 0 WHERE id = :id AND userId = :userId")
    suspend fun rename(id: String, userId: String, title: String, titleKey: String, updatedAt: Long): Int

    //borrada en otro telefono: solo si seguia sincronizada y sin cambios desde que se leyo
    @Query("DELETE FROM cookies WHERE id = :id AND userId = :userId AND isSynced = 1 AND updatedAt = :seenVersion")
    suspend fun deleteIfUnchanged(id: String, userId: String, seenVersion: Long): Int

    //imagenes que estan en el telefono (de cualquier cuenta: el presupuesto es de toda la carpeta), la menos usada primero
    @Query("SELECT id, lastOpenedAt, isSynced FROM cookies WHERE localImagePath IS NOT NULL ORDER BY lastOpenedAt ASC")
    suspend fun getImagesOnPhone(): List<CookieImageRow>

    //al desalojar la imagen solo se olvida la ruta local: hasImage y remoteImageUrl se conservan para volver a bajarla
    @Query("UPDATE cookies SET localImagePath = NULL WHERE id = :id")
    suspend fun clearLocalImage(id: String)
}

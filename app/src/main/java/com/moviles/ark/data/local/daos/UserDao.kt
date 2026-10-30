package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.moviles.ark.data.local.entities.UserEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla users
@Dao
interface UserDao {

    //el perfil de un usuario; avisa solo cada vez que la fila cambia (por ejemplo al editar el nombre)
    @Query("SELECT * FROM users WHERE uid = :uid")
    fun observeUserRaw(uid: String): Flow<UserEntity?>

    fun observeUser(uid: String): Flow<UserEntity?> {
        return observeUserRaw(uid).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "observeUser",
                throwable = e
            )
            throw e
        }
    }

    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUserRaw(uid: String): UserEntity?

    suspend fun getUser(uid: String): UserEntity? {
        return try {
            getUserRaw(uid)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "getUser",
                throwable = e
            )
            null
        }
    }

    //crea la fila o la reemplaza si ya existe
    @Upsert
    suspend fun upsertUserRaw(user: UserEntity)

    suspend fun upsertUser(user: UserEntity) {
        try {
            upsertUserRaw(user)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "upsertUser",
                throwable = e
            )
            throw e
        }
    }

    //cambio hecho por el usuario: queda pendiente de subir a firestore, con la hora en que se hizo
    @Query("UPDATE users SET name = :name, birthDate = :birthDate, isSynced = 0, profileEditedAt = :editedAt WHERE uid = :uid")
    suspend fun updateProfileRaw(uid: String, name: String, birthDate: String, editedAt: Long)

    suspend fun updateProfile(uid: String, name: String, birthDate: String, editedAt: Long) {
        try {
            updateProfileRaw(uid, name, birthDate, editedAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "updateProfile",
                throwable = e,
                extraKeys = mapOf("birthDate" to birthDate)
            )
            throw e
        }
    }

    //foto nueva elegida por el usuario: se muestra de una vez y queda pendiente de subir a storage
    @Query(
        "UPDATE users SET avatarPath = :avatarPath, photoUrl = NULL, usesPhoto = 1, isAvatarSynced = 0, " +
            "avatarEditedAt = :editedAt WHERE uid = :uid"
    )
    suspend fun updatePhotoRaw(uid: String, avatarPath: String, editedAt: Long)

    suspend fun updatePhoto(uid: String, avatarPath: String, editedAt: Long) {
        try {
            updatePhotoRaw(uid, avatarPath, editedAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "updatePhoto",
                throwable = e
            )
            throw e
        }
    }

    //el usuario eligio un avatar de la lista o volvio a su foto: queda pendiente de subir a firestore
    @Query("UPDATE users SET avatarId = :avatarId, usesPhoto = :usesPhoto, isAvatarSynced = 0, avatarEditedAt = :editedAt WHERE uid = :uid")
    suspend fun updateAvatarChoiceRaw(uid: String, avatarId: String, usesPhoto: Boolean, editedAt: Long)

    suspend fun updateAvatarChoice(uid: String, avatarId: String, usesPhoto: Boolean, editedAt: Long) {
        try {
            updateAvatarChoiceRaw(uid, avatarId, usesPhoto, editedAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "updateAvatarChoice",
                throwable = e,
                extraKeys = mapOf("avatar_id" to avatarId, "uses_photo" to usesPhoto.toString())
            )
            throw e
        }
    }

    //url de storage de la foto recien subida; solo si esa sigue siendo la foto del telefono
    @Query("UPDATE users SET photoUrl = :photoUrl WHERE uid = :uid AND avatarPath = :avatarPath")
    suspend fun savePhotoUrlRaw(uid: String, avatarPath: String, photoUrl: String)

    suspend fun savePhotoUrl(uid: String, avatarPath: String, photoUrl: String) {
        try {
            savePhotoUrlRaw(uid, avatarPath, photoUrl)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "savePhotoUrl",
                throwable = e
            )
            throw e
        }
    }

    //despues de subir el avatar (o de dejar el de la nube por ser mas nuevo); si el usuario lo volvio a cambiar
    //mientras se subia, su hora de edicion ya es otra y queda pendiente para la siguiente vez
    @Query("UPDATE users SET isAvatarSynced = 1 WHERE uid = :uid AND avatarEditedAt = :editedAt")
    suspend fun markAvatarSyncedRaw(uid: String, editedAt: Long)

    suspend fun markAvatarSynced(uid: String, editedAt: Long) {
        try {
            markAvatarSyncedRaw(uid, editedAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "markAvatarSynced",
                throwable = e
            )
            throw e
        }
    }

    @Query("UPDATE users SET streakDays = :streakDays, lastCheckInAt = :lastCheckInAt WHERE uid = :uid")
    suspend fun updateStreakRaw(uid: String, streakDays: Int, lastCheckInAt: Long?)

    suspend fun updateStreak(uid: String, streakDays: Int, lastCheckInAt: Long?) {
        try {
            updateStreakRaw(uid, streakDays, lastCheckInAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "updateStreak",
                throwable = e,
                extraKeys = mapOf("streak_days" to streakDays)
            )
            throw e
        }
    }

    //version de la nube que este telefono acaba de escribir o de leer (ver UserEntity.profileBase / avatarBase)
    @Query("UPDATE users SET profileBase = :base WHERE uid = :uid")
    suspend fun saveProfileBaseRaw(uid: String, base: String)

    suspend fun saveProfileBase(uid: String, base: String) {
        try {
            saveProfileBaseRaw(uid, base)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "saveProfileBase",
                throwable = e
            )
            throw e
        }
    }

    @Query("UPDATE users SET avatarBase = :base WHERE uid = :uid")
    suspend fun saveAvatarBaseRaw(uid: String, base: String)

    suspend fun saveAvatarBase(uid: String, base: String) {
        try {
            saveAvatarBaseRaw(uid, base)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "saveAvatarBase",
                throwable = e
            )
            throw e
        }
    }

    //despues de subir nombre y fecha a firestore (o de dejar los de la nube por ser mas nuevos);
    //una edicion hecha mientras se subia tiene otra hora y queda pendiente
    @Query("UPDATE users SET isSynced = 1 WHERE uid = :uid AND profileEditedAt = :editedAt")
    suspend fun markAsSyncedRaw(uid: String, editedAt: Long)

    suspend fun markAsSynced(uid: String, editedAt: Long) {
        try {
            markAsSyncedRaw(uid, editedAt)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "markAsSynced",
                throwable = e
            )
            throw e
        }
    }
}

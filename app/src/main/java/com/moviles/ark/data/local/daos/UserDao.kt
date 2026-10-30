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

    //cambio hecho por el usuario: queda pendiente de subir a firestore
    @Query("UPDATE users SET name = :name, birthDate = :birthDate, isSynced = 0 WHERE uid = :uid")
    suspend fun updateProfileRaw(uid: String, name: String, birthDate: String)

    suspend fun updateProfile(uid: String, name: String, birthDate: String) {
        try {
            updateProfileRaw(uid, name, birthDate)
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

    @Query("UPDATE users SET name = :name, age = :age, isSynced = 0 WHERE uid = :uid")
    suspend fun updateProfileWithAgeRaw(uid: String, name: String, age: Int)

    suspend fun updateProfileWithAge(uid: String, name: String, age: Int) {
        try {
            updateProfileWithAgeRaw(uid, name, age)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "updateProfileWithAge",
                throwable = e,
                extraKeys = mapOf("age" to age)
            )
            throw e
        }
    }

    //foto nueva elegida por el usuario: se muestra de una vez y queda pendiente de subir a storage
    @Query("UPDATE users SET avatarPath = :avatarPath, photoUrl = NULL, usesPhoto = 1, isAvatarSynced = 0 WHERE uid = :uid")
    suspend fun updatePhotoRaw(uid: String, avatarPath: String)

    suspend fun updatePhoto(uid: String, avatarPath: String) {
        try {
            updatePhotoRaw(uid, avatarPath)
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
    @Query("UPDATE users SET avatarId = :avatarId, usesPhoto = :usesPhoto, isAvatarSynced = 0 WHERE uid = :uid")
    suspend fun updateAvatarChoiceRaw(uid: String, avatarId: String, usesPhoto: Boolean)

    suspend fun updateAvatarChoice(uid: String, avatarId: String, usesPhoto: Boolean) {
        try {
            updateAvatarChoiceRaw(uid, avatarId, usesPhoto)
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

    //despues de subir el avatar; si el usuario lo cambio mientras se subia, queda pendiente para la siguiente vez
    @Query(
        "UPDATE users SET isAvatarSynced = 1 WHERE uid = :uid AND avatarId = :avatarId AND usesPhoto = :usesPhoto " +
            "AND IFNULL(photoUrl, '') = IFNULL(:photoUrl, '')"
    )
    suspend fun markAvatarSyncedRaw(uid: String, avatarId: String, usesPhoto: Boolean, photoUrl: String?)

    suspend fun markAvatarSynced(uid: String, avatarId: String, usesPhoto: Boolean, photoUrl: String?) {
        try {
            markAvatarSyncedRaw(uid, avatarId, usesPhoto, photoUrl)
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

    //despues de subir los datos a firestore
    @Query("UPDATE users SET isSynced = 1 WHERE uid = :uid")
    suspend fun markAsSyncedRaw(uid: String)

    suspend fun markAsSynced(uid: String) {
        try {
            markAsSyncedRaw(uid)
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

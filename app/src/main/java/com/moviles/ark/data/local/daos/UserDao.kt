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

    @Query("UPDATE users SET avatarPath = :avatarPath WHERE uid = :uid")
    suspend fun updateAvatarRaw(uid: String, avatarPath: String)

    suspend fun updateAvatar(uid: String, avatarPath: String) {
        try {
            updateAvatarRaw(uid, avatarPath)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "UserDao",
                action = "updateAvatar",
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

package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.UserProfile
import kotlinx.coroutines.flow.Flow

//perfil del usuario con sesion (#119): room primero para que cargue al instante y sin internet,
//firestore (users/{uid}) se trae o se actualiza cuando se puede
interface UserRepository {
    //perfil guardado en el telefono; null si nunca se ha guardado o no hay sesion
    fun observeProfile(): Flow<UserProfile?>

    //trae nombre y edad de firestore y los guarda en room; si hay un cambio local pendiente, primero lo sube
    suspend fun refreshProfile(): Result<Unit>

    //guarda en room de una vez y luego intenta subirlo a firestore
    suspend fun updateProfile(name: String, age: Int): Result<Unit>

    //copia la foto elegida al almacenamiento privado de la app y la deja como avatar
    suspend fun updateAvatar(sourceUri: String): Result<Unit>

    //guarda la racha calculada para poder mostrarla sin internet
    suspend fun saveStreak(streakDays: Int, lastCheckInAt: Long?): Result<Unit>
}

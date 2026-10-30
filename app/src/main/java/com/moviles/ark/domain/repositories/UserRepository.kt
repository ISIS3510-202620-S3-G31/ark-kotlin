package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.UserProfile
import kotlinx.coroutines.flow.Flow

//perfil del usuario con sesion: room primero para que cargue al instante y sin internet,
//firestore (users/{uid}) se trae o se actualiza cuando se puede
interface UserRepository {
    //perfil guardado en el telefono; null si nunca se ha guardado o no hay sesion
    fun observeProfile(): Flow<UserProfile?>

    //trae nombre y fecha de firestore y los guarda en room; si hay un cambio local pendiente, primero lo sube
    suspend fun refreshProfile(): Result<Unit>

    //guarda en room de una vez y luego intenta subirlo a firestore con fecha de nacimiento
    suspend fun updateProfile(name: String, birthDate: String): Result<Unit>

    //sobrecarga de compatibilidad por si se edita usando edad
    suspend fun updateProfile(name: String, age: Int): Result<Unit>

    //copia la foto elegida al almacenamiento privado de la app, la deja como imagen de perfil y la sube a la nube
    suspend fun updateAvatar(sourceUri: String): Result<Unit>

    //muestra un avatar de la lista; la foto subida se guarda para poder volver a ella
    suspend fun selectAvatar(avatarId: String): Result<Unit>

    //vuelve a mostrar la foto que ya se habia subido
    suspend fun selectPhoto(): Result<Unit>

    //sube lo que se cambio sin internet (nombre, fecha, avatar y foto); devuelve cuantas cosas subio
    suspend fun syncPendingProfile(): Int

    //guarda la racha calculada para poder mostrarla sin internet
    suspend fun saveStreak(streakDays: Int, lastCheckInAt: Long?): Result<Unit>

    //copia rapida del perfil guardada en el telefono (nombre, correo, edad); null si no hay
    fun cachedProfile(): UserProfile?

    //nombre de esa copia; vuelve a emitir cuando cambia
    fun observeCachedName(): Flow<String>

    //borra esa copia, por ejemplo al cerrar sesion
    suspend fun clearCachedProfile()
}

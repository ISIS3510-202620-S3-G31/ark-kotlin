package com.moviles.ark.data.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.moviles.ark.data.local.ProfilePreferences
import com.moviles.ark.data.local.daos.UserDao
import com.moviles.ark.data.local.entities.UserEntity
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.remote.dtos.UserDto
import com.moviles.ark.data.remote.dtos.editableFields
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.UserProfile
import com.moviles.ark.domain.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

//perfil del usuario: room primero, firestore despues
class UserRepositoryImpl(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao
) : UserRepository {

    override fun observeProfile(): Flow<UserProfile?> {
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return userDao.observeUser(uid).map { it?.toDomain() }
    }

    override suspend fun refreshProfile(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("No user is logged in")
            val local = userDao.getUser(user.uid)

            //el usuario edito su perfil sin internet: se sube lo local en vez de pisarlo con lo de firestore
            if (local != null && !local.isSynced) {
                if (local.birthDate.isNotBlank()) {
                    pushProfile(user.uid, local.name, local.birthDate)
                } else {
                    pushProfileWithAge(user.uid, local.name, local.age)
                }
                return@runCatching
            }

            //email y fecha de creacion vienen de la sesion guardada en el telefono; nombre y fecha de firestore
            //sin internet firestore responde con su copia local; si no tiene, se queda lo que habia en room
            val document = runCatching {
                firestore.collection("users").document(user.uid).get().await()
            }.onFailure { firestoreError ->
                CrashlyticsHelper.logNonFatal(
                    componentName = "UserRepository",
                    action = "refreshProfile_firestore_fetch",
                    throwable = firestoreError,
                    extraKeys = mapOf("user_id" to user.uid)
                )
            }.getOrNull()

            //firestore arma el dto con los campos del documento; si el documento no existe queda en null
            val remote = document?.toObject(UserDto::class.java)
            val remoteName = remote?.name.orEmpty()
            val remoteBirthDate = remote?.birthDate.orEmpty()
            val remoteAge = remote?.age ?: 0
            val resolvedName = remoteName.ifBlank { local?.name.orEmpty() }
            val resolvedAge = if (remoteAge > 0) remoteAge else local?.age ?: 0
            userDao.upsertUser(
                UserEntity(
                    uid = user.uid,
                    name = resolvedName,
                    email = user.email.orEmpty(),
                    birthDate = remoteBirthDate.ifBlank { local?.birthDate.orEmpty() },
                    age = resolvedAge,
                    streakDays = local?.streakDays ?: 0,
                    lastCheckInAt = local?.lastCheckInAt,
                    createdAt = user.metadata?.creationTimestamp ?: local?.createdAt ?: 0L,
                    avatarPath = local?.avatarPath,
                    isSynced = true
                )
            )
            //persiste los datos escalares en sharedpreferences en segundo plano (offline-first)
            ProfilePreferences.saveProfile(
                name = resolvedName,
                email = user.email.orEmpty(),
                age = resolvedAge
            )
            //si firestore no respondio y room tampoco tenia nada, la pantalla debe saber que fallo
            if (document == null && local == null) error("Could not load the profile")
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "refreshProfile",
                throwable = error
            )
        }
    }

    override suspend fun updateProfile(name: String, birthDate: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            //primero room: la pantalla cambia de una vez, haya o no internet
            userDao.updateProfile(uid, name.trim(), birthDate)
            ProfilePreferences.saveProfile(
                name = name.trim(),
                email = auth.currentUser?.email.orEmpty(),
                age = com.moviles.ark.domain.models.UserModel.ageOf(birthDate) ?: 0
            )
            pushProfile(uid, name.trim(), birthDate)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "updateProfile",
                throwable = error,
                extraKeys = mapOf("birthDate" to birthDate)
            )
        }
    }

    override suspend fun updateProfile(name: String, age: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            userDao.updateProfileWithAge(uid, name.trim(), age)
            ProfilePreferences.saveProfile(
                name = name.trim(),
                email = auth.currentUser?.email.orEmpty(),
                age = age
            )
            pushProfileWithAge(uid, name.trim(), age)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "updateProfileWithAge",
                throwable = error,
                extraKeys = mapOf("age" to age)
            )
        }
    }

    override suspend fun updateAvatar(sourceUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            val avatarsDir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            //nombre nuevo cada vez para que la pantalla note el cambio; la foto anterior se borra
            val destFile = File(avatarsDir, "avatar_${uid}_${System.currentTimeMillis()}.jpg")
            val avatar = decodeAvatar(Uri.parse(sourceUri))
            FileOutputStream(destFile).use { output ->
                avatar.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, output)
            }
            avatar.recycle()

            val previousPath = userDao.getUser(uid)?.avatarPath
            userDao.updateAvatar(uid, destFile.absolutePath)
            if (previousPath != null && previousPath != destFile.absolutePath) File(previousPath).delete()
            Unit
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "updateAvatar",
                throwable = error
            )
        }
    }

    //copia en sharedpreferences para mostrar el perfil y el saludo al instante
    override fun cachedProfile(): UserProfile? {
        val cached = ProfilePreferences.getScalarProfile()
        if (cached.name.isBlank() && cached.email.isBlank()) return null
        return UserProfile(name = cached.name, email = cached.email, memberSince = 0L, age = cached.age)
    }

    override fun observeCachedName(): Flow<String> = ProfilePreferences.userProfile.map { it.name }

    override suspend fun clearCachedProfile() {
        ProfilePreferences.clear()
    }

    override suspend fun saveStreak(streakDays: Int, lastCheckInAt: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            userDao.updateStreak(uid, streakDays, lastCheckInAt)
        }
    }

    //las reglas de firestore aceptan name, birthDate, age y email en users/{uid}: la racha y el avatar se quedan en room
    private suspend fun pushProfile(uid: String, name: String, birthDate: String) {
        //la edad la calcula el modelo a partir de la fecha
        val calculatedAge = com.moviles.ark.domain.models.UserModel.ageOf(birthDate) ?: 0
        writeProfileFields(uid, UserDto(name = name, birthDate = birthDate, age = calculatedAge))
    }

    //para retrocompatibilidad en caso de perfiles guardados anteriormente con edad
    private suspend fun pushProfileWithAge(uid: String, name: String, age: Int) {
        writeProfileFields(uid, UserDto(name = name, age = age))
    }

    //escribe solo los campos que el usuario edita (UserDto.editableFields); el correo y lo demas del documento no se tocan
    private suspend fun writeProfileFields(uid: String, profile: UserDto) {
        val written = withTimeoutOrNull(PUSH_TIMEOUT_MILLIS) {
            firestore.collection("users").document(uid)
                .set(profile, SetOptions.mergeFields(profile.editableFields()))
                .await()
            true
        } ?: false
        if (written) userDao.markAsSynced(uid)
    }

    //el avatar se guarda pequeno y ya derecho (segun el exif de la camara), asi la pantalla solo lo lee
    private fun decodeAvatar(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: error("Cannot open the photo: $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= AVATAR_SIZE_PX && bounds.outHeight / (sample * 2) >= AVATAR_SIZE_PX) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Cannot decode the photo: $uri")
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        private const val PUSH_TIMEOUT_MILLIS = 5_000L
        private const val AVATAR_SIZE_PX = 512
        private const val AVATAR_JPEG_QUALITY = 90
    }
}

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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.moviles.ark.data.local.ProfilePreferences
import com.moviles.ark.data.local.daos.UserDao
import com.moviles.ark.data.local.entities.UserEntity
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.remote.dtos.UserAvatarDto
import com.moviles.ark.data.remote.dtos.UserDto
import com.moviles.ark.data.remote.dtos.editableFields
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.Avatar
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
//la foto de perfil (avatar de la lista o foto propia) tambien va a users/{uid} y la foto a storage,
//asi se ve igual en cualquier telefono donde la cuenta inicie sesion
class UserRepositoryImpl(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
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
            //(tambien si cerro sesion antes de que subiera: room guarda la edicion por cuenta)
            if (local != null && !local.isSynced) {
                //el saludo del home lee estas preferencias; se borran al cerrar sesion, asi que se llenan con lo local
                ProfilePreferences.saveProfile(
                    name = local.name,
                    email = user.email.orEmpty(),
                    age = com.moviles.ark.domain.models.UserModel.ageOf(local.birthDate) ?: local.age
                )
                if (local.birthDate.isNotBlank()) {
                    pushProfile(user.uid, local.name, local.birthDate)
                } else {
                    pushProfileWithAge(user.uid, local.name, local.age)
                }
                if (!local.isAvatarSynced) pushAvatar(user.uid)
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
            val remoteAvatar = document?.toObject(UserAvatarDto::class.java)
            val avatar = resolveAvatar(user.uid, local, remoteAvatar)
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
                    avatarPath = avatar.avatarPath,
                    isSynced = true,
                    avatarId = avatar.avatarId,
                    usesPhoto = avatar.usesPhoto,
                    photoUrl = avatar.photoUrl,
                    isAvatarSynced = avatar.isAvatarSynced
                )
            )
            //lo que se eligio sin internet (o lo de antes de guardar el avatar en la nube) se sube ahora
            if (!avatar.isAvatarSynced) pushAvatar(user.uid)
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
            Unit
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
            Unit
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
            //nombre nuevo cada vez para que la pantalla note el cambio; la foto anterior se borra
            val destFile = newPhotoFile(uid)
            val avatar = decodeAvatar(Uri.parse(sourceUri))
            FileOutputStream(destFile).use { output ->
                avatar.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, output)
            }
            avatar.recycle()

            val previousPath = ensureLocalUser(uid).avatarPath
            //primero room: la foto se ve de una vez, haya o no internet; luego se sube a storage y firestore
            userDao.updatePhoto(uid, destFile.absolutePath)
            if (previousPath != null && previousPath != destFile.absolutePath) File(previousPath).delete()
            pushAvatar(uid)
            Unit
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "updateAvatar",
                throwable = error
            )
        }
    }

    override suspend fun selectAvatar(avatarId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            ensureLocalUser(uid)
            //la foto subida no se borra: el usuario puede volver a ella cuando quiera
            userDao.updateAvatarChoice(uid, Avatar.idOrDefault(avatarId), usesPhoto = false)
            pushAvatar(uid)
            Unit
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "selectAvatar",
                throwable = error,
                extraKeys = mapOf("avatar_id" to avatarId)
            )
        }
    }

    override suspend fun selectPhoto(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            val local = ensureLocalUser(uid)
            if (local.avatarPath == null) error("There is no uploaded photo to show")
            userDao.updateAvatarChoice(uid, local.avatarId, usesPhoto = true)
            pushAvatar(uid)
            Unit
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "selectPhoto",
                throwable = error
            )
        }
    }

    override suspend fun syncPendingProfile(): Int = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext 0
        val local = userDao.getUser(user.uid) ?: return@withContext 0
        var synced = 0
        if (!local.isSynced) {
            val written = if (local.birthDate.isNotBlank()) {
                pushProfile(user.uid, local.name, local.birthDate)
            } else {
                pushProfileWithAge(user.uid, local.name, local.age)
            }
            if (written) synced++
        }
        if (!local.isAvatarSynced && pushAvatar(user.uid)) synced++
        synced
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

    //las reglas de firestore aceptan name, birthDate, age, email y los campos del avatar en users/{uid}: la racha se queda en room
    private suspend fun pushProfile(uid: String, name: String, birthDate: String): Boolean {
        //la edad la calcula el modelo a partir de la fecha
        val calculatedAge = com.moviles.ark.domain.models.UserModel.ageOf(birthDate) ?: 0
        return writeProfileFields(uid, UserDto(name = name, birthDate = birthDate, age = calculatedAge))
    }

    //para retrocompatibilidad en caso de perfiles guardados anteriormente con edad
    private suspend fun pushProfileWithAge(uid: String, name: String, age: Int): Boolean {
        return writeProfileFields(uid, UserDto(name = name, age = age))
    }

    //escribe solo los campos que el usuario edita (UserDto.editableFields); el correo y lo demas del documento no se tocan
    private suspend fun writeProfileFields(uid: String, profile: UserDto): Boolean {
        val written = withTimeoutOrNull(PUSH_TIMEOUT_MILLIS) {
            firestore.collection("users").document(uid)
                .set(profile, SetOptions.mergeFields(profile.editableFields()))
                .await()
            true
        } ?: false
        if (written) userDao.markAsSynced(uid)
        return written
    }

    //sube el avatar pendiente: si hay una foto que aun no esta en storage la sube primero y luego escribe
    //avatarId, avatarSource y photoUrl en users/{uid}; si algo falla queda pendiente para el sync
    //la url solo se escribe cuando se acaba de subir una foto: asi este telefono nunca devuelve la nube
    //a una foto vieja que se cambio desde otro telefono
    private suspend fun pushAvatar(uid: String): Boolean {
        val local = userDao.getUser(uid)?.takeIf { !it.isAvatarSynced } ?: return false
        val path = local.avatarPath
        val uploadedUrl = if (path != null && local.photoUrl == null) {
            uploadPhoto(uid, File(path)) ?: return false
        } else {
            null
        }
        val avatar = UserAvatarDto(
            avatarId = local.avatarId,
            avatarSource = if (local.usesPhoto) UserAvatarDto.SOURCE_PHOTO else UserAvatarDto.SOURCE_PRESET,
            photoUrl = uploadedUrl.orEmpty()
        )
        val written = withTimeoutOrNull(PUSH_TIMEOUT_MILLIS) {
            firestore.collection("users").document(uid)
                .set(avatar, SetOptions.mergeFields(avatar.fields()))
                .await()
            true
        } ?: false
        if (!written) return false
        if (path != null && uploadedUrl != null) {
            userDao.savePhotoUrl(uid, path, uploadedUrl)
            deleteOldPhotos(uid, keepUrl = uploadedUrl)
        }
        userDao.markAvatarSynced(uid, local.avatarId, local.usesPhoto, uploadedUrl ?: local.photoUrl)
        return true
    }

    //decide la foto de perfil que queda en el telefono despues de leer users/{uid}
    private suspend fun resolveAvatar(uid: String, local: UserEntity?, remote: UserAvatarDto?): UserEntity {
        val current = local ?: UserEntity(uid = uid, name = "", email = "")
        //sin documento (sin internet y sin copia) o con un cambio sin subir: manda lo del telefono
        if (remote == null || !current.isAvatarSynced) return current
        //la nube nunca ha tenido avatar (cuentas de antes de este cambio o creadas en flutter): se sube lo del telefono
        //en telefonos de antes el avatar elegido estaba en las preferencias de cada cuenta
        if (remote.avatarSource.isBlank()) {
            val savedId = if (current.avatarId == Avatar.DEFAULT_ID) ProfilePreferences.savedAvatarFor(uid) else null
            return current.copy(avatarId = Avatar.idOrDefault(savedId ?: current.avatarId), isAvatarSynced = false)
        }
        //la nube manda; si la foto cambio en otro telefono se descarga
        //si no se puede descargar se queda la anterior y se vuelve a intentar en el siguiente refresco
        val remoteUrl = remote.photoUrl.ifBlank { null }
        var path = current.avatarPath
        var url = current.photoUrl
        if (remoteUrl != null && remoteUrl != url) {
            val downloaded = downloadPhoto(uid, remoteUrl)
            if (downloaded != null) {
                if (path != null && path != downloaded) File(path).delete()
                path = downloaded
                url = remoteUrl
            }
        }
        return current.copy(
            avatarId = Avatar.idOrDefault(remote.avatarId),
            usesPhoto = remote.usesPhoto(),
            avatarPath = path,
            photoUrl = url,
            isAvatarSynced = true
        )
    }

    //la fila de room de la cuenta; si todavia no existe (primer uso sin internet) se crea con lo de la sesion
    private suspend fun ensureLocalUser(uid: String): UserEntity {
        userDao.getUser(uid)?.let { return it }
        val user = auth.currentUser
        val created = UserEntity(
            uid = uid,
            name = ProfilePreferences.getScalarProfile().name,
            email = user?.email.orEmpty(),
            createdAt = user?.metadata?.creationTimestamp ?: 0L
        )
        userDao.upsertUser(created)
        return created
    }

    private fun newPhotoFile(uid: String): File {
        val avatarsDir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
        return File(avatarsDir, "avatar_${uid}_${System.currentTimeMillis()}.jpg")
    }

    //sube la foto a users/{uid}/avatar/ y devuelve su url de descarga; null si no se pudo (sin internet, por ejemplo)
    private suspend fun uploadPhoto(uid: String, file: File): String? {
        if (!file.exists()) return null
        return withTimeoutOrNull(PHOTO_TIMEOUT_MILLIS) {
            runCatching {
                val reference = storage.reference.child("${avatarFolder(uid)}/${file.name}")
                reference.putFile(Uri.fromFile(file), storageMetadata { contentType = "image/jpeg" }).await()
                reference.downloadUrl.await().toString()
            }.onFailure { error ->
                CrashlyticsHelper.logNonFatal(componentName = "UserRepository", action = "uploadAvatarPhoto", throwable = error)
            }.getOrNull()
        }
    }

    //baja a la app la foto de perfil que se subio desde otro telefono; null si no se pudo
    private suspend fun downloadPhoto(uid: String, url: String): String? {
        val destFile = newPhotoFile(uid)
        val downloaded = withTimeoutOrNull(PHOTO_TIMEOUT_MILLIS) {
            runCatching {
                storage.getReferenceFromUrl(url).getFile(destFile).await()
                destFile.absolutePath
            }.onFailure { error ->
                CrashlyticsHelper.logNonFatal(componentName = "UserRepository", action = "downloadAvatarPhoto", throwable = error)
            }.getOrNull()
        }
        if (downloaded == null) destFile.delete()
        return downloaded
    }

    //en storage solo queda la foto actual de la cuenta; si no se puede borrar no pasa nada, solo ocupa espacio
    private suspend fun deleteOldPhotos(uid: String, keepUrl: String) {
        withTimeoutOrNull(PHOTO_TIMEOUT_MILLIS) {
            runCatching {
                val keep = storage.getReferenceFromUrl(keepUrl).name
                storage.reference.child(avatarFolder(uid)).listAll().await().items
                    .filter { it.name != keep }
                    .forEach { it.delete().await() }
            }
        }
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
        //subir o bajar la foto tarda mas que escribir un documento
        private const val PHOTO_TIMEOUT_MILLIS = 20_000L

        //carpeta de la foto de perfil en storage (storage.rules: users/{userId}/avatar/{fileName})
        fun avatarFolder(uid: String): String = "users/$uid/avatar"
        private const val AVATAR_SIZE_PX = 512
        private const val AVATAR_JPEG_QUALITY = 90
    }
}

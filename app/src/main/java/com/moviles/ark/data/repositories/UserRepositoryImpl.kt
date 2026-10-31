package com.moviles.ark.data.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.moviles.ark.data.local.ProfilePreferences
import com.moviles.ark.data.local.daos.UserDao
import com.moviles.ark.data.local.entities.UserEntity
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.remote.dtos.UserAvatarDto
import com.moviles.ark.data.remote.dtos.UserDto
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.Avatar
import com.moviles.ark.domain.models.ProfileSaveStatus
import com.moviles.ark.domain.models.ProfileSyncResult
import com.moviles.ark.domain.models.UserModel
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
    private val userDao: UserDao,
    //true si hay internet; sin red no se intenta escribir en firestore y el cambio queda pendiente de una vez
    private val isOnline: () -> Boolean = { true },
    private val now: () -> Long = { System.currentTimeMillis() }
) : UserRepository {

    override fun observeProfile(): Flow<UserProfile?> {
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return userDao.observeUser(uid).map { it?.toDomain() }
    }

    override suspend fun refreshProfile(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("No user is logged in")
            val pending = userDao.getUser(user.uid)

            //lo que se cambio sin internet se sube primero (tambien si cerro sesion antes de que subiera:
            //room guarda la edicion por cuenta); si otro telefono lo cambio despues, se queda lo de la nube
            if (pending != null && !pending.isSynced) {
                //el saludo del home lee estas preferencias; se borran al cerrar sesion, asi que se llenan con lo local
                ProfilePreferences.saveProfile(
                    name = pending.name,
                    email = user.email.orEmpty(),
                    age = UserModel.ageOf(pending.birthDate) ?: pending.age
                )
                pushProfileEdit(user.uid)
            }
            if (pending != null && !pending.isAvatarSynced) pushAvatarEdit(user.uid)
            val local = userDao.getUser(user.uid)

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

            //el usuario edito algo mientras esto corria (bajar una foto puede tardar): se deja su cambio y no se pisa
            val latest = userDao.getUser(user.uid)
            if (local != null && latest != null &&
                (latest.profileEditedAt != local.profileEditedAt || latest.avatarEditedAt != local.avatarEditedAt)
            ) return@runCatching

            //si el cambio de nombre o fecha sigue sin subir (sin red), se muestra el del telefono, no el de la nube
            val profilePending = local != null && !local.isSynced
            val resolvedName = if (profilePending) local!!.name else remote?.name.orEmpty().ifBlank { local?.name.orEmpty() }
            val resolvedBirthDate = if (profilePending) local!!.birthDate else remote?.birthDate.orEmpty().ifBlank { local?.birthDate.orEmpty() }
            val remoteAge = remote?.age ?: 0
            val resolvedAge = if (!profilePending && remoteAge > 0) remoteAge else local?.age ?: 0
            userDao.upsertUser(
                UserEntity(
                    uid = user.uid,
                    name = resolvedName,
                    email = user.email.orEmpty(),
                    birthDate = resolvedBirthDate,
                    age = resolvedAge,
                    streakDays = local?.streakDays ?: 0,
                    lastCheckInAt = local?.lastCheckInAt,
                    createdAt = user.metadata?.creationTimestamp ?: local?.createdAt ?: 0L,
                    avatarPath = avatar.avatarPath,
                    isSynced = !profilePending,
                    avatarId = avatar.avatarId,
                    usesPhoto = avatar.usesPhoto,
                    photoUrl = avatar.photoUrl,
                    isAvatarSynced = avatar.isAvatarSynced,
                    profileEditedAt = local?.profileEditedAt ?: 0L,
                    avatarEditedAt = avatar.avatarEditedAt,
                    //lo que la nube tiene ahora es la version que este telefono vio por ultima vez
                    profileBase = if (!profilePending && document?.exists() == true) profileKeyOf(document) else local?.profileBase,
                    avatarBase = avatar.avatarBase
                )
            )
            //el avatar de antes de guardarlo en la nube (o de una cuenta creada en flutter) se sube ahora
            if (!avatar.isAvatarSynced) pushAvatarEdit(user.uid)
            //persiste los datos escalares en sharedpreferences en segundo plano (offline-first)
            ProfilePreferences.saveProfile(
                name = resolvedName,
                email = user.email.orEmpty(),
                age = UserModel.ageOf(resolvedBirthDate) ?: resolvedAge
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

    override suspend fun updateProfile(name: String, birthDate: String): Result<ProfileSaveStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            ensureLocalUser(uid)
            //primero room: la pantalla cambia de una vez, haya o no internet
            userDao.updateProfile(uid, name.trim(), birthDate, editedAt = now())
            ProfilePreferences.saveProfile(
                name = name.trim(),
                email = auth.currentUser?.email.orEmpty(),
                age = UserModel.ageOf(birthDate) ?: 0
            )
            pushProfileEdit(uid)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "updateProfile",
                throwable = error,
                extraKeys = mapOf("birthDate" to birthDate)
            )
        }
    }

    override suspend fun updateAvatar(sourceUri: String): Result<ProfileSaveStatus> = withContext(Dispatchers.IO) {
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
            userDao.updatePhoto(uid, destFile.absolutePath, editedAt = now())
            if (previousPath != null && previousPath != destFile.absolutePath) File(previousPath).delete()
            pushAvatarEdit(uid)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "updateAvatar",
                throwable = error
            )
        }
    }

    override suspend fun selectAvatar(avatarId: String): Result<ProfileSaveStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            ensureLocalUser(uid)
            //la foto subida no se borra: el usuario puede volver a ella cuando quiera
            userDao.updateAvatarChoice(uid, Avatar.idOrDefault(avatarId), usesPhoto = false, editedAt = now())
            pushAvatarEdit(uid)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "selectAvatar",
                throwable = error,
                extraKeys = mapOf("avatar_id" to avatarId)
            )
        }
    }

    override suspend fun selectPhoto(): Result<ProfileSaveStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("No user is logged in")
            val local = ensureLocalUser(uid)
            if (local.avatarPath == null) error("There is no uploaded photo to show")
            userDao.updateAvatarChoice(uid, local.avatarId, usesPhoto = true, editedAt = now())
            pushAvatarEdit(uid)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "UserRepository",
                action = "selectPhoto",
                throwable = error
            )
        }
    }

    override suspend fun syncPendingProfile(): ProfileSyncResult = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext ProfileSyncResult()
        val local = userDao.getUser(uid) ?: return@withContext ProfileSyncResult()
        val outcomes = listOfNotNull(
            if (!local.isSynced) pushProfileEdit(uid) else null,
            if (!local.isAvatarSynced) pushAvatarEdit(uid) else null
        )
        //si otro telefono lo cambio despues, se trae su version para que este telefono la muestre
        if (ProfileSaveStatus.CHANGED_ELSEWHERE in outcomes) refreshProfile()
        ProfileSyncResult(
            synced = outcomes.count { it != ProfileSaveStatus.PENDING_UPLOAD },
            failed = outcomes.count { it == ProfileSaveStatus.PENDING_UPLOAD }
        )
    }

    override suspend fun hasPendingProfileChanges(): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        userDao.getUser(uid)?.let { !it.isSynced || !it.isAvatarSynced } ?: false
    }

    override fun takeSyncNotice(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return ProfilePreferences.takeSyncNotice(uid)
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

    //sube nombre y fecha pendientes (las reglas aceptan name, birthDate, age, email y los campos del avatar)
    //va en una transaccion: si la nube ya no tiene la version que este telefono vio por ultima vez, otro telefono lo
    //cambio mientras este estaba sin internet y se deja ese cambio en vez de pisarlo; sin internet la transaccion falla
    //en vez de quedar en la cola de firestore, que la mandaba a ciegas al volver la red
    private suspend fun pushProfileEdit(uid: String): ProfileSaveStatus {
        val local = userDao.getUser(uid)?.takeIf { !it.isSynced } ?: return ProfileSaveStatus.UPLOADED
        if (!isOnline()) return ProfileSaveStatus.PENDING_UPLOAD
        val fields = mutableMapOf<String, Any>("name" to local.name)
        if (local.birthDate.isNotBlank()) {
            fields["birthDate"] = local.birthDate
            //la edad la calcula el modelo a partir de la fecha
            UserModel.ageOf(local.birthDate)?.let { fields["age"] = it }
        } else if (local.age > 0) {
            //perfiles viejos que solo tienen edad
            fields["age"] = local.age
        }
        val result = writeIfUnchanged(
            uid = uid,
            base = local.profileBase,
            keyOf = ::profileKeyOf,
            //como queda la nube despues de escribir: lo que no se escribe se queda como estaba
            resultKey = { remote ->
                profileKey(
                    name = local.name,
                    birthDate = (fields["birthDate"] as? String) ?: remote.getString("birthDate"),
                    age = (fields["age"] as? Int)?.toLong() ?: remote.getLong("age")
                )
            }
        ) { transaction, document -> transaction.set(document, fields, SetOptions.merge()) } ?: return ProfileSaveStatus.PENDING_UPLOAD
        userDao.markAsSynced(uid, local.profileEditedAt)
        if (result.outcome == WriteOutcome.CHANGED_ELSEWHERE) {
            ProfilePreferences.saveSyncNotice(uid, PROFILE_CHANGED_ELSEWHERE)
            return ProfileSaveStatus.CHANGED_ELSEWHERE
        }
        userDao.saveProfileBase(uid, result.cloudKey)
        return ProfileSaveStatus.UPLOADED
    }

    //sube el avatar pendiente: si hay una foto que aun no esta en storage la sube primero y luego escribe
    //avatarId, avatarSource y photoUrl en users/{uid}, con la misma regla: si otro telefono lo cambio, gana ese cambio
    //la url solo se escribe cuando se acaba de subir una foto: asi este telefono nunca devuelve la nube
    //a una foto vieja que se cambio desde otro telefono
    private suspend fun pushAvatarEdit(uid: String): ProfileSaveStatus {
        val local = userDao.getUser(uid)?.takeIf { !it.isAvatarSynced } ?: return ProfileSaveStatus.UPLOADED
        if (!isOnline()) return ProfileSaveStatus.PENDING_UPLOAD
        val path = local.avatarPath
        val uploadedUrl = if (path != null && local.photoUrl == null) {
            uploadPhoto(uid, File(path)) ?: return ProfileSaveStatus.PENDING_UPLOAD
        } else {
            null
        }
        val avatar = UserAvatarDto(
            avatarId = local.avatarId,
            avatarSource = if (local.usesPhoto) UserAvatarDto.SOURCE_PHOTO else UserAvatarDto.SOURCE_PRESET,
            photoUrl = uploadedUrl.orEmpty()
        )
        val result = writeIfUnchanged(
            uid = uid,
            base = local.avatarBase,
            keyOf = ::avatarKeyOf,
            resultKey = { remote -> avatarKey(avatar.avatarId, avatar.avatarSource, uploadedUrl ?: remote.getString("photoUrl")) }
        ) { transaction, document ->
            transaction.set(document, avatar, SetOptions.mergeFields(avatar.fields()))
        } ?: return ProfileSaveStatus.PENDING_UPLOAD
        if (result.outcome == WriteOutcome.CHANGED_ELSEWHERE) {
            //otro telefono cambio la foto de perfil: se queda la de la nube y la foto recien subida sobra
            uploadedUrl?.let { url -> runCatching { withTimeoutOrNull(PHOTO_TIMEOUT_MILLIS) { storage.getReferenceFromUrl(url).delete().await() } } }
            userDao.markAvatarSynced(uid, local.avatarEditedAt)
            ProfilePreferences.saveSyncNotice(uid, PICTURE_CHANGED_ELSEWHERE)
            return ProfileSaveStatus.CHANGED_ELSEWHERE
        }
        if (path != null && uploadedUrl != null) {
            userDao.savePhotoUrl(uid, path, uploadedUrl)
            deleteOldPhotos(uid, keepUrl = uploadedUrl)
        }
        userDao.saveAvatarBase(uid, result.cloudKey)
        userDao.markAvatarSynced(uid, local.avatarEditedAt)
        return ProfileSaveStatus.UPLOADED
    }

    private enum class WriteOutcome { WRITTEN, ALREADY_THERE, CHANGED_ELSEWHERE }

    //que paso al escribir y la version que queda en la nube
    private data class WriteResult(val outcome: WriteOutcome, val cloudKey: String)

    //escribe en users/{uid} solo si la nube sigue con la version que este telefono vio por ultima vez (base)
    //sin version vista (cuentas de antes de este cambio) se escribe como antes; si la nube ya quedo como se queria
    //(una escritura anterior si llego aunque no se supo) no se escribe otra vez
    //null si no se pudo hablar con firestore: el cambio queda pendiente
    private suspend fun writeIfUnchanged(
        uid: String,
        base: String?,
        keyOf: (DocumentSnapshot) -> String,
        resultKey: (DocumentSnapshot) -> String,
        write: (Transaction, DocumentReference) -> Unit
    ): WriteResult? {
        val document = firestore.collection("users").document(uid)
        return runCatching {
            withTimeoutOrNull(PUSH_TIMEOUT_MILLIS) {
                firestore.runTransaction { transaction ->
                    val remote = transaction.get(document)
                    val current = keyOf(remote)
                    val target = resultKey(remote)
                    when {
                        current == target -> WriteResult(WriteOutcome.ALREADY_THERE, current)
                        base != null && remote.exists() && current != base -> WriteResult(WriteOutcome.CHANGED_ELSEWHERE, current)
                        else -> {
                            write(transaction, document)
                            WriteResult(WriteOutcome.WRITTEN, target)
                        }
                    }
                }.await()
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(componentName = "UserRepository", action = "writeIfUnchanged", throwable = error)
        }.getOrNull()
    }

    //version de nombre, fecha y edad de users/{uid}, para compararla con la que vio este telefono
    private fun profileKey(name: String?, birthDate: String?, age: Long?): String =
        listOf(name.orEmpty(), birthDate.orEmpty(), (age ?: 0L).toString()).joinToString(KEY_SEPARATOR)

    private fun profileKeyOf(document: DocumentSnapshot): String =
        profileKey(document.getString("name"), document.getString("birthDate"), document.getLong("age"))

    //version de la foto de perfil; de la url solo cuenta el archivo (el token de descarga puede cambiar)
    private fun avatarKey(avatarId: String?, avatarSource: String?, photoUrl: String?): String =
        listOf(avatarId.orEmpty(), avatarSource.orEmpty(), photoUrl.orEmpty().substringBefore('?')).joinToString(KEY_SEPARATOR)

    private fun avatarKeyOf(document: DocumentSnapshot): String =
        avatarKey(document.getString("avatarId"), document.getString("avatarSource"), document.getString("photoUrl"))

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
            isAvatarSynced = true,
            avatarBase = avatarKey(remote.avatarId, remote.avatarSource, remote.photoUrl)
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
        private const val KEY_SEPARATOR = "|"
        //avisos para la pantalla de perfil cuando se dejo la version de otro telefono
        private const val PROFILE_CHANGED_ELSEWHERE =
            "Your profile was changed on another device, so that version was kept"
        private const val PICTURE_CHANGED_ELSEWHERE =
            "Your picture was changed on another device, so that one was kept"
        //subir o bajar la foto tarda mas que escribir un documento
        private const val PHOTO_TIMEOUT_MILLIS = 20_000L

        //carpeta de la foto de perfil en storage (storage.rules: users/{userId}/avatar/{fileName})
        fun avatarFolder(uid: String): String = "users/$uid/avatar"
        private const val AVATAR_SIZE_PX = 512
        private const val AVATAR_JPEG_QUALITY = 90
    }
}

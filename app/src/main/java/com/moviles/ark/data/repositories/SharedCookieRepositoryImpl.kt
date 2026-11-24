package com.moviles.ark.data.repositories

import android.util.Log
import com.moviles.ark.data.local.daos.SentGiftDao
import com.moviles.ark.data.local.entities.SentGiftEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.SharedCookieCloudStore
import com.moviles.ark.data.remote.isObjectNotFound
import com.moviles.ark.data.remote.dtos.SharedCookieDto
import com.moviles.ark.domain.exceptions.ConnectionLostException
import com.moviles.ark.domain.exceptions.GiftUnavailableException
import com.moviles.ark.domain.models.CookieGifts
import com.moviles.ark.domain.models.Friend
import com.moviles.ark.domain.models.GiftAcceptResult
import com.moviles.ark.domain.models.InboxSnapshot
import com.moviles.ark.domain.models.SentGift
import com.moviles.ark.domain.models.SharedCookie
import com.moviles.ark.domain.repositories.CookieRepository
import com.moviles.ark.domain.repositories.SharedCookieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

//regalos del cookie jar: solo orquesta
//la bandeja viene del backend (SharedCookieCloudStore) y no se guarda en el telefono; los enviados van a room (SentGiftDao);
//una galleta aceptada entra a las galletas del usuario por CookieRepository (room + almacenamiento privado + sync)
class SharedCookieRepositoryImpl(
    private val cloud: SharedCookieCloudStore,
    private val cookieRepository: CookieRepository,
    private val sentGiftDao: SentGiftDao,
    private val userIdProvider: () -> String?,
    //username propio para firmar el regalo (perfil publico)
    private val myUsername: suspend () -> String?,
    //foto huerfana en shared/ (fallo la red al borrarla): la borra un worker cuando haya red
    private val onOrphanImage: (userId: String, shareId: String) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) : SharedCookieRepository {

    override fun observeInbox(): Flow<InboxSnapshot> {
        val uid = userIdProvider() ?: return flowOf(InboxSnapshot.Offline)
        return cloud.observeInbox(uid).map { event ->
            //la cache de firestore no cuenta: sin red la bandeja no muestra nada
            if (event.isFromCache) InboxSnapshot.Offline else InboxSnapshot.Loaded(event.gifts.map { it.toModel() })
        }
    }

    override fun observeSentGifts(): Flow<List<SentGift>> {
        val uid = userIdProvider() ?: return flowOf(emptyList())
        return sentGiftDao.observeSent(uid).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun sendGift(cookieId: String, friend: Friend): Result<SentGift> = gifts("sendGift") {
        val uid = requireUser()
        val fromUsername = myUsername() ?: error("Turn on friends first")
        val cookie = cookieRepository.getCookie(cookieId) ?: error("This cookie no longer exists")
        val bytes = cookieRepository.imageBytesFor(cookieId).getOrThrow()
        val shareId = UUID.randomUUID().toString()
        val sentAt = now()
        val dto = SharedCookieDto(
            shareId = shareId,
            from = uid,
            fromUsername = fromUsername,
            to = friend.uid,
            title = cookie.title,
            text = cookie.text,
            hasImage = bytes != null,
            sentAt = sentAt,
            expiresAt = CookieGifts.expiresAt(sentAt),
        )
        try {
            //1. primero la foto: sin documento no hay regalo, asi que una foto sola no le llega a nadie
            if (bytes != null) {
                val started = System.nanoTime()
                cloud.uploadImage(shareId, uid, friend.uid, bytes)
                Log.d(TAG, "gift picture uploaded id=$shareId (${bytes.size / 1024} KB) in ${(System.nanoTime() - started) / 1_000_000} ms")
            }
            //2. el documento con una transaccion
            cloud.createGift(dto)
        } catch (e: Throwable) {
            //fallo o se cancelo (la red se cayo a mitad): la foto pudo alcanzar a subir y quedar huerfana
            if (bytes != null) withContext(NonCancellable) { discardIfOrphan(uid, shareId) }
            throw e
        }
        //el servidor confirmo: recien aqui el regalo cuenta como enviado (M23)
        val sent = SentGiftEntity(
            shareId = shareId, userId = uid, toUid = friend.uid, toUsername = friend.username,
            title = cookie.title, hasImage = bytes != null, sentAt = sentAt, expiresAt = dto.expiresAt,
        )
        withContext(NonCancellable) { sentGiftDao.insert(sent) }
        Log.d(TAG, "gift sent id=$shareId to @${friend.username}")
        sent.toModel()
    }

    override suspend fun cancelGift(shareId: String): Result<Boolean> = gifts("cancelGift") {
        val uid = requireUser()
        //primero el documento: el destinatario deja de verlo; despues la foto
        val canceled = cloud.deleteGiftIfExists(shareId)
        if (canceled) deleteImageOrEnqueue(uid, shareId)
        //cancelado o ya no existe: en los dos casos ya no hay nada que cancelar
        withContext(NonCancellable) { sentGiftDao.delete(shareId, uid) }
        Log.d(TAG, "cancel gift id=$shareId: ${if (canceled) "canceled" else "no longer exists"}")
        canceled
    }

    override suspend fun declineGift(gift: SharedCookie): Result<Unit> = gifts("declineGift") {
        val uid = requireUser()
        if (cloud.deleteGiftIfExists(gift.shareId) && gift.hasImage) deleteImageOrEnqueue(uid, gift.shareId)
        Log.d(TAG, "gift declined id=${gift.shareId}")
    }

    override suspend fun acceptGift(gift: SharedCookie, onSaved: () -> Unit): Result<GiftAcceptResult> = gifts("acceptGift") {
        val uid = requireUser()
        if (gift.toUid != uid) error("This gift is for another account")
        //1. la foto a memoria (red); si se corta no queda nada en el telefono y el regalo sigue en la bandeja
        val bytes = if (gift.hasImage) {
            try {
                cloud.downloadImage(gift.shareId)
            } catch (e: Exception) {
                if (e is CancellationException || e is ConnectionLostException || !e.isObjectNotFound()) throw e
                //sin foto en la nube: si el regalo tampoco existe se cancelo o vencio; si existe se acepta sin foto
                if (!cloud.giftExists(gift.shareId)) throw GiftUnavailableException()
                Log.w(TAG, "gift id=${gift.shareId} lost its picture, accepting the text only")
                null
            }
        } else {
            null
        }
        //2. .tmp -> .jpg y la fila en room con id = shareId: desde aqui la galleta es del usuario
        //NonCancellable: si la red se cae justo aqui la galleta igual queda guardada entera (archivo + fila), nunca a medias
        val cookie = withContext(NonCancellable) {
            cookieRepository.addReceivedCookie(
                id = gift.shareId,
                title = gift.title,
                text = gift.text,
                receivedFrom = gift.fromUsername,
                jpegBytes = bytes,
            ).getOrThrow().also { onSaved() }
        }
        //3. se borra el regalo del backend; si la red se cae aqui, la galleta ya esta a salvo y esto se termina al volver
        val finished = try {
            finishCleanup(uid, gift)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "gift id=${gift.shareId} saved on this phone, cleanup pending: ${e.javaClass.simpleName}")
            false
        }
        //4. la sube el SyncWorker (addReceivedCookie ya la pidio)
        if (finished) GiftAcceptResult.Done(cookie) else GiftAcceptResult.SavedPendingCleanup(cookie)
    }

    override suspend fun finishAccepted(gift: SharedCookie): Result<Unit> = gifts("finishAccepted") {
        finishCleanup(requireUser(), gift)
        Log.d(TAG, "finished the accepted gift id=${gift.shareId} in the background")
    }

    override suspend fun deleteExpired(gifts: List<SharedCookie>) {
        val uid = userIdProvider() ?: return
        for (gift in gifts) {
            gifts("deleteExpired") {
                if (cloud.deleteGiftIfExists(gift.shareId) && gift.hasImage) deleteImageOrEnqueue(uid, gift.shareId)
                Log.d(TAG, "expired gift deleted id=${gift.shareId}")
            }
        }
    }

    //la foto de shared/ es huerfana solo si su regalo ya no existe: un regalo que si llego conserva su foto
    override suspend fun deleteOrphanImage(userId: String, shareId: String): Result<Unit> = gifts("deleteOrphanImage") {
        if (requireUser() != userId) error("Another account is logged in")
        if (cloud.giftExists(shareId)) {
            Log.d(TAG, "picture id=$shareId belongs to a gift that arrived, keeping it")
        } else {
            cloud.deleteImage(shareId)
            Log.d(TAG, "orphan gift picture deleted id=$shareId")
        }
    }

    //paso 3: primero el documento y despues la foto (si la foto falla la borra el worker)
    private suspend fun finishCleanup(uid: String, gift: SharedCookie) {
        cloud.deleteGiftIfExists(gift.shareId)
        if (gift.hasImage) deleteImageOrEnqueue(uid, gift.shareId)
    }

    //la transaccion del regalo fallo, pero pudo llegar al servidor justo antes del timeout: la foto se borra solo si
    //el regalo de verdad no existe; si no se puede saber (sin red) decide el worker cuando vuelva la red
    private suspend fun discardIfOrphan(uid: String, shareId: String) {
        try {
            withContext(NonCancellable) { if (!cloud.giftExists(shareId)) cloud.deleteImage(shareId) }
        } catch (e: Exception) {
            Log.w(TAG, "gift picture id=$shareId may be orphan, queued: ${e.javaClass.simpleName}")
            onOrphanImage(uid, shareId)
        }
    }

    private suspend fun deleteImageOrEnqueue(uid: String, shareId: String) {
        try {
            withContext(NonCancellable) { cloud.deleteImage(shareId) }
        } catch (e: Exception) {
            Log.w(TAG, "gift picture id=$shareId not deleted yet, queued: ${e.javaClass.simpleName}")
            onOrphanImage(uid, shareId)
        }
    }

    private fun requireUser(): String = userIdProvider() ?: error("No user is logged in")

    private fun SharedCookieDto.toModel() = SharedCookie(
        shareId = shareId, fromUid = from, fromUsername = fromUsername, toUid = to, title = title, text = text,
        hasImage = hasImage, sentAt = sentAt, expiresAt = expiresAt,
    )

    private fun SentGiftEntity.toModel() = SentGift(
        shareId = shareId, toUid = toUid, toUsername = toUsername, title = title, sentAt = sentAt, expiresAt = expiresAt,
    )

    //cada operacion en Dispatchers.IO con Result; la cancelacion no es un error
    private suspend fun <T> gifts(action: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName} ${e.message}")
            if (e !is ConnectionLostException && e !is GiftUnavailableException) {
                CrashlyticsHelper.logNonFatal(componentName = "SharedCookieRepository", action = action, throwable = e)
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CookieGifts"
    }
}

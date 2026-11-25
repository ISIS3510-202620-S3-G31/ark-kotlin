package com.moviles.ark.data.repositories

import android.util.Log
import com.moviles.ark.data.local.daos.SentGiftDao
import com.moviles.ark.data.local.entities.SentGiftEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.SharedCookieCloudStore
import com.moviles.ark.data.remote.dtos.SharedCookieDto
import com.moviles.ark.data.remote.isObjectNotFound
import com.moviles.ark.domain.exceptions.ConnectionLostException
import com.moviles.ark.domain.exceptions.GiftUnavailableException
import com.moviles.ark.domain.models.CookieGifts
import com.moviles.ark.domain.models.CookieModel
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
            if (event.isFromCache) {
                InboxSnapshot.Offline
            } else {
                val gifts = event.gifts.map { it.toModel() }
                InboxSnapshot.Loaded(gifts)
            }
        }
    }

    override fun observeSentGifts(): Flow<List<SentGift>> {
        val uid = userIdProvider() ?: return flowOf(emptyList())
        return sentGiftDao.observeSent(uid).map { rows -> rows.map { it.toModel() } }
    }

    //regalar: primero la foto, despues el documento, y solo cuando el servidor confirmo se guarda en "enviados"
    override suspend fun sendGift(cookieId: String, friend: Friend): Result<SentGift> = runSafely("sendGift") {
        val uid = requireUser()
        val fromUsername = myUsername() ?: error("Turn on friends first")
        val cookie = cookieRepository.getCookie(cookieId) ?: error("This cookie no longer exists")
        val pictureBytes = cookieRepository.imageBytesFor(cookieId).getOrThrow()
        val gift = buildGift(uid, fromUsername, cookie, friend, pictureBytes != null)

        uploadGift(uid, gift, pictureBytes)

        //el servidor confirmo: recien aqui el regalo cuenta como enviado (M23)
        val sentGift = SentGiftEntity(
            shareId = gift.shareId,
            userId = uid,
            toUid = friend.uid,
            toUsername = friend.username,
            title = cookie.title,
            hasImage = gift.hasImage,
            sentAt = gift.sentAt,
            expiresAt = gift.expiresAt,
        )
        //aunque el usuario salga de la pantalla justo aqui, el regalo ya llego: se guarda igual
        withContext(NonCancellable) { sentGiftDao.insert(sentGift) }
        Log.d(TAG, "gift sent id=${gift.shareId} to @${friend.username}")
        sentGift.toModel()
    }

    //cancelar un regalo enviado: true si se cancelo, false si ya no existia
    override suspend fun cancelGift(shareId: String): Result<Boolean> = runSafely("cancelGift") {
        val uid = requireUser()
        //primero el documento: el destinatario deja de verlo; despues la foto
        val canceled = cloud.deleteGiftIfExists(shareId)
        if (canceled) {
            deleteImageOrEnqueue(uid, shareId)
        }
        //cancelado o ya no existe: en los dos casos ya no hay nada que cancelar
        withContext(NonCancellable) { sentGiftDao.delete(shareId, uid) }
        if (canceled) {
            Log.d(TAG, "cancel gift id=$shareId: canceled")
        } else {
            Log.d(TAG, "cancel gift id=$shareId: no longer exists")
        }
        canceled
    }

    override suspend fun declineGift(gift: SharedCookie): Result<Unit> = runSafely("declineGift") {
        val uid = requireUser()
        deleteGiftAndPicture(uid, gift)
        Log.d(TAG, "gift declined id=${gift.shareId}")
    }

    //1 baja la foto, 2 la guarda y crea la galleta en room, 3 borra el regalo del backend, 4 el sync la sube
    override suspend fun acceptGift(gift: SharedCookie, onSaved: () -> Unit): Result<GiftAcceptResult> = runSafely("acceptGift") {
        val uid = requireUser()
        if (gift.toUid != uid) error("This gift is for another account")

        //1. la foto a memoria (red); si se corta no queda nada en el telefono y el regalo sigue en la bandeja
        val pictureBytes = downloadGiftPicture(gift)

        //2. desde aqui la galleta es del usuario
        val cookie = saveGiftInJar(gift, pictureBytes, onSaved)

        //3. se borra el regalo del backend; si la red se cae aqui, la galleta ya esta a salvo y esto se termina al volver
        val cleanupFinished = tryFinishCleanup(uid, gift)

        //4. la sube el SyncWorker (addReceivedCookie ya la pidio)
        if (cleanupFinished) {
            GiftAcceptResult.Done(cookie)
        } else {
            GiftAcceptResult.SavedPendingCleanup(cookie)
        }
    }

    override suspend fun finishAccepted(gift: SharedCookie): Result<Unit> = runSafely("finishAccepted") {
        val uid = requireUser()
        finishCleanup(uid, gift)
        Log.d(TAG, "finished the accepted gift id=${gift.shareId} in the background")
    }

    //cada regalo vencido se borra por separado: si uno falla se sigue con los demas
    override suspend fun deleteExpired(gifts: List<SharedCookie>) {
        val uid = userIdProvider() ?: return
        for (gift in gifts) {
            runSafely("deleteExpired") {
                deleteGiftAndPicture(uid, gift)
                Log.d(TAG, "expired gift deleted id=${gift.shareId}")
            }
        }
    }

    //la foto de shared/ es huerfana solo si su regalo ya no existe: un regalo que si llego conserva su foto
    override suspend fun deleteOrphanImage(userId: String, shareId: String): Result<Unit> = runSafely("deleteOrphanImage") {
        val uid = requireUser()
        if (uid != userId) error("Another account is logged in")
        if (cloud.giftExists(shareId)) {
            Log.d(TAG, "picture id=$shareId belongs to a gift that arrived, keeping it")
        } else {
            cloud.deleteImage(shareId)
            Log.d(TAG, "orphan gift picture deleted id=$shareId")
        }
    }

    //el documento del regalo, con su id nuevo y su fecha de vencimiento (7 dias)
    private fun buildGift(uid: String, fromUsername: String, cookie: CookieModel, friend: Friend, hasImage: Boolean): SharedCookieDto {
        val sentAt = now()
        return SharedCookieDto(
            shareId = UUID.randomUUID().toString(),
            from = uid,
            fromUsername = fromUsername,
            to = friend.uid,
            title = cookie.title,
            text = cookie.text,
            hasImage = hasImage,
            sentAt = sentAt,
            expiresAt = CookieGifts.expiresAt(sentAt),
        )
    }

    //sube la foto (si tiene) y despues el documento con una transaccion
    //si algo falla o se cancela, la foto que alcanzo a subir no puede quedar huerfana
    private suspend fun uploadGift(uid: String, gift: SharedCookieDto, pictureBytes: ByteArray?) {
        try {
            //primero la foto: sin documento no hay regalo, asi que una foto sola no le llega a nadie
            if (pictureBytes != null) {
                val startedAt = System.nanoTime()
                cloud.uploadImage(gift.shareId, uid, gift.to, pictureBytes)
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                Log.d(TAG, "gift picture uploaded id=${gift.shareId} (${pictureBytes.size / 1024} KB) in $elapsedMs ms")
            }
            cloud.createGift(gift)
        } catch (e: Throwable) {
            if (pictureBytes != null) {
                withContext(NonCancellable) { discardIfOrphan(uid, gift.shareId) }
            }
            throw e
        }
    }

    //paso 1 de aceptar: la foto completa a memoria; null si el regalo no tiene foto
    private suspend fun downloadGiftPicture(gift: SharedCookie): ByteArray? {
        if (!gift.hasImage) return null
        try {
            return cloud.downloadImage(gift.shareId)
        } catch (e: Exception) {
            //sin red o cancelado: el regalo sigue en la bandeja
            if (e is CancellationException || e is ConnectionLostException) throw e
            if (!isObjectNotFound(e)) throw e
        }
        //la foto no esta en la nube: si el regalo tampoco existe se cancelo o vencio; si existe se acepta sin foto
        if (!cloud.giftExists(gift.shareId)) {
            throw GiftUnavailableException()
        }
        Log.w(TAG, "gift id=${gift.shareId} lost its picture, accepting the text only")
        return null
    }

    //paso 2 de aceptar: .tmp -> .jpg y la fila en room con id = shareId
    //NonCancellable: si la red se cae justo aqui la galleta igual queda guardada entera (archivo + fila), nunca a medias
    //onSaved avisa a la pantalla apenas la galleta quedo guardada
    private suspend fun saveGiftInJar(gift: SharedCookie, pictureBytes: ByteArray?, onSaved: () -> Unit): CookieModel {
        return withContext(NonCancellable) {
            val saveResult = cookieRepository.addReceivedCookie(
                id = gift.shareId,
                title = gift.title,
                text = gift.text,
                receivedFrom = gift.fromUsername,
                jpegBytes = pictureBytes,
            )
            val cookie = saveResult.getOrThrow()
            onSaved()
            cookie
        }
    }

    //paso 3 de aceptar; true si se termino, false si quedo pendiente porque fallo la red
    private suspend fun tryFinishCleanup(uid: String, gift: SharedCookie): Boolean {
        try {
            finishCleanup(uid, gift)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "gift id=${gift.shareId} saved on this phone, cleanup pending: ${e.javaClass.simpleName}")
            return false
        }
    }

    //paso 3: primero el documento y despues la foto (si la foto falla la borra el worker)
    private suspend fun finishCleanup(uid: String, gift: SharedCookie) {
        cloud.deleteGiftIfExists(gift.shareId)
        if (gift.hasImage) {
            deleteImageOrEnqueue(uid, gift.shareId)
        }
    }

    //rechazar o vencer: se borra el documento y, si de verdad estaba y tenia foto, tambien la foto
    private suspend fun deleteGiftAndPicture(uid: String, gift: SharedCookie) {
        val wasDeleted = cloud.deleteGiftIfExists(gift.shareId)
        if (wasDeleted && gift.hasImage) {
            deleteImageOrEnqueue(uid, gift.shareId)
        }
    }

    //la transaccion del regalo fallo, pero pudo llegar al servidor justo antes del timeout: la foto se borra solo si
    //el regalo de verdad no existe; si no se puede saber (sin red) decide el worker cuando vuelva la red
    private suspend fun discardIfOrphan(uid: String, shareId: String) {
        try {
            withContext(NonCancellable) {
                val giftArrived = cloud.giftExists(shareId)
                if (!giftArrived) {
                    cloud.deleteImage(shareId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "gift picture id=$shareId may be orphan, queued: ${e.javaClass.simpleName}")
            onOrphanImage(uid, shareId)
        }
    }

    //si no se puede borrar la foto ahora (sin red), se le deja el trabajo al worker
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
        shareId = shareId,
        fromUid = from,
        fromUsername = fromUsername,
        toUid = to,
        title = title,
        text = text,
        hasImage = hasImage,
        sentAt = sentAt,
        expiresAt = expiresAt,
    )

    private fun SentGiftEntity.toModel() = SentGift(
        shareId = shareId,
        toUid = toUid,
        toUsername = toUsername,
        title = title,
        sentAt = sentAt,
        expiresAt = expiresAt,
    )

    //cada operacion en Dispatchers.IO con Result; la cancelacion no es un error
    //sin red o un regalo que ya no existe son errores esperados: no se mandan a crashlytics
    private suspend fun <T> runSafely(action: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName} ${e.message}")
            val isExpected = e is ConnectionLostException || e is GiftUnavailableException
            if (!isExpected) {
                CrashlyticsHelper.logNonFatal(componentName = "SharedCookieRepository", action = action, throwable = e)
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CookieGifts"
    }
}

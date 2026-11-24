package com.moviles.ark.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.moviles.ark.data.remote.dtos.SharedCookieDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

//aqui se usa firebase: regalos del cookie jar
//firestore shared_cookies/{shareId} guarda la copia (titulo y texto) y storage shared/{shareId}.jpg la foto
//las fotos llevan en su metadata quien la envia y a quien (from, to): asi storage.rules sabe quien la puede borrar
//aunque el documento ya se haya borrado
class SharedCookieCloudStore(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {

    //lo que entrega el listener: los regalos y si vienen de la cache local de firestore (sin red)
    data class InboxEvent(val gifts: List<SharedCookieDto>, val isFromCache: Boolean)

    //listener en vivo de la bandeja (callbackFlow como NetworkConnectivityObserver): firestore llama en su hilo,
    //trySend lo pasa al flow y awaitClose quita el listener cuando nadie escucha
    //MetadataChanges.INCLUDE: tambien avisa cuando los mismos datos pasan de la cache al servidor y al reves (red que va y viene)
    fun observeInbox(uid: String): Flow<InboxEvent> = callbackFlow {
        val registration = gifts().whereEqualTo(FIELD_TO, uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    //el listener ya no sigue (por ejemplo sin permiso): se trata como sin red
                    Log.w(TAG, "inbox listener failed: ${error.code}")
                    trySend(InboxEvent(emptyList(), isFromCache = true))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val gifts = snapshot.documents.mapNotNull { doc -> doc.toObject(SharedCookieDto::class.java)?.copy(shareId = doc.id) }
                Log.d(TAG, "inbox snapshot: ${gifts.size} gifts, fromCache=${snapshot.metadata.isFromCache}")
                trySend(InboxEvent(gifts, snapshot.metadata.isFromCache))
            }
        awaitClose { registration.remove() }
    }

    //paso 1 de regalar: la foto ya comprimida de la galleta (no se vuelve a comprimir)
    //si se vence el tiempo se cancela la subida para que no siga sola en segundo plano
    suspend fun uploadImage(shareId: String, fromUid: String, toUid: String, jpegBytes: ByteArray) = withContext(Dispatchers.IO) {
        val metadata = storageMetadata {
            contentType = JPEG_CONTENT_TYPE
            setCustomMetadata(META_FROM, fromUid)
            setCustomMetadata(META_TO, toUid)
        }
        Log.d(
            TAG,
            "uploading ${imagePath(shareId)} (${jpegBytes.size} bytes) contentType=${metadata.contentType} " +
                "from=${metadata.getCustomMetadata(META_FROM)} to=${metadata.getCustomMetadata(META_TO)}"
        )
        val task = storage.reference.child(imagePath(shareId)).putBytes(jpegBytes, metadata)
        try {
            task.awaitOrConnectionLost(SOCIAL_PHOTO_TIMEOUT_MS)
        } catch (e: Throwable) {
            //timeout, red caida o el usuario salio: la subida no sigue sola en segundo plano
            task.cancel()
            throw e
        }
        Unit
    }

    //paso 2 de regalar: el documento con una transaccion (sin red falla, no queda en cola: o llego o no llego)
    //false si ya existia (un intento anterior si llego)
    suspend fun createGift(gift: SharedCookieDto): Boolean = withContext(Dispatchers.IO) {
        val ref = gifts().document(gift.shareId)
        firestore.runTransaction { transaction ->
            if (transaction.get(ref).exists()) {
                false
            } else {
                transaction.set(ref, gift)
                true
            }
        }.awaitOrConnectionLost()
    }

    //borra el regalo si existe; false si ya no estaba (se acepto, se rechazo, se cancelo o vencio)
    suspend fun deleteGiftIfExists(shareId: String): Boolean = withContext(Dispatchers.IO) {
        val ref = gifts().document(shareId)
        firestore.runTransaction { transaction ->
            if (transaction.get(ref).exists()) {
                transaction.delete(ref)
                true
            } else {
                false
            }
        }.awaitOrConnectionLost()
    }

    suspend fun giftExists(shareId: String): Boolean = withContext(Dispatchers.IO) {
        gifts().document(shareId).get(Source.SERVER).awaitOrConnectionLost().exists()
    }

    //paso 1 de aceptar: la foto completa a memoria; quien la guarda en el telefono es el almacenamiento privado
    suspend fun downloadImage(shareId: String): ByteArray = withContext(Dispatchers.IO) {
        storage.reference.child(imagePath(shareId)).getBytes(MAX_IMAGE_BYTES).awaitOrConnectionLost(SOCIAL_PHOTO_TIMEOUT_MS)
    }

    //borra la foto de un regalo; si ya no estaba no pasa nada (repetirlo no hace dano)
    //storage.rules decide con la metadata de la foto: si la foto no existe no hay metadata y storage responde
    //"sin permiso" en vez de "no existe"; en los dos casos no queda nada que este usuario pueda borrar
    suspend fun deleteImage(shareId: String) = withContext(Dispatchers.IO) {
        try {
            storage.reference.child(imagePath(shareId)).delete().awaitOrConnectionLost()
        } catch (e: Exception) {
            if (!e.isObjectNotFound() && !e.isNotAuthorized()) throw e
            Log.d(TAG, "gift picture ${imagePath(shareId)} not there (or not ours): nothing to delete")
        }
        Unit
    }

    private fun gifts() = firestore.collection(SHARED_COOKIES)

    companion object {
        private const val TAG = "CookieGifts"
        private const val SHARED_COOKIES = "shared_cookies"
        private const val FIELD_TO = "to"
        private const val META_FROM = "from"
        private const val META_TO = "to"
        private const val JPEG_CONTENT_TYPE = "image/jpeg"
        //el mismo tope de storage.rules
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

        fun imagePath(shareId: String) = "shared/$shareId.jpg"
    }
}

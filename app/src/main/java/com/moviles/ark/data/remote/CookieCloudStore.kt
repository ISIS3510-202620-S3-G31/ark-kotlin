package com.moviles.ark.data.remote

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import com.moviles.ark.data.remote.dtos.CookieDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

//aqui se usa firebase: la copia en la nube de las galletas del usuario
//firestore users/{uid}/cookies/{cookieId} guarda los datos y storage users/{uid}/cookies/{cookieId}.jpg la imagen
//toda llamada lleva timeout: una red que "miente" (wifi lento o con login) se trata igual que no tener red,
//y una escritura de firestore sin red nunca termina sola (queda en su cola), asi que sin timeout se esperaria para siempre
class CookieCloudStore(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {

    //sube el jpg ya comprimido y devuelve su url de descarga
    //mismo nombre que el id: si la subida se corta y se repite queda un solo archivo
    suspend fun uploadImage(userId: String, cookieId: String, jpegBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val reference = storage.reference.child(imagePath(userId, cookieId))
        val metadata = storageMetadata { contentType = JPEG_CONTENT_TYPE }
        reference.putBytes(jpegBytes, metadata).awaitWithTimeout(PHOTO_UPLOAD_TIMEOUT_MS)
        val downloadUrl = reference.downloadUrl.awaitWithTimeout()
        downloadUrl.toString()
    }

    //escribe el documento si la version de la nube no es mas nueva (last write wins por updatedAt)
    //devuelve null si se escribio, o la version de la nube si gano; es una transaccion: sin red falla en vez de quedar en cola
    suspend fun pushCookie(userId: String, cookie: CookieDto): CookieDto? = withContext(Dispatchers.IO) {
        val document = cookiesOf(userId).document(cookie.id)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val remoteUpdatedAt = snapshot.getLong(FIELD_UPDATED_AT)
            val cloudIsNewer = snapshot.exists() && remoteUpdatedAt != null && remoteUpdatedAt > cookie.updatedAt
            if (cloudIsNewer) {
                cookieFrom(snapshot)
            } else {
                transaction.set(document, cookie)
                null
            }
        }.awaitWithTimeout()
    }

    //metadatos de todas las galletas del usuario (sin imagenes), de a 100 por pagina y siempre del servidor
    //Source.SERVER: sin red falla en vez de devolver la copia vieja de la cache de firestore
    suspend fun fetchAll(userId: String): List<CookieDto> = withContext(Dispatchers.IO) {
        val allCookies = mutableListOf<CookieDto>()
        //ultimo documento de la pagina anterior: la siguiente pagina empieza despues de el
        var lastDocument: DocumentSnapshot? = null
        while (true) {
            var query = cookiesOf(userId).orderBy(FieldPath.documentId()).limit(PAGE_SIZE.toLong())
            if (lastDocument != null) {
                query = query.startAfter(lastDocument)
            }
            val page = query.get(Source.SERVER).awaitWithTimeout()
            for (document in page.documents) {
                val cookie = cookieFrom(document)
                if (cookie != null) allCookies.add(cookie)
            }
            //una pagina incompleta es la ultima
            if (page.size() < PAGE_SIZE) break
            lastDocument = page.documents.last()
        }
        allCookies
    }

    //baja el jpg completo a memoria (~400 kb); el que lo guarda en el telefono es el almacenamiento privado
    suspend fun downloadImage(userId: String, cookieId: String): ByteArray = withContext(Dispatchers.IO) {
        val reference = storage.reference.child(imagePath(userId, cookieId))
        reference.getBytes(MAX_IMAGE_BYTES).awaitWithTimeout(PHOTO_UPLOAD_TIMEOUT_MS)
    }

    //primero el documento (los otros telefonos dejan de verla) y despues la imagen
    //si la imagen falla queda un archivo huerfano y el worker reintenta; repetirlo no hace dano
    suspend fun deleteCookie(userId: String, cookieId: String) {
        withContext(Dispatchers.IO) {
            cookiesOf(userId).document(cookieId).delete().awaitWithTimeout()
            try {
                storage.reference.child(imagePath(userId, cookieId)).delete().awaitWithTimeout()
            } catch (e: StorageException) {
                //no tenia imagen o ya se habia borrado
                if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            }
        }
    }

    private fun cookiesOf(userId: String) = firestore.collection(USERS).document(userId).collection(COOKIES)

    //el id no es un campo del documento: se copia del id del documento
    private fun cookieFrom(snapshot: DocumentSnapshot): CookieDto? {
        val cookie = snapshot.toObject(CookieDto::class.java) ?: return null
        return cookie.copy(id = snapshot.id)
    }

    private suspend fun <T> Task<T>.awaitWithTimeout(timeoutMs: Long = REMOTE_TIMEOUT_MS): T = withTimeout(timeoutMs) { await() }

    companion object {
        const val REMOTE_TIMEOUT_MS = 20_000L
        //el jpg pesa mas que un documento y tarda mas en subir o bajar
        const val PHOTO_UPLOAD_TIMEOUT_MS = 90_000L
        const val PAGE_SIZE = 100
        //el mismo tope de storage.rules
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        private const val JPEG_CONTENT_TYPE = "image/jpeg"
        private const val USERS = "users"
        private const val COOKIES = "cookies"
        private const val FIELD_UPDATED_AT = "updatedAt"

        fun imagePath(userId: String, cookieId: String) = "users/$userId/cookies/$cookieId.jpg"
    }
}

package com.moviles.ark.data.remote

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.moviles.ark.domain.exceptions.ConnectionLostException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

//llamadas de red de amigos y regalos: todas con timeout (una red que "miente" se trata igual que no tener red)
//el timeout y la red caida salen como ConnectionLostException, que no es una cancelacion: el viewmodel la avisa
//(TimeoutCancellationException si es una cancelacion y se perderia en silencio)
internal const val SOCIAL_TIMEOUT_MS = 20_000L
//las fotos pesan mas que un documento
internal const val SOCIAL_PHOTO_TIMEOUT_MS = 90_000L

//espera la tarea de firebase con timeout, igual que awaitWithTimeout de SyncRepositoryImpl
internal suspend fun <T> Task<T>.awaitOrConnectionLost(timeoutMs: Long = SOCIAL_TIMEOUT_MS): T {
    try {
        return withTimeout(timeoutMs) { await() }
    } catch (e: TimeoutCancellationException) {
        throw ConnectionLostException(e)
    } catch (e: FirebaseFirestoreException) {
        //UNAVAILABLE: sin red (las transacciones y las lecturas del servidor no esperan a que vuelva)
        val noNetwork = e.code == FirebaseFirestoreException.Code.UNAVAILABLE
        val tooSlow = e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        if (noNetwork || tooSlow) {
            throw ConnectionLostException(e)
        }
        throw e
    } catch (e: StorageException) {
        val gaveUpRetrying = e.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED
        if (gaveUpRetrying || e.isRecoverableException) {
            throw ConnectionLostException(e)
        }
        throw e
    }
}

//true si firestore nego el permiso (por ejemplo el otro te bloqueo)
internal fun isPermissionDenied(error: Throwable): Boolean {
    if (error !is FirebaseFirestoreException) return false
    return error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
}

//true si el archivo de storage no existe
internal fun isObjectNotFound(error: Throwable): Boolean {
    if (error !is StorageException) return false
    return error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
}

//storage nego el permiso (o el archivo no existe y la regla no tiene metadata que revisar)
internal fun isNotAuthorized(error: Throwable): Boolean {
    if (error !is StorageException) return false
    return error.errorCode == StorageException.ERROR_NOT_AUTHORIZED
}

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

internal suspend fun <T> Task<T>.awaitOrConnectionLost(timeoutMs: Long = SOCIAL_TIMEOUT_MS): T {
    return try {
        withTimeout(timeoutMs) { await() }
    } catch (e: TimeoutCancellationException) {
        throw ConnectionLostException(e)
    } catch (e: FirebaseFirestoreException) {
        //UNAVAILABLE: sin red (las transacciones y las lecturas del servidor no esperan a que vuelva)
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE || e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
            throw ConnectionLostException(e)
        }
        throw e
    } catch (e: StorageException) {
        if (e.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED || e.isRecoverableException) throw ConnectionLostException(e)
        throw e
    }
}

//true si firestore nego el permiso (por ejemplo el otro te bloqueo)
internal fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

//true si el archivo de storage no existe
internal fun Throwable.isObjectNotFound(): Boolean =
    this is StorageException && errorCode == StorageException.ERROR_OBJECT_NOT_FOUND

//storage nego el permiso (o el archivo no existe y la regla no tiene metadata que revisar)
internal fun Throwable.isNotAuthorized(): Boolean =
    this is StorageException && errorCode == StorageException.ERROR_NOT_AUTHORIZED

package com.moviles.ark.data.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.moviles.ark.ArkApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

//borra de la nube (firestore y storage) una galleta que el usuario borro para siempre en el telefono
//workmanager guarda el pedido aunque se cierre la app y lo corre cuando hay red; primero el documento, luego la imagen
class RemoteCookieDeleteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(KEY_USER_ID)
        val cookieId = inputData.getString(KEY_COOKIE_ID)
        val repository = (applicationContext as? ArkApplication)?.container?.cookieRepository
        if (userId.isNullOrBlank() || cookieId.isNullOrBlank() || repository == null) return@withContext Result.failure()

        val deleteResult = repository.deleteRemoteCopy(userId, cookieId)
        if (deleteResult.isSuccess) {
            Log.d(TAG, "Deleted the cloud copy of cookie $cookieId")
            return@withContext Result.success()
        }
        Log.w(TAG, "Could not delete the cloud copy of cookie $cookieId yet", deleteResult.exceptionOrNull())
        //las reglas solo dejan borrar al dueno: si otra cuenta tiene la sesion se espera a que vuelva la suya
        if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "RemoteCookieDeleteWorker"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_COOKIE_ID = "cookie_id"
        private const val MAX_ATTEMPTS = 20
        //todos los borrados de galletas llevan esta etiqueta y otra con su id, para saber cuales siguen pendientes
        private const val WORK_TAG = "ark_cookie_delete"
        private const val COOKIE_TAG_PREFIX = "ark_cookie_delete_id:"

        //un pedido por galleta: si se pide dos veces queda uno solo
        fun enqueue(context: Context, userId: String, cookieId: String) {
            val request = OneTimeWorkRequestBuilder<RemoteCookieDeleteWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId, KEY_COOKIE_ID to cookieId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .addTag("$COOKIE_TAG_PREFIX$cookieId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ark_cookie_delete_${userId}_$cookieId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        //ids de las galletas cuyo borrado en la nube sigue pendiente: al bajar cambios no se deben traer de vuelta
        suspend fun pendingCookieIds(context: Context): Set<String> {
            val deleteRequests = WorkManager.getInstance(context).getWorkInfosByTagFlow(WORK_TAG).first()
            val pendingIds = mutableSetOf<String>()
            for (request in deleteRequests) {
                //los que ya terminaron (bien o mal) no cuentan
                if (request.state.isFinished) continue
                //el id de la galleta esta en la etiqueta "ark_cookie_delete_id:{id}"
                for (tag in request.tags) {
                    if (tag.startsWith(COOKIE_TAG_PREFIX)) {
                        pendingIds.add(tag.removePrefix(COOKIE_TAG_PREFIX))
                    }
                }
            }
            return pendingIds
        }
    }
}

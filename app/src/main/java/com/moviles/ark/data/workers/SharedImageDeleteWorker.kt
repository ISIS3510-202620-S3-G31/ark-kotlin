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
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

//borra de storage la foto huerfana de un regalo (shared/{shareId}.jpg) cuando no se pudo borrar en el momento
//mismo patron de RemoteCookieDeleteWorker: workmanager guarda el pedido aunque se cierre la app y lo corre con red
//antes de borrar revisa que el regalo ya no exista: la foto de un regalo que si llego no se toca
class SharedImageDeleteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(KEY_USER_ID)
        val shareId = inputData.getString(KEY_SHARE_ID)
        val repository = (applicationContext as? ArkApplication)?.container?.sharedCookieRepository
        if (userId.isNullOrBlank() || shareId.isNullOrBlank() || repository == null) return@withContext Result.failure()

        repository.deleteOrphanImage(userId, shareId).fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                Log.w(TAG, "Could not delete the gift picture $shareId yet", error)
                //las reglas solo dejan borrar a from o to: si otra cuenta tiene la sesion se espera a que vuelva
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        private const val TAG = "SharedImageDeleteWorker"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SHARE_ID = "share_id"
        private const val MAX_ATTEMPTS = 20

        //un pedido por foto: si se pide dos veces queda uno solo
        fun enqueue(context: Context, userId: String, shareId: String) {
            val request = OneTimeWorkRequestBuilder<SharedImageDeleteWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId, KEY_SHARE_ID to shareId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ark_shared_image_delete_$shareId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

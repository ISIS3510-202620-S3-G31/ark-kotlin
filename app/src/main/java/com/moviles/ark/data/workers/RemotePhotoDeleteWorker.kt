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

//borra de la nube (storage y firestore) una foto del dia que el usuario borro en el telefono
//workmanager guarda el pedido aunque se cierre la app o se apague el telefono y lo corre cuando hay red;
//antes, sin red, el borrado de storage se intentaba 4 segundos y se abandonaba: el jpg quedaba en la nube para siempre
class RemotePhotoDeleteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(KEY_USER_ID)
        val takenAt = inputData.getLong(KEY_TAKEN_AT, -1L)
        val repository = (applicationContext as? ArkApplication)?.container?.photoRepository
        if (userId.isNullOrBlank() || takenAt < 0 || repository == null) return@withContext Result.failure()

        repository.deleteRemoteCopy(userId, takenAt).fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                Log.w(TAG, "Could not delete the cloud copy of photo $takenAt yet", error)
                //las reglas solo dejan borrar al dueno: si otra cuenta tiene la sesion se espera a que vuelva la suya
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        private const val TAG = "RemotePhotoDeleteWorker"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TAKEN_AT = "taken_at"
        private const val MAX_ATTEMPTS = 20

        //un pedido por foto: si se pide dos veces el borrado de la misma foto queda uno solo
        fun enqueue(context: Context, userId: String, takenAt: Long) {
            val request = OneTimeWorkRequestBuilder<RemotePhotoDeleteWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId, KEY_TAKEN_AT to takenAt))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ark_photo_delete_${userId}_$takenAt",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

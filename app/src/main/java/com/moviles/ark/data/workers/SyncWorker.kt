package com.moviles.ark.data.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.moviles.ark.ArkApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

//trabajador en segundo plano que sincroniza registros pendientes de room hacia firestore
//se ejecuta sobre dispatchers.io y garantiza entrega eventual de check-ins, interacciones y feedback
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? ArkApplication
        val syncRepository = app?.container?.syncRepository

        if (syncRepository == null) {
            Log.e(TAG, "ArkApplication o syncRepository no estan disponibles")
            return@withContext Result.failure()
        }

        try {
            Log.d(TAG, "Iniciando sincronizacion en segundo plano de registros pendientes...")
            val result = syncRepository.syncPendingData()

            result.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Sincronizacion completada con exito: $count registros procesados")
                    Result.success(workDataOf(KEY_SYNCED_COUNT to count))
                },
                onFailure = { error ->
                    Log.w(TAG, "Fallo al sincronizar registros pendientes, reintentando con backoff", error)
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Excepcion no controlada durante la ejecucion de SyncWorker", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val UNIQUE_ONE_TIME_WORK_NAME = "ark_sync_worker_one_time"
        const val UNIQUE_PERIODIC_WORK_NAME = "ark_sync_worker_periodic"
        const val KEY_SYNCED_COUNT = "key_synced_count"

        //crea las restricciones para que el trabajo solo corra cuando haya conexion activa a internet
        private fun buildNetworkConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }

        //encola una ejecucion unica del worker tan pronto como se recupere la conexion
        fun enqueue(context: Context) {
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(buildNetworkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }

        //programa la sincronizacion periodica en segundo plano cada 15 minutos sujeta a conectividad
        fun schedulePeriodic(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(buildNetworkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}

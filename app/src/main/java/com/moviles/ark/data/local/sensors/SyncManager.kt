package com.moviles.ark.data.local.sensors

import android.content.Context
import android.util.Log
import com.moviles.ark.data.workers.SyncWorker
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

//gestor que vigila el estado de conexion a internet y coordina la sincronizacion con room y firestore
class SyncManager(
    private val context: Context? = null,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val syncRepository: SyncRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var monitoringJob: Job? = null

    //inicia la observacion de la red; cada vez que pasa de offline a online ejecuta la sincronizacion
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        monitoringJob = scope.launch {
            networkConnectivityObserver.observe().collectLatest { isConnected ->
                if (isConnected) {
                    Log.d("SyncManager", "Red recuperada, disparando sincronizacion automatica hacia Firebase")
                    //asegurar el trabajo en segundo plano con workmanager
                    context?.let { SyncWorker.enqueue(it) }
                    val result = syncRepository.syncPendingData()
                    result.onSuccess { count ->
                        Log.d("SyncManager", "Sincronizacion completada: $count registros sincronizados")
                    }
                }
            }
        }
    }

    //pide una subida inmediata o encola en workmanager si no hay red para cuando vuelva la conexion
    fun requestSync() {
        context?.let { SyncWorker.enqueue(it) }
        if (!networkConnectivityObserver.isConnected()) return
        scope.launch { syncRepository.syncPendingData() }
    }

    //detiene la observacion si es necesario
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
}

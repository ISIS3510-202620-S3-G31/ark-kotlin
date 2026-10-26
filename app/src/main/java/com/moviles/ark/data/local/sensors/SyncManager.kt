package com.moviles.ark.data.local.sensors

import android.util.Log
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

//gestor que vigila el estado de conexion a internet y dispara la sincronizacion automatica cuando vuelve (#33)
class SyncManager(
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
                    Log.d("SyncManager", "Network is back! Triggering automatic resynchronization to Firebase...")
                    val result = syncRepository.syncPendingData()
                    result.onSuccess { count ->
                        Log.d("SyncManager", "Resynchronization completed: $count items synced")
                    }
                }
            }
        }
    }

    //pide una subida ya, por ejemplo despues de guardar un check-in en room (#143)
    //sin internet no hace nada: el check-in queda pendiente y se sube cuando vuelva la red
    fun requestSync() {
        if (!networkConnectivityObserver.isConnected()) return
        scope.launch { syncRepository.syncPendingData() }
    }

    //detiene la observacion si es necesario
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
}

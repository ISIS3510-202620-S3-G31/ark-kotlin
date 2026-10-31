package com.moviles.ark.data.local.sensors

import android.content.Context
import android.util.Log
import com.moviles.ark.data.workers.SyncWorker
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

//gestor que vigila el estado de conexion a internet y coordina la sincronizacion con room y firestore
class SyncManager(
    private val context: Context? = null,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val syncRepository: SyncRepository,
    //trae lo que cambio en la nube mientras no habia red (por ejemplo el catalogo de herramientas); true si lo logro
    private val onOnline: suspend () -> Boolean = { true },
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
                    //la primera emision es el estado al abrir la app: con red tambien se trae lo nuevo al arrancar
                    //va aparte para que no lo cancele un cambio de red mientras corre el sync
                    scope.launch { runOnlineTasks() }
                    val result = syncRepository.syncPendingData()
                    result.onSuccess { count ->
                        Log.d("SyncManager", "Sincronizacion completada: $count registros sincronizados")
                    }
                }
            }
        }
    }

    //justo al volver la red el dns y firestore pueden no estar listos (UnknownHostException): se reintenta unas
    //veces con espera mientras siga habiendo red, en vez de rendirse al primer intento
    private suspend fun runOnlineTasks() {
        for (attempt in 1..ONLINE_TASK_ATTEMPTS) {
            if (onOnline()) return
            delay(ONLINE_RETRY_DELAY_MILLIS * attempt)
            if (!networkConnectivityObserver.isConnected()) return
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

    companion object {
        private const val ONLINE_TASK_ATTEMPTS = 3
        private const val ONLINE_RETRY_DELAY_MILLIS = 3_000L
    }
}

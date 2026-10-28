package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.repositories.StatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//estado de la interfaz para la pantalla de estadisticas
data class StatsUiState(
    val isLoading: Boolean = true,
    val stats: StatsModel? = null,
    val errorMessage: String? = null,
    //true mientras el pull-to-refresh espera a firestore
    val isRefreshing: Boolean = false,
    //aviso corto de una sola vez, por ejemplo cuando no hay red al refrescar
    val noticeMessage: String? = null,
    //indica si el intento de conexion detecto falta de internet
    val isOffline: Boolean = false
)

class StatsViewModel(
    private val statsRepository: StatsRepository? = null,
    //para avisar de inmediato si no hay red en vez de esperar a que firestore falle
    private val isOnline: () -> Boolean = { true }
) : ViewModel() {
    private val privateUiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = privateUiState.asStateFlow()

    //se cancela la carga anterior para que una respuesta vieja no pise a una nueva
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        loadStats()
    }

    //patron cache-then-network:
    //1) lee y muestra inmediatamente el cache de disco en 0 ms si existe
    //2) en paralelo, consulta a firestore en Dispatchers.IO el documento precomputado del backend
    //3) al responder firestore, actualiza la interfaz y refresca el cache del disco
    fun loadStats() {
        if (statsRepository == null) {
            privateUiState.value = StatsUiState(isLoading = false, stats = null)
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 1. CACHE: Muestra inmediatamente las ultimas stats guardadas en disco
            val cached = statsRepository.getCachedStats()
            if (cached != null) {
                showIfNotPoorer(cached)
                privateUiState.value = privateUiState.value.copy(isLoading = false)
                Log.d("StatsViewModel", "Stats displayed from disk cache: total=${cached.totalCheckIns}")
            } else {
                privateUiState.value = privateUiState.value.copy(
                    isLoading = privateUiState.value.stats == null,
                    errorMessage = null
                )
            }

            // 2. NETWORK: Peticion en segundo plano al backend (Cache-then-Network)
            fetchFromFirestore(showNotice = false)
        }
    }

    //pull-to-refresh y boton de refrescar: fuerza traer firestore y actualiza el cache del disco
    fun refresh() {
        if (statsRepository == null || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            fetchFromFirestore(showNotice = true)
        }
    }

    fun onNoticeShown() {
        privateUiState.value = privateUiState.value.copy(noticeMessage = null)
    }

    private suspend fun fetchFromFirestore(showNotice: Boolean) {
        val repository = statsRepository ?: return
        if (!isOnline()) {
            //si no habia cache en disco calculamos fallback local desde room
            if (privateUiState.value.stats == null) {
                repository.getLocalStats().getOrNull()?.let { showIfNotPoorer(it) }
            }
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isOffline = true,
                errorMessage = if (privateUiState.value.stats == null) "Offline" else null,
                noticeMessage = if (showNotice) OFFLINE_NOTICE else null
            )
            return
        }
        privateUiState.value = privateUiState.value.copy(isRefreshing = showNotice, isOffline = false)
        val result = repository.getUserStats()
        if (result.isSuccess) {
            val data = result.getOrNull()
            if (data != null) showIfNotPoorer(data)
            privateUiState.value = privateUiState.value.copy(isLoading = false, isRefreshing = false, isOffline = false)
            Log.d("StatsViewModel", "Stats loaded successfully: total=${data?.totalCheckIns}")
        } else {
            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Could not load statistics"
            Log.e("StatsViewModel", "Error loading stats: $errorMsg")
            //firestore tambien avisa "client is offline" aunque el telefono crea tener red
            val isNetError = !isOnline() || errorMsg.contains("offline", ignoreCase = true)
            //si fallo la red y no habia cache previa usamos fallback de room
            if (privateUiState.value.stats == null) {
                repository.getLocalStats().getOrNull()?.let { showIfNotPoorer(it) }
            }
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isOffline = isNetError,
                errorMessage = if (privateUiState.value.stats == null) errorMsg else null,
                noticeMessage = if (showNotice) OFFLINE_NOTICE else null
            )
        }
    }

    private fun showIfNotPoorer(data: StatsModel) {
        if (data.canReplace(privateUiState.value.stats)) {
            privateUiState.value = privateUiState.value.copy(isLoading = false, stats = data, errorMessage = null)
        }
    }

    companion object {
        private const val OFFLINE_NOTICE = "Couldn't refresh. Showing your saved stats."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                StatsViewModel(
                    statsRepository = app.container.statsRepository,
                    isOnline = { app.container.networkConnectivityObserver.isConnected() }
                )
            }
        }
    }
}

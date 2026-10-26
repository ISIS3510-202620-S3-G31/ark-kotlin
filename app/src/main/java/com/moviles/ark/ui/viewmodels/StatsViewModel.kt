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
    //true mientras el pull-to-refresh espera a firestore (#147)
    val isRefreshing: Boolean = false,
    //aviso corto de una sola vez, por ejemplo cuando no hay red al refrescar (#147)
    val noticeMessage: String? = null
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

    //orden al entrar (#147): 1) cache del disco al instante, 2) room recalculado (#135), 3) firestore solo con pull-to-refresh
    //cada fuente reemplaza a la anterior solo si no tiene menos check-ins (StatsModel.canReplace), asi la pantalla no salta hacia atras
    fun loadStats() {
        if (statsRepository == null) {
            privateUiState.value = StatsUiState(isLoading = false, stats = null)
            return
        }
        privateUiState.value = privateUiState.value.copy(
            isLoading = privateUiState.value.stats == null,
            errorMessage = null
        )
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = statsRepository.getCachedStats()
            if (cached != null) {
                showIfNotPoorer(cached)
                Log.d("StatsViewModel", "Stats from disk cache: total=${cached.totalCheckIns}")
            }

            statsRepository.getLocalStats().onSuccess { local ->
                showIfNotPoorer(local)
                Log.d("StatsViewModel", "Local stats from room: total=${local.totalCheckIns}")
            }
            privateUiState.value = privateUiState.value.copy(isLoading = false)

            //la primera vez no hay cache: se pide a firestore una vez para no quedar con la pantalla vacia
            if (cached == null) {
                fetchFromFirestore(showNotice = false)
            }
        }
    }

    //pull-to-refresh y boton de refrescar: fuerza traer firestore y actualiza el cache del disco (#147)
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
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                noticeMessage = if (showNotice) OFFLINE_NOTICE else null
            )
            return
        }
        privateUiState.value = privateUiState.value.copy(isRefreshing = showNotice)
        val result = repository.getUserStats()
        if (result.isSuccess) {
            val data = result.getOrNull()
            if (data != null) showIfNotPoorer(data)
            privateUiState.value = privateUiState.value.copy(isLoading = false, isRefreshing = false)
            Log.d("StatsViewModel", "Stats loaded successfully: total=${data?.totalCheckIns}")
        } else {
            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Could not load statistics"
            Log.e("StatsViewModel", "Error loading stats: $errorMsg")
            //se deja lo que habia (cache o room, aunque este vacio); solo hay error si no hay nada que mostrar
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
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

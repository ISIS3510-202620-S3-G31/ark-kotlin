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
    val errorMessage: String? = null
)

class StatsViewModel(
    private val statsRepository: StatsRepository? = null
) : ViewModel() {
    private val privateUiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = privateUiState.asStateFlow()

    //se cancela la carga anterior para que una respuesta vieja no pise a una nueva
    private var loadJob: Job? = null

    init {
        loadStats()
    }

    //primero calcula con room (instantaneo y sin red) y despues trae firestore en segundo plano (#135)
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
            val local = statsRepository.getLocalStats()
            local.onSuccess { data ->
                privateUiState.value = StatsUiState(isLoading = false, stats = data)
                Log.d("StatsViewModel", "Local stats from room: total=${data.totalCheckIns}")
            }

            //firestore trae el historial completo; mientras los check-ins no esten todos en room (#143) completa lo local
            val result = statsRepository.getUserStats()
            if (result.isSuccess) {
                val data = result.getOrNull()
                //si room tiene check-ins que aun no subieron, no se cambian por un resumen con menos
                if (data != null && data.canReplace(privateUiState.value.stats)) {
                    privateUiState.value = StatsUiState(isLoading = false, stats = data, errorMessage = null)
                } else {
                    privateUiState.value = privateUiState.value.copy(isLoading = false)
                }
                Log.d("StatsViewModel", "Stats loaded successfully: total=${data?.totalCheckIns}")
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Could not load statistics"
                Log.e("StatsViewModel", "Error loading stats: $errorMsg")
                //sin red se queda lo de room (aunque este vacio); solo hay error si room tambien fallo
                if (privateUiState.value.stats == null) {
                    privateUiState.value = StatsUiState(isLoading = false, errorMessage = errorMsg)
                }
            }
        }
    }

    //permite reintentar la carga
    fun refresh() {
        loadStats()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                StatsViewModel(
                    statsRepository = app.container.statsRepository
                )
            }
        }
    }
}

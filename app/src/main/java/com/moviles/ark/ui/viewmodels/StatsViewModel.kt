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

    init {
        loadStats()
    }

    //carga las estadisticas del usuario desde el repositorio
    fun loadStats() {
        if (statsRepository == null) {
            privateUiState.value = StatsUiState(isLoading = false, stats = null)
            return
        }
        privateUiState.value = privateUiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = statsRepository.getUserStats()
            if (result.isSuccess) {
                val data = result.getOrNull()
                privateUiState.value = StatsUiState(isLoading = false, stats = data, errorMessage = null)
                Log.d("StatsViewModel", "Stats loaded successfully: total=${data?.totalCheckIns}")
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Could not load statistics"
                Log.e("StatsViewModel", "Error loading stats: $errorMsg")
                privateUiState.value = StatsUiState(isLoading = false, errorMessage = errorMsg)
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

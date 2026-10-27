package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

//estado de toda la app: por donde se entra y si hay internet para el banner offline
data class AppUiState(
    val startRoute: String = REGISTER_ROUTE,
    val isConnected: Boolean = true
) {
    companion object {
        const val HOME_ROUTE = "home_screen"
        const val REGISTER_ROUTE = "register_screen"
    }
}

//viewmodel de la navegacion: la pantalla ya no lee el AppContainer ni los repositorios
class AppViewModel(
    authRepository: AuthRepository? = null,
    //conexion en tiempo real; vuelve a emitir cada vez que cambia
    connectivity: Flow<Boolean> = flowOf(true),
    initiallyConnected: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUiState(
            //si ya hay una sesion activa en el celular entra directo a home
            startRoute = if (authRepository?.isLoggedIn() == true) AppUiState.HOME_ROUTE else AppUiState.REGISTER_ROUTE,
            isConnected = initiallyConnected
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.collect { connected -> _uiState.update { it.copy(isConnected = connected) } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                val observer = app.container.networkConnectivityObserver
                AppViewModel(
                    authRepository = app.container.authRepository,
                    connectivity = observer.observe(),
                    initiallyConnected = observer.isConnected()
                )
            }
        }
    }
}

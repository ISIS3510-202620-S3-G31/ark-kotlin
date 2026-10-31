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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//estado de la interfaz para la pantalla de estadisticas
data class StatsUiState(
    val isLoading: Boolean = true,
    val stats: StatsModel? = null,
    //motivo por el que no hay stats para mostrar; la pantalla elige el mensaje con esto
    val emptyReason: StatsEmptyReason? = null,
    //true mientras el pull-to-refresh espera a firestore
    val isRefreshing: Boolean = false,
    //true mientras se piden stats nuevas a firestore con las guardadas ya en pantalla
    val isUpdating: Boolean = false,
    //aviso corto de una sola vez, por ejemplo cuando no hay red al refrescar
    val noticeMessage: String? = null,
    //sin internet: lo que se ve son las ultimas stats guardadas en el telefono
    val isOffline: Boolean = false,
    //sube cada vez que llegan stats nuevas de firestore; la pantalla parpadea para que se note
    val updateCount: Int = 0,
    //cantidad de veces que el usuario ha visitado la pantalla de estadisticas hoy (dato de la pregunta de negocio, no se muestra)
    val todayVisitsCount: Int = 0,
    //cosas hechas en este telefono que todavia no llegan a la nube: las stats de firestore aun no las cuentan
    val pendingUploads: Int = 0
)

//por que no hay stats en pantalla
enum class StatsEmptyReason {
    //sin internet y sin stats guardadas en el telefono (primera vez o se borro el cache)
    OFFLINE_NO_SAVED,
    //con internet pero firestore no respondio
    SERVER_ERROR
}

class StatsViewModel(
    private val statsRepository: StatsRepository? = null,
    //para avisar de inmediato si no hay red en vez de esperar a que firestore falle
    private val isOnline: () -> Boolean = { true },
    //true cuando hay internet; vuelve a emitir cada vez que cambia
    connectivity: Flow<Boolean> = emptyFlow(),
    //cuantas cosas siguen guardadas solo en el telefono (SyncRepository.pendingCount)
    private val pendingUploads: suspend () -> Int = { 0 }
) : ViewModel() {
    private val privateUiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = privateUiState.asStateFlow()

    //vuelve la red con la pantalla abierta: si se estaban viendo las stats guardadas se piden las nuevas
    //(antes el aviso de "offline" y los datos viejos se quedaban hasta refrescar a mano o volver a entrar)
    init {
        viewModelScope.launch {
            connectivity.collect { isConnected ->
                if (isConnected && privateUiState.value.isOffline && statsRepository != null) {
                    loadJob?.cancel()
                    loadJob = viewModelScope.launch { fetchFromFirestore(showNotice = false) }
                }
            }
        }
    }

    //se cancela la carga anterior para que una respuesta vieja no pise a una nueva
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    //las estadisticas se cargan al abrir la pantalla (onScreenOpened)
    //true si la pantalla se cerro solo porque el telefono giro: al volver a dibujarse no es una visita nueva
    private var reopeningAfterRotation = false

    //cada vez que el usuario entra a la pantalla cuenta una visita (pregunta de negocio de stats) y carga los datos
    //antes la visita se contaba en loadStats, que corria dos veces al entrar (init y pantalla), y tambien al refrescar
    fun onScreenOpened() {
        if (reopeningAfterRotation) {
            reopeningAfterRotation = false
        } else {
            viewModelScope.launch { recordDailyVisit() }
        }
        loadStats()
    }

    //la pantalla sale de la composicion; changingConfiguration = la actividad se recrea por un giro
    fun onScreenClosed(changingConfiguration: Boolean) {
        reopeningAfterRotation = changingConfiguration
    }

    //estrategia de conectividad eventual (cache-then-network):
    //1) muestra de inmediato las ultimas stats guardadas en cacheDir, con la fecha en que se cargaron
    //2) con internet pide stats nuevas a firestore; al llegar reemplazan a las guardadas y la pantalla parpadea
    //3) sin internet se quedan las guardadas; si no hay ninguna se explica por que no hay stats
    //nunca se calculan stats con datos sueltos del telefono: o son las de firestore o las ultimas que llegaron de ahi
    fun loadStats() {
        val repository = statsRepository ?: run {
            privateUiState.value = StatsUiState(isLoading = false, emptyReason = StatsEmptyReason.SERVER_ERROR)
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = repository.getCachedStats()
            if (cached != null) {
                privateUiState.value = privateUiState.value.copy(isLoading = false, stats = cached, emptyReason = null)
                Log.d("StatsViewModel", "Stats displayed from disk cache: total=${cached.totalCheckIns}")
            }
            fetchFromFirestore(showNotice = false)
        }
    }

    //registra la visita actual en Room/Firestore y actualiza el contador de hoy
    private suspend fun recordDailyVisit() {
        val repository = statsRepository ?: return
        val count = repository.recordStatsVisit().getOrDefault(0)
        privateUiState.value = privateUiState.value.copy(todayVisitsCount = count)
    }

    //permite consultar directamente cuántas veces ha entrado el usuario en el día
    fun getTodayVisitsCount(): Int = privateUiState.value.todayVisitsCount

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
        val shown = privateUiState.value.stats
        if (!isOnline()) {
            //sin red no cambia nada de lo que se ve, tampoco la fecha de "last updated"
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isUpdating = false,
                isOffline = true,
                emptyReason = if (shown == null) StatsEmptyReason.OFFLINE_NO_SAVED else null,
                noticeMessage = if (showNotice) offlineNotice(shown) else null
            )
            return
        }
        privateUiState.value = privateUiState.value.copy(
            isRefreshing = showNotice,
            isUpdating = shown != null,
            isOffline = false
        )
        val result = repository.getUserStats()
        val data = result.getOrNull()
        if (data != null) {
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isUpdating = false,
                stats = data,
                emptyReason = null,
                updateCount = privateUiState.value.updateCount + 1,
                //el sync espera maximo 10 s antes de calcular: si algo no alcanzo a subir, se dice
                pendingUploads = pendingUploads()
            )
            Log.d("StatsViewModel", "Stats loaded from Firestore: total=${data.totalCheckIns}")
        } else {
            val errorMsg = result.exceptionOrNull()?.localizedMessage.orEmpty()
            Log.e("StatsViewModel", "Error loading stats: $errorMsg")
            //firestore tambien avisa "client is offline" aunque el telefono crea tener red
            val isNetError = !isOnline() || errorMsg.contains("offline", ignoreCase = true)
            privateUiState.value = privateUiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isUpdating = false,
                isOffline = isNetError,
                emptyReason = when {
                    shown != null -> null
                    isNetError -> StatsEmptyReason.OFFLINE_NO_SAVED
                    else -> StatsEmptyReason.SERVER_ERROR
                },
                noticeMessage = if (showNotice) offlineNotice(shown) else null
            )
        }
    }

    //el aviso dice de cuando son las stats que se siguen viendo
    private fun offlineNotice(shown: StatsModel?): String =
        if (shown == null) NO_STATS_NOTICE else "Couldn't refresh. Showing stats from ${StatsModel.lastUpdatedLabel(shown.lastUpdated)}."

    companion object {
        private const val NO_STATS_NOTICE = "Couldn't refresh. Connect to the internet to load your stats."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                StatsViewModel(
                    statsRepository = app.container.statsRepository,
                    isOnline = { app.container.networkConnectivityObserver.isConnected() },
                    connectivity = app.container.networkConnectivityObserver.observe(),
                    pendingUploads = { app.container.syncRepository.pendingCount() }
                )
            }
        }
    }
}

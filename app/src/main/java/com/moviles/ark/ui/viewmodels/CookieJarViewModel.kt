package com.moviles.ark.ui.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.adapters.CookieImageLoader
import com.moviles.ark.domain.models.CookieJar
import com.moviles.ark.domain.models.CookieListItem
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.CookieRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//jarro, listado y detalle de cookie jar; las reglas (sorteo del dia, limites, titulos) estan en el modelo
class CookieJarViewModel(
    private val cookieRepository: CookieRepository,
    private val imageLoader: CookieImageLoader<Bitmap>,
    //true cuando hay internet; vuelve a emitir cada vez que cambia
    private val connectivity: Flow<Boolean> = emptyFlow(),
    private val isOnline: () -> Boolean = { true },
    //mide cuanto tardo en abrirse la herramienta; null en previews
    private val toolLatencyTracker: ToolLatencyTracker? = null,
    //cada visita en la que se crea o se come una galleta cuenta como un uso de la herramienta
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    private val zone: TimeZone = TimeZone.getDefault(),
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookieJarUiState())
    val uiState: StateFlow<CookieJarUiState> = _uiState.asStateFlow()

    //avisos de una sola vez: con un StateFlow se repetirian al girar la pantalla
    private val messages = Channel<CookieMessage>(Channel.BUFFERED)
    val messageFlow: Flow<CookieMessage> = messages.receiveAsFlow()

    private val sessionStart = now()
    //true si en esta visita se creo, se comio o se devolvio una galleta
    private var hasInteracted = false
    private var imageJob: Job? = null
    //ids del jarro de hoy la ultima vez que se calentaron las caches
    private var cachedDailyIds: Set<String> = emptySet()

    init {
        observeCookies()
        observeList()
        viewModelScope.launch {
            connectivity.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    //room vuelve a emitir con cada cambio; el sorteo del dia se recalcula en Dispatchers.Default
    private fun observeCookies() {
        viewModelScope.launch {
            cookieRepository.observeCookies()
                .catch { _uiState.update { it.copy(isLoading = false) } }
                .collect { cookies ->
                    val userId = cookieRepository.currentUserId().orEmpty()
                    val daily = withContext(Dispatchers.Default) { CookieJar.dailyPick(cookies, userId, dayKey()) }
                    _uiState.update { state ->
                        //si la galleta abierta cambio (por ejemplo se comio) el detalle muestra la version nueva
                        val selected = state.selectedCookie?.let { open -> cookies.find { it.id == open.id } }
                        state.copy(isLoading = false, jarCookies = daily, selectedCookie = selected ?: state.selectedCookie)
                    }
                    warmCaches(daily)
                }
        }
    }

    //cuando cambian las del jarro de hoy: sus imagenes pasan a memoria (LruCache) y se revisa el presupuesto del disco
    //sin bloquear la pantalla: la decodificacion va en Dispatchers.Default y el desalojo en Dispatchers.IO
    private fun warmCaches(daily: List<CookieModel>) {
        val ids = daily.map { it.id }.toSet()
        if (ids == cachedDailyIds) return
        cachedDailyIds = ids
        viewModelScope.launch {
            imageLoader.prefetch(daily)
            cookieRepository.enforceImageBudget(ids)
        }
    }

    private fun observeList() {
        viewModelScope.launch {
            cookieRepository.observeCookieList()
                .catch { }
                .collect { items -> _uiState.update { it.copy(listItems = items) } }
        }
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(TOOL_ID)
    }

    fun onOpenList() = _uiState.update { it.copy(showList = true) }

    fun onDismissList() = _uiState.update { it.copy(showList = false) }

    fun onSelectTab(tab: CookieListTab) = _uiState.update { it.copy(listTab = tab) }

    //abre el detalle: el texto sale de room y la imagen se carga aparte
    fun onCookieClick(cookieId: String) {
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            val cookie = cookieRepository.getCookie(cookieId) ?: return@launch
            _uiState.update { it.copy(selectedCookie = cookie, selectedImage = null, isImageLoading = cookie.hasLocalImage()) }
            cookieRepository.markOpened(cookieId)
            val image = if (cookie.hasLocalImage()) imageLoader.load(cookie) else null
            _uiState.update { state ->
                if (state.selectedCookie?.id != cookieId) state else state.copy(selectedImage = image, isImageLoading = false)
            }
        }
    }

    fun onDismissDetail() {
        imageJob?.cancel()
        _uiState.update { it.copy(selectedCookie = null, selectedImage = null, isImageLoading = false) }
    }

    //comer: sale del jarro y pasa al historial; el aviso trae "Undo"
    fun onEatCookie(cookieId: String) {
        viewModelScope.launch {
            cookieRepository.eatCookie(cookieId).onSuccess {
                hasInteracted = true
                onDismissDetail()
                messages.send(CookieMessage(CookieMessages.EATEN, actionLabel = CookieMessages.UNDO, undoEatId = cookieId))
            }
        }
    }

    //"Undo" del aviso de comer: vuelve al jarro sin otro aviso
    fun onUndoEat(cookieId: String) {
        viewModelScope.launch { cookieRepository.returnToJar(cookieId) }
    }

    //desde la pestana "Eaten"
    fun onReturnToJar(cookieId: String) {
        viewModelScope.launch {
            cookieRepository.returnToJar(cookieId).onSuccess { cookie ->
                hasInteracted = true
                onDismissDetail()
                messages.send(CookieMessage(CookieMessages.backInJar(cookie.title)))
            }
        }
    }

    fun onRequestDelete(item: CookieListItem) = _uiState.update { it.copy(pendingDelete = item) }

    fun onDismissDelete() = _uiState.update { it.copy(pendingDelete = null) }

    //borrar para siempre despues de confirmar: fila de room y archivo privado
    fun onConfirmDelete() {
        val item = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            cookieRepository.deleteForever(item.id).onSuccess {
                imageLoader.forget(item.id)
                if (_uiState.value.selectedCookie?.id == item.id) onDismissDetail()
            }
        }
    }

    //la pantalla de galleta nueva guardo una: el aviso se muestra aqui, en el jarro
    fun onCookieSaved(typedTitle: String, cookie: CookieModel) {
        hasInteracted = true
        viewModelScope.launch {
            //una imagen nueva puede pasar el presupuesto del disco
            if (cookie.hasImage) cookieRepository.enforceImageBudget(cachedDailyIds)
            val cleanTyped = CookieModel.cleanTitle(typedTitle)
            if (cookie.title != cleanTyped) {
                messages.send(CookieMessage(CookieMessages.renamed(cleanTyped, cookie.title), isLong = true))
            }
            if (!isOnline()) {
                messages.send(CookieMessage(CookieMessages.SAVED_OFFLINE, isLong = true))
            } else if (cookie.title == cleanTyped) {
                messages.send(CookieMessage(CookieMessages.ADDED))
            }
        }
    }

    //al salir: si hubo un uso real se guarda la interaccion y se pide el feedback, como en las demas herramientas
    fun finishSession(): Pair<Boolean, Int> {
        val durationSeconds = ((now() - sessionStart) / 1000).toInt().coerceAtLeast(1)
        if (!hasInteracted) return Pair(false, durationSeconds)
        hasInteracted = false
        val repository = toolInteractionRepository ?: return Pair(true, durationSeconds)
        viewModelScope.launch {
            //NonCancellable: al salir el viewmodel se limpia enseguida y no debe cortar el guardado
            withContext(NonCancellable) { repository.recordCompletion(TOOL_ID, durationSeconds, INTERACTION_TYPE, now()) }
        }
        return Pair(true, durationSeconds)
    }

    //fecha del telefono, semilla del sorteo del dia
    private fun dayKey(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = zone
        return format.format(Date(now()))
    }

    companion object {
        const val TOOL_ID = "cookie_jar"
        private const val INTERACTION_TYPE = "text"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                CookieJarViewModel(
                    cookieRepository = app.container.cookieRepository,
                    imageLoader = app.container.cookieImageLoader,
                    connectivity = app.container.networkConnectivityObserver.observe(),
                    isOnline = app.container.networkConnectivityObserver::isConnected,
                    toolLatencyTracker = app.container.toolLatencyTracker,
                    toolInteractionRepository = app.container.toolInteractionRepository,
                )
            }
        }
    }
}

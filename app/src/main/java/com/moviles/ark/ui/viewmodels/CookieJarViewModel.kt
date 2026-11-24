package com.moviles.ark.ui.viewmodels

import android.graphics.Bitmap
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//jarro, listado y detalle de cookie jar; las reglas (sorteo del dia, limites, titulos, sync) estan en el modelo
//conectividad eventual: todo sale de room al instante (cached) y, si hay red, se bajan los cambios de la nube (network)
class CookieJarViewModel(
    private val cookieRepository: CookieRepository,
    private val imageLoader: CookieImageLoader<Bitmap>,
    //true cuando hay internet; vuelve a emitir cada vez que cambia
    private val connectivity: Flow<Boolean> = emptyFlow(),
    private val isOnlineNow: () -> Boolean = { true },
    //mide cuanto tardo en abrirse la herramienta; null en previews
    private val toolLatencyTracker: ToolLatencyTracker? = null,
    //cada visita en la que se crea o se come una galleta cuenta como un uso de la herramienta
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    private val zone: TimeZone = TimeZone.getDefault(),
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookieJarUiState(isOnline = isOnlineNow()))
    val uiState: StateFlow<CookieJarUiState> = _uiState.asStateFlow()

    //true con internet; la ui habilita o deshabilita con esto lo que necesita red (amigos y regalos)
    private val _isOnline = MutableStateFlow(isOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    //avisos de una sola vez: con un StateFlow se repetirian al girar la pantalla
    private val messages = Channel<CookieMessage>(Channel.BUFFERED)
    val messageFlow: Flow<CookieMessage> = messages.receiveAsFlow()

    private val sessionStart = now()
    //true si en esta visita se creo, se comio o se devolvio una galleta
    private var hasInteracted = false
    private var imageJob: Job? = null
    //true mientras se baja la imagen del detalle (si se cae la red se cancela y se avisa)
    private var isDownloadingDetail = false
    //imagenes del jarro de hoy ya calentadas (id y ruta local: una imagen recien bajada se vuelve a calentar)
    private var cachedDailyKeys: Set<Pair<String, String?>> = emptySet()
    private var cachedDailyIds: Set<String> = emptySet()
    private var refreshJob: Job? = null
    private var dailyDownloadJob: Job? = null
    //imagenes del jarro que no se pudieron bajar en esta visita; se reintentan cuando vuelve la red
    private val failedDownloads = mutableSetOf<String>()
    //estado de la red ya anunciado (despues del debounce)
    private var announcedOnline = isOnlineNow()
    //hubo galletas sin subir mientras no habia red: al terminar de subirlas se avisa M3
    private var announceBackup = false
    private var backupDoneBeforeAnnounce = false

    init {
        observeCookies()
        observeList()
        observePendingUploads()
        observeConnectivity()
        observeNetworkChanges()
        refreshFromCloud()
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
                    downloadTodayImages()
                }
        }
    }

    //cuando cambian las del jarro de hoy: sus imagenes pasan a memoria (LruCache) y se revisa el presupuesto del disco
    //sin bloquear la pantalla: la decodificacion va en Dispatchers.Default y el desalojo en Dispatchers.IO
    private fun warmCaches(daily: List<CookieModel>) {
        val keys = daily.map { it.id to it.localImagePath }.toSet()
        if (keys == cachedDailyKeys) return
        cachedDailyKeys = keys
        cachedDailyIds = daily.map { it.id }.toSet()
        val ids = cachedDailyIds
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

    //⏳ arriba del jarro; cuando se termina de subir lo que se guardo sin red se avisa M3
    private fun observePendingUploads() {
        viewModelScope.launch {
            var previous = 0
            cookieRepository.observePendingUploads()
                .catch { }
                .collect { count ->
                    _uiState.update { it.copy(pendingUploads = count) }
                    if (count > 0 && !_isOnline.value) announceBackup = true
                    if (count == 0 && previous > 0 && announceBackup) {
                        //el M3 va despues del M2: si la subida termino antes del debounce se espera al M2
                        if (announcedOnline) announceBackedUpAfterRefresh() else backupDoneBeforeAnnounce = true
                    }
                    previous = count
                }
        }
    }

    //todos los avisos pasan por aqui; el log deja ver en logcat que aviso salio y cuando
    private suspend fun say(message: CookieMessage) {
        Log.d(TAG, "message: ${message.text}")
        messages.send(message)
    }

    //la bajada de cambios puede dejar algo nuevo por subir (un titulo con sufijo): el M3 se dice solo si sigue todo subido
    //se pregunta a room (no al estado de la pantalla, que puede ir un paso atras); si quedo algo, el M3 sale al subirlo
    private fun announceBackedUpAfterRefresh() {
        backupDoneBeforeAnnounce = false
        viewModelScope.launch {
            refreshJob?.join()
            if (announceBackup && cookieRepository.pendingUploadCount() == 0) sendBackedUp()
        }
    }

    private suspend fun sendBackedUp() {
        announceBackup = false
        backupDoneBeforeAnnounce = false
        say(CookieMessage(CookieMessages.ALL_BACKED_UP))
    }

    //estado de la red sin esperar: los botones que necesitan red cambian al instante
    //si la red se cae mientras baja la imagen del detalle, la descarga se cancela (no queda archivo a medias) y se avisa
    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivity.collect { online ->
                _isOnline.value = online
                _uiState.update { it.copy(isOnline = online) }
                if (!online && isDownloadingDetail) {
                    val cookieId = _uiState.value.selectedCookie?.id
                    imageJob?.cancel()
                    isDownloadingDetail = false
                    _uiState.update { it.copy(isImageLoading = false, imageNotice = CookieMessages.PICTURE_WHEN_ONLINE) }
                    if (cookieId != null) say(retryImageMessage(cookieId))
                }
            }
        }
    }

    //avisos de red con debounce: una red que va y viene no dispara un aviso por cada cambio
    @OptIn(FlowPreview::class)
    private fun observeNetworkChanges() {
        viewModelScope.launch {
            _isOnline.debounce(NETWORK_DEBOUNCE_MS).distinctUntilChanged().collect { online ->
                if (online == announcedOnline) return@collect
                announcedOnline = online
                if (online) onBackOnline() else say(CookieMessage(CookieMessages.WENT_OFFLINE, isLong = true, isNetworkStatus = true))
            }
        }
    }

    //volvio la red: el SyncManager ya pidio la subida; aqui se bajan los cambios y las imagenes que faltaban
    private suspend fun onBackOnline() {
        say(CookieMessage(CookieMessages.BACK_ONLINE, isNetworkStatus = true))
        if (_uiState.value.pendingUploads > 0) announceBackup = true
        failedDownloads.clear()
        refreshFromCloud()
        if (backupDoneBeforeAnnounce) announceBackedUpAfterRefresh()
        //si el detalle sigue abierto sin su imagen, se baja sola
        val state = _uiState.value
        val open = state.selectedCookie
        if (open != null && open.hasImage && state.selectedImage == null && imageJob?.isActive != true) {
            onCookieClick(open.id)
        }
    }

    //cached then network: room ya se esta mostrando; si hay red se traen los cambios de la nube en segundo plano
    fun refreshFromCloud() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (!_isOnline.value) {
                //telefono nuevo sin red: el jarro esta vacio porque las galletas siguen en la nube
                val isEmpty = runCatching { cookieRepository.observeCookies().first().isEmpty() }.getOrDefault(false)
                if (isEmpty && !cookieRepository.hasDownloadedFromCloud()) {
                    say(
                        CookieMessage(
                            CookieMessages.COOKIES_IN_CLOUD,
                            actionLabel = CookieMessages.RETRY,
                            isLong = true,
                            action = CookieMessageAction.RetryRefresh
                        )
                    )
                }
                return@launch
            }
            cookieRepository.refreshFromCloud()
                .onSuccess { result ->
                    if (result.arrived > 0) say(CookieMessage(CookieMessages.arrivedFromOtherDevices(result.arrived)))
                    result.renamed.forEach { (title, finalTitle) ->
                        say(CookieMessage(CookieMessages.existedOnTwoPhones(title, finalTitle), isLong = true))
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    //sin red ya avisa M1; con red es una red lenta o que miente
                    if (_isOnline.value) say(CookieMessage(CookieMessages.SYNC_FAILED))
                }
        }
    }

    //imagenes que faltan de las 10 del dia (llegaron de otro telefono o se desalojaron): se bajan una por una
    private fun downloadTodayImages() {
        if (!_isOnline.value || dailyDownloadJob?.isActive == true) return
        val missing = _uiState.value.jarCookies.filter { it.hasImage && !it.hasLocalImage() && it.id !in failedDownloads }
        if (missing.isEmpty()) return
        dailyDownloadJob = viewModelScope.launch {
            for (cookie in missing) {
                if (!_isOnline.value) break
                cookieRepository.downloadImage(cookie.id).onFailure { error ->
                    if (error is CancellationException) throw error
                    failedDownloads += cookie.id
                }
            }
            //ya hay imagenes bajadas (desalojables) en el disco
            cookieRepository.enforceImageBudget(cachedDailyIds)
        }
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(TOOL_ID)
    }

    fun onOpenList() = _uiState.update { it.copy(showList = true) }

    fun onDismissList() = _uiState.update { it.copy(showList = false) }

    fun onSelectTab(tab: CookieListTab) = _uiState.update { it.copy(listTab = tab) }

    //abre el detalle: el texto sale de room y la imagen se carga aparte (memoria, archivo privado o la nube)
    fun onCookieClick(cookieId: String) {
        imageJob?.cancel()
        isDownloadingDetail = false
        imageJob = viewModelScope.launch {
            val cookie = cookieRepository.getCookie(cookieId) ?: return@launch
            _uiState.update {
                it.copy(selectedCookie = cookie, selectedImage = null, isImageLoading = cookie.hasImage, imageNotice = null)
            }
            cookieRepository.markOpened(cookieId)
            showImage(cookie)
        }
    }

    private suspend fun showImage(cookie: CookieModel) {
        if (!cookie.hasImage) return
        var current = cookie
        //la imagen pudo quedar en memoria aunque ya no este en el disco
        val cached = imageLoader.load(cookie)
        if (cached == null && !cookie.hasLocalImage()) {
            //descarga perezosa: la imagen esta en la nube y no en el telefono
            if (!_isOnline.value) {
                _uiState.update { it.copy(isImageLoading = false, imageNotice = CookieMessages.PICTURE_WHEN_ONLINE) }
                say(CookieMessage(CookieMessages.PICTURE_NEEDS_INTERNET))
                return
            }
            isDownloadingDetail = true
            val result = cookieRepository.downloadImage(cookie.id)
            isDownloadingDetail = false
            current = result.getOrElse { error ->
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isImageLoading = false, imageNotice = CookieMessages.PICTURE_NOT_LOADED) }
                say(retryImageMessage(cookie.id))
                return
            }
            cookieRepository.enforceImageBudget(cachedDailyIds)
        }
        val image = cached ?: imageLoader.load(current)
        _uiState.update { state ->
            if (state.selectedCookie?.id != cookie.id) state
            else state.copy(
                selectedImage = image,
                isImageLoading = false,
                imageNotice = if (image == null) CookieMessages.PICTURE_NOT_LOADED else null
            )
        }
    }

    private fun retryImageMessage(cookieId: String) = CookieMessage(
        CookieMessages.PICTURE_FAILED,
        actionLabel = CookieMessages.RETRY,
        isLong = true,
        action = CookieMessageAction.RetryImage(cookieId)
    )

    fun onDismissDetail() {
        imageJob?.cancel()
        isDownloadingDetail = false
        _uiState.update { it.copy(selectedCookie = null, selectedImage = null, isImageLoading = false, imageNotice = null) }
    }

    //boton de un aviso: "Undo" de comer o "Retry"
    fun onMessageAction(action: CookieMessageAction) {
        when (action) {
            is CookieMessageAction.UndoEat -> onUndoEat(action.cookieId)
            is CookieMessageAction.RetryImage -> onCookieClick(action.cookieId)
            CookieMessageAction.RetryRefresh -> refreshFromCloud()
        }
    }

    //comer: sale del jarro y pasa al historial; el aviso trae "Undo"
    fun onEatCookie(cookieId: String) {
        viewModelScope.launch {
            cookieRepository.eatCookie(cookieId).onSuccess {
                hasInteracted = true
                onDismissDetail()
                say(
                    CookieMessage(CookieMessages.EATEN, actionLabel = CookieMessages.UNDO, action = CookieMessageAction.UndoEat(cookieId))
                )
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
                say(CookieMessage(CookieMessages.backInJar(cookie.title)))
            }
        }
    }

    fun onRequestDelete(item: CookieListItem) = _uiState.update { it.copy(pendingDelete = item) }

    fun onDismissDelete() = _uiState.update { it.copy(pendingDelete = null) }

    //borrar para siempre despues de confirmar: fila de room y archivo privado; la copia de la nube la borra un worker
    fun onConfirmDelete() {
        val item = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            cookieRepository.deleteForever(item.id).onSuccess {
                imageLoader.forget(item.id)
                if (_uiState.value.selectedCookie?.id == item.id) onDismissDetail()
                if (!_isOnline.value) say(CookieMessage(CookieMessages.DELETED_OFFLINE, isLong = true))
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
                say(CookieMessage(CookieMessages.renamed(cleanTyped, cookie.title), isLong = true))
            }
            if (!_isOnline.value) {
                say(CookieMessage(CookieMessages.SAVED_OFFLINE, isLong = true))
            } else if (cookie.title == cleanTyped) {
                say(CookieMessage(CookieMessages.ADDED))
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
        private const val TAG = "CookieJar"
        private const val INTERACTION_TYPE = "text"
        //una red que va y viene no dispara un aviso por cada cambio
        private const val NETWORK_DEBOUNCE_MS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                CookieJarViewModel(
                    cookieRepository = app.container.cookieRepository,
                    imageLoader = app.container.cookieImageLoader,
                    connectivity = app.container.networkConnectivityObserver.observe(),
                    isOnlineNow = app.container.networkConnectivityObserver::isConnected,
                    toolLatencyTracker = app.container.toolLatencyTracker,
                    toolInteractionRepository = app.container.toolInteractionRepository,
                )
            }
        }
    }
}

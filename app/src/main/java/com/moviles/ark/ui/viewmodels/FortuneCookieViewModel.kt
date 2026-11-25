package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.FortuneCardModel
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.repositories.FortuneRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//viewmodel de la galleta de la fortuna, la pantalla solo lee el estado y llama estas funciones
class FortuneCookieViewModel(private val fortuneRepository: FortuneRepository, private val toolInteractionRepository: ToolInteractionRepository? = null, private val now: () -> Long = { System.currentTimeMillis() }) : ViewModel() {

    private val _uiState = MutableStateFlow(FortuneUiState())
    val uiState: StateFlow<FortuneUiState> = _uiState.asStateFlow()

    private val sessionStart = now()
    private var hasInteracted = false
    private var tableJob: Job? = null

    init {
        //carga lo guardado en el telefono y arma la mesa mientras el usuario mira la pantalla
        viewModelScope.launch {
            fortuneRepository.initCache()
            val remaining = fortuneRepository.getRemainingChances()
            val recents = fortuneRepository.getRecentCookies()
            _uiState.update { it.copy(remainingChances = remaining, recentFortunes = recents) }
        }
        tableJob = viewModelScope.launch {
            val table = fortuneRepository.getTable()
            _uiState.update { it.copy(table = table) }
        }
        //con red se trae lo de la nube y se sube lo pendiente
        viewModelScope.launch {
            fortuneRepository.syncFromRemote()
            fortuneRepository.syncPending()
        }
        //favoritas de room en tiempo real, tambien actualiza los corazones de la pantalla
        viewModelScope.launch {
            fortuneRepository.observeFavorites().collect { favorites ->
                val ids = favorites.map { it.id }.toSet()
                _uiState.update { state ->
                    val opened = state.opened?.let { it.copy(fortune = it.fortune.copy(isFavorite = it.fortune.id in ids)) }
                    val preview = state.previewFortune?.let { it.copy(isFavorite = it.id in ids) }
                    val recents = state.recentFortunes.map { it.copy(isFavorite = it.id in ids) }
                    state.copy(favoriteFortunes = favorites, opened = opened, previewFortune = preview, recentFortunes = recents)
                }
            }
        }
        //imagenes guardadas en room
        viewModelScope.launch {
            fortuneRepository.observeSavedCards().collect { cards -> _uiState.update { it.copy(savedCards = cards) } }
        }
    }

    //abre la galleta elegida, solo se puede abrir una por sesion
    fun crackCookie(index: Int) {
        val state = _uiState.value
        if (state.phase != FortunePhase.CHOOSING) return
        if (state.remainingChances == 0) {
            _uiState.update { it.copy(snackbarMessage = "No cookies left today. New ones arrive tomorrow.") }
            return
        }
        _uiState.update { it.copy(phase = FortunePhase.OPENING, chosenIndex = index) }
        viewModelScope.launch {
            //la frase se prepara mientras corre la animacion, asi la espera no se nota
            val pending = async {
                tableJob?.join()
                val cookie = _uiState.value.table.getOrNull(index)
                if (cookie == null) null else Pair(cookie, fortuneRepository.openCookie(cookie))
            }
            delay(OPENING_MILLIS)
            val result = pending.await()
            val data = result?.second?.getOrNull()
            if (result != null && data != null) {
                hasInteracted = true
                val recents = fortuneRepository.getRecentCookies()
                _uiState.update { it.copy(phase = FortunePhase.REVEALED, opened = result.first.copy(fortune = data.fortune), remainingChances = data.remainingChances, recentFortunes = recents) }
            } else {
                val remaining = fortuneRepository.getRemainingChances()
                _uiState.update { it.copy(phase = FortunePhase.CHOOSING, chosenIndex = -1, remainingChances = remaining, snackbarMessage = "This cookie couldn't open. Try again.") }
            }
        }
    }

    //guarda o quita la frase de favoritas y luego intenta subir el cambio
    fun toggleFavorite(fortune: FortuneModel) {
        viewModelScope.launch {
            val saved = fortuneRepository.toggleFavorite(fortune)
            _uiState.update { it.copy(snackbarMessage = if (saved) "Kept in My collection" else "Removed from My collection") }
            fortuneRepository.syncPending()
        }
    }

    //abre una frase reciente para leerla otra vez, no gasta galletas
    fun openRecent(fortune: FortuneModel) {
        _uiState.update { it.copy(previewFortune = fortune) }
    }

    //cierra la frase reciente
    fun closeRecent() {
        _uiState.update { it.copy(previewFortune = null) }
    }

    //comparte la frase abierta como imagen
    fun shareOpened() {
        val fortune = _uiState.value.opened?.fortune ?: return
        share(fortune)
    }

    //comparte otra vez una imagen de la coleccion
    fun shareCard(card: FortuneCardModel) {
        share(FortuneModel(id = card.cookieId, quote = card.quote))
    }

    //la imagen se crea una sola vez, despues se reutiliza y solo se abre el menu de compartir
    private fun share(fortune: FortuneModel) {
        if (_uiState.value.isSharing) return
        _uiState.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            val result = fortuneRepository.getOrCreateCard(fortune)
            if (result == null) {
                _uiState.update { it.copy(isSharing = false, snackbarMessage = "The image couldn't be created. Try again.") }
                return@launch
            }
            val message = if (result.isNew) "Image saved in your gallery and in My collection" else null
            _uiState.update { it.copy(isSharing = false, shareUri = result.uri, snackbarMessage = message ?: it.snackbarMessage) }
            fortuneRepository.syncPending()
        }
    }

    //la pantalla avisa que ya abrio el menu de compartir
    fun onShareHandled() {
        _uiState.update { it.copy(shareUri = null) }
    }

    //limpia el mensaje del snackbar
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    //al salir se guarda la interaccion si se abrio una galleta, aunque luego se salte el feedback
    fun finishSession(): Pair<Boolean, Int> {
        val durationSeconds = ((now() - sessionStart) / 1000).toInt().coerceAtLeast(1)
        if (!hasInteracted) return Pair(false, durationSeconds)
        hasInteracted = false
        val repository = toolInteractionRepository ?: return Pair(true, durationSeconds)
        viewModelScope.launch {
            //NonCancellable para que el guardado no se corte cuando se cierra la pantalla
            withContext(NonCancellable) { repository.recordCompletion(TOOL_ID, durationSeconds, INTERACTION_TYPE, now()) }
        }
        return Pair(true, durationSeconds)
    }

    companion object {
        const val TOOL_ID = "fortune_cookie"
        private const val INTERACTION_TYPE = "touch"
        //lo que dura la galleta en llegar al centro, temblar y partirse
        private const val OPENING_MILLIS = 1500L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                FortuneCookieViewModel(fortuneRepository = app.container.fortuneRepository, toolInteractionRepository = app.container.toolInteractionRepository)
            }
        }
    }
}

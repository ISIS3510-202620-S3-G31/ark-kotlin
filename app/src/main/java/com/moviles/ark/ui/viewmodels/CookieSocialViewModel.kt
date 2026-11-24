package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.exceptions.GiftUnavailableException
import com.moviles.ark.domain.exceptions.UsernameNotFoundException
import com.moviles.ark.domain.exceptions.UsernameTakenException
import com.moviles.ark.domain.models.CookieGifts
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.FoundUser
import com.moviles.ark.domain.models.Friend
import com.moviles.ark.domain.models.FriendRelation
import com.moviles.ark.domain.models.FriendsOverview
import com.moviles.ark.domain.models.GiftAcceptResult
import com.moviles.ark.domain.models.InboxSnapshot
import com.moviles.ark.domain.models.InboxView
import com.moviles.ark.domain.models.SharedCookie
import com.moviles.ark.domain.models.UserProfile
import com.moviles.ark.domain.models.Usernames
import com.moviles.ark.domain.repositories.CookieRepository
import com.moviles.ark.domain.repositories.FriendRepository
import com.moviles.ark.domain.repositories.SharedCookieRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

//amigos y regalos del cookie jar (solo 18+); lo comparten el jarro, la pantalla de amigos y la bandeja
//todo lo social requiere red: sin red los botones se ven deshabilitados y al tocarlos se explica por que (snackbar)
//las reglas (edad, usernames, vencidos, que se muestra en la bandeja) estan en el modelo
class CookieSocialViewModel(
    private val friendRepository: FriendRepository,
    private val sharedCookieRepository: SharedCookieRepository,
    cookieRepository: CookieRepository,
    //perfil guardado en el telefono: de ahi sale la edad
    profile: Flow<UserProfile?>,
    //true cuando hay internet; vuelve a emitir cada vez que cambia
    connectivity: Flow<Boolean> = emptyFlow(),
    private val isOnlineNow: () -> Boolean = { true },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookieSocialUiState(isOnline = isOnlineNow()))
    val uiState: StateFlow<CookieSocialUiState> = _uiState.asStateFlow()

    //avisos de una sola vez, como en CookieJarViewModel
    private val messages = Channel<CookieMessage>(Channel.BUFFERED)
    val messageFlow: Flow<CookieMessage> = messages.receiveAsFlow()

    private val isAdultFlow = profile.map { it?.canUseFriends() == true }.distinctUntilChanged()

    //ids de las galletas del jarro (room): un regalo con id = shareId ya se acepto aqui
    private val cookieIds = cookieRepository.observeCookieList().map { items -> items.map { it.id }.toSet() }.catch { emit(emptySet()) }

    //reloj para los vencidos: solo corre mientras alguien escucha la bandeja
    private val clock = flow {
        while (true) {
            emit(now())
            delay(EXPIRY_CHECK_MS)
        }
    }

    //bandeja en vivo: listener de firestore (callbackFlow) que vive mientras se ve una pantalla de cookie jar
    //WhileSubscribed(5000) aguanta un giro de pantalla sin reconectar; para menores de 18 el listener ni se crea
    @OptIn(ExperimentalCoroutinesApi::class)
    val inbox: StateFlow<InboxUiState> = isAdultFlow.flatMapLatest { adult ->
        if (!adult) {
            flowOf(InboxUiState(status = InboxStatus.HIDDEN))
        } else {
            combine(
                sharedCookieRepository.observeInbox(),
                cookieIds,
                _uiState.map { it.acceptingIds to it.isOnline }.distinctUntilChanged(),
                clock,
            ) { snapshot, ids, (accepting, online), time -> onInboxSnapshot(snapshot, ids, accepting, online, time) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InboxUiState())

    //bandeja abierta en pantalla: ahi se borran los vencidos y se avisa M33
    private var inboxOpen = false
    private var lastInboxStatus = InboxStatus.LOADING
    //lo ultimo que confirmo el servidor (null sin red)
    private var lastLoaded: InboxSnapshot.Loaded? = null
    //regalos ya vistos en esta visita, para avisar solo los nuevos
    private val seenGiftIds = mutableSetOf<String>()
    private var announcedWaiting = false
    private var previousVisible: Set<String> = emptySet()
    //regalos aceptados que se estan terminando de borrar del backend en silencio
    private val finishingIds = mutableSetOf<String>()
    private val deletingExpired = mutableSetOf<String>()

    //aceptaciones en curso: si se cae la red se cancelan (saved dice si la galleta ya quedo en el jarro)
    private class Accepting(@Volatile var saved: Boolean = false) {
        var job: Job? = null
    }
    private val accepting = mutableMapOf<String, Accepting>()
    private var sendJob: Job? = null
    //el ultimo amigo al que se intento regalar ("Retry" de M24)
    private var lastGift: Pair<String, Friend>? = null
    //lo que se repite con "Retry" de M22
    private var retryLast: (() -> Unit)? = null
    private var friendsOpen = false

    init {
        viewModelScope.launch { isAdultFlow.collect { adult -> _uiState.update { it.copy(isAdult = adult) } } }
        viewModelScope.launch {
            sharedCookieRepository.observeSentGifts().catch { }.collect { sent -> _uiState.update { it.copy(sentGifts = sent) } }
        }
        observeConnectivity(connectivity)
    }

    //red cruda: los botones sociales cambian al instante
    //si la red se cae a mitad de aceptar o de regalar se cancela y se dice en que quedo
    private fun observeConnectivity(connectivity: Flow<Boolean>) {
        viewModelScope.launch {
            connectivity.collect { online ->
                val wasOnline = _uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (!online) onConnectionLost()
                if (online && !wasOnline && friendsOpen && _uiState.value.overview == null) loadOverview()
            }
        }
    }

    private fun onConnectionLost() {
        for ((shareId, entry) in accepting.toMap()) {
            entry.job?.cancel()
            accepting.remove(shareId)
            _uiState.update { it.copy(acceptingIds = it.acceptingIds - shareId) }
            //entre el paso 2 y el 3 la galleta ya es del usuario: se termina sola al volver la red
            if (entry.saved) say(CookieMessage(CookieMessages.GIFT_SAVED_PENDING, isLong = true)) else say(retryAcceptMessage(shareId))
        }
        if (sendJob?.isActive == true) {
            sendJob?.cancel()
            _uiState.update { it.copy(sendingToUid = null) }
            lastGift?.let { (cookieId, friend) -> say(retryGiftMessage(cookieId, friend)) }
        }
    }

    //red al momento de tocar: el estado del flow y, por si un aviso del sistema llego tarde, la consulta directa
    //si no coinciden se corrige el estado y los botones cambian
    private fun isOnline(): Boolean {
        val online = _uiState.value.isOnline && isOnlineNow()
        if (online != _uiState.value.isOnline) _uiState.update { it.copy(isOnline = online) }
        return online
    }

    private fun say(message: CookieMessage) {
        Log.d(TAG, "message: ${message.text}")
        messages.trySend(message)
    }

    //M18: algo social sin red
    private fun needsInternet() = say(CookieMessage(CookieMessages.FRIENDS_NEED_INTERNET))

    //M22 con "Retry": la red se cayo y no se cambio nada
    private fun connectionLost(retry: () -> Unit) {
        retryLast = retry
        say(CookieMessage(CookieMessages.CONNECTION_LOST, actionLabel = CookieMessages.RETRY, isLong = true, action = CookieMessageAction.RetrySocial))
    }

    private fun retryAcceptMessage(shareId: String) = CookieMessage(
        CookieMessages.GIFT_STILL_IN_INBOX,
        actionLabel = CookieMessages.RETRY,
        isLong = true,
        action = CookieMessageAction.RetryAccept(shareId)
    )

    private fun retryGiftMessage(cookieId: String, friend: Friend): CookieMessage {
        lastGift = cookieId to friend
        return CookieMessage(
            CookieMessages.GIFT_NOT_SENT,
            actionLabel = CookieMessages.RETRY,
            isLong = true,
            action = CookieMessageAction.RetryGift(cookieId, friend.uid)
        )
    }

    //boton de un aviso social ("View" lo atiende la navegacion)
    fun onMessageAction(action: CookieMessageAction.Social) {
        when (action) {
            CookieMessageAction.ViewInbox -> Unit
            CookieMessageAction.RetrySocial -> retryLast?.invoke()
            is CookieMessageAction.RetryGift -> lastGift?.takeIf { it.second.uid == action.friendUid }?.let { (cookieId, friend) -> sendGift(cookieId, friend) }
            is CookieMessageAction.RetryAccept -> inbox.value.gifts.find { it.shareId == action.shareId }?.let(::onAcceptGift)
        }
    }

    // ---------- bandeja ----------

    //cada snapshot del listener (o cambio de room, de la red o del reloj) se separa con la regla del modelo
    //sin red la bandeja no muestra nada; con red, un snapshot de la cache (firestore a veces manda uno de paso, por ejemplo
    //justo despues de una transaccion) no borra lo ultimo que confirmo el servidor
    private fun onInboxSnapshot(
        snapshot: InboxSnapshot,
        cookieIds: Set<String>,
        acceptingIds: Set<String>,
        online: Boolean,
        time: Long,
    ): InboxUiState {
        if (snapshot is InboxSnapshot.Loaded) lastLoaded = snapshot
        val loaded = lastLoaded
        if (!online || loaded == null) {
            val status = if (online) InboxStatus.LOADING else InboxStatus.OFFLINE
            if (status == InboxStatus.OFFLINE && inboxOpen && lastInboxStatus == InboxStatus.READY) {
                say(CookieMessage(CookieMessages.GIFTS_NEED_CONNECTION))
            }
            lastInboxStatus = status
            //al volver la red se espera un dato nuevo del servidor
            if (!online) lastLoaded = null
            return InboxUiState(status = status, now = time)
        }
        lastInboxStatus = InboxStatus.READY
        val view = CookieGifts.splitInbox(loaded.gifts, cookieIds, acceptingIds, time)
        handleInboxEffects(view)
        return InboxUiState(status = InboxStatus.READY, gifts = view.visible, now = time)
    }

    private fun handleInboxEffects(view: InboxView) {
        //ya aceptado en este telefono pero sigue en el backend (la red se cayo entre el paso 2 y el 3): se termina en silencio
        view.alreadyInJar.filter { finishingIds.add(it.shareId) }.forEach { gift ->
            viewModelScope.launch {
                sharedCookieRepository.finishAccepted(gift).onFailure { finishingIds.remove(gift.shareId) }
            }
        }
        if (inboxOpen) {
            //M33: vencio mientras se miraba la bandeja
            if (view.expired.any { it.shareId in previousVisible }) say(CookieMessage(CookieMessages.GIFT_EXPIRED))
            deleteExpired(view)
        }
        val visibleIds = view.visible.map { it.shareId }
        if (!announcedWaiting) {
            //primer dato del servidor: llegan todos los pendientes, tambien los que llegaron con la app cerrada (M28)
            announcedWaiting = true
            seenGiftIds += visibleIds
            if (view.visible.isNotEmpty() && !inboxOpen) say(giftsWaitingMessage(view.visible.size))
        } else {
            val fresh = CookieGifts.newArrivals(view.visible, seenGiftIds)
            seenGiftIds += visibleIds
            when {
                fresh.size == 1 -> say(
                    CookieMessage(
                        CookieMessages.newGift(fresh.first().fromUsername),
                        actionLabel = if (inboxOpen) null else CookieMessages.VIEW,
                        isLong = true,
                        action = if (inboxOpen) null else CookieMessageAction.ViewInbox
                    )
                )
                fresh.size > 1 && !inboxOpen -> say(giftsWaitingMessage(view.visible.size))
            }
        }
        previousVisible = visibleIds.toSet()
    }

    private fun giftsWaitingMessage(count: Int) = CookieMessage(
        CookieMessages.giftsWaiting(count),
        actionLabel = CookieMessages.VIEW,
        isLong = true,
        action = CookieMessageAction.ViewInbox
    )

    //los vencidos no se muestran y se borran del backend al abrir la bandeja (el destinatario puede borrarlos)
    private fun deleteExpired(view: InboxView) {
        val expired = view.expired.filter { deletingExpired.add(it.shareId) }
        if (expired.isEmpty()) return
        viewModelScope.launch { sharedCookieRepository.deleteExpired(expired) }
    }

    fun onInboxOpened() {
        inboxOpen = true
        if (inbox.value.status == InboxStatus.OFFLINE || !_uiState.value.isOnline) {
            say(CookieMessage(CookieMessages.GIFTS_NEED_CONNECTION))
        }
    }

    fun onInboxClosed() {
        inboxOpen = false
    }

    //aceptar: pasos 1 a 4 en el repositorio; acceptingIds deshabilita el boton para no lanzar dos aceptaciones
    fun onAcceptGift(gift: SharedCookie) {
        if (!isOnline()) return needsInternet()
        if (gift.shareId in _uiState.value.acceptingIds) return
        _uiState.update { it.copy(acceptingIds = it.acceptingIds + gift.shareId) }
        val entry = Accepting()
        accepting[gift.shareId] = entry
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = sharedCookieRepository.acceptGift(gift) { entry.saved = true }
            accepting.remove(gift.shareId)
            _uiState.update { it.copy(acceptingIds = it.acceptingIds - gift.shareId) }
            result.fold(
                onSuccess = { accepted ->
                    when (accepted) {
                        is GiftAcceptResult.Done -> {
                            //ya se borro del backend: si el listener lo trae una vez mas no hay nada que terminar
                            finishingIds.add(gift.shareId)
                            say(CookieMessage(CookieMessages.giftAccepted(accepted.cookie.title)))
                        }
                        is GiftAcceptResult.SavedPendingCleanup -> say(CookieMessage(CookieMessages.GIFT_SAVED_PENDING, isLong = true))
                    }
                },
                onFailure = { error ->
                    when {
                        entry.saved -> say(CookieMessage(CookieMessages.GIFT_SAVED_PENDING, isLong = true))
                        error is GiftUnavailableException -> say(CookieMessage(CookieMessages.GIFT_EXPIRED))
                        else -> say(retryAcceptMessage(gift.shareId))
                    }
                }
            )
        }
        entry.job = job
        job.start()
    }

    //rechazo silencioso: el remitente no se entera
    fun onDeclineGift(gift: SharedCookie) {
        if (!isOnline()) return needsInternet()
        if (gift.shareId in _uiState.value.decliningIds || gift.shareId in _uiState.value.acceptingIds) return
        _uiState.update { it.copy(decliningIds = it.decliningIds + gift.shareId) }
        viewModelScope.launch {
            val result = sharedCookieRepository.declineGift(gift)
            _uiState.update { it.copy(decliningIds = it.decliningIds - gift.shareId) }
            result.fold(
                onSuccess = { say(CookieMessage(CookieMessages.GIFT_DECLINED)) },
                onFailure = { connectionLost { onDeclineGift(gift) } }
            )
        }
    }

    // ---------- regalar ----------

    //🎁 en el detalle: sin red M25; con red se abren los amigos para elegir a quien
    fun onGiftClick(cookieId: String) {
        if (!isOnline()) return say(CookieMessage(CookieMessages.GIFTS_NEED_INTERNET))
        _uiState.update { it.copy(giftCookieId = cookieId, isPickerLoading = true, pickerOverview = null) }
        viewModelScope.launch {
            friendRepository.loadOverview().fold(
                onSuccess = { overview -> _uiState.update { it.copy(pickerOverview = overview, isPickerLoading = false) } },
                onFailure = {
                    _uiState.update { it.copy(giftCookieId = null, isPickerLoading = false) }
                    connectionLost { onGiftClick(cookieId) }
                }
            )
        }
    }

    fun onDismissGiftPicker() {
        if (_uiState.value.sendingToUid != null) return
        _uiState.update { it.copy(giftCookieId = null, pickerOverview = null, isPickerLoading = false) }
    }

    fun onSendGift(friend: Friend) {
        val cookieId = _uiState.value.giftCookieId ?: return
        sendGift(cookieId, friend)
    }

    //"Sent ✓" (M23) solo cuando el servidor confirmo la transaccion
    private fun sendGift(cookieId: String, friend: Friend) {
        if (!isOnline()) return say(CookieMessage(CookieMessages.GIFTS_NEED_INTERNET))
        if (_uiState.value.sendingToUid != null) return
        lastGift = cookieId to friend
        _uiState.update { it.copy(sendingToUid = friend.uid) }
        sendJob = viewModelScope.launch {
            val result = sharedCookieRepository.sendGift(cookieId, friend)
            _uiState.update { it.copy(sendingToUid = null) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(giftCookieId = null, pickerOverview = null) }
                    say(CookieMessage(CookieMessages.giftSent(friend.username)))
                },
                onFailure = { say(retryGiftMessage(cookieId, friend)) }
            )
        }
    }

    //cancelar un regalo enviado: si ya no existe el mensaje es neutro (M26b)
    fun onCancelGift(shareId: String) {
        runBusy(shareId, action = { sharedCookieRepository.cancelGift(shareId) }) { canceled ->
            say(CookieMessage(if (canceled) CookieMessages.GIFT_CANCELED else CookieMessages.GIFT_CANNOT_CANCEL))
        }
    }

    // ---------- amigos ----------

    fun onFriendsOpened() {
        friendsOpen = true
        loadOverview()
    }

    fun onFriendsClosed() {
        friendsOpen = false
    }

    private fun loadOverview() {
        if (!isOnline()) return
        _uiState.update { it.copy(isLoadingFriends = true) }
        viewModelScope.launch {
            friendRepository.loadOverview().fold(
                onSuccess = { overview -> _uiState.update { it.copy(overview = overview, isLoadingFriends = false) } },
                onFailure = {
                    _uiState.update { it.copy(isLoadingFriends = false) }
                    connectionLost { loadOverview() }
                }
            )
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(usernameInput = Usernames.limitInput(value), usernameError = null) }

    //activar amigos: la regla del username esta en el modelo; la unicidad la garantiza la transaccion
    fun onActivateFriends() {
        if (!isOnline()) return needsInternet()
        val input = _uiState.value.usernameInput
        Usernames.error(input)?.let { error -> return _uiState.update { it.copy(usernameError = error) } }
        if (_uiState.value.isActivating) return
        _uiState.update { it.copy(isActivating = true) }
        viewModelScope.launch {
            val result = friendRepository.activate(input)
            _uiState.update { it.copy(isActivating = false) }
            result.fold(
                onSuccess = { profile ->
                    _uiState.update { state ->
                        state.copy(usernameInput = "", overview = (state.overview ?: FriendsOverview(myProfile = null)).copy(myProfile = profile))
                    }
                    loadOverview()
                },
                onFailure = { error ->
                    if (error is UsernameTakenException) say(CookieMessage(CookieMessages.usernameTaken(error.username)))
                    else connectionLost { onActivateFriends() }
                }
            )
        }
    }

    fun onRequestDeactivate() = _uiState.update { it.copy(confirm = SocialConfirm.Deactivate) }

    fun onRequestRemoveFriend(friend: Friend) = _uiState.update { it.copy(confirm = SocialConfirm.RemoveFriend(friend.uid, friend.username)) }

    fun onRequestBlock(uid: String, username: String) = _uiState.update { it.copy(confirm = SocialConfirm.Block(uid, username)) }

    fun onDismissConfirm() = _uiState.update { it.copy(confirm = null) }

    fun onConfirm() {
        val confirm = _uiState.value.confirm ?: return
        _uiState.update { it.copy(confirm = null) }
        when (confirm) {
            SocialConfirm.Deactivate -> deactivate()
            is SocialConfirm.RemoveFriend -> runBusy(confirm.uid, action = { friendRepository.removeFriend(confirm.uid) }) { loadOverview() }
            is SocialConfirm.Block -> runBusy(confirm.uid, action = { friendRepository.block(confirm.uid, confirm.username) }) {
                _uiState.update { state ->
                    state.copy(searchResult = state.searchResult?.takeUnless { it.profile.uid == confirm.uid })
                }
                loadOverview()
            }
        }
    }

    //desactivar borra el perfil publico y el username
    private fun deactivate() {
        val uid = _uiState.value.overview?.myProfile?.uid ?: return
        runBusy(uid, action = { friendRepository.deactivate() }) {
            _uiState.update { it.copy(overview = it.overview?.copy(myProfile = null), searchResult = null) }
            loadOverview()
        }
    }

    fun onSearchChange(value: String) = _uiState.update { it.copy(searchInput = Usernames.limitInput(value)) }

    //busqueda solo por username exacto; "no existe" y "te bloqueo" dan el mismo M20
    fun onSearch() {
        if (!isOnline()) return needsInternet()
        val input = _uiState.value.searchInput
        if (input.isBlank() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true, searchResult = null) }
        viewModelScope.launch {
            val result = friendRepository.findByUsername(input)
            _uiState.update { it.copy(isSearching = false) }
            result.fold(
                onSuccess = { found -> _uiState.update { it.copy(searchResult = found) } },
                onFailure = { error ->
                    if (error is UsernameNotFoundException) say(CookieMessage(CookieMessages.NO_SUCH_USERNAME))
                    else connectionLost { onSearch() }
                }
            )
        }
    }

    //boton del resultado de la busqueda segun la relacion
    fun onSearchResultAction(found: FoundUser) {
        val profile = found.profile
        when (found.relation) {
            FriendRelation.NONE -> runBusy(profile.uid, action = { friendRepository.sendRequest(profile) }) {
                updateRelation(profile.uid, FriendRelation.REQUESTED)
                say(CookieMessage(CookieMessages.requestSent(profile.username)))
            }
            FriendRelation.WANTS_TO_BE_FRIENDS -> onAcceptRequest(profile.uid)
            FriendRelation.BLOCKED_BY_ME -> onUnblock(profile.uid)
            FriendRelation.REQUESTED, FriendRelation.FRIENDS, FriendRelation.YOU -> Unit
        }
    }

    fun onAcceptRequest(fromUid: String) {
        runBusy(fromUid, action = { friendRepository.acceptRequest(fromUid) }) {
            updateRelation(fromUid, FriendRelation.FRIENDS)
            loadOverview()
        }
    }

    fun onDeclineRequest(fromUid: String) {
        runBusy(fromUid, action = { friendRepository.declineRequest(fromUid) }) { loadOverview() }
    }

    fun onUnblock(uid: String) {
        runBusy(uid, action = { friendRepository.unblock(uid) }) {
            updateRelation(uid, FriendRelation.NONE)
            loadOverview()
        }
    }

    private fun updateRelation(uid: String, relation: FriendRelation) = _uiState.update { state ->
        state.copy(searchResult = state.searchResult?.let { if (it.profile.uid == uid) it.copy(relation = relation) else it })
    }

    //accion social sobre un uid o un regalo: sin red M18; mientras corre su boton se deshabilita; si falla M22 con "Retry"
    private fun <T> runBusy(id: String, action: suspend () -> Result<T>, onSuccess: (T) -> Unit) {
        if (!isOnline()) return needsInternet()
        if (id in _uiState.value.busyIds) return
        _uiState.update { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            val result = action()
            _uiState.update { it.copy(busyIds = it.busyIds - id) }
            result.fold(onSuccess = onSuccess, onFailure = { connectionLost { runBusy(id, action, onSuccess) } })
        }
    }

    companion object {
        private const val TAG = "CookieSocial"
        //cada cuanto se revisa si vencio algun regalo de la bandeja abierta
        private const val EXPIRY_CHECK_MS = 15_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                CookieSocialViewModel(
                    friendRepository = app.container.friendRepository,
                    sharedCookieRepository = app.container.sharedCookieRepository,
                    cookieRepository = app.container.cookieRepository,
                    profile = app.container.userRepository.observeProfile(),
                    connectivity = app.container.networkConnectivityObserver.observe(),
                    isOnlineNow = app.container.networkConnectivityObserver::isConnected,
                )
            }
        }
    }
}

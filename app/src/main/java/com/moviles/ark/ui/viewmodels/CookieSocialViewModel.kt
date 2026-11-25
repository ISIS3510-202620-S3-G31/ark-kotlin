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
import com.moviles.ark.domain.models.PublicProfile
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

    //true si el usuario puede usar amigos (18+); la regla esta en el perfil
    private val isAdultFlow: Flow<Boolean> = profile
        .map { userProfile -> userProfile?.canUseFriends() == true }
        .distinctUntilChanged()

    //ids de las galletas del jarro (room): un regalo con id = shareId ya se acepto en este telefono
    private val cookieIdsInJar: Flow<Set<String>> = cookieRepository.observeCookieList()
        .map { items -> items.map { it.id }.toSet() }
        .catch { emit(emptySet()) }

    //reloj para los vencidos: solo corre mientras alguien escucha la bandeja
    private val expiryClock: Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(EXPIRY_CHECK_MS)
        }
    }

    //lo que la bandeja usa del estado: los regalos que se estan aceptando y si hay red
    private val acceptingIdsAndOnline: Flow<Pair<Set<String>, Boolean>> = _uiState
        .map { state -> Pair(state.acceptingIds, state.isOnline) }
        .distinctUntilChanged()

    //bandeja abierta en pantalla: ahi se borran los vencidos y se avisa M33
    private var isInboxOpen = false
    private var lastInboxStatus = InboxStatus.LOADING
    //lo ultimo que confirmo el servidor (null sin red)
    private var lastServerSnapshot: InboxSnapshot.Loaded? = null
    //regalos ya vistos en esta visita, para avisar solo los nuevos
    private val seenGiftIds = mutableSetOf<String>()
    //false hasta que llega el primer dato del servidor
    private var firstServerDataArrived = false
    //regalos que se veian en la bandeja la vez anterior (para saber si uno vencio mientras se miraba)
    private var previousVisibleIds: Set<String> = emptySet()
    //regalos aceptados que se estan terminando de borrar del backend en silencio
    private val finishingIds = mutableSetOf<String>()
    //vencidos que ya se mandaron a borrar
    private val deletingExpiredIds = mutableSetOf<String>()

    //una aceptacion en curso; savedInJar se vuelve true cuando la galleta ya quedo en el jarro
    //@Volatile porque el repositorio lo marca desde otro hilo
    private class AcceptingGift {
        @Volatile
        var savedInJar: Boolean = false
        var job: Job? = null
    }

    //aceptaciones en curso por shareId: si se cae la red se cancelan
    private val acceptingGifts = mutableMapOf<String, AcceptingGift>()
    private var sendJob: Job? = null
    //el ultimo regalo que se intento enviar ("Retry" de M24)
    private var lastGiftCookieId: String? = null
    private var lastGiftFriend: Friend? = null
    //lo que se repite con "Retry" de M22
    private var retryLastAction: (() -> Unit)? = null
    private var isFriendsOpen = false

    //bandeja en vivo: listener de firestore (callbackFlow) que vive mientras se ve una pantalla de cookie jar
    //WhileSubscribed(5000) aguanta un giro de pantalla sin reconectar
    //flatMapLatest: si cambia la edad se suelta el flujo anterior; para menores de 18 el listener ni se crea
    @OptIn(ExperimentalCoroutinesApi::class)
    val inbox: StateFlow<InboxUiState> = isAdultFlow
        .flatMapLatest { isAdult ->
            if (isAdult) {
                observeInboxForAdult()
            } else {
                flowOf(InboxUiState(status = InboxStatus.HIDDEN))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InboxUiState())

    init {
        observeAge()
        observeSentGifts()
        observeConnectivity(connectivity)
    }

    //combine: la bandeja se vuelve a calcular cuando llega un snapshot, cambia el jarro, cambia la red o pasa el reloj
    private fun observeInboxForAdult(): Flow<InboxUiState> {
        return combine(
            sharedCookieRepository.observeInbox(),
            cookieIdsInJar,
            acceptingIdsAndOnline,
            expiryClock,
        ) { snapshot, cookieIds, acceptingAndOnline, time ->
            val acceptingIds = acceptingAndOnline.first
            val online = acceptingAndOnline.second
            onInboxSnapshot(snapshot, cookieIds, acceptingIds, online, time)
        }
    }

    private fun observeAge() {
        viewModelScope.launch {
            isAdultFlow.collect { isAdult ->
                _uiState.update { it.copy(isAdult = isAdult) }
            }
        }
    }

    //regalos enviados desde este telefono (room)
    private fun observeSentGifts() {
        viewModelScope.launch {
            sharedCookieRepository.observeSentGifts()
                .catch { }
                .collect { sentGifts ->
                    _uiState.update { it.copy(sentGifts = sentGifts) }
                }
        }
    }

    //red cruda (sin esperar): los botones sociales cambian al instante
    private fun observeConnectivity(connectivity: Flow<Boolean>) {
        viewModelScope.launch {
            connectivity.collect { online ->
                val wasOnline = _uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (!online) {
                    onConnectionLost()
                }
                //volvio la red con la pantalla de amigos abierta y sin datos: se cargan
                val cameBackOnline = online && !wasOnline
                if (cameBackOnline && isFriendsOpen && _uiState.value.overview == null) {
                    loadOverview()
                }
            }
        }
    }

    //si la red se cae a mitad de aceptar o de regalar se cancela y se dice en que quedo
    private fun onConnectionLost() {
        cancelAcceptingGifts()
        cancelSendingGift()
    }

    private fun cancelAcceptingGifts() {
        //se recorre una copia porque dentro del for se borra del mapa
        val inProgress = acceptingGifts.toMap()
        for (entry in inProgress) {
            val shareId = entry.key
            val accepting = entry.value
            accepting.job?.cancel()
            acceptingGifts.remove(shareId)
            _uiState.update { it.copy(acceptingIds = it.acceptingIds - shareId) }
            //si la galleta ya quedo en el jarro ya es del usuario: lo que falta se termina solo al volver la red
            if (accepting.savedInJar) {
                say(savedPendingMessage())
            } else {
                say(retryAcceptMessage(shareId))
            }
        }
    }

    private fun cancelSendingGift() {
        if (sendJob?.isActive != true) return
        sendJob?.cancel()
        _uiState.update { it.copy(sendingToUid = null) }
        val cookieId = lastGiftCookieId
        val friend = lastGiftFriend
        if (cookieId != null && friend != null) {
            say(retryGiftMessage(cookieId, friend))
        }
    }

    //red al momento de tocar: el estado del flow y, por si un aviso del sistema llego tarde, la consulta directa
    //si no coinciden se corrige el estado y los botones cambian
    private fun isOnline(): Boolean {
        val online = _uiState.value.isOnline && isOnlineNow()
        if (online != _uiState.value.isOnline) {
            _uiState.update { it.copy(isOnline = online) }
        }
        return online
    }

    private fun say(message: CookieMessage) {
        Log.d(TAG, "message: ${message.text}")
        messages.trySend(message)
    }

    //M18: algo social sin red
    private fun sayNeedsInternet() {
        say(CookieMessage(CookieMessages.FRIENDS_NEED_INTERNET))
    }

    //M22 con "Retry": la red se cayo y no se cambio nada; retry es lo que se repite si lo tocan
    private fun sayConnectionLost(retry: () -> Unit) {
        retryLastAction = retry
        say(
            CookieMessage(
                CookieMessages.CONNECTION_LOST,
                actionLabel = CookieMessages.RETRY,
                isLong = true,
                action = CookieMessageAction.RetrySocial
            )
        )
    }

    private fun savedPendingMessage(): CookieMessage {
        return CookieMessage(CookieMessages.GIFT_SAVED_PENDING, isLong = true)
    }

    private fun retryAcceptMessage(shareId: String): CookieMessage {
        return CookieMessage(
            CookieMessages.GIFT_STILL_IN_INBOX,
            actionLabel = CookieMessages.RETRY,
            isLong = true,
            action = CookieMessageAction.RetryAccept(shareId)
        )
    }

    private fun retryGiftMessage(cookieId: String, friend: Friend): CookieMessage {
        return CookieMessage(
            CookieMessages.GIFT_NOT_SENT,
            actionLabel = CookieMessages.RETRY,
            isLong = true,
            action = CookieMessageAction.RetryGift(cookieId, friend.uid)
        )
    }

    private fun giftsWaitingMessage(count: Int): CookieMessage {
        return CookieMessage(
            CookieMessages.giftsWaiting(count),
            actionLabel = CookieMessages.VIEW,
            isLong = true,
            action = CookieMessageAction.ViewInbox
        )
    }

    //M27: con la bandeja abierta no hace falta el boton "View"
    private fun newGiftMessage(fromUsername: String): CookieMessage {
        if (isInboxOpen) {
            return CookieMessage(CookieMessages.newGift(fromUsername), isLong = true)
        }
        return CookieMessage(
            CookieMessages.newGift(fromUsername),
            actionLabel = CookieMessages.VIEW,
            isLong = true,
            action = CookieMessageAction.ViewInbox
        )
    }

    //boton de un aviso social ("View" lo atiende la navegacion)
    fun onMessageAction(action: CookieMessageAction.Social) {
        when (action) {
            CookieMessageAction.ViewInbox -> Unit
            CookieMessageAction.RetrySocial -> retryLastAction?.invoke()
            is CookieMessageAction.RetryGift -> retryGift(action.friendUid)
            is CookieMessageAction.RetryAccept -> retryAccept(action.shareId)
        }
    }

    //"Retry" de M24: se vuelve a enviar solo si el ultimo intento fue a ese mismo amigo
    private fun retryGift(friendUid: String) {
        val cookieId = lastGiftCookieId
        val friend = lastGiftFriend
        if (cookieId == null || friend == null) return
        if (friend.uid != friendUid) return
        sendGift(cookieId, friend)
    }

    //"Retry" de M31: se acepta otra vez si el regalo sigue en la bandeja
    private fun retryAccept(shareId: String) {
        val gift = inbox.value.gifts.find { it.shareId == shareId }
        if (gift == null) return
        onAcceptGift(gift)
    }


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
        if (snapshot is InboxSnapshot.Loaded) {
            lastServerSnapshot = snapshot
        }
        val serverSnapshot = lastServerSnapshot
        if (!online || serverSnapshot == null) {
            return inboxWithoutGifts(online, time)
        }
        lastInboxStatus = InboxStatus.READY
        val view = CookieGifts.splitInbox(serverSnapshot.gifts, cookieIds, acceptingIds, time)
        finishAcceptedGifts(view)
        if (isInboxOpen) {
            warnIfGiftExpiredWhileOpen(view)
            deleteExpired(view)
        }
        announceNewGifts(view)
        previousVisibleIds = view.visible.map { it.shareId }.toSet()
        return InboxUiState(status = InboxStatus.READY, gifts = view.visible, now = time)
    }

    //sin red o esperando el primer dato del servidor: no se muestra ningun regalo
    private fun inboxWithoutGifts(online: Boolean, time: Long): InboxUiState {
        val status = if (online) InboxStatus.LOADING else InboxStatus.OFFLINE
        //M29: se fue la red con la bandeja abierta y mostrando regalos
        if (status == InboxStatus.OFFLINE && isInboxOpen && lastInboxStatus == InboxStatus.READY) {
            say(CookieMessage(CookieMessages.GIFTS_NEED_CONNECTION))
        }
        lastInboxStatus = status
        //al volver la red se espera un dato nuevo del servidor
        if (!online) {
            lastServerSnapshot = null
        }
        return InboxUiState(status = status, now = time)
    }

    //ya aceptado en este telefono pero sigue en el backend (la red se cayo entre el paso 2 y el 3): se termina en silencio
    private fun finishAcceptedGifts(view: InboxView) {
        for (gift in view.alreadyInJar) {
            //add da false si ese regalo ya se estaba terminando
            val isNew = finishingIds.add(gift.shareId)
            if (!isNew) continue
            viewModelScope.launch {
                val result = sharedCookieRepository.finishAccepted(gift)
                if (result.isFailure) {
                    finishingIds.remove(gift.shareId)
                }
            }
        }
    }

    //M33: un regalo que se estaba viendo vencio con la bandeja abierta
    private fun warnIfGiftExpiredWhileOpen(view: InboxView) {
        for (gift in view.expired) {
            if (gift.shareId in previousVisibleIds) {
                say(CookieMessage(CookieMessages.GIFT_EXPIRED))
                return
            }
        }
    }

    //los vencidos no se muestran y se borran del backend al abrir la bandeja (el destinatario puede borrarlos)
    private fun deleteExpired(view: InboxView) {
        val newExpired = mutableListOf<SharedCookie>()
        for (gift in view.expired) {
            val isNew = deletingExpiredIds.add(gift.shareId)
            if (isNew) {
                newExpired.add(gift)
            }
        }
        if (newExpired.isEmpty()) return
        viewModelScope.launch {
            sharedCookieRepository.deleteExpired(newExpired)
        }
    }

    //avisos de regalos que llegaron: el primero del servidor trae todos los pendientes, despues solo los nuevos
    private fun announceNewGifts(view: InboxView) {
        val visibleIds = view.visible.map { it.shareId }
        if (!firstServerDataArrived) {
            //tambien llegan los que se enviaron con la app cerrada (M28)
            firstServerDataArrived = true
            seenGiftIds.addAll(visibleIds)
            if (view.visible.isNotEmpty() && !isInboxOpen) {
                say(giftsWaitingMessage(view.visible.size))
            }
            return
        }
        val newGifts = CookieGifts.newArrivals(view.visible, seenGiftIds)
        seenGiftIds.addAll(visibleIds)
        if (newGifts.size == 1) {
            say(newGiftMessage(newGifts[0].fromUsername))
        } else if (newGifts.size > 1 && !isInboxOpen) {
            say(giftsWaitingMessage(view.visible.size))
        }
    }

    fun onInboxOpened() {
        isInboxOpen = true
        if (inbox.value.status == InboxStatus.OFFLINE || !_uiState.value.isOnline) {
            say(CookieMessage(CookieMessages.GIFTS_NEED_CONNECTION))
        }
    }

    fun onInboxClosed() {
        isInboxOpen = false
    }

    //aceptar: los pasos estan en el repositorio; acceptingIds deshabilita el boton para no lanzar dos aceptaciones
    fun onAcceptGift(gift: SharedCookie) {
        if (!isOnline()) {
            sayNeedsInternet()
            return
        }
        if (gift.shareId in _uiState.value.acceptingIds) return
        _uiState.update { it.copy(acceptingIds = it.acceptingIds + gift.shareId) }
        val accepting = AcceptingGift()
        acceptingGifts[gift.shareId] = accepting
        //LAZY: el job se crea sin arrancar para guardarlo antes de que corra (asi se puede cancelar si se va la red)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            //el repositorio avisa cuando la galleta ya quedo guardada en el jarro
            val result = sharedCookieRepository.acceptGift(gift) { accepting.savedInJar = true }
            acceptingGifts.remove(gift.shareId)
            _uiState.update { it.copy(acceptingIds = it.acceptingIds - gift.shareId) }
            if (result.isSuccess) {
                onGiftAccepted(gift, result.getOrThrow())
            } else {
                onGiftAcceptFailed(gift, accepting, result.exceptionOrNull())
            }
        }
        accepting.job = job
        job.start()
    }

    private fun onGiftAccepted(gift: SharedCookie, accepted: GiftAcceptResult) {
        if (accepted is GiftAcceptResult.Done) {
            //ya se borro del backend: si el listener lo trae una vez mas no hay nada que terminar
            finishingIds.add(gift.shareId)
            say(CookieMessage(CookieMessages.giftAccepted(accepted.cookie.title)))
        } else {
            //quedo en el jarro pero sigue en el backend: se termina solo
            say(savedPendingMessage())
        }
    }

    private fun onGiftAcceptFailed(gift: SharedCookie, accepting: AcceptingGift, error: Throwable?) {
        if (accepting.savedInJar) {
            say(savedPendingMessage())
        } else if (error is GiftUnavailableException) {
            say(CookieMessage(CookieMessages.GIFT_EXPIRED))
        } else {
            say(retryAcceptMessage(gift.shareId))
        }
    }

    //rechazo silencioso: el remitente no se entera
    fun onDeclineGift(gift: SharedCookie) {
        if (!isOnline()) {
            sayNeedsInternet()
            return
        }
        val state = _uiState.value
        if (gift.shareId in state.decliningIds || gift.shareId in state.acceptingIds) return
        _uiState.update { it.copy(decliningIds = it.decliningIds + gift.shareId) }
        viewModelScope.launch {
            val result = sharedCookieRepository.declineGift(gift)
            _uiState.update { it.copy(decliningIds = it.decliningIds - gift.shareId) }
            if (result.isSuccess) {
                say(CookieMessage(CookieMessages.GIFT_DECLINED))
            } else {
                sayConnectionLost { onDeclineGift(gift) }
            }
        }
    }


    //🎁 en el detalle: sin red M25; con red se abren los amigos para elegir a quien
    fun onGiftClick(cookieId: String) {
        if (!isOnline()) {
            say(CookieMessage(CookieMessages.GIFTS_NEED_INTERNET))
            return
        }
        _uiState.update { it.copy(giftCookieId = cookieId, isPickerLoading = true, pickerOverview = null) }
        viewModelScope.launch {
            val result = friendRepository.loadOverview()
            if (result.isSuccess) {
                val overview = result.getOrThrow()
                _uiState.update { it.copy(pickerOverview = overview, isPickerLoading = false) }
            } else {
                _uiState.update { it.copy(giftCookieId = null, isPickerLoading = false) }
                sayConnectionLost { onGiftClick(cookieId) }
            }
        }
    }

    fun onDismissGiftPicker() {
        //mientras se envia no se cierra
        if (_uiState.value.sendingToUid != null) return
        _uiState.update { it.copy(giftCookieId = null, pickerOverview = null, isPickerLoading = false) }
    }

    fun onSendGift(friend: Friend) {
        val cookieId = _uiState.value.giftCookieId
        if (cookieId == null) return
        sendGift(cookieId, friend)
    }

    //"Sent ✓" (M23) solo cuando el servidor confirmo la transaccion
    private fun sendGift(cookieId: String, friend: Friend) {
        if (!isOnline()) {
            say(CookieMessage(CookieMessages.GIFTS_NEED_INTERNET))
            return
        }
        if (_uiState.value.sendingToUid != null) return
        lastGiftCookieId = cookieId
        lastGiftFriend = friend
        _uiState.update { it.copy(sendingToUid = friend.uid) }
        sendJob = viewModelScope.launch {
            val result = sharedCookieRepository.sendGift(cookieId, friend)
            _uiState.update { it.copy(sendingToUid = null) }
            if (result.isSuccess) {
                _uiState.update { it.copy(giftCookieId = null, pickerOverview = null) }
                say(CookieMessage(CookieMessages.giftSent(friend.username)))
            } else {
                say(retryGiftMessage(cookieId, friend))
            }
        }
    }

    //cancelar un regalo enviado: si ya no existe el mensaje es neutro (M26b)
    fun onCancelGift(shareId: String) {
        if (!startBusy(shareId)) return
        viewModelScope.launch {
            val result = sharedCookieRepository.cancelGift(shareId)
            stopBusy(shareId)
            if (result.isFailure) {
                sayConnectionLost { onCancelGift(shareId) }
            } else if (result.getOrThrow()) {
                say(CookieMessage(CookieMessages.GIFT_CANCELED))
            } else {
                say(CookieMessage(CookieMessages.GIFT_CANNOT_CANCEL))
            }
        }
    }


    fun onFriendsOpened() {
        isFriendsOpen = true
        loadOverview()
    }

    fun onFriendsClosed() {
        isFriendsOpen = false
    }

    //perfil, solicitudes, amigos y bloqueados: se piden al servidor cada vez que se abre la pantalla
    private fun loadOverview() {
        if (!isOnline()) return
        _uiState.update { it.copy(isLoadingFriends = true) }
        viewModelScope.launch {
            val result = friendRepository.loadOverview()
            if (result.isSuccess) {
                val overview = result.getOrThrow()
                _uiState.update { it.copy(overview = overview, isLoadingFriends = false) }
            } else {
                _uiState.update { it.copy(isLoadingFriends = false) }
                sayConnectionLost { loadOverview() }
            }
        }
    }

    fun onUsernameChange(value: String) {
        val limitedValue = Usernames.limitInput(value)
        _uiState.update { it.copy(usernameInput = limitedValue, usernameError = null) }
    }

    //activar amigos: la regla del username esta en el modelo; la unicidad la garantiza la transaccion
    fun onActivateFriends() {
        if (!isOnline()) {
            sayNeedsInternet()
            return
        }
        val input = _uiState.value.usernameInput
        val usernameError = Usernames.error(input)
        if (usernameError != null) {
            _uiState.update { it.copy(usernameError = usernameError) }
            return
        }
        if (_uiState.value.isActivating) return
        _uiState.update { it.copy(isActivating = true) }
        viewModelScope.launch {
            val result = friendRepository.activate(input)
            _uiState.update { it.copy(isActivating = false) }
            if (result.isSuccess) {
                showMyNewProfile(result.getOrThrow())
                loadOverview()
                return@launch
            }
            val error = result.exceptionOrNull()
            if (error is UsernameTakenException) {
                say(CookieMessage(CookieMessages.usernameTaken(error.username)))
            } else {
                sayConnectionLost { onActivateFriends() }
            }
        }
    }

    //el perfil nuevo se ve de una vez, antes de que vuelva a cargar la pantalla
    private fun showMyNewProfile(myProfile: PublicProfile) {
        _uiState.update { state ->
            val oldOverview = state.overview ?: FriendsOverview(myProfile = null)
            state.copy(usernameInput = "", overview = oldOverview.copy(myProfile = myProfile))
        }
    }

    fun onRequestDeactivate() {
        _uiState.update { it.copy(confirm = SocialConfirm.Deactivate) }
    }

    fun onRequestRemoveFriend(friend: Friend) {
        _uiState.update { it.copy(confirm = SocialConfirm.RemoveFriend(friend.uid, friend.username)) }
    }

    fun onRequestBlock(uid: String, username: String) {
        _uiState.update { it.copy(confirm = SocialConfirm.Block(uid, username)) }
    }

    fun onDismissConfirm() {
        _uiState.update { it.copy(confirm = null) }
    }

    fun onConfirm() {
        val confirm = _uiState.value.confirm
        if (confirm == null) return
        _uiState.update { it.copy(confirm = null) }
        when (confirm) {
            SocialConfirm.Deactivate -> deactivate()
            is SocialConfirm.RemoveFriend -> removeFriend(confirm.uid)
            is SocialConfirm.Block -> block(confirm.uid, confirm.username)
        }
    }

    //desactivar borra el perfil publico y el username
    private fun deactivate() {
        val myUid = _uiState.value.overview?.myProfile?.uid
        if (myUid == null) return
        runFriendAction(
            id = myUid,
            action = { friendRepository.deactivate() },
            onSuccess = {
                _uiState.update { it.copy(overview = it.overview?.copy(myProfile = null), searchResult = null) }
                loadOverview()
            }
        )
    }

    private fun removeFriend(uid: String) {
        runFriendAction(
            id = uid,
            action = { friendRepository.removeFriend(uid) },
            onSuccess = { loadOverview() }
        )
    }

    //bloquear tambien quita la amistad; si era el resultado de la busqueda se quita de la pantalla
    private fun block(uid: String, username: String) {
        runFriendAction(
            id = uid,
            action = { friendRepository.block(uid, username) },
            onSuccess = {
                removeSearchResultOf(uid)
                loadOverview()
            }
        )
    }

    private fun removeSearchResultOf(uid: String) {
        _uiState.update { state ->
            val found = state.searchResult
            if (found != null && found.profile.uid == uid) {
                state.copy(searchResult = null)
            } else {
                state
            }
        }
    }

    fun onSearchChange(value: String) {
        val limitedValue = Usernames.limitInput(value)
        _uiState.update { it.copy(searchInput = limitedValue) }
    }

    //busqueda solo por username exacto; "no existe" y "te bloqueo" dan el mismo M20
    fun onSearch() {
        if (!isOnline()) {
            sayNeedsInternet()
            return
        }
        val input = _uiState.value.searchInput
        if (input.isBlank() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true, searchResult = null) }
        viewModelScope.launch {
            val result = friendRepository.findByUsername(input)
            _uiState.update { it.copy(isSearching = false) }
            if (result.isSuccess) {
                val found = result.getOrThrow()
                _uiState.update { it.copy(searchResult = found) }
            } else if (result.exceptionOrNull() is UsernameNotFoundException) {
                say(CookieMessage(CookieMessages.NO_SUCH_USERNAME))
            } else {
                sayConnectionLost { onSearch() }
            }
        }
    }

    //boton del resultado de la busqueda segun la relacion
    fun onSearchResultAction(found: FoundUser) {
        val profile = found.profile
        when (found.relation) {
            FriendRelation.NONE -> sendRequest(profile)
            FriendRelation.WANTS_TO_BE_FRIENDS -> onAcceptRequest(profile.uid)
            FriendRelation.BLOCKED_BY_ME -> onUnblock(profile.uid)
            //en estos casos el boton esta apagado
            FriendRelation.REQUESTED, FriendRelation.FRIENDS, FriendRelation.YOU -> Unit
        }
    }

    private fun sendRequest(profile: PublicProfile) {
        runFriendAction(
            id = profile.uid,
            action = { friendRepository.sendRequest(profile) },
            onSuccess = {
                changeSearchResultRelation(profile.uid, FriendRelation.REQUESTED)
                say(CookieMessage(CookieMessages.requestSent(profile.username)))
            }
        )
    }

    fun onAcceptRequest(fromUid: String) {
        runFriendAction(
            id = fromUid,
            action = { friendRepository.acceptRequest(fromUid) },
            onSuccess = {
                changeSearchResultRelation(fromUid, FriendRelation.FRIENDS)
                loadOverview()
            }
        )
    }

    fun onDeclineRequest(fromUid: String) {
        runFriendAction(
            id = fromUid,
            action = { friendRepository.declineRequest(fromUid) },
            onSuccess = { loadOverview() }
        )
    }

    fun onUnblock(uid: String) {
        runFriendAction(
            id = uid,
            action = { friendRepository.unblock(uid) },
            onSuccess = {
                changeSearchResultRelation(uid, FriendRelation.NONE)
                loadOverview()
            }
        )
    }

    //cambia el boton del resultado de la busqueda sin volver a buscar
    private fun changeSearchResultRelation(uid: String, relation: FriendRelation) {
        _uiState.update { state ->
            val found = state.searchResult
            if (found != null && found.profile.uid == uid) {
                state.copy(searchResult = found.copy(relation = relation))
            } else {
                state
            }
        }
    }

    //accion de amigos sobre un uid: action es la llamada al repositorio y onSuccess lo que se hace si salio bien
    //sin red M18; mientras corre su boton se deshabilita; si falla M22 con "Retry" (que la repite igual)
    private fun runFriendAction(id: String, action: suspend () -> Result<Unit>, onSuccess: () -> Unit) {
        if (!startBusy(id)) return
        viewModelScope.launch {
            val result = action()
            stopBusy(id)
            if (result.isSuccess) {
                onSuccess()
            } else {
                sayConnectionLost { runFriendAction(id, action, onSuccess) }
            }
        }
    }

    //deshabilita el boton de ese id; false si no hay red (y avisa M18) o si ya habia una accion en curso
    private fun startBusy(id: String): Boolean {
        if (!isOnline()) {
            sayNeedsInternet()
            return false
        }
        if (id in _uiState.value.busyIds) return false
        _uiState.update { it.copy(busyIds = it.busyIds + id) }
        return true
    }

    private fun stopBusy(id: String) {
        _uiState.update { it.copy(busyIds = it.busyIds - id) }
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

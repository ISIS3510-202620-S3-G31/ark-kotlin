package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.FoundUser
import com.moviles.ark.domain.models.Friend
import com.moviles.ark.domain.models.FriendsOverview
import com.moviles.ark.domain.models.SentGift
import com.moviles.ark.domain.models.SharedCookie

//confirmaciones antes de una accion social que no se deshace sola
sealed interface SocialConfirm {
    data object Deactivate : SocialConfirm
    data class RemoveFriend(val uid: String, val username: String) : SocialConfirm
    data class Block(val uid: String, val username: String) : SocialConfirm
}

//estado de amigos y del envio de regalos (solo 18+)
data class CookieSocialUiState(
    //false para menores de 18 o si no se sabe la edad: no se ven 🎁, bandeja ni amigos
    val isAdult: Boolean = false,
    //red cruda (sin debounce): con esto se deshabilitan los botones sociales
    val isOnline: Boolean = true,
    //pantalla de amigos: lo trae el servidor al abrirla
    val overview: FriendsOverview? = null,
    val isLoadingFriends: Boolean = false,
    val usernameInput: String = "",
    val usernameError: String? = null,
    val isActivating: Boolean = false,
    val searchInput: String = "",
    val searchResult: FoundUser? = null,
    val isSearching: Boolean = false,
    //uid o shareId con una accion en curso: su boton se deshabilita
    val busyIds: Set<String> = emptySet(),
    val confirm: SocialConfirm? = null,
    //regalos enviados desde este telefono (room)
    val sentGifts: List<SentGift> = emptyList(),
    //regalar: galleta elegida y amigos para elegir a quien
    val giftCookieId: String? = null,
    val pickerOverview: FriendsOverview? = null,
    val isPickerLoading: Boolean = false,
    val sendingToUid: String? = null,
    //bandeja: regalos que se estan aceptando o rechazando (evita el doble toque)
    val acceptingIds: Set<String> = emptySet(),
    val decliningIds: Set<String> = emptySet(),
) {
    val isFriendsOn: Boolean get() = overview?.myProfile != null
    val pickerFriends: List<Friend> get() = pickerOverview?.friends.orEmpty()
    //ya cargo el selector y no ha activado amigos: no tiene username para firmar el regalo
    val pickerNeedsActivation: Boolean get() = pickerOverview != null && pickerOverview.myProfile == null
}

//estado de la bandeja
enum class InboxStatus {
    //menor de 18: no hay bandeja ni listener
    HIDDEN,
    LOADING,
    //sin red o solo con datos de la cache: no se muestra nada (M29)
    OFFLINE,
    READY,
}

data class InboxUiState(
    val status: InboxStatus = InboxStatus.LOADING,
    val gifts: List<SharedCookie> = emptyList(),
    //hora con la que se calcularon los vencidos ("Expires in N days")
    val now: Long = 0L,
) {
    //badge 🎁 del jarro
    val hasGifts: Boolean get() = status == InboxStatus.READY && gifts.isNotEmpty()
}

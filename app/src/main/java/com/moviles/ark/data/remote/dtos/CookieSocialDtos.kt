package com.moviles.ark.data.remote.dtos

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

//dtos de amigos y regalos del cookie jar en firestore; los campos son los mismos que revisa firestore.rules

//public_profiles/{uid}: solo username y avatar (nombre, correo y fecha siguen privados en users/{uid})
@IgnoreExtraProperties
data class PublicProfileDto(
    @get:Exclude
    val uid: String = "",
    val username: String = "",
    val avatarId: String = "",
)

//friend_requests/{fromUid_toUid}
@IgnoreExtraProperties
data class FriendRequestDto(
    val from: String = "",
    val to: String = "",
    val fromUsername: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Long = 0L,
) {
    companion object {
        const val STATUS_PENDING = "pending"
    }
}

//friendships/{uidMenor_uidMayor}
@IgnoreExtraProperties
data class FriendshipDto(
    //los dos uid ordenados
    val members: List<String> = emptyList(),
    val since: Long = 0L,
)

//users/{uid}/blocked/{otroUid}
@IgnoreExtraProperties
data class BlockedDto(
    @get:Exclude
    val uid: String = "",
    val username: String = "",
    val blockedAt: Long = 0L,
)

//shared_cookies/{shareId}: copia de la galleta (sin la foto, que va en storage shared/{shareId}.jpg)
@IgnoreExtraProperties
data class SharedCookieDto(
    @get:Exclude
    val shareId: String = "",
    val from: String = "",
    val fromUsername: String = "",
    val to: String = "",
    val title: String = "",
    val text: String = "",
    val hasImage: Boolean = false,
    val sentAt: Long = 0L,
    //a los 7 dias vence
    val expiresAt: Long = 0L,
)

package com.moviles.ark.domain.models

import java.util.Locale

//amigos y regalos del cookie jar (solo 18+): las reglas viven aqui para que la pantalla y los repositorios usen las mismas

//perfil publico minimo: lo unico que otro usuario ve de ti (username y avatar)
data class PublicProfile(
    val uid: String,
    val username: String,
    val avatarId: String,
)

//un amigo: su perfil publico y desde cuando son amigos
data class Friend(
    val uid: String,
    val username: String,
    val avatarId: String,
    val since: Long,
)

//solicitud de amistad que llego; quien la envio no se entera si se rechaza
data class FriendRequest(
    val fromUid: String,
    val fromUsername: String,
    val createdAt: Long,
)

//alguien que este usuario bloqueo: no lo encuentra, no le pide amistad ni le regala
data class BlockedUser(
    val uid: String,
    val username: String,
    val blockedAt: Long,
)

//relacion con alguien encontrado por username
enum class FriendRelation { NONE, REQUESTED, WANTS_TO_BE_FRIENDS, FRIENDS, BLOCKED_BY_ME, YOU }

data class FoundUser(
    val profile: PublicProfile,
    val relation: FriendRelation,
)

//todo lo de la pantalla de amigos, traido del servidor
data class FriendsOverview(
    //null si todavia no activo amigos (no tiene username)
    val myProfile: PublicProfile?,
    val requests: List<FriendRequest> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val blocked: List<BlockedUser> = emptyList(),
)

//reglas del username: 3 a 20 caracteres a-z 0-9 _ . y en minusculas (@David_F es @david_f)
object Usernames {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20
    private val VALID = Regex("^[a-z0-9_.]+$")

    //como se guarda y se busca: sin espacios, sin la @ del principio y en minusculas
    fun normalize(input: String): String = input.trim().removePrefix("@").lowercase(Locale.ROOT)

    //el error del username escrito, o null si sirve
    fun error(input: String): String? {
        val name = normalize(input)
        return when {
            name.isEmpty() -> "Choose a username"
            !name.matches(VALID) -> "Use only letters, numbers, _ and ."
            name.length < MIN_LENGTH -> "Use at least $MIN_LENGTH characters"
            name.length > MAX_LENGTH -> "Use at most $MAX_LENGTH characters"
            else -> null
        }
    }

    //el campo no deja escribir mas de lo que cabe (con la @ opcional)
    fun limitInput(input: String): String = input.replace(" ", "").take(MAX_LENGTH + 1)
}

//regalo pendiente en la bandeja (vive en el backend, no en el telefono)
data class SharedCookie(
    val shareId: String,
    val fromUid: String,
    val fromUsername: String,
    val toUid: String,
    val title: String,
    val text: String,
    val hasImage: Boolean,
    val sentAt: Long,
    val expiresAt: Long,
) {
    fun isExpired(now: Long): Boolean = now >= expiresAt

    //dias que le quedan para "Expires in N days": entre 1 y 7 aunque el reloj de los dos telefonos no coincida
    fun daysLeft(now: Long): Int =
        (((expiresAt - now) + CookieGifts.DAY_MS - 1) / CookieGifts.DAY_MS).toInt().coerceIn(1, CookieGifts.EXPIRY_DAYS)
}

//regalo enviado: guardado solo en el telefono del remitente, que solo sabe que llego ("Sent ✓")
data class SentGift(
    val shareId: String,
    val toUid: String,
    val toUsername: String,
    val title: String,
    val sentAt: Long,
    val expiresAt: Long,
) {
    //despues de 7 dias el regalo ya no existe: no se ofrece cancelar
    fun canCancel(now: Long): Boolean = now < expiresAt
}

//lo que entrega el listener de la bandeja
sealed interface InboxSnapshot {
    //datos de la cache de firestore o sin red: la bandeja no muestra nada (no se guarda en el telefono)
    data object Offline : InboxSnapshot

    //datos confirmados por el servidor
    data class Loaded(val gifts: List<SharedCookie>) : InboxSnapshot
}

//la bandeja separada para la pantalla
data class InboxView(
    //se muestran, del mas nuevo al mas viejo
    val visible: List<SharedCookie> = emptyList(),
    //vencidos: no se muestran y se borran del backend al abrir la bandeja
    val expired: List<SharedCookie> = emptyList(),
    //ya aceptados en este telefono (existe una galleta con id = shareId) pero sin borrar del backend:
    //no se muestran y se termina el borrado en silencio
    val alreadyInJar: List<SharedCookie> = emptyList(),
)

//resultado de aceptar un regalo
sealed interface GiftAcceptResult {
    val cookie: CookieModel

    //la galleta quedo en el jarro y el regalo se borro del backend
    data class Done(override val cookie: CookieModel) : GiftAcceptResult

    //la galleta quedo en el jarro pero el regalo sigue en el backend (se cayo la red): se termina al volver
    data class SavedPendingCleanup(override val cookie: CookieModel) : GiftAcceptResult
}

//reglas de los regalos y de la bandeja
object CookieGifts {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val EXPIRY_DAYS = 7
    const val EXPIRY_MS = EXPIRY_DAYS * DAY_MS

    fun expiresAt(sentAt: Long): Long = sentAt + EXPIRY_MS

    //la amistad usa los dos uid ordenados: los dos llegan al mismo documento
    fun friendshipId(a: String, b: String): String = if (a < b) "${a}_$b" else "${b}_$a"

    //una solicitud por pareja y direccion: reintentarla no la duplica
    fun requestId(fromUid: String, toUid: String): String = "${fromUid}_$toUid"

    //separa lo que se muestra, lo vencido y lo ya aceptado
    //acceptingIds: regalos que se estan aceptando ahora; siguen visibles (con el boton deshabilitado) hasta terminar
    fun splitInbox(gifts: List<SharedCookie>, cookieIdsInJar: Set<String>, acceptingIds: Set<String>, now: Long): InboxView {
        val alreadyInJar = gifts.filter { it.shareId in cookieIdsInJar && it.shareId !in acceptingIds }
        val rest = gifts.filter { it.shareId !in cookieIdsInJar || it.shareId in acceptingIds }
        val (expired, visible) = rest.partition { it.isExpired(now) && it.shareId !in acceptingIds }
        return InboxView(visible = visible.sortedByDescending { it.sentAt }, expired = expired, alreadyInJar = alreadyInJar)
    }

    //regalos que no se habian visto en esta visita (para el aviso en vivo)
    fun newArrivals(visible: List<SharedCookie>, seenIds: Set<String>): List<SharedCookie> = visible.filter { it.shareId !in seenIds }
}

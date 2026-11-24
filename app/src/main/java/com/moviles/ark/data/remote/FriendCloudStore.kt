package com.moviles.ark.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.remote.dtos.BlockedDto
import com.moviles.ark.data.remote.dtos.FriendRequestDto
import com.moviles.ark.data.remote.dtos.FriendshipDto
import com.moviles.ark.data.remote.dtos.PublicProfileDto
import com.moviles.ark.domain.models.CookieGifts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//aqui se usa firebase: amigos del cookie jar en firestore
//usernames/{username} -> {uid}, public_profiles/{uid} -> {username, avatarId}, friend_requests/{from_to},
//friendships/{uidMenor_uidMayor} y users/{uid}/blocked/{otroUid}
//las lecturas son Source.SERVER y las escrituras son transacciones: sin red fallan en vez de quedar en la cola de
//firestore (si quedaran en cola, "Nothing was changed" seria mentira y se aplicarian solas mas tarde)
class FriendCloudStore(private val firestore: FirebaseFirestore) {

    //resultado de reservar un username
    sealed interface ActivateOutcome {
        data object Created : ActivateOutcome
        //el username ya era de esta cuenta (un intento anterior si llego): no se escribe nada
        data object AlreadyMine : ActivateOutcome
        data object Taken : ActivateOutcome
    }

    suspend fun getProfile(uid: String): PublicProfileDto? = withContext(Dispatchers.IO) {
        try {
            val snapshot = profiles().document(uid).get(Source.SERVER).awaitOrConnectionLost()
            snapshot.toObject(PublicProfileDto::class.java)?.copy(uid = snapshot.id)
        } catch (e: Exception) {
            //quien te bloqueo no te deja leer su perfil: es como si no existiera
            if (e.isPermissionDenied()) null else throw e
        }
    }

    //uid del username exacto (get, nunca list); null si no existe o si su dueno te bloqueo
    suspend fun findUid(username: String): String? = withContext(Dispatchers.IO) {
        try {
            usernames().document(username).get(Source.SERVER).awaitOrConnectionLost().getString(FIELD_UID)
        } catch (e: Exception) {
            if (e.isPermissionDenied()) null else throw e
        }
    }

    //reserva el username y crea el perfil publico en una sola transaccion: si el doc del username ya existe es de otro
    suspend fun activate(uid: String, username: String, avatarId: String): ActivateOutcome = withContext(Dispatchers.IO) {
        val usernameRef = usernames().document(username)
        val profileRef = profiles().document(uid)
        try {
            firestore.runTransaction { transaction ->
                val owner = transaction.get(usernameRef)
                val ownerUid = owner.getString(FIELD_UID)
                when {
                    owner.exists() && ownerUid == uid -> ActivateOutcome.AlreadyMine
                    owner.exists() -> ActivateOutcome.Taken
                    else -> {
                        transaction.set(usernameRef, mapOf(FIELD_UID to uid))
                        transaction.set(profileRef, PublicProfileDto(username = username, avatarId = avatarId))
                        ActivateOutcome.Created
                    }
                }
            }.awaitOrConnectionLost()
        } catch (e: Exception) {
            //el dueno de ese username te bloqueo (las reglas no dejan leerlo): para ti esta tomado
            if (e.isPermissionDenied()) ActivateOutcome.Taken else throw e
        }
    }

    //borra el perfil publico y su username juntos
    suspend fun deactivate(uid: String) = withContext(Dispatchers.IO) {
        val profileRef = profiles().document(uid)
        firestore.runTransaction { transaction ->
            val profile = transaction.get(profileRef)
            val username = profile.getString(FIELD_USERNAME)
            if (profile.exists() && username != null) {
                val usernameRef = usernames().document(username)
                val owner = transaction.get(usernameRef)
                if (owner.getString(FIELD_UID) == uid) transaction.delete(usernameRef)
                transaction.delete(profileRef)
            }
        }.awaitOrConnectionLost()
        Unit
    }

    suspend fun updateAvatar(uid: String, avatarId: String) = withContext(Dispatchers.IO) {
        val profileRef = profiles().document(uid)
        firestore.runTransaction { transaction ->
            if (transaction.get(profileRef).exists()) transaction.update(profileRef, FIELD_AVATAR_ID, avatarId)
        }.awaitOrConnectionLost()
        Unit
    }

    suspend fun incomingRequests(uid: String): List<FriendRequestDto> = withContext(Dispatchers.IO) {
        requests().whereEqualTo(FIELD_TO, uid).get(Source.SERVER).awaitOrConnectionLost()
            .documents.mapNotNull { it.toObject(FriendRequestDto::class.java) }
    }

    suspend fun requestExists(fromUid: String, toUid: String): Boolean = withContext(Dispatchers.IO) {
        requests().document(CookieGifts.requestId(fromUid, toUid)).get(Source.SERVER).awaitOrConnectionLost().exists()
    }

    //id fijo from_to: si ya existe no se vuelve a escribir (reintentar no duplica)
    suspend fun sendRequest(fromUid: String, toUid: String, fromUsername: String, now: Long) = withContext(Dispatchers.IO) {
        val ref = requests().document(CookieGifts.requestId(fromUid, toUid))
        firestore.runTransaction { transaction ->
            if (!transaction.get(ref).exists()) {
                transaction.set(ref, FriendRequestDto(from = fromUid, to = toUid, fromUsername = fromUsername, createdAt = now))
            }
        }.awaitOrConnectionLost()
        Unit
    }

    //borra la solicitud y crea la amistad en la misma transaccion; false si la solicitud ya no estaba
    suspend fun acceptRequest(myUid: String, fromUid: String, now: Long): Boolean = withContext(Dispatchers.IO) {
        val requestRef = requests().document(CookieGifts.requestId(fromUid, myUid))
        val friendshipRef = friendships().document(CookieGifts.friendshipId(myUid, fromUid))
        firestore.runTransaction { transaction ->
            val request = transaction.get(requestRef)
            val friendship = transaction.get(friendshipRef)
            if (!request.exists()) return@runTransaction friendship.exists()
            transaction.delete(requestRef)
            if (!friendship.exists()) {
                transaction.set(friendshipRef, FriendshipDto(members = listOf(myUid, fromUid).sorted(), since = now))
            }
            true
        }.awaitOrConnectionLost()
    }

    suspend fun deleteRequest(fromUid: String, toUid: String) = deleteIfExists(requests().document(CookieGifts.requestId(fromUid, toUid)))

    suspend fun friendships(uid: String): List<FriendshipDto> = withContext(Dispatchers.IO) {
        friendships().whereArrayContains(FIELD_MEMBERS, uid).get(Source.SERVER).awaitOrConnectionLost()
            .documents.mapNotNull { it.toObject(FriendshipDto::class.java) }
    }

    suspend fun friendshipExists(a: String, b: String): Boolean = withContext(Dispatchers.IO) {
        friendships().document(CookieGifts.friendshipId(a, b)).get(Source.SERVER).awaitOrConnectionLost().exists()
    }

    suspend fun removeFriendship(a: String, b: String) = deleteIfExists(friendships().document(CookieGifts.friendshipId(a, b)))

    suspend fun blocked(uid: String): List<BlockedDto> = withContext(Dispatchers.IO) {
        blockedOf(uid).get(Source.SERVER).awaitOrConnectionLost()
            .documents.mapNotNull { doc -> doc.toObject(BlockedDto::class.java)?.copy(uid = doc.id) }
    }

    suspend fun isBlockedByMe(myUid: String, otherUid: String): Boolean = withContext(Dispatchers.IO) {
        blockedOf(myUid).document(otherUid).get(Source.SERVER).awaitOrConnectionLost().exists()
    }

    //bloquear: se guarda el bloqueo y se borran la amistad y las solicitudes entre los dos, todo junto
    suspend fun block(myUid: String, otherUid: String, otherUsername: String, now: Long) = withContext(Dispatchers.IO) {
        val blockedRef = blockedOf(myUid).document(otherUid)
        val related = listOf(
            friendships().document(CookieGifts.friendshipId(myUid, otherUid)),
            requests().document(CookieGifts.requestId(otherUid, myUid)),
            requests().document(CookieGifts.requestId(myUid, otherUid)),
        )
        firestore.runTransaction { transaction ->
            //las lecturas van antes que las escrituras; solo se borra lo que existe (las reglas revisan el documento)
            val existing = related.filter { transaction.get(it).exists() }
            transaction.set(blockedRef, BlockedDto(username = otherUsername, blockedAt = now))
            existing.forEach { transaction.delete(it) }
        }.awaitOrConnectionLost()
        Unit
    }

    suspend fun unblock(myUid: String, otherUid: String) = withContext(Dispatchers.IO) {
        val ref = blockedOf(myUid).document(otherUid)
        firestore.runTransaction { transaction -> transaction.delete(ref) }.awaitOrConnectionLost()
        Unit
    }

    //borra un documento solo si existe, en una transaccion (sin red falla en vez de quedar en cola)
    private suspend fun deleteIfExists(ref: DocumentReference) = withContext(Dispatchers.IO) {
        firestore.runTransaction { transaction ->
            if (transaction.get(ref).exists()) transaction.delete(ref)
        }.awaitOrConnectionLost()
        Unit
    }

    private fun usernames() = firestore.collection(USERNAMES)
    private fun profiles() = firestore.collection(PUBLIC_PROFILES)
    private fun requests() = firestore.collection(FRIEND_REQUESTS)
    private fun friendships() = firestore.collection(FRIENDSHIPS)
    private fun blockedOf(uid: String) = firestore.collection(USERS).document(uid).collection(BLOCKED)

    companion object {
        private const val USERNAMES = "usernames"
        private const val PUBLIC_PROFILES = "public_profiles"
        private const val FRIEND_REQUESTS = "friend_requests"
        private const val FRIENDSHIPS = "friendships"
        private const val USERS = "users"
        private const val BLOCKED = "blocked"
        private const val FIELD_UID = "uid"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_AVATAR_ID = "avatarId"
        private const val FIELD_TO = "to"
        private const val FIELD_MEMBERS = "members"
    }
}

package com.moviles.ark.data.repositories

import android.util.Log
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.FriendCloudStore
import com.moviles.ark.data.remote.dtos.PublicProfileDto
import com.moviles.ark.domain.exceptions.UsernameNotFoundException
import com.moviles.ark.domain.exceptions.UsernameTakenException
import com.moviles.ark.domain.models.BlockedUser
import com.moviles.ark.domain.models.FoundUser
import com.moviles.ark.domain.models.Friend
import com.moviles.ark.domain.models.FriendRelation
import com.moviles.ark.domain.models.FriendRequest
import com.moviles.ark.domain.models.FriendsOverview
import com.moviles.ark.domain.models.PublicProfile
import com.moviles.ark.domain.models.Usernames
import com.moviles.ark.domain.repositories.FriendRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

//amigos del cookie jar: solo orquesta; firestore lo toca FriendCloudStore
//nada se guarda en el telefono salvo el perfil propio en memoria (para firmar los regalos con el username)
class FriendRepositoryImpl(
    private val cloud: FriendCloudStore,
    //uid del usuario con sesion; null sin sesion
    private val userIdProvider: () -> String?,
    //avatar de la lista que eligio el usuario (el perfil publico nunca lleva su foto)
    private val avatarIdProvider: suspend () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) : FriendRepository {

    //perfil publico propio leido del servidor en esta sesion de la app
    @Volatile
    private var cachedProfile: PublicProfile? = null
    @Volatile
    private var cachedProfileUid: String? = null

    //las cuatro lecturas van en paralelo (async en Dispatchers.IO): la pantalla espera la mas lenta, no la suma
    override suspend fun loadOverview(): Result<FriendsOverview> = social("loadOverview") {
        val uid = requireUser()
        coroutineScope {
            val profile = async { cloud.getProfile(uid) }
            val requests = async { cloud.incomingRequests(uid) }
            val friends = async { friendsOf(uid) }
            val blocked = async { cloud.blocked(uid) }
            val myProfile = profile.await()?.toModel()?.let { syncAvatar(uid, it) }
            remember(uid, myProfile)
            FriendsOverview(
                myProfile = myProfile,
                requests = requests.await()
                    .map { FriendRequest(fromUid = it.from, fromUsername = it.fromUsername, createdAt = it.createdAt) }
                    .sortedByDescending { it.createdAt },
                friends = friends.await(),
                blocked = blocked.await().map { BlockedUser(uid = it.uid, username = it.username, blockedAt = it.blockedAt) },
            )
        }
    }

    override suspend fun activate(username: String): Result<PublicProfile> = social("activate") {
        val uid = requireUser()
        Usernames.error(username)?.let { error(it) }
        val name = Usernames.normalize(username)
        val avatarId = avatarIdProvider()
        when (cloud.activate(uid, name, avatarId)) {
            FriendCloudStore.ActivateOutcome.Taken -> throw UsernameTakenException(name)
            else -> Unit
        }
        val profile = PublicProfile(uid = uid, username = name, avatarId = avatarId)
        remember(uid, profile)
        Log.d(TAG, "friends on as @$name")
        profile
    }

    override suspend fun deactivate(): Result<Unit> = social("deactivate") {
        val uid = requireUser()
        cloud.deactivate(uid)
        remember(uid, null)
        Log.d(TAG, "friends off: public profile and username deleted")
    }

    override suspend fun findByUsername(username: String): Result<FoundUser> = social("findByUsername") {
        val uid = requireUser()
        val name = Usernames.normalize(username)
        if (Usernames.error(name) != null) throw UsernameNotFoundException()
        val otherUid = cloud.findUid(name) ?: throw UsernameNotFoundException()
        val profile = cloud.getProfile(otherUid)?.toModel() ?: throw UsernameNotFoundException()
        if (otherUid == uid) return@social FoundUser(profile, FriendRelation.YOU)
        //la relacion se arma con cuatro lecturas en paralelo
        val relation = coroutineScope {
            val blockedByMe = async { cloud.isBlockedByMe(uid, otherUid) }
            val friends = async { cloud.friendshipExists(uid, otherUid) }
            val requested = async { cloud.requestExists(uid, otherUid) }
            val theyAsked = async { cloud.requestExists(otherUid, uid) }
            when {
                blockedByMe.await() -> FriendRelation.BLOCKED_BY_ME
                friends.await() -> FriendRelation.FRIENDS
                theyAsked.await() -> FriendRelation.WANTS_TO_BE_FRIENDS
                requested.await() -> FriendRelation.REQUESTED
                else -> FriendRelation.NONE
            }
        }
        FoundUser(profile, relation)
    }

    override suspend fun sendRequest(to: PublicProfile): Result<Unit> = social("sendRequest") {
        val uid = requireUser()
        val myUsername = myProfile(uid)?.username ?: error("Turn on friends first")
        cloud.sendRequest(uid, to.uid, myUsername, now())
    }

    override suspend fun acceptRequest(fromUid: String): Result<Unit> = social("acceptRequest") {
        cloud.acceptRequest(requireUser(), fromUid, now())
        Unit
    }

    //rechazo silencioso: solo se borra la solicitud
    override suspend fun declineRequest(fromUid: String): Result<Unit> = social("declineRequest") {
        cloud.deleteRequest(fromUid, requireUser())
    }

    override suspend fun removeFriend(uid: String): Result<Unit> = social("removeFriend") {
        cloud.removeFriendship(requireUser(), uid)
    }

    override suspend fun block(uid: String, username: String): Result<Unit> = social("block") {
        cloud.block(requireUser(), uid, username, now())
    }

    override suspend fun unblock(uid: String): Result<Unit> = social("unblock") {
        cloud.unblock(requireUser(), uid)
    }

    override suspend fun listFriends(): Result<List<Friend>> = social("listFriends") {
        val uid = requireUser()
        //el regalo se firma con el username: se lee de una vez si aun no se tiene
        coroutineScope {
            val profile = async { myProfile(uid) }
            val friends = async { friendsOf(uid) }
            profile.await()
            friends.await()
        }
    }

    override suspend fun myUsername(): String? {
        val uid = userIdProvider() ?: return null
        return runCatching { myProfile(uid)?.username }.getOrNull()
    }

    //amistades del usuario y el perfil publico de cada amigo, todos los perfiles en paralelo
    //un amigo que desactivo amigos (sin perfil) no aparece: no se le puede regalar
    private suspend fun friendsOf(uid: String): List<Friend> = coroutineScope {
        cloud.friendships(uid).map { friendship ->
            async {
                val otherUid = friendship.members.firstOrNull { it != uid } ?: return@async null
                cloud.getProfile(otherUid)?.let { Friend(uid = otherUid, username = it.username, avatarId = it.avatarId, since = friendship.since) }
            }
        }.awaitAll().filterNotNull().sortedBy { it.username }
    }

    //perfil propio: de memoria si ya se leyo para esta cuenta, si no del servidor
    private suspend fun myProfile(uid: String): PublicProfile? {
        if (cachedProfileUid == uid) cachedProfile?.let { return it }
        val profile = cloud.getProfile(uid)?.toModel()
        remember(uid, profile)
        return profile
    }

    //si el usuario cambio su avatar despues de activar amigos, el perfil publico se pone al dia
    private suspend fun syncAvatar(uid: String, profile: PublicProfile): PublicProfile {
        val current = avatarIdProvider()
        if (current == profile.avatarId) return profile
        return runCatching { cloud.updateAvatar(uid, current) }.fold(
            onSuccess = { profile.copy(avatarId = current) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                profile
            }
        )
    }

    private fun remember(uid: String, profile: PublicProfile?) {
        cachedProfileUid = uid
        cachedProfile = profile
    }

    private fun requireUser(): String = userIdProvider() ?: error("No user is logged in")

    private fun PublicProfileDto.toModel() = PublicProfile(uid = uid, username = username, avatarId = avatarId)

    //cada operacion corre en Dispatchers.IO y devuelve Result; la cancelacion (salir de la pantalla) no se trata como error
    private suspend fun <T> social(action: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName} ${e.message}")
            if (e !is UsernameTakenException && e !is UsernameNotFoundException) {
                CrashlyticsHelper.logNonFatal(componentName = "FriendRepository", action = action, throwable = e)
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CookieFriends"
    }
}

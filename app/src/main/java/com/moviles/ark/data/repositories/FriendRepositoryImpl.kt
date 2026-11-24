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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    //perfil publico propio leido del servidor en esta sesion de la app, y de que cuenta es
    @Volatile
    private var cachedProfile: PublicProfile? = null
    @Volatile
    private var cachedProfileUid: String? = null

    //las cuatro lecturas van en paralelo (async en Dispatchers.IO): la pantalla espera la mas lenta, no la suma
    override suspend fun loadOverview(): Result<FriendsOverview> = runSafely("loadOverview") {
        val uid = requireUser()
        coroutineScope {
            val profileCall = async { cloud.getProfile(uid) }
            val requestsCall = async { cloud.incomingRequests(uid) }
            val friendsCall = async { friendsOf(uid) }
            val blockedCall = async { cloud.blocked(uid) }

            val myProfile = loadMyProfileWithAvatar(uid, profileCall.await())
            remember(uid, myProfile)

            val requests = mutableListOf<FriendRequest>()
            for (request in requestsCall.await()) {
                requests.add(FriendRequest(fromUid = request.from, fromUsername = request.fromUsername, createdAt = request.createdAt))
            }
            val blockedUsers = mutableListOf<BlockedUser>()
            for (blocked in blockedCall.await()) {
                blockedUsers.add(BlockedUser(uid = blocked.uid, username = blocked.username, blockedAt = blocked.blockedAt))
            }

            FriendsOverview(
                myProfile = myProfile,
                requests = requests.sortedByDescending { it.createdAt },
                friends = friendsCall.await(),
                blocked = blockedUsers,
            )
        }
    }

    override suspend fun activate(username: String): Result<PublicProfile> = runSafely("activate") {
        val uid = requireUser()
        val usernameError = Usernames.error(username)
        if (usernameError != null) error(usernameError)

        val name = Usernames.normalize(username)
        val avatarId = avatarIdProvider()
        val outcome = cloud.activate(uid, name, avatarId)
        if (outcome == FriendCloudStore.ActivateOutcome.TAKEN) {
            throw UsernameTakenException(name)
        }

        val profile = PublicProfile(uid = uid, username = name, avatarId = avatarId)
        remember(uid, profile)
        Log.d(TAG, "friends on as @$name")
        profile
    }

    override suspend fun deactivate(): Result<Unit> = runSafely("deactivate") {
        val uid = requireUser()
        cloud.deactivate(uid)
        remember(uid, null)
        Log.d(TAG, "friends off: public profile and username deleted")
    }

    override suspend fun findByUsername(username: String): Result<FoundUser> = runSafely("findByUsername") {
        val uid = requireUser()
        val name = Usernames.normalize(username)
        //un username invalido no puede existir: no se pregunta a la nube
        if (Usernames.error(name) != null) throw UsernameNotFoundException()

        val otherUid = cloud.findUid(name) ?: throw UsernameNotFoundException()
        val otherProfileDto = cloud.getProfile(otherUid) ?: throw UsernameNotFoundException()
        val otherProfile = otherProfileDto.toModel()

        if (otherUid == uid) {
            FoundUser(otherProfile, FriendRelation.YOU)
        } else {
            val relation = relationWith(uid, otherUid)
            FoundUser(otherProfile, relation)
        }
    }

    override suspend fun sendRequest(to: PublicProfile): Result<Unit> = runSafely("sendRequest") {
        val uid = requireUser()
        val myProfile = myProfile(uid) ?: error("Turn on friends first")
        cloud.sendRequest(uid, to.uid, myProfile.username, now())
    }

    override suspend fun acceptRequest(fromUid: String): Result<Unit> = runSafely("acceptRequest") {
        val uid = requireUser()
        cloud.acceptRequest(uid, fromUid, now())
        Unit
    }

    //rechazo silencioso: solo se borra la solicitud
    override suspend fun declineRequest(fromUid: String): Result<Unit> = runSafely("declineRequest") {
        val uid = requireUser()
        cloud.deleteRequest(fromUid, uid)
    }

    override suspend fun removeFriend(uid: String): Result<Unit> = runSafely("removeFriend") {
        val myUid = requireUser()
        cloud.removeFriendship(myUid, uid)
    }

    override suspend fun block(uid: String, username: String): Result<Unit> = runSafely("block") {
        val myUid = requireUser()
        cloud.block(myUid, uid, username, now())
    }

    override suspend fun unblock(uid: String): Result<Unit> = runSafely("unblock") {
        val myUid = requireUser()
        cloud.unblock(myUid, uid)
    }

    override suspend fun listFriends(): Result<List<Friend>> = runSafely("listFriends") {
        val uid = requireUser()
        //el regalo se firma con el username: se lee de una vez (en paralelo) si aun no se tiene
        coroutineScope {
            val profileCall = async { myProfile(uid) }
            val friendsCall = async { friendsOf(uid) }
            profileCall.await()
            friendsCall.await()
        }
    }

    override suspend fun myUsername(): String? {
        val uid = userIdProvider() ?: return null
        val profile = runCatching { myProfile(uid) }.getOrNull()
        return profile?.username
    }

    //relacion con otra persona: cuatro lecturas en paralelo
    private suspend fun relationWith(uid: String, otherUid: String): FriendRelation = coroutineScope {
        val blockedByMeCall = async { cloud.isBlockedByMe(uid, otherUid) }
        val friendsCall = async { cloud.friendshipExists(uid, otherUid) }
        val iAskedCall = async { cloud.requestExists(uid, otherUid) }
        val theyAskedCall = async { cloud.requestExists(otherUid, uid) }

        //el orden importa: un bloqueo pesa mas que una amistad, y una amistad mas que una solicitud
        if (blockedByMeCall.await()) {
            FriendRelation.BLOCKED_BY_ME
        } else if (friendsCall.await()) {
            FriendRelation.FRIENDS
        } else if (theyAskedCall.await()) {
            FriendRelation.WANTS_TO_BE_FRIENDS
        } else if (iAskedCall.await()) {
            FriendRelation.REQUESTED
        } else {
            FriendRelation.NONE
        }
    }

    //amistades del usuario y el perfil publico de cada amigo, todos los perfiles en paralelo
    //un amigo que desactivo amigos (sin perfil) no aparece: no se le puede regalar
    private suspend fun friendsOf(uid: String): List<Friend> = coroutineScope {
        val friendships = cloud.friendships(uid)

        //primero se piden todos los perfiles a la vez
        val friendCalls = mutableListOf<Deferred<Friend?>>()
        for (friendship in friendships) {
            val call = async { friendFromFriendship(uid, friendship.members, friendship.since) }
            friendCalls.add(call)
        }

        //despues se espera cada respuesta
        val friends = mutableListOf<Friend>()
        for (call in friendCalls) {
            val friend = call.await()
            if (friend != null) friends.add(friend)
        }
        friends.sortedBy { it.username }
    }

    //el otro miembro de la amistad con su perfil publico; null si ya no tiene perfil
    private suspend fun friendFromFriendship(uid: String, members: List<String>, since: Long): Friend? {
        val otherUid = members.firstOrNull { it != uid } ?: return null
        val profile = cloud.getProfile(otherUid) ?: return null
        return Friend(uid = otherUid, username = profile.username, avatarId = profile.avatarId, since = since)
    }

    //perfil propio: de memoria si ya se leyo para esta cuenta, si no del servidor
    private suspend fun myProfile(uid: String): PublicProfile? {
        val profileInMemory = cachedProfile
        if (cachedProfileUid == uid && profileInMemory != null) {
            return profileInMemory
        }
        val profileDto = cloud.getProfile(uid)
        val profile = profileDto?.toModel()
        remember(uid, profile)
        return profile
    }

    //perfil propio de la pantalla de amigos; null si no ha activado amigos
    private suspend fun loadMyProfileWithAvatar(uid: String, profileDto: PublicProfileDto?): PublicProfile? {
        if (profileDto == null) return null
        return syncAvatar(uid, profileDto.toModel())
    }

    //si el usuario cambio su avatar despues de activar amigos, el perfil publico se pone al dia
    //si falla no importa: se muestra el avatar viejo y se intenta otra vez la proxima vez
    private suspend fun syncAvatar(uid: String, profile: PublicProfile): PublicProfile {
        val currentAvatarId = avatarIdProvider()
        if (currentAvatarId == profile.avatarId) return profile
        try {
            cloud.updateAvatar(uid, currentAvatarId)
            return profile.copy(avatarId = currentAvatarId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return profile
        }
    }

    private fun remember(uid: String, profile: PublicProfile?) {
        cachedProfileUid = uid
        cachedProfile = profile
    }

    private fun requireUser(): String = userIdProvider() ?: error("No user is logged in")

    private fun PublicProfileDto.toModel() = PublicProfile(uid = uid, username = username, avatarId = avatarId)

    //cada operacion corre en Dispatchers.IO y devuelve Result; la cancelacion (salir de la pantalla) no se trata como error
    //los errores esperados (username tomado o que no existe) no se mandan a crashlytics
    private suspend fun <T> runSafely(action: String, block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName} ${e.message}")
            val isExpected = e is UsernameTakenException || e is UsernameNotFoundException
            if (!isExpected) {
                CrashlyticsHelper.logNonFatal(componentName = "FriendRepository", action = action, throwable = e)
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CookieFriends"
    }
}

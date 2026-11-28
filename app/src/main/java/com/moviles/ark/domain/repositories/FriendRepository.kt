package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.FoundUser
import com.moviles.ark.domain.models.Friend
import com.moviles.ark.domain.models.FriendsOverview
import com.moviles.ark.domain.models.PublicProfile

//amigos del cookie jar (solo 18+): username unico, solicitudes, amistades y bloqueos
//todo vive en el backend y requiere red: cada llamada tiene timeout y, si la red se cae, falla con ConnectionLostException
//sin quedar en cola (no se cambia nada)
interface FriendRepository {
    //perfil publico, solicitudes que llegaron, amigos y bloqueados, todo del servidor
    suspend fun loadOverview(): Result<FriendsOverview>

    //activar amigos: reserva el username y crea el perfil publico (solo username y avatar) en una transaccion
    //falla con UsernameTakenException si es de otra cuenta
    suspend fun activate(username: String): Result<PublicProfile>

    //desactivar amigos: borra el perfil publico y el username
    suspend fun deactivate(): Result<Unit>

    //busca por username exacto; UsernameNotFoundException si no existe (o si esa persona te bloqueo)
    suspend fun findByUsername(username: String): Result<FoundUser>

    suspend fun sendRequest(to: PublicProfile): Result<Unit>

    //aceptar crea la amistad y borra la solicitud; rechazar solo la borra (quien la envio no se entera)
    suspend fun acceptRequest(fromUid: String): Result<Unit>

    suspend fun declineRequest(fromUid: String): Result<Unit>

    suspend fun removeFriend(uid: String): Result<Unit>

    //bloquear tambien borra la amistad y las solicitudes entre los dos
    suspend fun block(uid: String, username: String): Result<Unit>

    suspend fun unblock(uid: String): Result<Unit>

    //amigos para elegir a quien regalar
    suspend fun listFriends(): Result<List<Friend>>

    //username propio ya leido del servidor en esta sesion; null si no ha activado amigos o no se ha leido
    suspend fun myUsername(): String?
}

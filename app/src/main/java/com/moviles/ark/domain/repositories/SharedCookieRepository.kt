package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.Friend
import com.moviles.ark.domain.models.GiftAcceptResult
import com.moviles.ark.domain.models.InboxSnapshot
import com.moviles.ark.domain.models.SentGift
import com.moviles.ark.domain.models.SharedCookie
import kotlinx.coroutines.flow.Flow

//regalos del cookie jar: copia de una galleta para un amigo
//la bandeja vive en el backend (no se guarda en el telefono); la lista de regalos enviados vive solo en room del remitente
interface SharedCookieRepository {
    //listener en vivo de los regalos para el usuario: al empezar entrega todo lo pendiente y despues cada regalo nuevo
    //lo que viene de la cache de firestore (sin red) se entrega como Offline
    fun observeInbox(): Flow<InboxSnapshot>

    //regalos enviados desde este telefono (room)
    fun observeSentGifts(): Flow<List<SentGift>>

    //primero la foto y despues el documento con una transaccion: o llego o no llego, nada queda en cola
    suspend fun sendGift(cookieId: String, friend: Friend): Result<SentGift>

    //true si se cancelo; false si el regalo ya no existe (respuesta neutra)
    suspend fun cancelGift(shareId: String): Result<Boolean>

    suspend fun declineGift(gift: SharedCookie): Result<Unit>

    //1 baja la foto, 2 la guarda y crea la galleta en room (onSaved), 3 borra el regalo del backend, 4 el sync la sube
    suspend fun acceptGift(gift: SharedCookie, onSaved: () -> Unit = {}): Result<GiftAcceptResult>

    //paso 3 de un regalo que ya quedo en el jarro pero seguia en el backend (se cayo la red entre el 2 y el 3)
    suspend fun finishAccepted(gift: SharedCookie): Result<Unit>

    //borra del backend los regalos vencidos (los puede borrar el destinatario)
    suspend fun deleteExpired(gifts: List<SharedCookie>)

    //foto huerfana de shared/: se borra solo si su regalo ya no existe; lo llama el worker de borrado
    suspend fun deleteOrphanImage(userId: String, shareId: String): Result<Unit>
}
